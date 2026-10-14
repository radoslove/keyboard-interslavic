package com.radoslove.interslavic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * The prediction model (android_PLAN.md M2). Loads `assets/main_<lang>.combined`
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

    /** Asset per language; see [Lang]. */
    private fun asset(lang: String) = "main_$lang.combined"

    /** Every language loaded so far. A language is read the first time it is
     *  used and then kept, so 🌐 switches back without a second load. */
    private val loaded = HashMap<String, Array<Entry>>()
    private val loading = HashSet<String>()

    @Volatile private var current = Lang.MAIN
    @Volatile private var ready = false
    @Volatile private var entries: Array<Entry> = emptyArray()

    /** Idempotent; makes the keyboard's current language the active list and
     *  kicks off its background load the first time. */
    fun ensureLoaded(context: Context) = use(context, Lang.current(context))

    /** Switch the active list to [lang]; until it has loaded, [suggest] is empty. */
    @Synchronized
    fun use(context: Context, lang: String) {
        current = lang
        val have = loaded[lang]
        if (have != null) { entries = have; ready = true; return }
        ready = false
        entries = emptyArray()
        if (!loading.add(lang)) return
        val app = context.applicationContext
        Thread({ load(app, lang) }, "dict-load-$lang").apply { isDaemon = true; start() }
    }

    private fun load(context: Context, lang: String) {
        var arr: Array<Entry>? = null
        try {
            val list = ArrayList<Entry>(if (lang == Lang.PL) 410_000 else 250_000)
            context.assets.open(asset(lang)).use { stream ->
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
            val known = HashSet<String>(list.size * 2)
            for (e in list) known.add(e.word)
            for (w in UserWords.all(context, lang)) {
                if (known.add(w)) list.add(Entry(w, USER_FREQ))
            }
            list.sortBy { it.word }
            arr = list.toTypedArray()
        } catch (_: Throwable) {
            // A missing/broken asset just means no predictions — never crash typing.
        }
        synchronized(this) {
            loading.remove(lang)
            if (arr != null) {
                loaded[lang] = arr
                if (current == lang) { entries = arr; ready = true }
            }
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

    /** Fold a held letter to the key it lives on: č->c ž->z ě->e, and the
     *  Polish ą ć ę ł ń ó ś ź ż likewise - a glide over `żółw` crosses z o l w. */
    private fun foldBase(c: Char): Char = when (c) {
        'č', 'ć' -> 'c'; 'š', 'ś' -> 's'; 'ž', 'ż', 'ź' -> 'z'; 'ě', 'ę', 'é', 'è', 'ê' -> 'e'
        'ą', 'à' -> 'a'; 'ł' -> 'l'; 'ń' -> 'n'; 'ó', 'ò', 'ô' -> 'o'
        // Norwegian: ä and ö are held on æ and ø, which are keys of their own.
        'ä' -> 'æ'; 'ö' -> 'ø'
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
        coarse: (String) -> Double,
        fine: (String) -> Double,
        boost: (String) -> Double = { 0.0 },
    ): List<String> {
        if (!ready) return emptyList()

        // PASS 1 - cheap. Every word that survives the letter gates gets a
        // low-resolution shape comparison. This is where the volume is: a glide
        // for `pisati` crosses fifteen keys, so the length gate admits words up
        // to seventeen letters and roughly sixteen THOUSAND candidates reach
        // scoring. Running the full comparison on all of them took ~0.5 s - and
        // it runs again every 80 ms while the finger is still moving, which is
        // exactly the lag felt on a slower phone.
        val kept = arrayOfNulls<String>(SHORTLIST)
        val keptScore = DoubleArray(SHORTLIST) { Double.NEGATIVE_INFINITY }
        for (e in entries) {
            val w = e.word
            if (w.length < 2 || w.length > maxLen) continue
            if (foldBase(w.first()) !in firstKeys) continue
            // Cheap gate before the costly geometry: a glide ends ON its last
            // letter, so a word ending elsewhere cannot be what was drawn.
            if (lastKeys.isNotEmpty() && foldBase(w.last()) !in lastKeys) continue
            val folded = buildString { for (c in w) append(foldBase(c)) }
            val geo = coarse(folded)
            if (geo == Double.NEGATIVE_INFINITY) continue
            val s = geo + freqTerm(e.freq)
            if (s > keptScore[SHORTLIST - 1]) {
                var j = SHORTLIST - 1
                while (j > 0 && keptScore[j - 1] < s) {
                    keptScore[j] = keptScore[j - 1]; kept[j] = kept[j - 1]; j--
                }
                keptScore[j] = s; kept[j] = w
            }
        }

        // PASS 2 - the real comparison, on the shortlist only. Measured on
        // synthetic glides: identical top-1 and top-3 against scoring everything,
        // and about eight times faster.
        val topWords = arrayOfNulls<String>(n)
        val topScore = DoubleArray(n) { Double.NEGATIVE_INFINITY }
        for (w in kept) {
            if (w == null) continue
            val folded = buildString { for (c in w) append(foldBase(c)) }
            val geo = fine(folded)
            if (geo == Double.NEGATIVE_INFINITY) continue
            // geo is negative px (0 = letters sit exactly on the path); a small
            // frequency nudge breaks ties, and adaptive usage nudges harder so a
            // word you keep swiping wins the near-ties geometry cannot resolve.
            val freq = freqOf(w)
            val s = geo + freqTerm(freq) + boost(w) -
                doubles(folded) * DOUBLE_W
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

    /**
     * Lists built by log-scaling corpus counts into `f` (FrequencyWords: isc, nb,
     * en) pull LINEARLY in `f`: taking log(f) again flattened them, and everyday
     * `her` (f=217) beat a rare three-letter word (f=60) by only ~4.5 px, so `her`
     * and `vi` lost glides to rarer neighbours (tester report, ISC test 0.1.3).
     * FREQ_LIN * f gives that pair ~18 px, with FREQ_W's log as the floor.
     * The Interslavic and Polish lists keep the log.
     */
    private const val FREQ_LIN = 0.15
    private val LINEAR_FREQ = setOf(Lang.ISC, Lang.NB, Lang.EN)

    private fun freqTerm(f: Int): Double {
        val log = Math.log((f + 1).toDouble()) * FREQ_W
        return if (current in LINEAR_FREQ) maxOf(log, f * FREQ_LIN) else log
    }

    /**
     * A doubled letter is invisible in a glide: `žže` and `že` draw the same
     * line, so geometry ties and the decision falls to frequency and usage.
     * One kept `žže` (usage +8) outweighed the whole frequency gap (že 204,
     * žže 74 = +3.5), and `žže` then came first every time (owner 2026-10-05).
     * Each double therefore costs a fixed penalty: the double form stays in the
     * strip, one tap away, but needs real repeated use to lead.
     */
    private const val DOUBLE_W = 10.0

    private fun doubles(folded: String): Int {
        var n = 0
        for (i in 1 until folded.length) if (folded[i] == folded[i - 1]) n++
        return n
    }

    /** How many words survive pass 1. Measured: 100, 200 and 400 all score the
     *  same, so this sits in the middle rather than at the edge of what works. */
    private const val SHORTLIST = 200

    /** How hard a word's own frequency pulls. Measured on synthetic glides:
     *  3.5 is the peak - a sloppy gesture gains two points of top-1 over 2.5,
     *  a tidy one is unchanged, and past 3.5 it starts costing. This is the
     *  "round towards the word people actually use" knob. */
    private const val FREQ_W = 3.5

    private fun freqOf(word: String): Int {
        val arr = entries
        val i = lowerBound(arr, word)
        return if (i < arr.size && arr[i].word == word) arr[i].freq else 0
    }

    /**
     * A word the user added themselves ([UserWords]). Middling frequency: it has
     * to be offered and glidable, but must not outrank everyday words that share
     * its prefix. Measured shipped frequencies run to roughly 250.
     */
    private const val USER_FREQ = 120

    /** Insert a user-added word into the live list; no-op if known or not loaded. */
    @Synchronized
    fun addUserWord(word: String) {
        if (!ready || contains(word)) return
        val arr = entries
        val i = lowerBound(arr, word)
        val out = arrayOfNulls<Entry>(arr.size + 1)
        System.arraycopy(arr, 0, out, 0, i)
        out[i] = Entry(word, USER_FREQ)
        System.arraycopy(arr, i, out, i + 1, arr.size - i)
        @Suppress("UNCHECKED_CAST")
        entries = out as Array<Entry>
        loaded[current] = entries
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
