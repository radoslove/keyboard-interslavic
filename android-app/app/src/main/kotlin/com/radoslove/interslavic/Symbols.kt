package com.radoslove.interslavic

import android.content.Context
import java.io.File

/**
 * How often each mark is typed: `?`, `,`, `„`, `€`, digits - everything that is
 * not a letter or a space - per keyboard language, and whether it came from a
 * plain tap or from a hold popup.
 *
 * Key placement is decided by measurement, not by feel: the more a mark is used,
 * the nearer the thumb it belongs (owner 2026-10-14, after `?` moved next to
 * backspace). Only the mark itself is counted, never the text around it, so
 * nothing written can be read back from this.
 *
 * Always on and on-device, like [Usage]. It leaves the phone only inside the
 * export batch, and only when the popularity ranking is switched on (the user's
 * consent to share counts). Storage: `filesDir/symbols.tsv`,
 * `lang\tmark\tvia\tcount`.
 */
object Symbols {

    const val TAP = "tap"
    const val HOLD = "hold"

    private const val FILE = "symbols.tsv"

    /** (lang, mark, via) -> count */
    private val counts = HashMap<Triple<String, String, String>, Int>()
    private var loaded = false

    /** Record [text] if it is a mark (no letters, not whitespace). */
    @Synchronized
    fun record(context: Context, text: String, via: String) {
        if (text.isEmpty() || text.any { it.isLetter() || it.isWhitespace() }) return
        ensureLoaded(context)
        val k = Triple(Lang.current(context), text, via)
        counts[k] = (counts[k] ?: 0) + 1
        persist(context)
    }

    /** Totals per mark over every language and both gestures, most used first. */
    @Synchronized
    fun top(context: Context, n: Int): List<Pair<String, Int>> {
        ensureLoaded(context)
        val sum = HashMap<String, Int>()
        for ((k, c) in counts) sum[k.second] = (sum[k.second] ?: 0) + c
        return sum.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
    }

    /** Every row, for the export batch. */
    @Synchronized
    fun rows(context: Context): List<Pair<Triple<String, String, String>, Int>> {
        ensureLoaded(context)
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    @Synchronized
    fun clear(context: Context) {
        counts.clear()
        persist(context)
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 4) {
                    val n = p[3].toIntOrNull() ?: 0
                    if (p[1].isNotEmpty()) counts[Triple(p[0], p[1], p[2])] = n
                }
            }
        } catch (_: Throwable) {
            // A broken file just starts empty — never crash typing.
        }
    }

    private fun persist(context: Context) {
        try {
            val sb = StringBuilder()
            for ((k, n) in counts) {
                sb.append(k.first).append('\t').append(k.second).append('\t')
                    .append(k.third).append('\t').append(n).append('\n')
            }
            file(context).writeText(sb.toString())
        } catch (_: Throwable) {
        }
    }
}
