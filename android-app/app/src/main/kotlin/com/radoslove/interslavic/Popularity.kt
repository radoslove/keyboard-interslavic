package com.radoslove.interslavic

import android.content.Context
import java.io.File

/**
 * Popularity contribution (owner-approved 2026-08-10). When — and ONLY when —
 * the user has switched it on, every CONFIRMED word (a swipe commit, a pick from
 * the bar, or a hand-typed word that is already in the dictionary) is logged
 * automatically and silently to a local queue. That queue feeds the community
 * "most-used Interslavic words" ranking on export to the `submissions` table.
 *
 * Distinct from [Collector]: new/MISS words still need the explicit "＋ save"
 * confirmation — this is only the frictionless popularity signal for ordinary
 * use. Distinct from [Usage]: Usage is always-on LOCAL personal ranking that
 * never leaves the phone; this is a consent-gated CONTRIBUTION.
 *
 * Consent rules honoured: default OFF, nothing logged until the switch is on,
 * pseudonymous (the same `kbd-xxxxxxxx` id as the collector), no personal data,
 * and still on-device only — the app has no INTERNET permission, so "to the
 * base" means a local queue exported deliberately, never an automatic upload.
 */
object Popularity {

    private const val PREFS = "isv_collector"
    private const val KEY_ENABLED = "contribute_popularity"
    private const val FILE = "popularity.tsv"

    private val counts = HashMap<String, Int>()
    private var loaded = false

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** No-op unless the user consented. Counts a confirmed, real word. */
    @Synchronized
    fun record(context: Context, word: String) {
        if (!isEnabled(context)) return
        val w = word.lowercase()
        if (w.length < 2 || !w.all { it.isLetter() }) return
        ensureLoaded(context)
        counts[w] = (counts[w] ?: 0) + 1
        persist(context)
    }

    /**
     * Undo a count when the user rejects the word. The popularity ranking is
     * meant to say which forms people actually WRITE - a glide result deleted
     * or replaced a second later was never written, and letting it stand would
     * quietly poison the data we intend to share.
     */
    @Synchronized
    fun unrecord(context: Context, word: String) {
        if (!isEnabled(context)) return
        val w = word.lowercase()
        if (w.isEmpty()) return
        ensureLoaded(context)
        val n = (counts[w] ?: 0) - 1
        if (n > 0) counts[w] = n else counts.remove(w)
        persist(context)
    }

    /** A copy of the accumulated counts, for the batch export. */
    @Synchronized
    fun snapshot(context: Context): Map<String, Int> {
        ensureLoaded(context)
        return HashMap(counts)
    }

    @Synchronized
    fun pendingCount(context: Context): Int {
        ensureLoaded(context)
        return counts.size
    }

    @Synchronized
    fun clear(context: Context) {
        counts.clear()
        File(context.filesDir, FILE).delete()
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.filesDir, FILE)
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
            File(context.filesDir, FILE).writeText(sb.toString())
        } catch (_: Throwable) {
        }
    }
}
