package com.radoslove.interslavic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * The prediction model (android_PLAN.md M2). Loads `assets/main_isv.combined`
 * — the same wordlist that feeds HeliBoard's swipe dictionary, 248 845 forms in
 * the format `word=X,f=N` — into memory and answers prefix queries ranked by
 * frequency.
 *
 * Android has no keyboard memory cap the way an iOS extension does, so this
 * loads the whole list on purpose: M2 is where we learn how large a model is
 * actually *useful* before iOS forces a budget on us. Loading happens once, on
 * a background thread; until it finishes, [suggest] simply returns nothing.
 *
 * The list is standard orthography only (č š ž ě), matching every other surface.
 */
object Dictionary {

    private class Entry(val word: String, val freq: Int)

    @Volatile private var ready = false
    private var entries: Array<Entry> = emptyArray()

    /** Idempotent; the first call kicks off the background load. */
    fun ensureLoaded(context: Context) {
        if (ready || loading) return
        loading = true
        val app = context.applicationContext
        Thread({ load(app) }, "isv-dict-load").apply { isDaemon = true; start() }
    }

    @Volatile private var loading = false

    private fun load(context: Context) {
        try {
            val list = ArrayList<Entry>(250_000)
            context.assets.open("main_isv.combined").use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { br ->
                    var line = br.readLine()
                    while (line != null) {
                        val s = line.trim()
                        if (s.startsWith("word=")) {
                            val fi = s.indexOf(",f=")
                            if (fi > 5) {
                                val w = s.substring(5, fi)
                                val f = s.substring(fi + 3).toIntOrNull() ?: 0
                                if (w.isNotEmpty()) list.add(Entry(w, f))
                            }
                        }
                        line = br.readLine()
                    }
                }
            }
            list.sortBy { it.word }
            entries = list.toTypedArray()
            ready = true
        } catch (_: Throwable) {
            // A missing/broken asset just means no predictions — never crash typing.
            ready = false
        } finally {
            loading = false
        }
    }

    /**
     * Up to [n] words that start with [prefix] (lowercased by the caller),
     * highest frequency first. Empty while the model is still loading.
     */
    fun suggest(prefix: String, n: Int, boost: (String) -> Int = { 0 }): List<String> {
        if (!ready || prefix.isEmpty()) return emptyList()
        val arr = entries
        val lo = lowerBound(arr, prefix)
        // Rank by a composite: frequency dominates, but a whole inflected
        // paradigm shares ONE frequency (the lemma's), so ties are common —
        // and breaking them alphabetically buried the everyday form (možemo)
        // under an aorist (možehmo). Prefer the SHORTER form on a tie; the
        // common present-tense forms are shorter than the aorist/imperfect
        // ones. (64 > any word length, so frequency still wins outright.)
        val topWords = arrayOfNulls<String>(n)
        val topScore = IntArray(n) { Int.MIN_VALUE }
        var scanned = 0
        var i = lo
        while (i < arr.size && scanned < 6000) {
            val e = arr[i]
            if (!e.word.startsWith(prefix)) break
            // + adaptive usage: words the user actually uses climb the ranking.
            val score = e.freq * 64 - e.word.length + boost(e.word)
            if (score > topScore[n - 1]) {
                var j = n - 1
                while (j > 0 && topScore[j - 1] < score) {
                    topScore[j] = topScore[j - 1]
                    topWords[j] = topWords[j - 1]
                    j--
                }
                topScore[j] = score
                topWords[j] = e.word
            }
            i++
            scanned++
        }
        val out = ArrayList<String>(n)
        for (w in topWords) if (w != null) out.add(w)
        return out
    }

    /** Whether the background load has finished. */
    fun isReady(): Boolean = ready

    // ---- Swipe decoding (M4) --------------------------------------------

    /** Fold an accent to its base key: č->c š->s ž->z ě->e (others unchanged). */
    private fun foldBase(c: Char): Char = when (c) {
        'č' -> 'c'; 'š' -> 's'; 'ž' -> 'z'; 'ě' -> 'e'
        else -> c
    }

    /**
     * Geometry glide decoder. The caller (KeyboardView, which has the key
     * positions) passes a [score] function that, for a folded candidate word,
     * returns how well its letters lie along the finger's actual path — higher
     * is better, Double.NEGATIVE_INFINITY means "a letter never came near the
     * path, reject". This replaces the brittle ordered-key-crossing model:
     * on a long diagonal the finger crosses keys OUT of the word's letter order,
     * so order can't be trusted — distance-to-path can.
     *
     * We still anchor the FIRST letter to the start of the path to keep the
     * candidate set small, but to the few keys NEAREST that start rather than
     * to one. Anchoring to a single key silently excluded the right word:
     * `p` sits directly above `l`, so a glide for `pisati` begun a few pixels
     * low anchored on `l` and only `l...` words were ever scored - `pisati`
     * did not lose to `ležati`, it never entered the race. Geometry still
     * decides between the survivors. Frequency is only a tiebreak between words
     * that fit the path about equally well. Accents/digraphs match on their base
     * letters; the real accented word is returned.
     */
    fun decodeSwipeGeo(
        firstKeys: Set<Char>,
        lastKeys: Set<Char>,
        maxLen: Int,
        n: Int,
        score: (String) -> Double,
        boost: (String) -> Double = { 0.0 },
    ): List<String> {
        if (!ready) return emptyList()
        val topWords = arrayOfNulls<String>(n)
        val topScore = DoubleArray(n) { Double.NEGATIVE_INFINITY }
        for (e in entries) {
            val w = e.word
            if (w.length < 2 || w.length > maxLen) continue
            if (foldBase(w.first()) !in firstKeys) continue
            // Cheap gate before the costly geometry: a glide ends ON its last
            // letter, so a word ending elsewhere cannot be what was drawn.
            if (lastKeys.isNotEmpty() && foldBase(w.last()) !in lastKeys) continue
            val folded = buildString { for (c in w) append(foldBase(c)) }
            val geo = score(folded)
            if (geo == Double.NEGATIVE_INFINITY) continue
            // geo is negative px (0 = letters sit exactly on the path); a small
            // frequency nudge breaks ties, and adaptive usage nudges harder so a
            // word you keep swiping wins the near-ties geometry cannot resolve.
            val s = geo + Math.log((e.freq + 1).toDouble()) * 2.5 + boost(w)
            if (s > topScore[n - 1]) {
                var j = n - 1
                while (j > 0 && topScore[j - 1] < s) {
                    topScore[j] = topScore[j - 1]; topWords[j] = topWords[j - 1]; j--
                }
                topScore[j] = s; topWords[j] = w
            }
        }
        return topWords.filterNotNull()
    }

    /** Exact membership test (binary search). Used to decide MISS for M3. */
    fun contains(word: String): Boolean {
        if (!ready) return false
        val arr = entries
        val i = lowerBound(arr, word)
        return i < arr.size && arr[i].word == word
    }

    /** First index whose word is >= prefix (binary search). */
    private fun lowerBound(arr: Array<Entry>, prefix: String): Int {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].word < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
