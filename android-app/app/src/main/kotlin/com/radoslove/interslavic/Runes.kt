package com.radoslove.interslavic

import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import java.text.Normalizer

/**
 * Runes as a script of the keyboard, Norwegian only (owner 261118: the rows
 * to choose and to switch; other languages only if asked). Port of iOS 0.6.42
 * `ios/Keyboard/Runes.swift`, tables copied 1:1; the elder row is new here
 * (owner 261127) and goes to iOS from this file.
 *
 * Like Morse, everything inside stays in letters and [Conn] turns the text
 * into runes at the editor. All runes are in the BMP Runic block.
 *
 * Three rows, oldest first (owner 261127), each a transliteration of modern
 * Norwegian, not a historical spelling:
 *  - [Row.ELDER] (ᚨᛒᚲ): the elder futhark, c. 150-800, 24 runes. Letters it
 *    lacks: v = w (ᚹ), y = u (ᚢ), q and c = k (ᚲ, owner 261127: the
 *    Old English rune *cen* is c), and four letters as TWO runes: x ᚲᛊ, æ ᚨᛖ, ø ᛟᛖ, å ᚨᚨ.
 *  - [Row.YOUNG] (ᚠᚢᚦ): the long-branch younger futhark, c. 800-1100, 16
 *    runes, so several letters share one (e i j -> ᛁ, o å -> ᚬ, d t -> ᛏ,
 *    g k c q x -> ᚴ, b p -> ᛒ, u v w y ø -> ᚢ, s z -> ᛋ, a æ -> ᛅ).
 *  - [Row.MEDIEVAL] (ᛆᛒᛍ): the Norwegian medieval runes, c. 1100-1500, the
 *    younger futhark with the dotted runes splitting the shared sounds back
 *    apart; nearly one rune per letter, `j` shares ᛁ with `i`, `å` takes ᚬ.
 * ⚠ Working tables (261118, elder 261127), not checked against a source table yet.
 *
 * One letter may be two runes (elder row): the editor's counts are in runes,
 * the keyboard's in letters, and [Conn] translates. ⌫ after ᚲᛊ removes the
 * whole `x` while the keyboard's record of what it wrote ([tail]) lines up
 * with the editor; text it did not write reads back rune by rune (ᚲᛊ = ks),
 * because `oe` and `ae` are ordinary Norwegian (noe, poet, Israel).
 */
object Runes {

    enum class Row(val key: String) { ELDER("runes_e"), YOUNG("runes_b"), MEDIEVAL("runes_a") }

    fun label(r: Row) = when (r) {
        Row.ELDER -> "ᚨᛒᚲ"
        Row.YOUNG -> "ᚠᚢᚦ"
        Row.MEDIEVAL -> "ᛆᛒᛍ"
    }

    fun has(lang: String) = lang == Lang.NB

    private val ELDER = mapOf(
        'a' to "ᚨ", 'b' to "ᛒ", 'c' to "ᚲ", 'd' to "ᛞ", 'e' to "ᛖ", 'f' to "ᚠ", 'g' to "ᚷ", 'h' to "ᚺ",
        'i' to "ᛁ", 'j' to "ᛃ", 'k' to "ᚲ", 'l' to "ᛚ", 'm' to "ᛗ", 'n' to "ᚾ", 'o' to "ᛟ", 'p' to "ᛈ",
        'q' to "ᚲ", 'r' to "ᚱ", 's' to "ᛊ", 't' to "ᛏ", 'u' to "ᚢ", 'v' to "ᚹ", 'w' to "ᚹ", 'x' to "ᚲᛊ",
        'y' to "ᚢ", 'z' to "ᛉ", 'æ' to "ᚨᛖ", 'ø' to "ᛟᛖ", 'å' to "ᚨᚨ", 'ä' to "ᚨᛖ", 'ö' to "ᛟᛖ",
    )

    private val YOUNG = mapOf(
        'a' to "ᛅ", 'b' to "ᛒ", 'c' to "ᚴ", 'd' to "ᛏ", 'e' to "ᛁ", 'f' to "ᚠ", 'g' to "ᚴ", 'h' to "ᚼ",
        'i' to "ᛁ", 'j' to "ᛁ", 'k' to "ᚴ", 'l' to "ᛚ", 'm' to "ᛘ", 'n' to "ᚾ", 'o' to "ᚬ", 'p' to "ᛒ",
        'q' to "ᚴ", 'r' to "ᚱ", 's' to "ᛋ", 't' to "ᛏ", 'u' to "ᚢ", 'v' to "ᚢ", 'w' to "ᚢ", 'x' to "ᚴ",
        'y' to "ᚢ", 'z' to "ᛋ", 'æ' to "ᛅ", 'ø' to "ᚢ", 'å' to "ᚬ", 'ä' to "ᛅ", 'ö' to "ᚢ",
    )

    private val MEDIEVAL = mapOf(
        'a' to "ᛆ", 'b' to "ᛒ", 'c' to "ᛍ", 'd' to "ᛑ", 'e' to "ᛂ", 'f' to "ᚠ", 'g' to "ᚵ", 'h' to "ᚼ",
        'i' to "ᛁ", 'j' to "ᛁ", 'k' to "ᚴ", 'l' to "ᛚ", 'm' to "ᛘ", 'n' to "ᚿ", 'o' to "ᚮ", 'p' to "ᛔ",
        'q' to "ᛩ", 'r' to "ᚱ", 's' to "ᛋ", 't' to "ᛐ", 'u' to "ᚢ", 'v' to "ᚡ", 'w' to "ᚥ", 'x' to "ᛪ",
        'y' to "ᛦ", 'z' to "ᛎ", 'æ' to "ᛅ", 'ø' to "ᚯ", 'å' to "ᚬ", 'ä' to "ᛅ", 'ö' to "ᚯ",
    )

    private fun table(r: Row) = when (r) {
        Row.ELDER -> ELDER
        Row.YOUNG -> YOUNG
        Row.MEDIEVAL -> MEDIEVAL
    }

    /** Rune -> the letter it is read back as when the keyboard has no record
     *  of what was typed. A shared rune reads as the first letter of this
     *  order that has it (ᛁ = i, ᚴ = k, ᛏ = t, ᛅ = a, ᚬ = o; elder ᚹ = v,
     *  ᚲ = k, ᚢ = u). Two-rune letters are not read back: ᚲᛊ is `ks`. */
    private val BACK_ORDER = mapOf(
        Row.ELDER to "abdefghiklmnoprstuvzcjqwy",
        Row.YOUNG to "aiukbtsofhlmnræøåcdegjpqvwxyz",
        Row.MEDIEVAL to "abcdefghiklmnopqrstuvwxyzæøåj",
    )
    private val BACK: Map<Row, Map<Char, Char>> = Row.values().associateWith { r ->
        val b = HashMap<Char, Char>()
        for (k in BACK_ORDER.getValue(r)) table(r)[k]?.let { v ->
            if (v.length == 1 && v[0] !in b) b[v[0]] = k
        }
        b
    }

    /** One character as written: its rune(s) (held accents on their base
     *  letter), or itself. */
    fun rune(c: Char, r: Row): String {
        val t = table(r)
        val low = c.lowercaseChar()
        t[low]?.let { return it }
        val base = Normalizer.normalize(low.toString(), Normalizer.Form.NFD).firstOrNull()
        return base?.let { t[it] } ?: c.toString()
    }

    fun encode(s: String, r: Row): String = buildString(s.length) { for (c in s) append(rune(c, r)) }

    fun decodeLossy(s: String, r: Row): String {
        val b = BACK.getValue(r)
        return buildString(s.length) { for (c in s) append(b[c] ?: c) }
    }

    /** The letters this keyboard wrote last, lined up with the end of the
     *  text before the cursor - so `ide` in ᚠᚢᚦ reads back as `ide`, not
     *  `idi`, and `x` in ᚨᛒᚲ as `x`, not `ks`. Dropped as soon as the editor
     *  no longer ends with them. */
    @Volatile var tail = ""
    @Volatile var tailRow: Row? = null

    /** The last letters of [tail] that cover [runes] runes at its end. */
    private fun tailCovering(runes: Int, r: Row): String {
        var i = tail.length
        var n = 0
        while (i > 0 && n < runes) { i--; n += rune(tail[i], r).length }
        return tail.substring(i)
    }

    /** The text before the cursor, read back as letters. */
    fun read(raw: String, r: Row): String {
        if (tailRow == r && tail.isNotEmpty()) {
            val written = encode(tail, r)
            if (raw.endsWith(written)) return decodeLossy(raw.dropLast(written.length), r) + tail
            // The editor handed over only the last stretch of text.
            if (written.endsWith(raw)) return tailCovering(raw.length, r)
        }
        tail = ""
        return decodeLossy(raw, r)
    }

    /** How many runes the last [letters] letters before the cursor take, with
     *  [raw] the editor's text before the cursor. Letters the record holds
     *  count their own runes; older text is one rune per letter. */
    fun runesBefore(letters: Int, raw: String, r: Row): Int {
        if (tailRow == r && tail.isNotEmpty() && raw.endsWith(encode(tail, r))) {
            val own = tail.takeLast(letters)
            return encode(own, r).length + (letters - own.length)
        }
        return letters
    }

    /** Before writing [s] after [raw]: keep the record only while it still
     *  lines up with the editor. */
    fun wrote(s: String, raw: String, r: Row) {
        if (tailRow != r || !raw.endsWith(encode(tail, r))) tail = ""
        tailRow = r
        tail += s
        if (tail.length > 400) tail = tail.takeLast(300)
    }

    /** The editor seen through runes: the keyboard writes and reads letters,
     *  the editor holds runes, and the counts are translated between them. */
    class Conn(target: InputConnection, private val row: Row) : InputConnectionWrapper(target, false) {

        /** The editor's own text before the cursor, enough to hold [letters]
         *  letters and the whole record. */
        private fun rawBefore(letters: Int): String {
            val want = maxOf(letters * 2, encode(tail, row).length + 1)
            return super.getTextBeforeCursor(want, 0)?.toString().orEmpty()
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
            val raw = super.getTextBeforeCursor(n * 2, 0)?.toString() ?: return null
            return read(raw, row).takeLast(n)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? =
            super.getTextAfterCursor(n, 0)?.toString()?.let { decodeLossy(it, row) }

        override fun getSelectedText(flags: Int): CharSequence? =
            super.getSelectedText(0)?.toString()?.let { decodeLossy(it, row) }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val s = text?.toString() ?: return super.commitText(text, newCursorPosition)
            if (s.isEmpty()) return super.commitText(text, newCursorPosition)
            // Enough of the editor to see whether the record still lines up.
            val raw = super.getTextBeforeCursor(encode(tail, row).length + 1, 0)?.toString().orEmpty()
            wrote(s, raw, row)
            return super.commitText(encode(s, row), newCursorPosition)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            // Letters -> runes (ᚲᛊ is one `x`); the record loses the letters
            // taken off its end. Text after the cursor is one rune per letter.
            val before = if (beforeLength > 0) runesBefore(beforeLength, rawBefore(beforeLength), row) else 0
            if (beforeLength > 0 && tail.isNotEmpty())
                tail = tail.dropLast(beforeLength.coerceAtMost(tail.length))
            return super.deleteSurroundingText(before, afterLength)
        }
    }
}
