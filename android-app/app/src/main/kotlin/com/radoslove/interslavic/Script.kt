package com.radoslove.interslavic

import android.content.Context

/**
 * The script the keyboard WRITES in: Latin (default), Cyrillic or Glagolitic.
 *
 * Everything inside the keyboard stays Latin - the dictionary, the glide decoder,
 * the usage ranking, the user's own words. Only three things cross this boundary:
 * key labels, the text committed to the editor, and the text read back from
 * before the cursor (turned back into Latin to look it up). The letters sit on
 * the same keys as in Latin (c -> ц, hold for ч), so a glide drawn for `ljubov`
 * is decoded exactly as before and written out as `љубов` or `ⰾⱓⰱⱁⰲ`.
 *
 * Standard orthography only (docs/ms-latin-table.md). Source: the community
 * transliterator medzuslovjansky/js-utils (MIT), packages/translit; details and
 * open questions in docs/script-tables.md.
 *   Cyrillic   = latn2cyrl `core` (shared by every converter there), standard
 *                letters: lj -> љ, nj -> њ, ě -> є, y -> ы, j -> ј; dž = д + ж
 *   Glagolitic = the ORIGINAL interslavic-dictionary.com converter ("v1", ported
 *                1:1 as latn2glag2), not the newer latn2glag: m -> ⱞ, y -> ⱐⰹ,
 *                ja/ju/jo -> ⱝ/ⱓ/ⱖ, j before another vowel -> ⰹ, a softening j
 *                after l/n with no vowel after it -> ⱐ (lj -> ⰾⱐ). v1 is what
 *                readers of the dictionary have seen; its one known artefact (a
 *                stray ⱏ inside `zsk`) is not reproduced.
 *
 * Letters with no counterpart (q, w, x) pass through unchanged.
 */
object Script {

    enum class Mode { LATIN, CYRILLIC, GLAGOLITIC }

    private const val PREFS = "isv_collector"
    private const val KEY = "script"

    fun mode(context: Context): Mode =
        when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "latin")) {
            "cyrillic" -> Mode.CYRILLIC
            "glagolitic" -> Mode.GLAGOLITIC
            else -> Mode.LATIN
        }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, mode.name.lowercase()).apply()
    }

    /** Latin -> Cyrillic -> Glagolitic -> Latin; used by the script key next to 🌐. */
    fun next(context: Context): Mode {
        val m = Mode.values()[(mode(context).ordinal + 1) % Mode.values().size]
        setMode(context, m)
        return m
    }

    /** The label of the script key: the first three letters of the current alphabet,
     *  the same convention as `?123`/`ABC` and the ABC/АБВ switch on Serbian keyboards. */
    fun keyLabel(context: Context): String = when (mode(context)) {
        Mode.LATIN -> "ABC"
        Mode.CYRILLIC -> "АБВ"
        Mode.GLAGOLITIC -> "ⰀⰁⰂ"
    }

    // ---- Cyrillic: a flat table, two-letter keys tried first -------------------

    private val CYRILLIC = linkedMapOf(
        "lj" to "љ", "nj" to "њ",
        "a" to "а", "b" to "б", "c" to "ц", "č" to "ч", "d" to "д", "e" to "е", "ě" to "є",
        "f" to "ф", "g" to "г", "h" to "х", "i" to "и", "j" to "ј", "k" to "к", "l" to "л",
        "m" to "м", "n" to "н", "o" to "о", "p" to "п", "r" to "р", "s" to "с", "š" to "ш",
        "t" to "т", "u" to "у", "v" to "в", "y" to "ы", "z" to "з", "ž" to "ж",
    )

    // ---- Glagolitic (v1): letters + the j rules below ----------------------------

    private val GLAG_LETTERS = mapOf(
        'a' to "ⰰ", 'b' to "ⰱ", 'c' to "ⱌ", 'č' to "ⱍ", 'd' to "ⰴ", 'e' to "ⰵ", 'ě' to "ⱑ",
        'f' to "ⱇ", 'g' to "ⰳ", 'h' to "ⱈ", 'i' to "ⰻ", 'k' to "ⰽ", 'l' to "ⰾ", 'm' to "ⱞ",
        'n' to "ⱀ", 'o' to "ⱁ", 'p' to "ⱂ", 'r' to "ⱃ", 's' to "ⱄ", 'š' to "ⱎ", 't' to "ⱅ",
        'u' to "ⱆ", 'v' to "ⰲ", 'y' to "ⱐⰹ", 'z' to "ⰸ", 'ž' to "ⰶ",
    )
    private val J_LIGATURE = mapOf('a' to "ⱝ", 'u' to "ⱓ", 'o' to "ⱖ")
    private val VOWELS = setOf('a', 'e', 'ě', 'i', 'o', 'u', 'y')

    /** Latin text -> the current script. Case follows the source: `Lj` -> `Љ`, `LJUBOV` -> `ЉУБОВ`. */
    fun out(context: Context, latin: String): String = convert(latin, mode(context))

    fun convert(latin: String, m: Mode): String = when {
        latin.isEmpty() || m == Mode.LATIN -> latin
        m == Mode.CYRILLIC -> cyrillic(latin)
        else -> glagolitic(latin)
    }

    private fun cased(mapped: String, source: Char, allCaps: Boolean): String = when {
        !source.isUpperCase() -> mapped
        allCaps -> mapped.uppercase()
        else -> mapped.replaceFirstChar { it.uppercaseChar() }
    }

    private fun allCaps(s: String) = s.any { it.isLetter() } && s.none { it.isLowerCase() }

    private fun cyrillic(latin: String): String {
        val caps = allCaps(latin)
        val sb = StringBuilder()
        var i = 0
        while (i < latin.length) {
            val two = if (i + 1 < latin.length) latin.substring(i, i + 2) else null
            val hit2 = two?.let { CYRILLIC[it.lowercase()] }
            val chunk = if (hit2 != null) two else latin.substring(i, i + 1)
            val mapped = hit2 ?: CYRILLIC[chunk.lowercase()]
            sb.append(if (mapped == null) chunk else cased(mapped, chunk[0], caps))
            i += chunk.length
        }
        return sb.toString()
    }

    private fun glagolitic(latin: String): String {
        val caps = allCaps(latin)
        val low = latin.lowercase()
        val sb = StringBuilder()
        var i = 0
        while (i < latin.length) {
            val c = low[i]
            if (c == 'j') {
                val next = low.getOrNull(i + 1)
                val prev = low.getOrNull(i - 1)
                val lig = next?.let { J_LIGATURE[it] }
                when {
                    lig != null -> { sb.append(cased(lig, latin[i], caps)); i += 2; continue }
                    next != null && next in VOWELS -> sb.append(cased("ⰹ", latin[i], caps))
                    prev == 'l' || prev == 'n' -> sb.append(cased("ⱐ", latin[i], caps))
                    else -> sb.append(cased("ⰹ", latin[i], caps))
                }
                i++
                continue
            }
            val mapped = GLAG_LETTERS[c]
            sb.append(if (mapped == null) latin[i].toString() else cased(mapped, latin[i], caps))
            i++
        }
        return sb.toString()
    }

    // ---- back to Latin (for dictionary lookups) --------------------------------

    private val REVERSE: Map<Mode, List<Pair<String, String>>> = mapOf(
        Mode.CYRILLIC to CYRILLIC.entries.map { it.value to it.key }.sortedByDescending { it.first.length },
        Mode.GLAGOLITIC to (
            listOf("ⱐⰹ" to "y", "ⱝ" to "ja", "ⱓ" to "ju", "ⱖ" to "jo", "ⱐ" to "j", "ⰹ" to "j", "ⰿ" to "m") +
                GLAG_LETTERS.filterKeys { it != 'y' }.entries.map { it.value to it.key.toString() }
            ).sortedByDescending { it.first.length },
    )

    /** Text in the current script -> Latin. Latin passes through. */
    fun toLatin(context: Context, s: String): String = back(s, mode(context))

    fun back(s: String, m: Mode): String {
        if (m == Mode.LATIN || s.isEmpty()) return s
        val rev = REVERSE.getValue(m)
        val sb = StringBuilder()
        var i = 0
        outer@ while (i < s.length) {
            for ((scr, lat) in rev) {
                if (s.regionMatches(i, scr, 0, scr.length, ignoreCase = true)) {
                    sb.append(if (s[i].isUpperCase()) lat.replaceFirstChar { it.uppercaseChar() } else lat)
                    i += scr.length
                    continue@outer
                }
            }
            sb.append(s[i]); i++
        }
        return sb.toString()
    }
}
