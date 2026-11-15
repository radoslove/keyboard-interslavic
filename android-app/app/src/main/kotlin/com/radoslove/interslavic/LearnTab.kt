package com.radoslove.interslavic

import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/*
 * The app's learning tab (2026-11-15): the user's own words - the ones saved
 * with ＋ in the suggestion bar - as cards for spaced repetition, with a
 * category, a note and an example of the user's own. The list of saved words
 * lives here now (it used to sit in the settings tab), so the words gather
 * where they are learned. Storage and schedule: Learn.kt; the words: UserWords.
 */

/** Rebuilds the tab from the stored words; set when the tab is built. */
internal var learnRefresh: () -> Unit = {}

/** Category filter: null = all, "" = words without a category. */
private var learnFilter: String? = null

/** The running review: cards left, the one shown, whether its back is shown. */
private var learnQueue: MutableList<Learn.Card>? = null
private var learnRevealed = false
private var learnJustDone = false

internal fun SetupActivity.buildLearnTab(root: LinearLayout) {
    val d = resources.displayMetrics.density
    val ui = Ui.current(this)
    fun lname(lang: String) = Ui.langName(ui, lang)
    fun small(s: String, size: Float = 13.5f) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(col("#546E7A", "#9AABB4"))
        setPadding(0, 4, 0, 8)
    }

    root.addView(header(t("learn_head")))
    root.addView(small(t("learn_body")))

    val dueLine = TextView(this).apply { textSize = 17f; setTextColor(cText) }
    root.addView(dueLine)
    val cardBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
    }
    root.addView(cardBox)

    val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    root.addView(HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(catRow)
    })
    root.addView(header(t("words_head")))
    val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(listBox)

    fun filtered(all: List<Learn.Card>) = when (val f = learnFilter) {
        null -> all
        else -> all.filter { it.cat == f }
    }

    lateinit var refresh: () -> Unit

    /** The gloss of [card] from the Wiktionary tables, off the UI thread. */
    fun loadGloss(card: Learn.Card, into: TextView, credit: TextView) {
        Thread {
            var g: String? = null
            try {
                if (Gloss.has(this, card.lang)) {
                    val to = if (card.lang == Lang.PL) Gloss.targets(this, Lang.PL).firstOrNull() else Lang.PL
                    if (to != null) {
                        val w = Gloss.word(Gloss.table(this, card.lang), card.word, card.lang, to)
                        if (w != null) g = "${lname(to)}: $w"
                    }
                }
            } catch (_: Throwable) {
            }
            runOnUiThread {
                if (g != null) {
                    into.text = g
                    credit.visibility = View.VISIBLE
                } else if (card.note.isEmpty()) {
                    into.text = t("learn_nogloss")
                    into.setTextColor(cGloss)
                } else into.visibility = View.GONE
            }
        }.start()
    }

    fun showCard() {
        cardBox.removeAllViews()
        val q = learnQueue
        if (q == null) {
            val due = Learn.due(filtered(Learn.cards(this)))
            if (learnJustDone) cardBox.addView(TextView(this).apply {
                text = t("learn_done")
                textSize = 17f
                setTextColor(cText)
            })
            cardBox.addView(Button(this).apply {
                text = t("learn_start")
                isAllCaps = false
                textSize = 17f
                isEnabled = due.isNotEmpty()
                setOnClickListener {
                    learnQueue = due.toMutableList()
                    learnJustDone = false
                    learnRevealed = false
                    showCard()
                }
            })
            return
        }
        if (q.isEmpty()) {
            learnQueue = null
            learnJustDone = true
            refresh()
            return
        }
        val card = q.first()
        cardBox.addView(small(lname(card.lang) + if (card.cat.isNotEmpty()) " · ${card.cat}" else ""))
        cardBox.addView(TextView(this).apply {
            text = card.word
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cText)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        })
        if (!learnRevealed) {
            cardBox.addView(Button(this).apply {
                text = t("learn_show")
                isAllCaps = false
                textSize = 17f
                setOnClickListener { learnRevealed = true; showCard() }
            })
            return
        }
        val glossView = TextView(this).apply { textSize = 19f; setTextColor(cText) }
        val credit = TextView(this).apply {
            text = "Wiktionary · CC BY-SA 4.0"
            textSize = 12f
            setTextColor(cGloss)
            visibility = View.GONE
        }
        cardBox.addView(glossView)
        cardBox.addView(credit)
        loadGloss(card, glossView, credit)
        if (card.note.isNotEmpty()) cardBox.addView(TextView(this).apply {
            text = card.note
            textSize = 17f
            setTextColor(cText)
            setPadding(0, (8 * d).toInt(), 0, 0)
        })
        if (card.ex.isNotEmpty()) cardBox.addView(TextView(this).apply {
            text = card.ex
            textSize = 16f
            setTypeface(typeface, Typeface.ITALIC)
            setTextColor(cText)
            setPadding(0, (4 * d).toInt(), 0, 0)
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (10 * d).toInt(), 0, 0)
        }
        for ((g, key) in listOf(0 to "learn_g0", 1 to "learn_g1", 2 to "learn_g2")) row.addView(Button(this).apply {
            text = t(key)
            isAllCaps = false
            textSize = 16f
            setOnClickListener {
                Learn.grade(this@buildLearnTab, card, g)
                q.removeAt(0)
                // "Don't know" comes back at the end of the same review.
                if (g == 0) q.add(card)
                learnRevealed = false
                showCard()
                refresh()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cardBox.addView(row)
    }

    fun editCard(card: Learn.Card, cats: List<String>) {
        val pad = (20 * d).toInt()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val catBox = EditText(this).apply { hint = t("learn_cat"); setText(card.cat); isSingleLine = true }
        form.addView(catBox)
        if (cats.isNotEmpty()) {
            val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in cats) chips.addView(Button(this).apply {
                text = c
                isAllCaps = false
                narrow(this)
                setOnClickListener { catBox.setText(c) }
            })
            form.addView(HorizontalScrollView(this).apply { addView(chips) })
        }
        val noteBox = EditText(this).apply { hint = t("learn_note"); setText(card.note) }
        val exBox = EditText(this).apply { hint = t("learn_ex"); setText(card.ex) }
        form.addView(noteBox)
        form.addView(exBox)
        AlertDialog.Builder(this)
            .setTitle(card.word)
            .setView(form)
            .setPositiveButton(t("learn_save")) { _, _ ->
                card.cat = catBox.text.toString().trim()
                card.note = noteBox.text.toString().trim()
                card.ex = exBox.text.toString().trim()
                Learn.update(this, card)
                refresh()
            }
            .setNegativeButton(t("learn_cancel"), null)
            .show()
    }

    refresh = {
        val all = Learn.cards(this)
        val cats = Learn.categories(all)
        if (learnFilter != null && learnFilter != "" && learnFilter !in cats) learnFilter = null
        val shown = filtered(all)
        dueLine.text = t("learn_due").replace("{k}", Learn.due(shown).size.toString())

        catRow.removeAllViews()
        val filters = ArrayList<Pair<String?, String>>()
        if (cats.isNotEmpty()) {
            filters.add(null to t("learn_all"))
            for (c in cats) filters.add(c to c)
            if (all.any { it.cat.isEmpty() }) filters.add("" to t("learn_nocat"))
        }
        for ((f, label) in filters) catRow.addView(Button(this).apply {
            text = label
            isAllCaps = false
            narrow(this)
            alpha = if (f == learnFilter) 1f else 0.5f
            setOnClickListener {
                learnFilter = f
                learnQueue = null
                refresh()
            }
        })

        listBox.removeAllViews()
        if (all.isEmpty()) listBox.addView(small(t("words_none")))
        var lastLang = ""
        for (c in shown) {
            if (c.lang != lastLang) {
                listBox.addView(small(lname(c.lang), 16f))
                lastLang = c.lang
            }
            val info = listOfNotNull(
                c.cat.ifEmpty { null },
                if (c.isNew) t("learn_new") else Learn.daysLeft(c).takeIf { it > 0 }
                    ?.let { t("learn_in").replace("{k}", it.toString()) },
            ).joinToString(" · ")
            listBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(this@buildLearnTab).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@buildLearnTab).apply {
                        text = c.word
                        textSize = 17f
                        setTextColor(cText)
                    })
                    if (info.isNotEmpty()) addView(TextView(this@buildLearnTab).apply {
                        text = info
                        textSize = 12.5f
                        setTextColor(cGloss)
                    })
                    setOnClickListener { editCard(c, cats) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Button(this@buildLearnTab).apply {
                    text = "✎"
                    narrow(this)
                    setOnClickListener { editCard(c, cats) }
                })
                addView(Button(this@buildLearnTab).apply {
                    text = t("words_remove")
                    isAllCaps = false
                    narrow(this)
                    setOnClickListener {
                        UserWords.remove(this@buildLearnTab, c.lang, c.word)
                        toast("${t("words_removed")}: ${c.word}")
                        learnQueue?.removeAll { it.lang == c.lang && it.word == c.word }
                        refresh()
                        showCard()
                    }
                })
            })
        }
        if (learnQueue == null) showCard()
    }
    learnRefresh = refresh
    refresh()
}
