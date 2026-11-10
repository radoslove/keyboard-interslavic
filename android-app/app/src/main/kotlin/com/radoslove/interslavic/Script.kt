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

    /**
     * The scripts a language offers. Serbian is the second one to get this key
     * (2026-11-10): Serbs write the same language in both alphabets, the state
     * standard for the Cyrillic keyboard (JUS I.K1.003) IS the Latin QWERTZ
     * layout with `љ` and `њ` on `q` and `w` - which this keyboard already drew
     * for Interslavic - and the two alphabets map one to one, so no second
     * wordlist is needed. Two modes, not three: Serbian has no Glagolitic.
     */
    fun offered(lang: String): List<Mode> = when (lang) {
        Lang.ISV -> listOf(Mode.LATIN, Mode.CYRILLIC, Mode.GLAGOLITIC)
        Lang.SR -> listOf(Mode.LATIN, Mode.CYRILLIC)
        // Cyrillic ONLY: there is no Latin Macedonian to offer, so the script
        // key does not appear and the keyboard opens in Cyrillic. The wordlist
        // is already in the internal Latin (tools/build_mk_wordlist.py); the
        // alphabet the user sees is this one.
        Lang.MK -> listOf(Mode.CYRILLIC)
        else -> listOf(Mode.LATIN)
    }

    /** Each language remembers its own script. Interslavic keeps the old key, so
     *  nobody's chosen alphabet is lost to this change. */
    private fun prefKey(lang: String) = if (lang == Lang.ISV) KEY else KEY + "_" + lang

    /** The chosen script of the language being typed; Latin for the languages
     *  that offer nothing else. */
    fun mode(context: Context): Mode = mode(context, Lang.current(context))

    fun mode(context: Context, lang: String): Mode {
        val can = offered(lang)
        // One script = that script, which for Macedonian is Cyrillic, not Latin.
        if (can.size < 2) return can.first()
        val m = when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(prefKey(lang), "latin")) {
            "cyrillic" -> Mode.CYRILLIC
            "glagolitic" -> Mode.GLAGOLITIC
            else -> Mode.LATIN
        }
        return if (m in can) m else Mode.LATIN
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(prefKey(Lang.current(context)), mode.name.lowercase()).apply()
    }

    /** The next script this language has, wrapping round; the script key next to 🌐. */
    fun next(context: Context): Mode {
        val can = offered(Lang.current(context))
        val m = can[(can.indexOf(mode(context)) + 1) % can.size]
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

    /**
     * Serbian Cyrillic: the standard, fully reversible pairing of the two
     * Serbian alphabets. Its own table rather than the Interslavic one, because
     * the two disagree where it matters: Interslavic has `ě` and `y` (`є`, `ы`),
     * which Serbian does not, Serbian has `ć` and `đ` (`ћ`, `ђ`), which stand on
     * their own letters here, and `dž` is ONE letter (`џ`) in Serbian while
     * Interslavic writes it `дж`.
     *
     * ⚠ Open, and the same class of problem as the Interslavic `-nje`: a `d`
     * that ends a prefix before a `ž` is not `џ` (`nadživeti` is `надживети`,
     * not `наџивети`). Telling the two apart needs the morpheme boundary, not a
     * table, so the two-letter rule wins for now - as it does in every other
     * converter that works letter by letter.
     */
    private val CYRILLIC_SR = linkedMapOf(
        "lj" to "љ", "nj" to "њ", "dž" to "џ",
        "a" to "а", "b" to "б", "c" to "ц", "č" to "ч", "ć" to "ћ", "d" to "д", "đ" to "ђ",
        "e" to "е", "f" to "ф", "g" to "г", "h" to "х", "i" to "и", "j" to "ј", "k" to "к",
        "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п", "r" to "р", "s" to "с",
        "š" to "ш", "t" to "т", "u" to "у", "v" to "в", "z" to "з", "ž" to "ж",
    )

    /**
     * Macedonian: the internal Latin of `tools/build_mk_wordlist.py` back into
     * Cyrillic. Every letter is ONE character on both sides, which is the whole
     * point - the usual transliteration writes `љ њ џ` as `lj nj dž`, and
     * Macedonian spells those sequences as two letters too (`илјада`
     * "thousand", `волја`, `инјекција`, `надживее`: 49 forms in the 50 000 of
     * the frequency list), so a pair-based table could not be turned back.
     * `ǉ ǌ ǆ` fold onto the `q w x` keys, where these letters sit in the
     * Cyrillic layouts of this family, and `ǵ ḱ ẑ` onto `g k z`, where they are
     * held.
     */
    private val CYRILLIC_MK = linkedMapOf(
        "a" to "\u0430", "b" to "\u0431", "v" to "\u0432", "g" to "\u0433",
        "d" to "\u0434", "\u01f5" to "\u0453", "e" to "\u0435", "\u017e" to "\u0436",
        "z" to "\u0437", "\u1e91" to "\u0455", "i" to "\u0438", "j" to "\u0458",
        "k" to "\u043a", "l" to "\u043b", "\u01c9" to "\u0459", "m" to "\u043c",
        "n" to "\u043d", "\u01cc" to "\u045a", "o" to "\u043e", "p" to "\u043f",
        "r" to "\u0440", "s" to "\u0441", "t" to "\u0442", "\u1e31" to "\u045c",
        "u" to "\u0443", "f" to "\u0444", "h" to "\u0445", "c" to "\u0446",
        "\u010d" to "\u0447", "\u01c6" to "\u045f", "\u0161" to "\u0448",
    )

    /** The Cyrillic table of [lang]. */
    private fun cyrTable(lang: String) = when (lang) {
        Lang.SR -> CYRILLIC_SR
        Lang.MK -> CYRILLIC_MK
        else -> CYRILLIC
    }

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
    fun out(context: Context, latin: String): String =
        convert(latin, mode(context), Lang.current(context))

    fun convert(latin: String, m: Mode, lang: String = Lang.ISV): String = when {
        latin.isEmpty() || m == Mode.LATIN -> latin
        m == Mode.CYRILLIC -> cyrillic(latin, cyrTable(lang))
        else -> glagolitic(latin)
    }

    private fun cased(mapped: String, source: Char, allCaps: Boolean): String = when {
        !source.isUpperCase() -> mapped
        allCaps -> mapped.uppercase()
        else -> mapped.replaceFirstChar { it.uppercaseChar() }
    }

    private fun allCaps(s: String) = s.any { it.isLetter() } && s.none { it.isLowerCase() }

    private fun cyrillic(latin: String, table: Map<String, String>): String {
        val caps = allCaps(latin)
        val sb = StringBuilder()
        var i = 0
        while (i < latin.length) {
            val two = if (i + 1 < latin.length) latin.substring(i, i + 2) else null
            val hit2 = two?.let { table[it.lowercase()] }
            val chunk = if (hit2 != null) two else latin.substring(i, i + 1)
            val mapped = hit2 ?: table[chunk.lowercase()]
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

    private fun reversed(table: Map<String, String>) =
        table.entries.map { it.value to it.key }.sortedByDescending { it.first.length }

    private val REVERSE_SR = reversed(CYRILLIC_SR)
    private val REVERSE_MK = reversed(CYRILLIC_MK)

    private val REVERSE: Map<Mode, List<Pair<String, String>>> = mapOf(
        Mode.CYRILLIC to reversed(CYRILLIC),
        Mode.GLAGOLITIC to (
            listOf("ⱐⰹ" to "y", "ⱝ" to "ja", "ⱓ" to "ju", "ⱖ" to "jo", "ⱐ" to "j", "ⰹ" to "j", "ⰿ" to "m") +
                GLAG_LETTERS.filterKeys { it != 'y' }.entries.map { it.value to it.key.toString() }
            ).sortedByDescending { it.first.length },
    )

    /** Text in the current script -> Latin. Latin passes through. */
    fun toLatin(context: Context, s: String): String =
        back(s, mode(context), Lang.current(context))

    fun back(s: String, m: Mode, lang: String = Lang.ISV): String {
        if (m == Mode.LATIN || s.isEmpty()) return s
        val rev = when {
            m == Mode.CYRILLIC && lang == Lang.SR -> REVERSE_SR
            m == Mode.CYRILLIC && lang == Lang.MK -> REVERSE_MK
            else -> REVERSE.getValue(m)
        }
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
