package com.radoslove.interslavic

import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import java.text.Normalizer

/**
 * Morse code as one more script of the keyboard (stage 1: the keyboard writes
 * Morse, the glide works as in every other script).
 *
 * Everything inside the keyboard stays in letters - dictionary, glide decoder,
 * completions, backspace by word. Morse exists only at the edge: [Conn] wraps
 * the editor, turns every text the keyboard commits into dots and dashes and
 * reads the editor back as letters. So the keyboard never sees the spaces
 * between letters and needs no change of its own.
 *
 * Written the way it is SENT: the gaps of the timing (dot 1, dash 3, gap
 * between letters 3, between words 7 units) become spaces - two between
 * letters, five between words (three and seven looked too wide on a phone). [LETTER_GAP] and [WORD_GAP] are the only
 * place this lives; if it does not work in practice, `" / "` is the fallback.
 *
 * Signs: `·` U+00B7 and `−` U+2212.
 *
 * Tables, per language, keyed by the letter as WRITTEN (Latin, or Cyrillic for
 * Cyrillic-only languages):
 *  - ITU-R M.1677-1: a-z, 0-9, punctuation, `é` (the only accented letter).
 *    `!` `_` `$` `&` are outside ITU but in common use.
 *  - Polish: national extension (pl.wikipedia "Kod Morse'a" + ZPE, which agree
 *    except `ę`, where ZPE gives the ITU `?`).
 *  - German: `ä ö ü` (en.wikipedia, Gerke 1851).
 *  - Serbian: Kingdom of Yugoslavia telegraph table (sr.wikipedia
 *    "Морзеова азбука", from Свезнање 1937). Its Latin letters, and Croatian
 *    and Bosnian, take the same codes through the one-to-one transliteration -
 *    derived, not sourced.
 *  - Russian, Ukrainian, Bulgarian: en.wikipedia "Morse code for non-Latin
 *    alphabets". Russian `ъ` has no code in the Russian standard.
 *  - `ch` = `−−−−` (cs.wikipedia; also pl, de) is read back, never written:
 *    `c` and `h` typed one after the other stay two letters.
 *
 * A letter with no code in its language is sent as its base letter (`č` as
 * `c`, `ě` as `e`, `å` as `a`) - the way Czech, which has no codes for its
 * diacritics, does it. A character with no code and no base passes through.
 * ⚠ Interslavic `č š ž ě` have no source of their own (open, 261117).
 */
object Morse {

    const val DOT = '·'
    const val DASH = '−'
    const val LETTER_GAP = "  "
    const val WORD_GAP = "     "

    /** From ITU notation (`.` and `-`) to the signs written here. */
    private fun m(s: String) = s.replace('.', DOT).replace('-', DASH)

    private val ITU: Map<String, String> = linkedMapOf(
        "a" to ".-", "b" to "-...", "c" to "-.-.", "d" to "-..", "e" to ".", "f" to "..-.",
        "g" to "--.", "h" to "....", "i" to "..", "j" to ".---", "k" to "-.-", "l" to ".-..",
        "m" to "--", "n" to "-.", "o" to "---", "p" to ".--.", "q" to "--.-", "r" to ".-.",
        "s" to "...", "t" to "-", "u" to "..-", "v" to "...-", "w" to ".--", "x" to "-..-",
        "y" to "-.--", "z" to "--..", "é" to "..-..",
        "1" to ".----", "2" to "..---", "3" to "...--", "4" to "....-", "5" to ".....",
        "6" to "-....", "7" to "--...", "8" to "---..", "9" to "----.", "0" to "-----",
    ).mapValues { m(it.value) }

    private val PUNCT: Map<String, String> = linkedMapOf(
        "." to ".-.-.-", "," to "--..--", "?" to "..--..", "'" to ".----.", "/" to "-..-.",
        "(" to "-.--.", ")" to "-.--.-", ":" to "---...", "=" to "-...-", "+" to ".-.-.",
        "-" to "-....-", "\"" to ".-..-.", "@" to ".--.-.",
        "!" to "-.-.--", "_" to "..--.-", "$" to "...-..-", "&" to ".-...",
    ).mapValues { m(it.value) }

    private val POLISH = mapOf(
        "ą" to ".-.-", "ć" to "-.-..", "ę" to "..-..", "ł" to ".-..-", "ń" to "--.--",
        "ó" to "---.", "ś" to "...-...", "ź" to "--..-", "ż" to "--..-.",
    ).mapValues { m(it.value) }

    private val GERMAN = mapOf("ä" to ".-.-", "ö" to "---.", "ü" to "..--").mapValues { m(it.value) }

    /** Yugoslav table, in Latin: Ђ đ, Ћ ć, Ч č, Ш š, Ж ž (Љ Њ Џ are two letters in Latin). */
    private val SERBO_CROATIAN_LATIN = mapOf(
        "đ" to "-..--", "ć" to "-.-..", "č" to "-.--.", "š" to "---.-", "ž" to "--..-",
    ).mapValues { m(it.value) }

    private val SERBIAN_CYRILLIC = mapOf(
        "а" to ".-", "б" to "-...", "в" to ".--", "г" to "--.", "д" to "-..", "ђ" to "-..--",
        "е" to ".", "ж" to "--..-", "з" to "--..", "и" to "..", "ј" to ".---", "к" to "-.-",
        "л" to ".-..", "љ" to ".---.", "м" to "--", "н" to "-.", "њ" to "--.--", "о" to "---",
        "п" to ".--.", "р" to ".-.", "с" to "...", "т" to "-", "ћ" to "-.-..", "у" to "..-",
        "ф" to "..-.", "х" to "....", "ц" to "-.-.", "ч" to "-.--.", "џ" to "--.-.", "ш" to "---.-",
    ).mapValues { m(it.value) }

    private val RUSSIAN = mapOf(
        "а" to ".-", "б" to "-...", "в" to ".--", "г" to "--.", "д" to "-..", "е" to ".",
        "ё" to ".", "ж" to "...-", "з" to "--..", "и" to "..", "й" to ".---", "к" to "-.-",
        "л" to ".-..", "м" to "--", "н" to "-.", "о" to "---", "п" to ".--.", "р" to ".-.",
        "с" to "...", "т" to "-", "у" to "..-", "ф" to "..-.", "х" to "....", "ц" to "-.-.",
        "ч" to "---.", "ш" to "----", "щ" to "--.-", "ы" to "-.--", "ь" to "-..-",
        "э" to "..-..", "ю" to "..--", "я" to ".-.-",
    ).mapValues { m(it.value) }

    private val UKRAINIAN = (RUSSIAN - setOf("ё", "ы", "э") + mapOf(
        "и" to m("-.--"), "і" to m(".."), "є" to m("..-.."), "ї" to m(".---."),
    ))

    private val BULGARIAN = (RUSSIAN - setOf("ё", "ы", "э") + mapOf(
        "ъ" to m("-..-"), "ь" to m("-.--"),
    ))

    /** Read back only (see the class comment). */
    private val CH = mapOf("ch" to m("----"))

    /** The languages that offer Morse, and their table: letter -> code. */
    private val TABLES: Map<String, Map<String, String>> = run {
        val latin = ITU + PUNCT
        val raw = mapOf(
            Lang.ISV to latin,
            Lang.PL to latin + POLISH,
            Lang.CS to latin, Lang.SK to latin, Lang.SL to latin,
            Lang.HR to latin + SERBO_CROATIAN_LATIN,
            Lang.BS to latin + SERBO_CROATIAN_LATIN,
            Lang.SR to latin + SERBO_CROATIAN_LATIN,
            Lang.DE to latin + GERMAN,
            Lang.EN to latin, Lang.IT to latin, Lang.ES to latin, Lang.NB to latin,
            Lang.RU to RUSSIAN + PUNCT + ITU.filterKeys { it[0].isDigit() },
            Lang.UK to UKRAINIAN + PUNCT + ITU.filterKeys { it[0].isDigit() },
            Lang.BG to BULGARIAN + PUNCT + ITU.filterKeys { it[0].isDigit() },
        )
        // A punctuation mark whose code is a letter of the language (Serbian
        // `č` = ITU `(`) would come back as that letter: it passes through as
        // itself instead.
        raw.mapValues { (_, t) ->
            val letters = t.filterKeys { it !in PUNCT }.values.toSet()
            t.filter { (k, v) -> k !in PUNCT || v !in letters }
        }
    }

    /** Code -> letter for reading back; a code shared by a punctuation mark and
     *  a national letter (Serbian `č` = ITU `(`) reads as the letter. */
    private val DECODE: Map<String, Map<String, String>> = TABLES.mapValues { (lang, t) ->
        val back = HashMap<String, String>()
        for ((k, v) in t) if (k != "ё") back[v] = k
        if (lang in setOf(Lang.CS, Lang.SK, Lang.PL, Lang.DE) && CH.getValue("ch") !in back)
            back[CH.getValue("ch")] = "ch"
        back
    }

    fun has(lang: String) = lang in TABLES

    private fun table(lang: String) = TABLES[lang] ?: TABLES.getValue(Lang.ISV)

    /** The code of one character, or null: own code, else the base letter's. */
    fun code(c: Char, lang: String): String? {
        val t = table(lang)
        val low = c.lowercaseChar().toString()
        t[low]?.let { return it }
        val base = Normalizer.normalize(low, Normalizer.Form.NFD).firstOrNull()?.toString()
        return base?.let { t[it] }
    }

    private fun isGap(c: Char) = c == ' ' || c == '\n' || c == '\t'

    /** Text -> Morse. [glyphBefore]: the editor already ends with a letter's
     *  code, so the first letter here needs the gap before it. */
    fun encode(text: String, lang: String, glyphBefore: Boolean = false): String {
        val sb = StringBuilder()
        var prevGlyph = glyphBefore
        for (c in text) {
            when {
                c == ' ' -> { sb.append(WORD_GAP); prevGlyph = false }
                isGap(c) -> { sb.append(c); prevGlyph = false }
                else -> {
                    if (prevGlyph) sb.append(LETTER_GAP)
                    sb.append(code(c, lang) ?: c.toString())
                    prevGlyph = true
                }
            }
        }
        return sb.toString()
    }

    /** One character of the read-back text and where it stands in the Morse
     *  text: [start] includes the letter gap before it. */
    class Piece(val text: String, val start: Int, val end: Int)

    /** Morse -> pieces. Runs longer than [LETTER_GAP] are a word space, shorter runs only
     *  separate letters; an unknown run of signs comes back as itself. */
    fun pieces(s: String, lang: String): List<Piece> {
        val back = DECODE[lang] ?: DECODE.getValue(Lang.ISV)
        val out = ArrayList<Piece>()
        var i = 0
        var gapStart = -1
        while (i < s.length) {
            val c = s[i]
            when {
                c == ' ' -> {
                    var j = i
                    while (j < s.length && s[j] == ' ') j++
                    if (j - i > LETTER_GAP.length) out.add(Piece(" ", i, j))
                    else gapStart = i
                    i = j
                    continue
                }
                c == DOT || c == DASH -> {
                    var j = i
                    while (j < s.length && (s[j] == DOT || s[j] == DASH)) j++
                    val sig = s.substring(i, j)
                    out.add(Piece(back[sig] ?: sig, if (gapStart >= 0) gapStart else i, j))
                }
                else -> {
                    val j = i + Character.charCount(s.codePointAt(i))
                    out.add(Piece(s.substring(i, j), if (gapStart >= 0) gapStart else i, j))
                    i = j
                    gapStart = -1
                    continue
                }
            }
            i = out.last().end
            gapStart = -1
        }
        return out
    }

    fun decode(s: String, lang: String): String = pieces(s, lang).joinToString("") { it.text }

    /** A key label: the letter over its code, so the keys can be learned. */
    fun keyLabel(letter: String, lang: String): String {
        val code = letter.mapNotNull { code(it, lang) }.joinToString(" ")
        return if (code.isEmpty()) letter else letter + "\n" + code
    }

    /**
     * The editor seen through Morse: the keyboard writes and reads letters,
     * the editor holds dots and dashes. Counts the keyboard gives (characters
     * to delete) are in letters and are turned into Morse characters here.
     */
    class Conn(target: InputConnection, private val lang: String) : InputConnectionWrapper(target, false) {

        /** How far back to read for [n] letters: the longest code (7) plus a
         *  word gap per letter is the worst case. */
        private fun span(n: Int) = (n.coerceAtLeast(1) * 9 + 16).coerceAtMost(4000)

        /** The Morse text before the cursor, cut to whole pieces. */
        private fun rawBefore(n: Int): String {
            val want = span(n)
            val raw = super.getTextBeforeCursor(want, 0)?.toString().orEmpty()
            if (raw.length < want) return raw
            // Started mid-code: drop up to the first gap.
            val cut = raw.indexOf(' ')
            return if (cut < 0) "" else raw.substring(cut)
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
            val text = decode(rawBefore(n), lang)
            return if (text.length > n) text.substring(text.length - n) else text
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
            val want = span(n)
            var raw = super.getTextAfterCursor(want, 0)?.toString().orEmpty()
            if (raw.length >= want) raw.lastIndexOf(' ').let { if (it > 0) raw = raw.substring(0, it) }
            val text = decode(raw, lang)
            return if (text.length > n) text.substring(0, n) else text
        }

        override fun getSelectedText(flags: Int): CharSequence? =
            super.getSelectedText(flags)?.toString()?.let { decode(it, lang) }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val s = text?.toString() ?: return super.commitText(text, newCursorPosition)
            if (s.isEmpty()) return super.commitText(text, newCursorPosition)
            val prev = super.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            val glyphBefore = prev.isNotEmpty() && !isGap(prev.last())
            return super.commitText(encode(s, lang, glyphBefore), newCursorPosition)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            var b = 0
            if (beforeLength > 0) {
                val raw = rawBefore(beforeLength)
                val ps = pieces(raw, lang)
                b = if (ps.size <= beforeLength) raw.length
                else raw.length - ps[ps.size - beforeLength].start
            }
            var a = 0
            if (afterLength > 0) {
                val raw = super.getTextAfterCursor(span(afterLength), 0)?.toString().orEmpty()
                val ps = pieces(raw, lang)
                a = if (ps.size <= afterLength) raw.length else ps[afterLength].start
            }
            return super.deleteSurroundingText(b, a)
        }
    }
}
