package com.radoslove.interslavic

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Spaced repetition for the app's learning tab (2026-11-15).
 *
 * The cards are the words the user chose to LEARN - typed into the tab or
 * tapped under the writing box - not the `＋` words of [UserWords]: those are
 * words the keyboard was missing, which the user usually knows. A `＋` word
 * becomes a card only when the user puts it there. Each card has its own
 * translation (filled from the Wiktionary gloss when there is one, editable),
 * so the back of the card is something to check oneself against.
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
        var tr: String = "",
        var deck: Boolean = false,
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
                    tr = o.optString("tr"), deck = o.optBoolean("deck"),
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
                if (c.tr.isNotEmpty()) put("tr", c.tr)
                if (c.deck) put("deck", true)
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

    /** The cards being learned, in language order then alphabetically. */
    @Synchronized
    fun cards(context: Context): List<Card> {
        val order = Lang.ALL
        return load(context).values.filter { it.deck }
            .sortedWith(compareBy({ order.indexOf(it.lang) }, { it.word.lowercase() }))
    }

    /** The stored card of [word], or a fresh one (not in the deck). */
    @Synchronized
    fun card(context: Context, lang: String, word: String): Card =
        load(context)[key(lang, word)] ?: Card(lang, word)

    /** Put [word] among the cards; a card met before keeps its history. */
    @Synchronized
    fun add(context: Context, lang: String, word: String, tr: String?) {
        val w = word.trim()
        if (w.isEmpty()) return
        val c = card(context, lang, w)
        c.deck = true
        if (c.tr.isEmpty() && !tr.isNullOrEmpty()) c.tr = tr
        update(context, c)
    }

    /** Take [card] out of the deck; its data stays for a later return. */
    fun drop(context: Context, card: Card) {
        card.deck = false
        update(context, card)
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

    /** The language a [lang] word is translated into: Polish for every other
     *  language, and for Polish the target last chosen under the writing box. */
    fun target(context: Context, lang: String): String? {
        if (lang != Lang.PL) return Lang.PL
        val ts = Gloss.targets(context, Lang.PL)
        val chosen = context.getSharedPreferences("isv_collector", Context.MODE_PRIVATE)
            .getString("gloss_target_pl", null)
        return ts.firstOrNull { it == chosen } ?: ts.firstOrNull { it in Lang.enabled(context) } ?: ts.firstOrNull()
    }

    /** The Wiktionary gloss of [word], or null. Loads the table: off the UI thread. */
    fun gloss(context: Context, lang: String, word: String): String? = try {
        val to = target(context, lang)
        if (to == null || !Gloss.has(context, lang)) null
        else Gloss.word(Gloss.table(context, lang), word, lang, to)
    } catch (_: Throwable) { null }

    /*
     * A card answered "know" today does not come back today, not even in
     * "study" (iOS `learn_known_day`): the day is the local one, so it is
     * back after midnight. Only today's cards are kept.
     */
    private const val KNOWN_DAY = "learn_known_day"
    private const val KNOWN_IDS = "learn_known_ids"

    private fun today(): Long {
        val c = java.util.Calendar.getInstance()
        return (c.timeInMillis + c.get(java.util.Calendar.ZONE_OFFSET) + c.get(java.util.Calendar.DST_OFFSET)) / DAY
    }

    private fun knownIds(context: Context): Set<String> {
        val p = context.getSharedPreferences("isv_collector", Context.MODE_PRIVATE)
        return if (p.getLong(KNOWN_DAY, -1) == today()) p.getStringSet(KNOWN_IDS, emptySet()) ?: emptySet()
        else emptySet()
    }

    fun knownToday(context: Context, card: Card): Boolean = key(card.lang, card.word) in knownIds(context)

    fun markKnown(context: Context, card: Card) {
        val ids = knownIds(context) + key(card.lang, card.word)
        context.getSharedPreferences("isv_collector", Context.MODE_PRIVATE).edit()
            .putLong(KNOWN_DAY, today()).putStringSet(KNOWN_IDS, HashSet(ids)).apply()
    }

    /** Whole days until [card] is due again; 0 when due now. */
    fun daysLeft(card: Card, now: Long = System.currentTimeMillis()): Int =
        if (card.due <= now) 0 else ((card.due - now + DAY - 1) / DAY).toInt()
}
