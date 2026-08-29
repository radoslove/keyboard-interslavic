package com.radoslove.interslavic

import android.content.Context
import java.io.File

/**
 * Adaptive ranking (the owner's "ranking that builds up through usage"). Every
 * word the user actually commits — typed, swiped, or picked from the bar — gets
 * a local count. That count boosts the word in both the prediction bar and the
 * swipe decoder, so the words YOU use surface first. This is the achievable form
 * of "context": no text corpus needed (we have none), it learns from you.
 *
 * Always on, on-device, no opt-in and no privacy surface — it never leaves the
 * phone on its own (syncing the counts INTO the database, so they are "visible
 * in the base", reuses the M3 export path and is a deliberate, separate step).
 *
 * Storage: `filesDir/usage.tsv` — `word\tcount`.
 */
object Usage {

    private const val FILE = "usage.tsv"
    private val counts = HashMap<String, Int>()
    private var loaded = false

    @Synchronized
    fun record(context: Context, word: String) {
        val w = word.lowercase()
        if (w.isEmpty()) return
        ensureLoaded(context)
        counts[w] = (counts[w] ?: 0) + 1
        persist(context)
    }

    /**
     * Take back a count recorded a moment ago, because the user rejected the
     * word — deleted it whole, or replaced it with an alternative.
     *
     * Without this the adaptive ranking learns the WRONG lesson: a bad glide
     * result was recorded on commit, so every time the user corrected it the
     * mistake gained as much weight as the correction and came back stronger.
     * `možehmo` beating `možemo` was this loop, not the geometry alone.
     */
    @Synchronized
    fun unrecord(context: Context, word: String) {
        val w = word.lowercase()
        if (w.isEmpty()) return
        ensureLoaded(context)
        val n = (counts[w] ?: 0) - 1
        if (n > 0) counts[w] = n else counts.remove(w)
        persist(context)
    }

    @Synchronized
    fun count(context: Context, word: String): Int {
        ensureLoaded(context)
        return counts[word.lowercase()] ?: 0
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val w = line.substring(0, tab)
                    val n = line.substring(tab + 1).toIntOrNull() ?: 0
                    if (w.isNotEmpty()) counts[w] = n
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun persist(context: Context) {
        try {
            val sb = StringBuilder()
            for ((w, n) in counts) sb.append(w).append('\t').append(n).append('\n')
            file(context).writeText(sb.toString())
        } catch (_: Throwable) {
        }
    }
}
