package com.radoslove.interslavic

import android.content.Context
import java.io.File

/**
 * The collection loop (android_PLAN.md M3) — the reason this laboratory exists.
 * An Android IME reaches storage like any app, so it can gather the words a user
 * types that the dictionary does NOT know (a MISS), and queue them for review.
 * iOS forbids exactly this from a keyboard, so learning the shape here is the
 * point.
 *
 * Discipline baked in, matching the binding rules:
 *  - OPT-IN, default OFF. Nothing is collected until the user turns it on in the
 *    setup screen.
 *  - On-device only. This class never touches the network; export is a file the
 *    owner moves deliberately. The canonical online sink (host/hetz) is a
 *    separate infra seam, not wired here.
 *  - A MISS is flagged for REVIEW, never treated as a correction — absence from
 *    the wordlist (≈⅓ of the DB) is not proof a form is wrong.
 *  - Crude name filter: capitalised words are skipped (proper nouns), erring
 *    toward collecting less.
 *
 * The queue is `filesDir/collected.tsv` — `word\tcount`, one per line. Export
 * renders it as the `- form = note` inbox that `review_lexicon.py` already reads.
 */
object Collector {

    private const val PREFS = "isv_collector"
    private const val KEY_ENABLED = "enabled"
    private const val QUEUE = "collected.tsv"
    private const val MIN_LEN = 3

    private val counts = LinkedHashMap<String, Int>()
    private var loaded = false

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /**
     * Offer a just-finished [rawWord] (original case) for collection. No-ops
     * unless collection is on, the dictionary has loaded, and the word is a real
     * MISS worth queuing.
     */
    @Synchronized
    fun record(context: Context, rawWord: String) {
        if (!isEnabled(context)) return
        if (!Dictionary.isReady()) return
        if (rawWord.length < MIN_LEN) return
        if (rawWord.first().isUpperCase()) return          // crude name filter
        if (!rawWord.all { it.isLetter() }) return
        val word = rawWord.lowercase()
        if (Dictionary.contains(word)) return              // known -> not a MISS

        ensureLoaded(context)
        counts[word] = (counts[word] ?: 0) + 1
        persist(context)
    }

    /** Number of distinct MISS forms queued. */
    @Synchronized
    fun pendingCount(context: Context): Int {
        ensureLoaded(context)
        return counts.size
    }

    /**
     * Write the queue as an inbox file (`- form = seen N×`, most-seen first) and
     * return it. This is the deliberate, owner-driven export — the file that goes
     * to review, and later to the canonical online sink.
     */
    @Synchronized
    fun exportInboxText(context: Context): String {
        ensureLoaded(context)
        if (counts.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("<!-- medžuslovjansky collected MISS words — for review_lexicon.py -->\n")
        counts.entries.sortedByDescending { it.value }.forEach { (w, n) ->
            sb.append("- ").append(w).append(" = seen ").append(n).append("×\n")
        }
        return sb.toString()
    }

    @Synchronized
    fun clear(context: Context) {
        counts.clear()
        queueFile(context).delete()
    }

    // ---- storage ---------------------------------------------------------

    private fun queueFile(context: Context) = File(context.filesDir, QUEUE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = queueFile(context)
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
            // A broken queue just starts empty — never crash typing.
        }
    }

    private fun persist(context: Context) {
        try {
            val sb = StringBuilder()
            for ((w, n) in counts) sb.append(w).append('\t').append(n).append('\n')
            queueFile(context).writeText(sb.toString())
        } catch (_: Throwable) {
        }
    }
}
