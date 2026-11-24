package com.radoslove.interslavic

import android.content.Context

/**
 * The titlo (tytło), the Glagolitic abbreviation and number mark, while
 * writing Glagolitic (isv and pl). Port of iOS `Titlo` (26f315c, 0593919,
 * 151c9e6). Held on every letter after its own variants: plain, opening half,
 * closing half; and the titlo button on the suggestion bar.
 *
 * A long titlo is STORED as the opening half over the first letter, the
 * conjoining macron over the letters between and the closing half over the
 * last (owner 261124). What the user presses is not what is stored: the
 * keyboard fills the halves itself.
 *  - text selected + any titlo item: every run of letters in it gets one
 *    titlo (plain over a single letter), titlos already there are replaced;
 *  - opening half: shows at once over its letter, so an unclosed run stays
 *    VISIBLE instead of being a hidden mode;
 *  - closing half: finds that opening half before the caret and fills the
 *    letters in between. No state is kept - the half line on screen is it.
 *
 * Kotlin works on UTF-16 chars, not on grapheme clusters as Swift does, so a
 * letter here is a base character plus the non-spacing marks after it
 * (`ⱁ́` is one letter, as on iOS).
 */
object Titlo {
    const val PLAIN = "҃"
    const val OPEN = "︮"
    const val CLOSE = "︯"
    /** Over the letters between the halves: the conjoining macron (iOS 151c9e6). */
    const val MIDDLE = "︦"
    /** Kept in the set so it is stripped too (iOS `lift`, not written any more). */
    private const val LIFT = "̅"
    val MARKS = listOf(PLAIN, OPEN, CLOSE)
    private val SCALARS = setOf('҃', '︮', '︯', '︦', '̅')

    /** Glagolitic only - the alphabet the titlo belongs to here. */
    fun offered(context: Context): Boolean =
        Script.chosen(context) == Script.Mode.GLAGOLITIC

    fun isMark(c: Char) = c in SCALARS

    /** A popup item `a` + mark -> (`a`, mark); null for anything else. */
    fun split(s: String): Pair<String, String>? {
        if (s.length < 2 || s.last() !in SCALARS) return null
        return s.dropLast(1) to s.last().toString()
    }

    fun has(s: CharSequence) = s.any { it in SCALARS }

    fun strip(s: String): String = s.filterNot { it in SCALARS }

    private fun isNsm(c: Char) = Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    /** [s] cut into letters-with-their-marks (the Swift `Character`, roughly). */
    fun clusters(s: String): List<String> {
        val out = ArrayList<String>()
        for (c in s) {
            if (isNsm(c) && out.isNotEmpty()) out[out.size - 1] = out.last() + c
            else out.add(c.toString())
        }
        return out
    }

    private fun isLetter(cluster: String) = cluster.isNotEmpty() && cluster[0].isLetter()

    /** A mark put on what ONE key wrote. A letter written with two glyphs
     *  (ś = ⱄⱐ, ł = ⰾⱏ, y = ⱏⰹ, isv lj = ⰾⱐ) takes it over both: plain and
     *  closing become a long titlo over the pair, the opening half goes over
     *  each glyph (owner 261123: "na obydwa od razu"). */
    fun apply(glyphs: String, mark: String): String {
        val cs = clusters(glyphs)
        if (cs.size < 2 || !cs.all { isLetter(it) }) return glyphs + mark
        if (mark == OPEN) return cs.joinToString("") { it + OPEN }
        return over(glyphs)
    }

    /** One titlo over every run of letters: plain over a single letter, the
     *  halves over a longer one. Spaces and marks stay bare, so each word of a
     *  selection gets its own line. */
    fun over(text: String): String {
        val cs = clusters(strip(text))
        val out = StringBuilder()
        var i = 0
        while (i < cs.size) {
            if (!isLetter(cs[i])) { out.append(cs[i]); i++; continue }
            var j = i
            while (j < cs.size && isLetter(cs[j])) j++
            if (j - i == 1) {
                out.append(cs[i]).append(PLAIN)
            } else {
                for (k in i until j) out.append(cs[k]).append(if (k == i) OPEN else if (k == j - 1) CLOSE else MIDDLE)
            }
            i = j
        }
        return out.toString()
    }

    /** A number 1-999 in Glagolitic letters, capitals (owner 261124), table
     *  `vault_002/Projects/INTERSLOVE/glagolica_liczby_261122_v01.md`: units
     *  Ⰰ-Ⰸ, tens Ⰹ Ⰻ-Ⱂ (Ⰺ has no value), hundreds Ⱃ-Ⱋ; 11-19 put the unit
     *  before the ten, as the number is said. Null outside 1-999. */
    fun numeral(n: Int): String? {
        if (n !in 1..999) return null
        val units = "ⰀⰁⰂⰃⰄⰅⰆⰇⰈ"
        val tens = "ⰉⰋⰌⰍⰎⰏⰐⰑⰒ"
        val hundreds = "ⰓⰔⰕⰖⰗⰘⰙⰚⰛ"
        val h = n / 100
        val t = n / 10 % 10
        val u = n % 10
        val out = StringBuilder()
        if (h > 0) out.append(hundreds[h - 1])
        if (t == 1 && u > 0) return out.append(units[u - 1]).append(tens[0]).toString()
        if (t > 0) out.append(tens[t - 1])
        if (u > 0) out.append(units[u - 1])
        return out.toString()
    }

    /** The open run before the caret: from the last letter carrying the
     *  opening half to the caret, or null when none is open on this line. */
    fun openRun(before: String): String? {
        val cs = clusters(before)
        var k = cs.size - 1
        var seen = 0
        while (k >= 0 && seen < 80) {
            val c = cs[k]
            if (c.contains('\n')) return null
            if (c.contains('︯') || c.contains('҃')) return null
            if (c.contains('︮')) return cs.subList(k, cs.size).joinToString("")
            k--; seen++
        }
        return null
    }
}
