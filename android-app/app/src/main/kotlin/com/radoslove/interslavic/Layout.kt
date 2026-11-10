package com.radoslove.interslavic

import android.content.Context

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

    /**
     * Czech keyboard: the háček and length marks live on LONGPRESS of their base
     * letter, exactly like the Interslavic ones - a Czech national keyboard puts
     * them on the number row, but a phone has no room for a sixth row and the
     * habit `c` held -> `č` is then the same in both languages.
     *
     * Rows follow [KeyRows]: QWERTZ by default, as on every Czech keyboard
     * (owner 2026-11-10, reversing the one-layout rule of 2026-11-01).
     *
     * Two keys carry two letters, and the ORDER is measured, not guessed - on
     * `main_cs.combined`, weighted by word frequency: ě (265 564) before é
     * (146 362), ů (73 974) before ú (21 897). `e` -> `ě` first also matches
     * the Interslavic popup, so the muscle memory carries across 🌐.
     */
    private val csOwn: Map<Char, List<String>> = mapOf(
        'a' to listOf("á"), 'c' to listOf("č"), 'd' to listOf("ď"),
        'e' to listOf("ě", "é"), 'i' to listOf("í"), 'n' to listOf("ň"),
        'o' to listOf("ó"), 'r' to listOf("ř"), 's' to listOf("š"),
        't' to listOf("ť"), 'u' to listOf("ů", "ú"), 'y' to listOf("ý"),
        'z' to listOf("ž"),
    )

    /**
     * Slovak keyboard: like Czech, every mark on LONGPRESS of its base letter.
     * Where a key carries two, the order is measured on `main_sk.combined`,
     * weighted by word frequency: á (419 386) before ä (16 380), ľ (78 351)
     * before ĺ (1 397), ô (25 172) before ó (20 768).
     */
    private val skOwn: Map<Char, List<String>> = mapOf(
        'a' to listOf("á", "ä"), 'c' to listOf("č"), 'd' to listOf("ď"),
        'e' to listOf("é"), 'i' to listOf("í"), 'l' to listOf("ľ", "ĺ"),
        'n' to listOf("ň"), 'o' to listOf("ô", "ó"), 'r' to listOf("ŕ"),
        's' to listOf("š"), 't' to listOf("ť"), 'u' to listOf("ú"),
        'y' to listOf("ý"), 'z' to listOf("ž"),
    )

    /** Polish keyboard: the Interslavic letters wait at the end of the popup. */
    private val isvOnPl: Map<Char, List<String>> = mapOf(
        'c' to listOf("č"), 's' to listOf("š"), 'z' to listOf("ž"), 'e' to listOf("ě"),
    )

    /** Held on `.`: punctuation our MS texts actually use. */
    val periodHold = listOf("…", "!", "„", "”", "\"", "'")

    /** Held on `.` of the LETTER layer, in the owner's own order (2026-11-07,
     *  read off his phone): `,` first and over the key, then `!`, `-`, `'`,
     *  `"`, and `:` `;` last - plus `?` back in second place, next to the
     *  comma (owner 2026-11-07, once the comma finally sat over the key): the
     *  two marks he reaches for most are then the two nearest the thumb.
     *
     *  SEVEN marks, not eleven - and that is a measurement, not taste. The
     *  strip draws one cell per mark, at least 38dp, so eleven asked for 418dp
     *  on a row 339dp wide (1080px at density 510): the strip was clamped to
     *  the edge and the first TWO cells, `,` and `:`, ended up off the screen -
     *  which is why the comma was not where it belonged and `?` sat over the
     *  key instead.  Seven cells take 266dp and fit.
     *
     *  Nothing went out of reach, only off the letters: `?` is in the third row
     *  of the `123` page, `…` `„` `”` are held on the `.` there (`numericHold`
     *  keeps `periodHold`), `–` on its `-`. */
    val periodLetterHold = listOf(",", "?", "!", "-", "'", "\"", ":", ";")

    /** Held on `,`: the rest of the sentence punctuation. Unused since the
     *  comma key left the bottom row (owner 2026-11-07) - kept, not deleted,
     *  for the day a comma key comes back; iOS keeps its `commaAccents` too. */
    val commaHold = listOf(":", ";", "-", "–", "(", ")")


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

    /** No script but Latin has a letter on `q w x`, and a foreign word inside a
     *  Cyrillic sentence is written WHOLE in Latin (`Linux`, `WhatsApp`), never
     *  as three Latin letters inside a Cyrillic word - so the way to it is the
     *  script key, not a stray key, and the same rule `variants` applies to the
     *  other foreign letters (`ñ ø ł ą` are hidden there) applies here too.
     *
     *  **Both non-Latin scripts keep the first two positions and put the `lj` and
     *  `nj` keys there** - in Cyrillic `љ` and `њ`, exactly where the Serbian and
     *  Macedonian standards have them, and in Glagolitic `ⰾⱐ` and `ⱀⱐ` (owner
     *  2026-11-08, first for Cyrillic - better to have them in plain sight, and it
     *  is practised elsewhere - then "to elegancko jakby było tak analogicznie" for
     *  Glagolitic). Glagolitic has no single SIGN for either pair, but a two-sign
     *  key is nothing new on this keyboard: `y` has stood there as `ⱐⰹ` all along,
     *  and the key is a shortcut to the digraph, not a claim that it is a letter.
     *
     *  The key stays the character `q` inside; what it writes is `Layout.keyText`,
     *  and the glide decoder reads a route over it as the PAIR `lj`
     *  (`Dictionary.foldWord`, which also keeps the route over `l`+`j`).
     *  `x` gets nothing in either script, because `dž` is settled as two letters
     *  (`дж`, not `џ`), so that one key goes. */
    private val nonLatinRows: List<String> = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zcvbnm",
    )

    /** [latinRows] with `y` and `z` swapped, as on the Czech, Slovak, Slovenian,
     *  Croatian and Serbian keyboards. Only two keys move, so the grid - and with
     *  it everything the decoder measures in key widths - stays the same. */
    private val qwertzRows: List<String> = listOf(
        "qwertzuiop",
        "asdfghjkl",
        "yxcvbnm",
    )

    fun letterRows(
        lang: String,
        script: Script.Mode = Script.Mode.LATIN,
        qwertz: Boolean = false,
    ): List<String> = when {
        lang == Lang.NB || lang == Lang.ISC -> nbRows
        script != Script.Mode.LATIN -> nonLatinRows
        qwertz -> qwertzRows
        else -> latinRows
    }

    /** What a letter key WRITES, in Latin - normally its own character, but in
     *  Cyrillic and Glagolitic `q` and `w` carry the digraphs behind `љ`/`њ` and
     *  `ⰾⱐ`/`ⱀⱐ`. The keyboard stays Latin inside, so everything downstream
     *  (dictionary, glide, ranking, the `Script` converter) still sees `lj` and
     *  `nj`; the converter then writes them in the script in force, with its own
     *  context rules. */
    fun keyText(ch: Char, script: Script.Mode): String = when {
        script == Script.Mode.LATIN -> ch.toString()
        ch == 'q' -> "lj"
        ch == 'w' -> "nj"
        else -> ch.toString()
    }

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
        Lang.CS -> csOwn
        Lang.SK -> skOwn
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

/**
 * QWERTY or QWERTZ, chosen per language in the settings (owner 2026-11-10):
 * "so everyone can type the way they are used to in their own language". The
 * default is the layout of that language's own keyboards - QWERTZ for Czech and
 * Slovak (later Slovenian, Croatian, Serbian), QWERTY for the rest. Languages
 * with their own row of letters (`nb`, `isc`) have no choice to make.
 */
object KeyRows {

    /** Languages whose national keyboard is QWERTZ. */
    val QWERTZ_BY_DEFAULT = setOf(Lang.CS, Lang.SK)

    /** Languages where the choice exists at all. */
    fun offered(lang: String) = lang != Lang.NB && lang != Lang.ISC

    private const val PREFS = "isv_collector"
    private fun key(lang: String) = "qwertz_$lang"

    fun isQwertz(context: Context, lang: String): Boolean =
        offered(lang) && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(lang), lang in QWERTZ_BY_DEFAULT)

    fun set(context: Context, lang: String, qwertz: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(key(lang), qwertz).apply()
    }
}
