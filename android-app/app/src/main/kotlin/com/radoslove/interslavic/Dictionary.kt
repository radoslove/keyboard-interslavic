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
    fun suggest(prefix: String, n: Int): List<String> {
        if (!ready || prefix.isEmpty()) return emptyList()
        val arr = entries
        var lo = lowerBound(arr, prefix)
        // Collect top-n by frequency over the prefix range, capped so a very
        // short prefix (thousands of hits) stays snappy.
        val topWords = arrayOfNulls<String>(n)
        val topFreq = IntArray(n) { -1 }
        var scanned = 0
        var i = lo
        while (i < arr.size && scanned < 6000) {
            val e = arr[i]
            if (!e.word.startsWith(prefix)) break
            // insert e into the tiny top-n by frequency
            if (e.freq > topFreq[n - 1]) {
                var j = n - 1
                while (j > 0 && topFreq[j - 1] < e.freq) {
                    topFreq[j] = topFreq[j - 1]
                    topWords[j] = topWords[j - 1]
                    j--
                }
                topFreq[j] = e.freq
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
