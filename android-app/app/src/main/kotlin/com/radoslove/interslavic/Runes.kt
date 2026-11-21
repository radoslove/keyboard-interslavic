package com.radoslove.interslavic

import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import java.text.Normalizer

/**
 * Runes as a script of the keyboard, Norwegian only (owner 261118: both rows,
 * to choose and to switch; other languages only if asked). Port of iOS 0.6.42
 * `ios/Keyboard/Runes.swift`, tables copied 1:1.
 *
 * Like Morse, everything inside stays in letters and [Conn] turns the text
 * into runes at the editor. Every letter is ONE rune (all in the BMP Runic
 * block), so counts of characters are the same on both sides.
 *
 * Two rows, both a transliteration of modern Norwegian, not a historical
 * spelling:
 *  - [Row.MEDIEVAL] (ᛆᛒᛍ): the Norwegian medieval runes (c. 1100-1500, the
 *    younger futhark with the dotted runes), nearly one rune per letter;
 *    `j` shares ᛁ with `i`, `å` takes ᚬ.
 *  - [Row.YOUNG] (ᚠᚢᚦ): the long-branch younger futhark, 16 runes, so several
 *    letters share one (e i j -> ᛁ, o å -> ᚬ, d t -> ᛏ, g k c q x -> ᚴ,
 *    b p -> ᛒ, u v w y ø -> ᚢ, s z -> ᛋ, a æ -> ᛅ).
 * ⚠ Working tables (261118), not checked against a source table yet.
 */
object Runes {

    enum class Row(val key: String) { MEDIEVAL("runes_a"), YOUNG("runes_b") }

    fun label(r: Row) = if (r == Row.MEDIEVAL) "ᛆᛒᛍ" else "ᚠᚢᚦ"

    fun has(lang: String) = lang == Lang.NB

    private val MEDIEVAL = mapOf(
        'a' to 'ᛆ', 'b' to 'ᛒ', 'c' to 'ᛍ', 'd' to 'ᛑ', 'e' to 'ᛂ', 'f' to 'ᚠ', 'g' to 'ᚵ', 'h' to 'ᚼ',
        'i' to 'ᛁ', 'j' to 'ᛁ', 'k' to 'ᚴ', 'l' to 'ᛚ', 'm' to 'ᛘ', 'n' to 'ᚿ', 'o' to 'ᚮ', 'p' to 'ᛔ',
        'q' to 'ᛩ', 'r' to 'ᚱ', 's' to 'ᛋ', 't' to 'ᛐ', 'u' to 'ᚢ', 'v' to 'ᚡ', 'w' to 'ᚥ', 'x' to 'ᛪ',
        'y' to 'ᛦ', 'z' to 'ᛎ', 'æ' to 'ᛅ', 'ø' to 'ᚯ', 'å' to 'ᚬ', 'ä' to 'ᛅ', 'ö' to 'ᚯ',
    )

    private val YOUNG = mapOf(
        'a' to 'ᛅ', 'b' to 'ᛒ', 'c' to 'ᚴ', 'd' to 'ᛏ', 'e' to 'ᛁ', 'f' to 'ᚠ', 'g' to 'ᚴ', 'h' to 'ᚼ',
        'i' to 'ᛁ', 'j' to 'ᛁ', 'k' to 'ᚴ', 'l' to 'ᛚ', 'm' to 'ᛘ', 'n' to 'ᚾ', 'o' to 'ᚬ', 'p' to 'ᛒ',
        'q' to 'ᚴ', 'r' to 'ᚱ', 's' to 'ᛋ', 't' to 'ᛏ', 'u' to 'ᚢ', 'v' to 'ᚢ', 'w' to 'ᚢ', 'x' to 'ᚴ',
        'y' to 'ᚢ', 'z' to 'ᛋ', 'æ' to 'ᛅ', 'ø' to 'ᚢ', 'å' to 'ᚬ', 'ä' to 'ᛅ', 'ö' to 'ᚢ',
    )

    private fun table(r: Row) = if (r == Row.MEDIEVAL) MEDIEVAL else YOUNG

    /** Rune -> the letter it is read back as when the keyboard has no record
     *  of what was typed. A shared rune reads as the first letter of this
     *  order that has it (ᛁ = i, ᚴ = k, ᛏ = t, ᛅ = a, ᚬ = o). */
    private val BACK_ORDER = mapOf(
        Row.MEDIEVAL to "abcdefghiklmnopqrstuvwxyzæøåj",
        Row.YOUNG to "aiukbtsofhlmnræøåcdegjpqvwxyz",
    )
    private val BACK: Map<Row, Map<Char, Char>> = Row.values().associateWith { r ->
        val b = HashMap<Char, Char>()
        for (k in BACK_ORDER.getValue(r)) table(r)[k]?.let { v -> if (v !in b) b[v] = k }
        b
    }

    /** One character as written: its rune (held accents on their base
     *  letter), or itself. */
    fun rune(c: Char, r: Row): Char {
        val t = table(r)
        val low = c.lowercaseChar()
        t[low]?.let { return it }
        val base = Normalizer.normalize(low.toString(), Normalizer.Form.NFD).firstOrNull()
        return base?.let { t[it] } ?: c
    }

    fun encode(s: String, r: Row): String = buildString(s.length) { for (c in s) append(rune(c, r)) }

    fun decodeLossy(s: String, r: Row): String {
        val b = BACK.getValue(r)
        return buildString(s.length) { for (c in s) append(b[c] ?: c) }
    }

    /** The letters this keyboard wrote last, lined up with the end of the
     *  text before the cursor - so `ide` in ᚠᚢᚦ reads back as `ide`, not
     *  `idi`. Dropped as soon as the editor no longer ends with them. */
    @Volatile var tail = ""
    @Volatile var tailRow: Row? = null

    /** The text before the cursor, read back as letters. */
    fun read(raw: String, r: Row): String {
        if (tailRow == r && tail.isNotEmpty()) {
            val written = encode(tail, r)
            if (raw.endsWith(written)) return decodeLossy(raw.dropLast(tail.length), r) + tail
            // The editor handed over only the last stretch of text.
            if (written.endsWith(raw)) return tail.takeLast(raw.length)
        }
        tail = ""
        return decodeLossy(raw, r)
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
     *  the editor holds runes, one for one. */
    class Conn(target: InputConnection, private val row: Row) : InputConnectionWrapper(target, false) {

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
            val raw = super.getTextBeforeCursor(n, 0)?.toString() ?: return null
            return read(raw, row)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? =
            super.getTextAfterCursor(n, 0)?.toString()?.let { decodeLossy(it, row) }

        override fun getSelectedText(flags: Int): CharSequence? =
            super.getSelectedText(0)?.toString()?.let { decodeLossy(it, row) }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val s = text?.toString() ?: return super.commitText(text, newCursorPosition)
            if (s.isEmpty()) return super.commitText(text, newCursorPosition)
            // Enough of the editor to see whether the record still lines up.
            val raw = super.getTextBeforeCursor(tail.length + 1, 0)?.toString().orEmpty()
            wrote(s, raw, row)
            return super.commitText(encode(s, row), newCursorPosition)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            // One rune = one letter: the counts pass through; the record loses
            // the letters taken off its end.
            if (beforeLength > 0 && tail.isNotEmpty())
                tail = tail.dropLast(beforeLength.coerceAtMost(tail.length))
            return super.deleteSurroundingText(beforeLength, afterLength)
        }
    }
}
