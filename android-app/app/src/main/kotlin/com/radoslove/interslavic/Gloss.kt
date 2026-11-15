package com.radoslove.interslavic

import android.content.Context

/**
 * Offline word-by-word gloss for the app's try box (2026-11-15), from the Polish
 * Wiktionary (`tools/build_gloss.py`, assets `gloss_<lang>.tsv`, CC BY-SA 4.0).
 *
 * A keyboard language other than Polish glosses INTO Polish (`Haus` -> `dom`);
 * Polish glosses into any keyboard language its entry has a translation for
 * (`dom` -> `Haus`, `dům`, `дом`). It is a gloss, not a translation: one word for
 * one word, no grammar - enough to understand and to copy a first draft.
 */
object Gloss {
    private val tables = HashMap<String, Map<String, String>>()

    fun has(context: Context, lang: String): Boolean = try {
        context.assets.open("gloss_$lang.tsv").close(); true
    } catch (_: Throwable) { false }

    /** Every form of the file -> its line's gloss; the first line wins. Slow the
     *  first time (hundreds of thousands of forms): call it off the UI thread. */
    @Synchronized
    fun table(context: Context, lang: String): Map<String, String> = tables.getOrPut(lang) {
        val m = HashMap<String, String>()
        val folded = HashMap<String, String>()
        try {
            context.assets.open("gloss_$lang.tsv").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val t = line.indexOf('\t')
                    if (t <= 0) continue
                    val v = line.substring(t + 1)
                    for (k in line.substring(0, t).split(' ')) {
                        if (k.isEmpty()) continue
                        val old = m[k]
                        // Polish: a form under several entries (`jest` alone and in
                        // `być`) keeps the translations of all of them, first first.
                        m[k] = if (old == null) v else if (lang == Lang.PL) "$old|$v" else old
                    }
                    // The same forms without diacritics, so `duzy` finds `duży`
                    // (owner 2026-11-15); a real spelling always wins over a fold.
                    for (k in line.substring(0, t).split(' ')) {
                        val f = fold(k)
                        if (f != k && f.isNotEmpty()) folded.putIfAbsent(f, v)
                    }
                }
            }
        } catch (_: Throwable) {
            // No asset, no gloss - never a crash.
        }
        for ((k, v) in folded) m.putIfAbsent(k, v)
        m
    }

    /** `duży` -> `duzy`, `Straße` -> `Strasse`, `ł` -> `l`: what is typed without diacritics. */
    fun fold(s: String): String {
        val n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder(n.length)
        for (c in n) when {
            Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> {}
            c == 'ł' -> sb.append('l'); c == 'Ł' -> sb.append('L')
            c == 'ß' -> sb.append("ss")
            c == 'đ' -> sb.append('d'); c == 'Đ' -> sb.append('D')
            else -> sb.append(c)
        }
        return sb.toString()
    }

    /** The languages [from] can be glossed into. */
    fun targets(context: Context, from: String): List<String> = when {
        !has(context, from) -> emptyList()
        from == Lang.PL -> {
            val all = listOf(Lang.DE, Lang.EN, Lang.NB, Lang.CS, Lang.SK, Lang.SL, Lang.HR, Lang.SR,
                Lang.BS, Lang.MK, Lang.RU, Lang.UK, Lang.BG, Lang.BE)
            // The keyboard's own languages first.
            val on = Lang.enabled(context)
            all.filter { it in on } + all.filter { it !in on }
        }
        else -> listOf(Lang.PL)
    }

    /** German has no inflection tables in this source: a few endings are cut. */
    private val DE_ENDINGS = listOf("en", "er", "es", "em", "e", "n", "s", "st", "t")

    private fun find(table: Map<String, String>, word: String, from: String): String? {
        val low = word.lowercase()
        if (from == Lang.DE) {
            val cap = low.replaceFirstChar { it.uppercaseChar() }
            table[word]?.let { return it }
            table[cap]?.let { return it }
            table[low]?.let { return it }
            table[fold(word)]?.let { return it }
            for (e in DE_ENDINGS) if (low.length > e.length + 2 && low.endsWith(e)) {
                val stem = low.dropLast(e.length)
                (table[stem] ?: table[stem.replaceFirstChar { it.uppercaseChar() }])?.let { return it }
            }
            return null
        }
        return table[low] ?: table[fold(low)]
    }

    /** One gloss for [word] in [to], or null. */
    fun word(table: Map<String, String>, word: String, from: String, to: String): String? {
        val v = find(table, word, from) ?: return null
        if (from != Lang.PL) return v
        for (part in v.split('|')) if (part.startsWith("$to=")) return part.substring(to.length + 1)
        return null
    }

    private val TOKEN = Regex("\\p{L}+")

    /** [text] cut into pieces, each with whether it was glossed: words not
     *  found stay as typed and are shown greyed, so nothing pretends to be a
     *  translation that is not one. */
    fun pieces(table: Map<String, String>, text: String, from: String, to: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var last = 0
        for (m in TOKEN.findAll(text)) {
            if (m.range.first > last) out.add(text.substring(last, m.range.first) to true)
            val g = glossOne(table, text, m, from, to)
            out.add((g ?: m.value) to (g != null))
            last = m.range.last + 1
        }
        if (last < text.length) out.add(text.substring(last) to true)
        return out
    }

    /** [text] with every word replaced by its gloss; unknown words stay as typed. */
    fun text(table: Map<String, String>, text: String, from: String, to: String): String =
        pieces(table, text, from, to).joinToString("") { it.first }

    private fun glossOne(table: Map<String, String>, text: String, m: MatchResult, from: String, to: String): String? =
        run {
            val w = m.value
            val g = word(table, w, from, to) ?: return null
            // A capital only where a sentence starts: German nouns are written
            // with one, their Polish gloss is not (`Das Haus` -> `To dom`).
            val before = text.substring(0, m.range.first).trimEnd()
            val starts = before.isEmpty() || before.last() in ".!?\n"
            if (starts && w[0].isUpperCase()) g.replaceFirstChar { it.uppercaseChar() }
            else if (from == Lang.DE) g.replaceFirstChar { it.lowercaseChar() } else g
        }
}
