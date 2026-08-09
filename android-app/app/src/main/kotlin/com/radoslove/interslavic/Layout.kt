package com.radoslove.interslavic

/**
 * The canonical character table — the same one as `docs/ms-latin-table.md`,
 * `ios/Keyboard/Layout.swift`, `build_keyman.py` and the Android layout XML.
 * Standard orthography only (HOUSE_STYLE §1): the extended alphabet
 * (ų ė ę ȯ å ŕ ť ď ľ đ …) is deliberately absent everywhere, not just here.
 *
 * A phone has no AltGr, so the four letters live on LONGPRESS of their base key
 * — hold `c` to get `č`, the same mnemonic as AltGr+C on desktop. The three
 * digraphs sit on longpress of `d` / `l` / `n`, matching `android/isv_latin.xml`.
 *
 * If that table ever changes, this file is one more place that changes with it.
 */
object Layout {

    /** base letter -> the accented letter reached by holding it */
    val accents: Map<Char, Char> = mapOf(
        'c' to 'č',
        's' to 'š',
        'z' to 'ž',
        'e' to 'ě',
    )

    /** base letter -> the digraph reached by holding it */
    val digraphs: Map<Char, String> = mapOf(
        'd' to "dž",
        'l' to "lj",
        'n' to "nj",
    )

    val letterRows: List<String> = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
    )

    /**
     * Symbol / numeric layer (the ?123 page). Row 3 leads with the canonical MS
     * punctuation as direct keys — „ ” – — and ’ — so the four glyphs from
     * `docs/ms-latin-table.md` are reachable without a popup picker. `?` and the
     * rest of the common punctuation live here too; on the letter layer only
     * `.` and `,` are exposed directly.
     */
    val symbolRows: List<String> = listOf(
        "1234567890",
        "@#\$%&*()/-",
        "„”–—’:;!?",
    )

    /**
     * Longpress output for a base key given the shift state, or null if the key
     * has no longpress. Uppercase is applied explicitly here — the iOS caps bug
     * was a reminder that the shifted accent must be produced deliberately, not
     * assumed.
     */
    fun longPress(key: Char, uppercase: Boolean): String? {
        accents[key]?.let { acc ->
            return if (uppercase) acc.uppercaseChar().toString() else acc.toString()
        }
        digraphs[key]?.let { dg ->
            return if (uppercase) dg.replaceFirstChar { it.uppercaseChar() } else dg
        }
        return null
    }
}
