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
 * The app's learning tab (2026-11-15): flashcards with spaced repetition.
 *
 * The cards are words the user chose to learn - typed in here, or tapped under
 * the writing box of the try tab - each with a translation to check oneself
 * against. Below them, the `＋` words (UserWords): the keyboard was missing
 * them, so they are not necessarily unknown; one tap puts one among the cards.
 * Storage and schedule: Learn.kt.
 */

/** Rebuilds the tab; set when the tab is built. */
internal var learnRefresh: () -> Unit = {}

/** Category filter: null = all, "" = cards without a category. */
private var learnFilter: String? = null

/** The running review: cards left, whether the shown one is turned over. */
private var learnQueue: MutableList<Learn.Card>? = null
private var learnRevealed = false
private var learnJustDone = false

/** Wiktionary glosses already looked up ("" = none), "lang\tword" -> gloss. */
private val learnGlosses = HashMap<String, String>()
private var learnLooking = false

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
    lateinit var refresh: () -> Unit

    /** The card's own translation, else the gloss already looked up, else null. */
    fun trOf(lang: String, word: String, own: String = ""): String? {
        if (own.isNotEmpty()) return own
        return learnGlosses["$lang\t$word"]?.takeIf { it.isNotEmpty() }
    }

    /** Look up every gloss not looked up yet, once, then redraw. */
    fun lookUp(words: List<Pair<String, String>>) {
        val missing = words.filter { (l, w) -> "$l\t$w" !in learnGlosses }
        if (missing.isEmpty() || learnLooking) return
        learnLooking = true
        Thread {
            val found = missing.map { (l, w) -> "$l\t$w" to (Learn.gloss(this, l, w) ?: "") }
            runOnUiThread {
                for ((k, v) in found) learnGlosses[k] = v
                learnLooking = false
                refresh()
            }
        }.start()
    }

    root.addView(header(t("learn_head")))
    root.addView(small(t("learn_body")))

    // Add a word to learn, in the keyboard's current language.
    val addRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val addBox = EditText(this).apply {
        hint = t("learn_add_hint")
        isSingleLine = true
        textSize = 18f
    }
    addRow.addView(addBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    addRow.addView(Button(this).apply {
        text = t("learn_add")
        isAllCaps = false
        narrow(this)
        setOnClickListener {
            val w = addBox.text.toString().trim()
            if (w.isEmpty()) return@setOnClickListener
            val lang = Lang.current(this@buildLearnTab)
            addBox.setText("")
            // The gloss is looked up for display, not copied into the card:
            // a card's own translation is the user's, the gloss keeps its credit.
            Learn.add(this@buildLearnTab, lang, w, null)
            toast("${t("learn_added")}: $w")
            refresh()
        }
    })
    root.addView(addRow)

    val dueLine = TextView(this).apply {
        textSize = 17f
        setTextColor(cText)
        setPadding(0, (10 * d).toInt(), 0, 0)
    }
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
    root.addView(header(t("learn_deck_head")))
    val deckBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(deckBox)
    root.addView(header(t("words_head")))
    root.addView(small(t("learn_my_body")))
    val myBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(myBox)

    fun filtered(all: List<Learn.Card>) = when (val f = learnFilter) {
        null -> all
        else -> all.filter { it.cat == f }
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
                    learnRevealed = false
                    learnJustDone = false
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
        // The back: the translation to check oneself against.
        val tr = trOf(card.lang, card.word, card.tr)
        cardBox.addView(TextView(this).apply {
            text = tr ?: t("learn_notr")
            textSize = if (tr != null) 24f else 17f
            setTextColor(if (tr != null) cText else cGloss)
            gravity = Gravity.CENTER_HORIZONTAL
            if (tr == null) setOnClickListener { learnEdit(card, refresh) }
        })
        if (card.tr.isEmpty() && tr != null) cardBox.addView(TextView(this).apply {
            text = "Wiktionary · CC BY-SA 4.0"
            textSize = 11f
            setTextColor(cGloss)
            gravity = Gravity.CENTER_HORIZONTAL
        })
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
            textSize = 15f
            maxLines = 1
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

    /** One line of a list: the word, its translation under it, buttons on the right. */
    fun line(word: String, tr: String?, onTap: (() -> Unit)?, vararg buttons: Button) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(this@buildLearnTab).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@buildLearnTab).apply {
                    text = word
                    textSize = 17f
                    setTextColor(cText)
                })
                addView(TextView(this@buildLearnTab).apply {
                    text = tr ?: "…"
                    textSize = 14.5f
                    setTextColor(if (tr != null) cText else cGloss)
                    alpha = if (tr != null) 0.8f else 1f
                })
                if (onTap != null) setOnClickListener { onTap() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            for (b in buttons) addView(b)
        }

    fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        narrow(this)
        setOnClickListener { onClick() }
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
        for ((f, label) in filters) catRow.addView(smallButton(label) {
            learnFilter = f
            learnQueue = null
            refresh()
        }.apply { alpha = if (f == learnFilter) 1f else 0.5f })

        // The cards: word and translation; tap or ✎ to edit, ✕ out of the deck.
        deckBox.removeAllViews()
        if (all.isEmpty()) deckBox.addView(small(t("learn_empty")))
        var lastLang = ""
        for (c in shown) {
            if (c.lang != lastLang) {
                deckBox.addView(small(lname(c.lang), 16f))
                lastLang = c.lang
            }
            val tr = trOf(c.lang, c.word, c.tr) ?: if ("${c.lang}\t${c.word}" in learnGlosses) t("learn_notr") else null
            deckBox.addView(line(c.word + if (c.cat.isNotEmpty()) "  · ${c.cat}" else "", tr, { learnEdit(c, refresh) },
                smallButton("✎") { learnEdit(c, refresh) },
                smallButton("✕") {
                    Learn.drop(this, c)
                    toast("${t("learn_unlearn")}: ${c.word}")
                    learnQueue?.removeAll { it.lang == c.lang && it.word == c.word }
                    refresh()
                    showCard()
                }))
        }

        // The ＋ words, each with its translation; ＋ Learn puts one among the cards.
        myBox.removeAllViews()
        val inDeck = all.map { "${it.lang}\t${it.word}" }.toSet()
        val mine = ArrayList<Pair<String, String>>()
        for (lang in Lang.ALL) for (w in UserWords.all(this, lang).sorted()) mine.add(lang to w)
        if (mine.isEmpty()) myBox.addView(small(t("words_none")))
        lastLang = ""
        for ((lang, w) in mine) {
            if (lang != lastLang) {
                myBox.addView(small(lname(lang), 16f))
                lastLang = lang
            }
            val key = "$lang\t$w"
            val tr = trOf(lang, w) ?: if (key in learnGlosses) "—" else null
            val buttons = ArrayList<Button>()
            if (key !in inDeck) buttons.add(smallButton(t("learn_add")) {
                Learn.add(this, lang, w, null)
                toast("${t("learn_added")}: $w")
                refresh()
            })
            buttons.add(smallButton(t("words_remove")) {
                UserWords.remove(this, lang, w)
                toast("${t("words_removed")}: $w")
                refresh()
            })
            myBox.addView(line(w, tr, null, *buttons.toTypedArray()))
        }

        lookUp(all.filter { it.tr.isEmpty() }.map { it.lang to it.word } + mine)
        if (learnQueue == null) showCard()
    }
    learnRefresh = refresh
    refresh()
}

/** Edit a card: translation, category (with the ones in use as buttons), note, example. */
internal fun SetupActivity.learnEdit(card: Learn.Card, after: () -> Unit) {
    val d = resources.displayMetrics.density
    val pad = (20 * d).toInt()
    val cats = Learn.categories(Learn.cards(this))
    val form = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
    }
    val trBox = EditText(this).apply { hint = t("learn_tr"); setText(card.tr) }
    form.addView(trBox)
    // Offered from Wiktionary; kept as the card's own only when changed.
    var offered: String? = null
    if (card.tr.isEmpty()) Thread {
        val g = Learn.gloss(this, card.lang, card.word)
        if (g != null) runOnUiThread { if (trBox.text.isEmpty()) { offered = g; trBox.setText(g) } }
    }.start()
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
            val tr = trBox.text.toString().trim()
            card.tr = if (tr == offered) "" else tr
            card.cat = catBox.text.toString().trim()
            card.note = noteBox.text.toString().trim()
            card.ex = exBox.text.toString().trim()
            Learn.update(this, card)
            after()
        }
        .setNegativeButton(t("learn_cancel"), null)
        .show()
}
