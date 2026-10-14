package com.radoslove.interslavic

/**
 * The canonical character table — the same one as `docs/ms-latin-table.md`,
 * `ios/Keyboard/Layout.swift`, `build_keyman.py` and the Android layout XML.
 * Standard orthography only (HOUSE_STYLE §1): the extended alphabet
 * (ų ė ę ȯ å ŕ ť ď ľ đ …) is deliberately absent from the Interslavic letters.
 *
 * A phone has no AltGr, so the letters live on LONGPRESS of their base key
 * — hold `c` to get `č`, the same mnemonic as AltGr+C on desktop. The three
 * digraphs sit on longpress of `d` / `l` / `n`, matching `android/isv_latin.xml`.
 *
 * The hold popup, the punctuation holds and the `123` / `#+=` pages are ported
 * from iOS (7801f4b, 559a71f) so both phones offer the same keys in the same
 * places. If that table ever changes, this file is one more place that changes
 * with it - see docs/PLATFORM_PARITY.md.
 */
object Layout {

    /** Interslavic: base letter -> the letters/digraphs reached by holding it. */
    private val isvOwn: Map<Char, List<String>> = mapOf(
        'c' to listOf("č"),
        's' to listOf("š"),
        'z' to listOf("ž"),
        'e' to listOf("ě"),
        'd' to listOf("dž"),
        'l' to listOf("lj"),
        'n' to listOf("nj"),
    )

    /** Polish keyboard: its own letters come first in the popup. */
    private val plOwn: Map<Char, List<String>> = mapOf(
        'a' to listOf("ą"), 'c' to listOf("ć"), 'e' to listOf("ę"), 'l' to listOf("ł"),
        'n' to listOf("ń"), 'o' to listOf("ó"), 's' to listOf("ś"), 'z' to listOf("ż", "ź"),
    )

    /**
     * Letters of OTHER languages, after the keyboard's own in the same popup
     * (owner 2026-10-10: `mañana` in a letter meant switching keyboards). Polish
     * first, then other Slavic, then the rest. For writing other languages only -
     * the Interslavic wordlist stays standard orthography.
     */
    private val foreign: Map<Char, List<String>> = mapOf(
        'a' to listOf("ą", "á", "ä", "à", "â", "ã", "å", "æ"),
        'c' to listOf("ć", "ç"),
        'd' to listOf("ď", "đ"),
        'e' to listOf("ę", "é", "è", "ê", "ë", "ė"),
        'i' to listOf("í", "ì", "î", "ï"),
        'l' to listOf("ł", "ľ", "ĺ"),
        'n' to listOf("ń", "ň", "ñ"),
        'o' to listOf("ó", "ô", "ö", "ò", "õ", "ø", "ő"),
        'r' to listOf("ř", "ŕ"),
        's' to listOf("ś", "ß"),
        't' to listOf("ť"),
        'u' to listOf("ú", "ů", "ü", "ù", "û", "ű"),
        'y' to listOf("ý", "ÿ"),
        'z' to listOf("ż", "ź"),
    )

    /** Norwegian: æ ø å have keys of their own, where every Norwegian keyboard
     *  has them (owner 2026-10-06, Interscandinavian); held, they give the Swedish
     *  and Danish neighbours. Accents for loanwords sit on the base letters. */
    private val nbOwn: Map<Char, List<String>> = mapOf(
        'e' to listOf("é", "è", "ê"), 'o' to listOf("ó", "ò", "ô"), 'a' to listOf("à"),
        'æ' to listOf("ä"), 'ø' to listOf("ö"), 'å' to listOf("ä", "ö"),
    )

    /** Polish keyboard: the Interslavic letters wait at the end of the popup. */
    private val isvOnPl: Map<Char, List<String>> = mapOf(
        'c' to listOf("č"), 's' to listOf("š"), 'z' to listOf("ž"), 'e' to listOf("ě"),
    )

    /** Held on `.`: punctuation our MS texts actually use. */
    val periodHold = listOf("…", "!", "„", "”", "\"", "'")

    /** Held on `,`: the rest of the sentence punctuation. */
    val commaHold = listOf(";", ":", "-", "–", "(", ")")


    /** Held on keys of the numeric pages; the page keeps only the frequent ones. */
    val numericHold: Map<Char, List<String>> = mapOf(
        '-' to listOf("–", "—", "_", "+", "="),
        '&' to listOf("%", "#", "*"),
        '€' to listOf("$", "£"),
        '"' to listOf("„", "”", "«", "»"),
        '(' to listOf("[", "{", "<"),
        ')' to listOf("]", "}", ">"),
        '/' to listOf("\\", "|"),
        '.' to periodHold,
        // ¿ ¡ used to wait behind the letter-layer `?`, which is gone.
        '?' to listOf("¿"),
        '!' to listOf("¡"),
    )

    private val latinRows: List<String> = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
    )

    private val nbRows: List<String> = listOf(
        "qwertyuiopå",
        "asdfghjkløæ",
        "zxcvbnm",
    )

    fun letterRows(lang: String): List<String> =
        if (lang == Lang.NB || lang == Lang.ISC) nbRows else latinRows

    /** `123` page - rows 1-2 as on the iOS system keyboard. Row 3 sits between
     *  `#+=` and backspace and runs from the rarest mark to the most used, so `?`
     *  and `!` are right next to backspace, nearest the thumb (owner 2026-10-14). */
    val numericRows: List<String> = listOf(
        "1234567890",
        "-/:;()€&@\"",
        ".,'!?",
    )

    /** `#+=` page, second symbol page as on iOS. */
    val symbolRows: List<String> = listOf(
        "[]{}#%^*+=",
        "_\\|~<>€£¥•",
        ".,'!?",
    )

    private fun own(lang: String): Map<Char, List<String>> = when (lang) {
        Lang.PL -> plOwn
        Lang.NB, Lang.ISC -> nbOwn
        Lang.EN -> emptyMap()
        else -> isvOwn
    }

    /** The keyboard's OWN letters on [key], case applied; drives the upward flick. */
    fun longPress(key: Char, uppercase: Boolean, lang: String = Lang.MAIN): String? =
        own(lang)[key]?.firstOrNull()?.let { cased(it, uppercase) }

    /**
     * Everything the hold popup offers for a letter key, own letters first.
     * Foreign letters only in Latin: in Cyrillic or Glagolitic they would be
     * Latin letters in the middle of the other script.
     */
    fun variants(key: Char, uppercase: Boolean, lang: String, latin: Boolean): List<String> {
        val mine = own(lang)[key].orEmpty()
        if (!latin) return mine.map { cased(it, uppercase) }
        val extra = foreign[key].orEmpty() + if (lang == Lang.PL) isvOnPl[key].orEmpty() else emptyList()
        return (mine + extra.filter { it !in mine }).distinct().map { cased(it, uppercase) }
    }

    /** `ß` has no one-letter capital; a digraph capitalises its first letter only (`Dž`). */
    private fun cased(s: String, uppercase: Boolean): String = when {
        !uppercase -> s
        s == "ß" -> s
        else -> s.replaceFirstChar { it.uppercaseChar() }
    }
}
