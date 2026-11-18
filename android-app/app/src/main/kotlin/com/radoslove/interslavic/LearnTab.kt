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
/** The running pass is "study" (every card, outside the schedule), not a review. */
private var learnStudy = false

/*
 * Pacing inside one pass. The cards not shown yet wait in [learnQueue]; the
 * ones already met wait in [learnLine], each put back a few places ahead
 * after its grade, so a card not known comes back soon and a known one later.
 * A new card comes in while few are still being learned, and now and then
 * anyway so a pass does not turn into one long drill, unless the last
 * answers were mostly "don't know". "Know" ends a card's pass; it comes
 * back the next day at the earliest. Nothing blocks: the numbers only set the order.
 */
private val learnLine = ArrayList<Learn.Card>()
private val learnFirstGrade = HashMap<String, Int>()
/** How often each card was not known in this pass: it then comes back a little later each time. */
private val learnMisses = HashMap<String, Int>()
private val learnRecent = ArrayList<Int>()
private var learnSinceNew = 0
private var learnCurrent: Learn.Card? = null
private const val PACE_LEARNING = 3
private const val PACE_EVERY = 8
/** Each card in play beyond [PACE_LEARNING] puts this many more cards before the next new one. */
private const val PACE_SLOWER = 4
private const val PACE_BACK_0 = 2
private const val PACE_BACK_1 = 5
private const val PACE_REST = 4
/** Morse taught by families: dots, dashes, a dash added, their mirrors, the rest. */
private const val MORSE_ORDER = "eishtmoauvndbwgkrjpzqcylfx"

private fun learnKey(c: Learn.Card) = c.lang + "\t" + c.word

private fun learnStart(cards: List<Learn.Card>) {
    // Alphabet cards whose answer is a letter: Morse in families, not A to Z.
    fun rank(c: Learn.Card): Int {
        if (!c.cat.endsWith(Script.name(Script.Mode.MORSE))) return 0
        val i = MORSE_ORDER.indexOf(c.tr.lowercase().firstOrNull() ?: ' ')
        return if (c.tr.length == 1 && i >= 0) i else MORSE_ORDER.length
    }
    // Learned cards due again first, then the ones missed before (never
    // learned yet), then the new ones in teaching order.
    val (old, rest) = cards.partition { it.reps > 0 }
    val (missed, new) = rest.partition { !it.isNew }
    learnQueue = (old + missed.sortedBy { rank(it) } + new.sortedBy { rank(it) }).toMutableList()
    learnLine.clear()
    learnFirstGrade.clear()
    learnMisses.clear()
    learnRecent.clear()
    learnSinceNew = 0
    learnCurrent = null
}

/** The next card of the pass, or null when it is over. */
private fun learnNext(): Learn.Card? {
    val pool = learnQueue ?: return null
    val struggling = learnRecent.takeLast(5).count { it == 0 } >= 3
    val takeNew = pool.isNotEmpty() && (learnLine.isEmpty() ||
        learnLine.size <= PACE_LEARNING ||
        (!struggling && learnSinceNew >= PACE_EVERY + PACE_SLOWER * (learnLine.size - PACE_LEARNING)))
    return if (takeNew) {
        learnSinceNew = 0
        pool.removeAt(0)
    } else {
        learnSinceNew++
        learnLine.removeFirstOrNull()
    }
}
/** The language being learned; null = the keyboard's current language. */
private var learnLang: String? = null
/** Whether the word lists under the card are opened. */
private var learnShowWords = false
private var learnShowMine = false
/** The script word cards are shown in (null = the language's own), to practise reading. */
private var learnScript: Script.Mode? = null

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

    // One language at a time: the cards, the review and new words follow the
    // language chosen here (the keyboard switches with it, the screen follows).
    fun curLang(): String = learnLang ?: Lang.current(this)
    fun langCards() = Learn.cards(this).filter { it.lang == curLang() }
    val langButton = Button(this).apply {
        isAllCaps = false
        textSize = 18f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }
    root.addView(langButton)

    // Topics as tiles: own categories and ready sets in one place; a set not
    // taken yet is a tile with ＋.
    val topicsHead = header(t("learn_topics"))
    root.addView(topicsHead)
    val topicGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(topicGrid)
    // Alphabets apart from the word topics; the heading needs no translation.
    val alphaHead = header("ABC · АБВ · ⰀⰁⰂ · ·−")
    root.addView(alphaHead)
    val alphaGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(alphaGrid)

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

    // The word lists stay folded until asked for: on first sight the tab is
    // language, topics and the card, nothing else.
    fun fold(toggle: () -> Unit) = TextView(this).apply {
        textSize = 19f
        setTextColor(cText)
        setPadding(0, (16 * d).toInt(), 0, (6 * d).toInt())
        setOnClickListener { toggle() }
    }
    val wordsHead = fold { learnShowWords = !learnShowWords; refresh() }
    root.addView(wordsHead)
    val wordsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(wordsBox)

    // Add a word to the chosen language (and topic, when one is chosen).
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
            val lang = curLang()
            addBox.setText("")
            // The gloss is looked up for display, not copied into the card:
            // a card's own translation is the user's, the gloss keeps its credit.
            Learn.add(this@buildLearnTab, lang, w, null)
            // With a topic chosen, the new word goes into it: that is how a
            // ready set or one's own topic grows.
            learnFilter?.takeIf { it.isNotEmpty() }?.let { cat ->
                val c = Learn.card(this@buildLearnTab, lang, w)
                if (c.cat.isEmpty()) { c.cat = cat; Learn.update(this@buildLearnTab, c) }
            }
            toast("${t("learn_added")}: $w")
            refresh()
        }
    })
    wordsBox.addView(addRow)
    val deckBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    wordsBox.addView(deckBox)

    val mineHead = fold { learnShowMine = !learnShowMine; refresh() }
    root.addView(mineHead)
    val mineSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    mineSection.addView(small(t("learn_my_body")))
    val myBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    mineSection.addView(myBox)
    root.addView(mineSection)

    fun filtered(all: List<Learn.Card>) = when (val f = learnFilter) {
        null -> all
        else -> all.filter { it.cat == f }
    }

    fun showCard() {
        cardBox.removeAllViews()
        val q = learnQueue
        if (q == null) {
            val shown = filtered(langCards())
            val due = Learn.due(shown)
            if (learnJustDone) cardBox.addView(TextView(this).apply {
                text = t("learn_done")
                textSize = 17f
                setTextColor(cText)
            })
            // Review takes the cards due now; study goes through every card of the
            // chosen category in random order, so there is always something to do
            // after the review is over.
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((key, cards, study) in listOf(
                Triple("learn_start", due, false),
                Triple("learn_study", shown.shuffled(), true),
            )) buttons.addView(Button(this).apply {
                text = t(key)
                isAllCaps = false
                textSize = 17f
                maxLines = 1
                isEnabled = cards.isNotEmpty()
                setOnClickListener {
                    learnStart(cards)
                    learnStudy = study
                    learnRevealed = false
                    learnJustDone = false
                    showCard()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            cardBox.addView(buttons)
            return
        }
        val card = learnCurrent ?: learnNext()?.also { learnCurrent = it }
        if (card == null) {
            learnQueue = null
            learnJustDone = true
            refresh()
            return
        }
        cardBox.addView(small(lname(card.lang) + if (card.cat.isNotEmpty()) " · ${card.cat}" else ""))
        // Reading practice: a word card can be shown in another script of its
        // language (Interslavic in Cyrillic or Glagolitic, Polish in Glagolitic).
        // Alphabet cards are already in their script.
        val scripts = Script.offered(card.lang)
        val alphabetCard = card.cat.contains(" → ")
        if (scripts.size > 1 && !alphabetCard) cardBox.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            for (m in scripts) addView(Button(this@buildLearnTab).apply {
                text = Script.name(m)
                isAllCaps = false
                narrow(this)
                val on = (learnScript ?: scripts.first()) == m
                alpha = if (on) 1f else 0.5f
                setOnClickListener { learnScript = if (m == scripts.first()) null else m; showCard() }
            })
        })
        val mode = learnScript?.takeIf { it in scripts && !alphabetCard }
        cardBox.addView(TextView(this).apply {
            text = if (mode == null) card.word else Script.convert(card.word, mode, card.lang)
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
        // Read in another script: the back first shows the word as written normally.
        if (mode != null) cardBox.addView(TextView(this).apply {
            text = card.word
            textSize = 20f
            setTextColor(cGloss)
            gravity = Gravity.CENTER_HORIZONTAL
        })
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
                // Studying ahead of the schedule must not stretch the intervals, so
                // there only "don't know" counts: a forgotten word is due again soon.
                val key = learnKey(card)
                val first = learnFirstGrade[key]
                // Only the first answer of a pass sets the schedule; the repeats
                // inside the pass only bring a forgotten card back sooner.
                if (first == null) {
                    learnFirstGrade[key] = g
                    if (!learnStudy || g == 0) Learn.grade(this@buildLearnTab, card, g)
                } else if (g == 0) Learn.grade(this@buildLearnTab, card, 0)
                learnRecent.add(g)
                learnCurrent = null
                // "Know" ends the card's pass: it comes back the next day at
                // the earliest, then less and less often.
                if (g == 2) {
                    // Learned in this pass: a card once forgotten here starts
                    // again from one day, not from a minute.
                    if (!learnStudy && first != null && first < 2 && card.due - System.currentTimeMillis() < 3_600_000L)
                        Learn.grade(this@buildLearnTab, card, 1)
                } else {
                    // Not to drill one card to boredom: every miss in this pass
                    // puts it one place further back, and after PACE_REST misses
                    // it waits at the end of the pass.
                    val misses = (learnMisses[key] ?: 0) + 1
                    learnMisses[key] = misses
                    val back = (if (g == 0) PACE_BACK_0 else PACE_BACK_1) + misses - 1
                    learnLine.add(if (misses >= PACE_REST) learnLine.size else minOf(back, learnLine.size), card)
                }
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
        val every = Learn.cards(this)
        val langs = Lang.enabled(this)
        learnLang = null
        fun switchTo(l: String) {
            learnFilter = null
            learnQueue = null
            learnJustDone = false
            val ime = ImeService.running
            if (ime == null || !ime.switchToLanguage(l)) Lang.setCurrent(this, l)
            if (Ui.current(this) != ui) recreate() else { refresh(); showCard() }
        }
        fun counted(l: String) = lname(l) + every.count { it.lang == l }.let { if (it > 0) " ($it)" else "" }
        // One button: the language being learned; the list offers the keyboard's
        // languages first, then ＋ the others (turned on in the keyboard when picked).
        langButton.text = "🌐  " + counted(curLang()) + "  ▾"
        langButton.setOnClickListener {
            val others = Lang.ALL.filter { it !in langs }
            val labels = (langs.map { counted(it) } + others.map { "＋ " + counted(it) }).toTypedArray()
            AlertDialog.Builder(this).setItems(labels) { _, k ->
                if (k < langs.size) { if (langs[k] != curLang()) switchTo(langs[k]) }
                else {
                    val l = others[k - langs.size]
                    Lang.setEnabled(this, Lang.enabled(this) + l)
                    switchTo(l)
                }
            }.show()
        }

        val all = langCards()
        val cats = Learn.categories(all)
        if (learnFilter != null && learnFilter != "" && learnFilter !in cats) learnFilter = null
        val shown = filtered(all)
        dueLine.text = t("learn_due").replace("{k}", Learn.due(shown).size.toString())

        // Tiles: all, every topic in use, no topic, then the ready sets not taken.
        val lang = curLang()
        val target = Learn.target(this, lang)?.takeIf { it != lang } ?: if (lang == "en") Lang.PL else "en"
        class Tile(val label: String, val on: Boolean, val tap: () -> Unit)
        val tiles = ArrayList<Tile>()
        val alphaTiles = ArrayList<Tile>()
        fun pick(f: String?) { learnFilter = f; learnQueue = null; learnJustDone = false; refresh(); showCard() }
        if (all.isNotEmpty()) tiles.add(Tile("${t("learn_all")} (${all.size})", learnFilter == null) { pick(null) })
        for (c in cats) (if (c.contains(" → ")) alphaTiles else tiles)
            .add(Tile("$c (${all.count { it.cat == c }})", learnFilter == c) { pick(c) })
        if (cats.isNotEmpty() && all.any { it.cat.isEmpty() })
            tiles.add(Tile("${t("learn_nocat")} (${all.count { it.cat.isEmpty() }})", learnFilter == "") { pick("") })
        for (set in Sets.names(this, lang)) {
            val name = Sets.title(this, set, ui)
            if (name in cats) continue
            tiles.add(Tile("＋ $name", false) {
                for ((w, tr) in Sets.words(this, set, lang, target)) {
                    val c = Learn.card(this, lang, w)
                    c.deck = true
                    if (c.tr.isEmpty()) c.tr = tr
                    if (c.cat.isEmpty()) c.cat = name
                    Learn.update(this, c)
                }
                toast("${t("learn_added")}: $name")
                pick(name)
            })
        }
        // Alphabets: the language's letters in its other scripts, as tiles too.
        for ((name, cards) in Alphabets.sets(lang)) {
            if (name in cats) continue
            alphaTiles.add(Tile("＋ $name", false) {
                for ((front, letter) in cards) {
                    val c = Learn.card(this, lang, front)
                    c.deck = true
                    if (c.tr.isEmpty()) c.tr = letter
                    if (c.cat.isEmpty()) c.cat = name
                    Learn.update(this, c)
                }
                toast("${t("learn_added")}: $name")
                pick(name)
            })
        }
        fun fill(grid: LinearLayout, tiles: List<Tile>) {
        grid.removeAllViews()
        for (row in tiles.chunked(2)) grid.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (tile in row) addView(Button(this@buildLearnTab).apply {
                text = tile.label
                isAllCaps = false
                textSize = 15f
                maxLines = 2
                alpha = if (tile.on) 1f else 0.6f
                setTypeface(typeface, if (tile.on) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener { tile.tap() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (row.size == 1) addView(View(this@buildLearnTab), LinearLayout.LayoutParams(0, 0, 1f))
        })
        }
        fill(topicGrid, tiles)
        fill(alphaGrid, alphaTiles)
        alphaHead.visibility = if (alphaTiles.isEmpty()) View.GONE else View.VISIBLE
        // A language with no word set and no cards yet: no empty heading.
        topicsHead.visibility = if (tiles.isEmpty()) View.GONE else View.VISIBLE
        // Only the scripts this language has: Spanish is "ABC · ·−", not all four.
        alphaHead.text = (Script.offered(lang).take(1) + Script.choices(lang).drop(1).filter { m ->
            Alphabets.sets(lang).any { it.first == Alphabets.title(lang, m) }
        }).joinToString(" · ") { Script.name(it) }

        wordsHead.text = (if (learnShowWords) "▾ " else "▸ ") + t("learn_words") + " (${shown.size})"
        wordsBox.visibility = if (learnShowWords) View.VISIBLE else View.GONE

        deckBox.removeAllViews()
        if (all.isEmpty()) deckBox.addView(small(t("learn_empty")))
        var lastLang = ""
        for (c in shown) {
            val tr = trOf(c.lang, c.word, c.tr) ?: if ("${c.lang}\t${c.word}" in learnGlosses) t("learn_notr") else null
            deckBox.addView(line(c.word + if (c.cat.isNotEmpty()) "  · ${c.cat}" else "", tr, { learnEdit(c, refresh) },
                smallButton("✎") { learnEdit(c, refresh) },
                smallButton("✕") {
                    Learn.drop(this, c)
                    toast("${t("learn_unlearn")}: ${c.word}")
                    learnQueue?.removeAll { it.lang == c.lang && it.word == c.word }
                    learnLine.removeAll { it.lang == c.lang && it.word == c.word }
                    if (learnCurrent?.let { it.lang == c.lang && it.word == c.word } == true) learnCurrent = null
                    refresh()
                    showCard()
                }))
        }

        // The ＋ words, each with its translation; ＋ Learn puts one among the cards.
        myBox.removeAllViews()
        val inDeck = all.map { "${it.lang}\t${it.word}" }.toSet()
        val mine = ArrayList<Pair<String, String>>()
        for (w in UserWords.all(this, curLang()).sorted()) mine.add(curLang() to w)
        if (mine.isEmpty()) myBox.addView(small(t("words_none")))
        mineHead.text = (if (learnShowMine) "▾ " else "▸ ") + t("words_head") + " (${mine.size})"
        mineSection.visibility = if (learnShowMine) View.VISIBLE else View.GONE
        lastLang = ""
        for ((lang, w) in mine) {
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
