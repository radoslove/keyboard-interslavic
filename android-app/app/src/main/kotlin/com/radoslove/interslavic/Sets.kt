package com.radoslove.interslavic

import android.content.Context

/**
 * Ready word sets for the learning tab ("Numbers", "Family", ...): one table,
 * `assets/learn_sets.tsv`, with a column per language, so the same set serves
 * every language as the word and every other one as its translation. A row
 * whose id starts with `#` is the set's own name.
 *
 * A set taken into learning becomes an ordinary category of cards: the user
 * adds words to it, edits it or makes their own next to it.
 */
object Sets {

    data class Row(val set: String, val id: String, val cells: Map<String, String>)

    private var rows: List<Row>? = null

    @Synchronized
    private fun rows(context: Context): List<Row> = rows ?: try {
        context.assets.open("learn_sets.tsv").bufferedReader().useLines { lines ->
            val it = lines.iterator()
            val head = it.next().split('\t')
            val out = ArrayList<Row>()
            for (line in it) {
                val f = line.split('\t')
                if (f.size < 2) continue
                val cells = HashMap<String, String>()
                for (i in 2 until minOf(f.size, head.size)) if (f[i].isNotBlank()) cells[head[i]] = f[i].trim()
                out.add(Row(f[0], f[1], cells))
            }
            out
        }
    } catch (_: Throwable) { emptyList() }.also { rows = it }

    /** The sets that have words in [lang], in file order. */
    fun names(context: Context, lang: String): List<String> =
        rows(context).filter { !it.id.startsWith("#") && lang in it.cells }.map { it.set }.distinct()

    /** A set's name in the screen language [ui] (`ms` = Interslavic), else English. */
    fun title(context: Context, set: String, ui: String): String {
        val r = rows(context).firstOrNull { it.set == set && it.id.startsWith("#") } ?: return set
        val l = if (ui == "ms") Lang.ISV else ui.removeSuffix("-Cyrl")
        return r.cells[l] ?: r.cells["en"] ?: set
    }

    /** The set's words in [lang], each with its translation in [target]. */
    fun words(context: Context, set: String, lang: String, target: String?): List<Pair<String, String>> =
        rows(context).filter { it.set == set && !it.id.startsWith("#") }.mapNotNull { r ->
            val w = r.cells[lang] ?: return@mapNotNull null
            w to (target?.let { r.cells[it] } ?: r.cells["en"] ?: "")
        }
}

/**
 * Alphabet sets: the letters of a language shown in another of its scripts
 * (Interslavic in Cyrillic and Glagolitic, Polish in Glagolitic, Serbian in
 * Cyrillic, every language with a Morse table in Morse), the letter itself as
 * the answer. Built from the keyboard's own conversion, so nothing is stored.
 */
object Alphabets {

    private val LETTERS = mapOf(
        Lang.ISV to "a b c č ć d dž đ e ě f g h i j k l lj m n nj o p r s š t u v y z ž",
        Lang.PL to "a ą b c ć d e ę f g h i j k l ł m n ń o ó p r s ś t u w y z ź ż",
        Lang.CS to "a á b c č d ď e é ě f g h i í j k l m n ň o ó p r ř s š t ť u ú ů v y ý z ž",
        Lang.SK to "a á ä b c č d ď e é f g h i í j k l ĺ ľ m n ň o ó ô p r ŕ s š t ť u ú v y ý z ž",
        Lang.SL to "a b c č d e f g h i j k l m n o p r s š t u v z ž",
        Lang.HR to "a b c č ć d dž đ e f g h i j k l lj m n nj o p r s š t u v z ž",
        Lang.BS to "a b c č ć d dž đ e f g h i j k l lj m n nj o p r s š t u v z ž",
        Lang.SR to "a b c č ć d dž đ e f g h i j k l lj m n nj o p r s š t u v z ž",
        Lang.EN to "a b c d e f g h i j k l m n o p q r s t u v w x y z",
        Lang.NB to "a b c d e f g h i j k l m n o p q r s t u v w x y z æ ø å",
        Lang.DE to "a ä b c d e f g h i j k l m n o ö p q r s ß t u ü v w x y z",
        Lang.ES to "a b c d e f g h i j k l m n ñ o p q r s t u v w x y z",
        Lang.IT to "a b c d e f g h i l m n o p q r s t u v z",
        Lang.FR to "a à b c ç d e é è f g h i j k l m n o p q r s t u v w x y z",
        Lang.NL to "a b c d e f g h i j k l m n o p q r s t u v w x y z",
        Lang.RO to "a ă â b c d e f g h i î j k l m n o p q r s ș t ț u v w x y z",
        Lang.RU to "а б в г д е ё ж з и й к л м н о п р с т у ф х ц ч ш щ ъ ы ь э ю я",
        Lang.UK to "а б в г ґ д е є ж з и і ї й к л м н о п р с т у ф х ц ч ш щ ь ю я",
        Lang.BG to "а б в г д е ж з и й к л м н о п р с т у ф х ц ч ш щ ъ ь ю я",
    )

    /** A name that needs no translation: "ABC → ⰀⰁⰂ". */
    fun title(lang: String, m: Script.Mode) =
        Script.name(Script.offered(lang).first()) + " → " + Script.name(m)

    fun has(lang: String) = lang in LETTERS

    /**
     * Every script [lang] can be written in, its own first, Morse last (iOS
     * `Alphabets.scripts`, 261118). For learning, both rune rows are offered to
     * every language written in Latin letters, not only Norwegian as on the
     * keyboard: a letter with an accent takes its base letter's rune (č -> ᛍ).
     */
    fun scripts(lang: String): List<Script.Mode> {
        val letters = LETTERS[lang] ?: return emptyList()
        val out = Script.choices(lang).toMutableList()
        val latin = letters.any { it in 'a'..'z' }
        if (latin && Script.Mode.RUNES_E !in out) {
            val at = out.indexOf(Script.Mode.MORSE).let { if (it < 0) out.size else it }
            out.addAll(at, Script.RUNE_MODES)
        }
        return out
    }

    /** Letter [l] of [lang] in script [m]; null when the script has no sign for it. */
    private fun render(l: String, m: Script.Mode, lang: String): String? {
        val row = Script.runeRow(m)
        val f = when {
            m == Script.Mode.MORSE -> if (l.length == 1) Morse.code(l[0], lang) else null
            row != null -> if (l.length == 1) Runes.rune(l[0], row) else null
            m == Script.offered(lang).first() -> l
            else -> Script.convert(l, m, lang)
        }
        return f?.takeIf { it.isNotBlank() }
    }

    /** "ABC → ⰀⰁⰂ", from any script to any other. */
    fun title(from: Script.Mode, to: Script.Mode) = Script.name(from) + " → " + Script.name(to)

    /**
     * Cards from script [from] to [to]: the letter in [to] on the front, in
     * [from] as the answer. Letters sharing one sign (runic ᛁ = e i j, Morse
     * c = č) make one card with every letter in the answer.
     */
    fun pair(lang: String, from: Script.Mode, to: Script.Mode): List<Pair<String, String>> {
        val letters = LETTERS[lang]?.split(' ') ?: return emptyList()
        if (from == to) return emptyList()
        val by = LinkedHashMap<String, MutableList<String>>()
        // A letter with an accent that only borrows its base letter's sign
        // (runes: ą = ᛆ as a) is left out: "ᛆ a", not "ᛆ a / ą" (owner 261127:
        // no Polish diacritics). One with a sign of its own (Morse ą, the
        // Glagolitic ć) stays.
        fun base(l: String) = when (l) {
            "ł" -> "l"; "đ" -> "d"
            else -> java.text.Normalizer.normalize(l, java.text.Normalizer.Form.NFD).take(1)
        }
        for (l in letters) {
            val f = render(l, to, lang) ?: continue
            val bl = base(l)
            if (bl != l && bl in letters && render(bl, to, lang) == f) continue
            val b = render(l, from, lang) ?: continue
            if (f == b) continue
            val list = by.getOrPut(f) { ArrayList() }
            if (b !in list) list.add(b)
        }
        return by.map { (f, bs) -> f to bs.joinToString(" / ") }
    }

    /** For [lang]: (title, [(letter in the other script, letter)]) per script. */
    fun sets(lang: String): List<Pair<String, List<Pair<String, String>>>> {
        val letters = LETTERS[lang]?.split(' ') ?: return emptyList()
        val native = Script.offered(lang).first()
        return Script.choices(lang).filter { it != native }.mapNotNull { m ->
            val seen = HashSet<String>()
            val cards = letters.mapNotNull { l ->
                val front = if (m == Script.Mode.MORSE) {
                    if (l.length != 1) null else Morse.code(l[0], lang)
                } else Script.convert(l, m, lang)
                // A letter whose code is its base letter's (Morse č = c) would
                // make the same card twice: the first one keeps it.
                front?.takeIf { it.isNotBlank() && it != l && seen.add(it) }?.let { it to l }
            }
            if (cards.isEmpty()) null else title(lang, m) to cards
        }
    }
}
