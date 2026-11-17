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
