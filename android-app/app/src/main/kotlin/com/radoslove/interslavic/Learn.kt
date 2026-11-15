package com.radoslove.interslavic

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Spaced repetition over the user's own words ([UserWords]) for the app's
 * learning tab (2026-11-15).
 *
 * The words themselves stay in [UserWords] - that is what the keyboard reads -
 * and this file keeps only what learning adds on top: a category, a note, an
 * example and the review schedule. A word removed from the keyboard simply
 * stops showing up here; its card data stays, so adding it again brings the
 * history back.
 *
 * The schedule is a small SM-2 (the family Anki comes from), with three grades:
 * 0 "don't know" starts the word over and shows it again in a minute,
 * 1 "so-so" grows the interval slowly, 2 "know" multiplies it by the ease.
 *
 * On-device only: `filesDir/learn.json`, written through a temporary file so a
 * crash mid-write never leaves half a file.
 */
object Learn {

    data class Card(
        val lang: String,
        val word: String,
        var cat: String = "",
        var note: String = "",
        var ex: String = "",
        var reps: Int = 0,
        var ivl: Double = 0.0,      // days
        var ease: Double = 2.5,
        var due: Long = 0L,         // epoch ms; 0 = new, due now
    ) {
        val isNew get() = reps == 0 && due == 0L
    }

    private const val FILE = "learn.json"
    private const val DAY = 86_400_000L
    private const val MINUTE = 60_000L

    private fun key(lang: String, word: String) = "$lang\t$word"

    @Synchronized
    private fun load(context: Context): MutableMap<String, Card> {
        val out = LinkedHashMap<String, Card>()
        try {
            val f = File(context.filesDir, FILE)
            if (!f.exists()) return out
            val root = JSONObject(f.readText())
            for (k in root.keys()) {
                val o = root.getJSONObject(k)
                val t = k.indexOf('\t')
                if (t <= 0) continue
                out[k] = Card(
                    lang = k.substring(0, t), word = k.substring(t + 1),
                    cat = o.optString("cat"), note = o.optString("note"), ex = o.optString("ex"),
                    reps = o.optInt("reps"), ivl = o.optDouble("ivl", 0.0),
                    ease = o.optDouble("ease", 2.5), due = o.optLong("due"),
                )
            }
        } catch (_: Throwable) {
            // A broken file must not take the app down; the words are safe in UserWords.
        }
        return out
    }

    @Synchronized
    private fun save(context: Context, cards: Map<String, Card>) {
        try {
            val root = JSONObject()
            for ((k, c) in cards) root.put(k, JSONObject().apply {
                if (c.cat.isNotEmpty()) put("cat", c.cat)
                if (c.note.isNotEmpty()) put("note", c.note)
                if (c.ex.isNotEmpty()) put("ex", c.ex)
                put("reps", c.reps); put("ivl", c.ivl); put("ease", c.ease); put("due", c.due)
            })
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(File(context.filesDir, FILE))) {
                File(context.filesDir, FILE).writeText(root.toString())
                tmp.delete()
            }
        } catch (_: Throwable) {
        }
    }

    /** One card per saved word of every language, in language order then alphabetically. */
    @Synchronized
    fun cards(context: Context): List<Card> {
        val stored = load(context)
        val out = ArrayList<Card>()
        for (lang in Lang.ALL) for (w in UserWords.all(context, lang).sorted())
            out.add(stored[key(lang, w)] ?: Card(lang, w))
        return out
    }

    /** Cards due at [now], the longest-overdue first, new words last. */
    fun due(cards: List<Card>, now: Long = System.currentTimeMillis()): List<Card> =
        cards.filter { it.due <= now }.sortedWith(compareBy({ it.isNew }, { it.due }))

    /** Every category in use, alphabetically. */
    fun categories(cards: List<Card>): List<String> =
        cards.map { it.cat }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() }

    @Synchronized
    fun update(context: Context, card: Card) {
        val all = load(context)
        all[key(card.lang, card.word)] = card
        save(context, all)
    }

    /** Apply a grade (0 don't know, 1 so-so, 2 know) and store the card. */
    fun grade(context: Context, card: Card, g: Int, now: Long = System.currentTimeMillis()) {
        when (g) {
            0 -> {
                card.reps = 0
                card.ivl = 0.0
                card.ease = maxOf(1.3, card.ease - 0.2)
                card.due = now + MINUTE
            }
            1 -> {
                card.ivl = if (card.reps == 0) 1.0 else maxOf(1.0, card.ivl * 1.2)
                card.ease = maxOf(1.3, card.ease - 0.15)
                card.reps++
                card.due = now + (card.ivl * DAY).toLong()
            }
            else -> {
                card.ivl = when (card.reps) {
                    0 -> 1.0
                    1 -> 3.0
                    else -> maxOf(card.ivl + 1, card.ivl * card.ease)
                }
                card.reps++
                card.due = now + (card.ivl * DAY).toLong()
            }
        }
        update(context, card)
        // A plain history of grades (time, language, word, grade), so the user's
        // own progress can be counted later; it never leaves the phone.
        try {
            File(context.filesDir, "learn_log.tsv").appendText("$now\t${card.lang}\t${card.word}\t$g\n")
        } catch (_: Throwable) {
        }
    }

    /** How many grades have been given so far. */
    fun gradesGiven(context: Context): Int = try {
        val f = File(context.filesDir, "learn_log.tsv")
        if (f.exists()) f.readLines().count { it.isNotBlank() } else 0
    } catch (_: Throwable) { 0 }

    /** Whole days until [card] is due again; 0 when due now. */
    fun daysLeft(card: Card, now: Long = System.currentTimeMillis()): Int =
        if (card.due <= now) 0 else ((card.due - now + DAY - 1) / DAY).toInt()
}
