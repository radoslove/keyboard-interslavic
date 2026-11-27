package com.radoslove.interslavic

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
/** Cards in the running pass when it began, for the "3 / 12" over the card. */
private var learnPassTotal = 0
/** Ends the running pass on the system Back key; true = it was ended (else
 *  Back left the whole app in the middle of a card). */
internal var learnBack: (() -> Boolean)? = null

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
/** One sign with one answer is one card to the learner, whatever language it
 *  was taken under (Morse `··` = i in isv, nb, es...): a pass and its counts. */
private fun learnOnce(cards: List<Learn.Card>) = cards.distinctBy {
    if (isAlphabet(it)) "a\t${it.word}\t${it.tr}" else "w\t${it.lang}\t${it.word}"
}

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
    learnPassTotal = cards.size
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
private var learnShowLetters = false
/** The script word cards are shown in (null = the language's own), to practise reading. */
private var learnScript: Script.Mode? = null
/** The tab shows words (false) or alphabets (true); kept between launches (iOS `learn_alpha_mode`). */
private var learnAlphaMode: Boolean? = null
/** The two alphabet wheels: from which script, into which. */
private var learnAlphaFrom: Script.Mode? = null
private var learnAlphaTo: Script.Mode? = null
/** The filter value for every alphabet card at once. */
private const val ALL_ALPHA = "\u0001alphabets"

/** Wiktionary glosses already looked up ("" = none), "lang\tword" -> gloss. */
private val learnGlosses = HashMap<String, String>()
private var learnLooking = false

private fun isAlphabet(c: Learn.Card) = c.cat.contains(" → ")
private fun isAlphaFilter(f: String?) = f != null && (f == ALL_ALPHA || f.contains(" → "))

internal fun SetupActivity.buildLearnTab(root: LinearLayout) {
    val d = resources.displayMetrics.density
    val ui = Ui.current(this)
    val prefs = getSharedPreferences("isv_collector", android.content.Context.MODE_PRIVATE)
    if (learnAlphaMode == null) learnAlphaMode = prefs.getBoolean("learn_alpha_mode", false)
    fun lname(lang: String) = Ui.langName(ui, lang)
    // Colour only where there is something to press or a state to read (owner
    // 261127: the buttons should stand out, the rest stay quiet). The blue is
    // the icon's own: white text on it 5.1:1. The other buttons get a surface
    // with an outline 3:1 to the page (the grey button was 1.2:1, recognised
    // only by its round corners). The yellow of the icon marks the cards due.
    val cAccent = Color.parseColor("#3A66D9")
    val cCount = col("#8A6D00", "#F5C400")
    val cMuted = col("#5F6B73", "#8A99A1")
    val cMiss = col("#B3261E", "#FF8A80")
    val cEdge = col("#7D8C96", "#5F6B73")
    fun look(b: Button, fill: Int, edge: Int, width: Float, ink: Int) {
        b.background = InsetDrawable(GradientDrawable().apply {
            cornerRadius = 18 * d
            setColor(fill)
            if (width > 0) setStroke((width * d).toInt(), edge)
        }, (3 * d).toInt(), (4 * d).toInt(), (3 * d).toInt(), (4 * d).toInt())
        b.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        b.setTextColor(ink)
        b.stateListAnimator = null
        b.alpha = if (b.isEnabled) 1f else 0.4f
    }
    fun plain(b: Button, edge: Int = cEdge, ink: Int = cText) = look(b, col("#ECEFF1", "#262A2E"), edge, 1f, ink)
    fun accent(b: Button) = if (b.isEnabled) look(b, cAccent, cAccent, 0f, Color.WHITE) else plain(b)
    fun chosen(b: Button) = look(b, col("#DCE5FB", "#2A3550"), cAccent, 2f, col("#2F57C4", "#AFC4FF"))
    /** "3 / 11": the cards due now in the count colour, then how many there are. */
    fun counts(all: List<Learn.Card>): CharSequence = SpannableStringBuilder().apply {
        val cards = learnOnce(all)
        val due = Learn.due(cards).size.toString()
        append(due)
        setSpan(ForegroundColorSpan(cCount), 0, due.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), 0, due.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(" / ${cards.size}")
    }
    fun small(s: String, size: Float = 13.5f) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(cMuted)
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

    // One language at a time: the cards, the review and new words follow the
    // language chosen with 🌐 (the keyboard switches with it, the screen follows).
    // Alphabet cards of every language are there too, each kept under its own
    // language: alphabets do not depend on the language being learned.
    fun curLang(): String = learnLang ?: Lang.current(this)
    fun langCards() = Learn.cards(this).filter { it.lang == curLang() || isAlphabet(it) }
    /** The topic a card is shown under: its category, and for an alphabet of
     *  another language that language's code in front ("NB · ABC → ᚠᚢᚦ"). */
    // Alphabets do not belong to a language (owner 261127: "język tutaj nie
    // powinien być uwzględniany w ogóle"): one deck per pair of scripts,
    // whatever language a card was once taken under.
    fun topic(c: Learn.Card) = c.cat
    /** Words and alphabets never mix: "all" is every word, the alphabets have
     *  their own "all" (owner 261118: learning alphabets, the words barged in). */
    fun filtered(all: List<Learn.Card>) = when (val f = learnFilter) {
        null -> all.filter { !isAlphabet(it) }
        ALL_ALPHA -> all.filter { isAlphabet(it) }
        else -> all.filter { topic(it) == f }
    }
    fun pick(f: String?) {
        learnFilter = f; learnQueue = null; learnCurrent = null; learnJustDone = false
        refresh()
    }

    // The review first, high up: what the tab is for (iOS 261118).
    val dueLine = TextView(this).apply {
        textSize = 18f
        setTextColor(cText)
        setPadding(0, (4 * d).toInt(), 0, 0)
    }
    root.addView(dueLine)
    val cardBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
    }
    root.addView(cardBox)

    // Then what to learn: words of a language, or alphabets.
    val modeRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, (8 * d).toInt(), 0, (4 * d).toInt())
    }
    // What to learn comes first: it says what the count under it is about.
    root.addView(modeRow, root.indexOfChild(dueLine))
    val modeButtons = listOf(false to t("learn_words"), true to "ABC  А  Ⰰ  ᚠ  ·−").map { (on, label) ->
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            maxLines = 1
            setOnClickListener {
                if (learnAlphaMode == on) return@setOnClickListener
                learnAlphaMode = on
                prefs.edit().putBoolean("learn_alpha_mode", on).apply()
                learnScript = null
                pick(null)
            }
        }.also { modeRow.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    // ---- Words ----
    val wordsSec = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(wordsSec)
    // The how-to only while there is nothing yet (it took 13 % of the screen
    // above 23 cards).
    val wordsBody = small(t("learn_body"))
    wordsSec.addView(wordsBody)
    fun wheel() = android.widget.NumberPicker(this).apply {
        wrapSelectorWheel = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    fun wheelRow(a: android.widget.NumberPicker, b: android.widget.NumberPicker) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@buildLearnTab).apply {
            text = "→"
            textSize = 20f
            setTextColor(cGloss)
            setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
        })
        addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    val handler = android.os.Handler(mainLooper)
    // Two wheels, as for the alphabets: the language learned -> the language
    // its words are translated into (owner 261127: only Polish was shown and
    // nothing said the words came in English).
    val langWheel = wheel()
    val trWheel = wheel()
    wordsSec.addView(wheelRow(langWheel, trWheel))
    var wheelLangs: List<String> = emptyList()
    var wheelTrs: List<String> = emptyList()
    var onLangWheel: (String) -> Unit = {}
    val langStop = Runnable {
        wheelLangs.getOrNull(langWheel.value)?.let { if (it != curLang()) onLangWheel(it) }
    }
    langWheel.setOnValueChangedListener { _, _, _ ->
        handler.removeCallbacks(langStop)
        handler.postDelayed(langStop, 450)
    }
    // Only Polish has more than one language to translate into (Wiktionary);
    // the choice is the writing box's one, so both say the same.
    val trStop = Runnable {
        val to = wheelTrs.getOrNull(trWheel.value) ?: return@Runnable
        if (curLang() != Lang.PL || to == Learn.target(this, Lang.PL)) return@Runnable
        prefs.edit().putString("gloss_target_pl", to).apply()
        learnGlosses.keys.removeAll { it.startsWith(Lang.PL + "\t") }
        refresh()
    }
    trWheel.setOnValueChangedListener { _, _, _ ->
        handler.removeCallbacks(trStop)
        handler.postDelayed(trStop, 450)
    }
    // Topics as tiles: own categories and ready sets in one place; a set not
    // taken yet is a tile with ＋.
    val topicsHead = header(t("learn_topics"))
    wordsSec.addView(topicsHead)
    val topicGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    wordsSec.addView(topicGrid)

    // The lists stay folded until asked for.
    fun fold(toggle: () -> Unit) = TextView(this).apply {
        textSize = 16f
        setTextColor(cMuted)
        setPadding(0, (16 * d).toInt(), 0, (6 * d).toInt())
        setOnClickListener { toggle() }
    }
    val wordsHead = fold { learnShowWords = !learnShowWords; refresh() }
    wordsSec.addView(wordsHead)
    val wordsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    wordsSec.addView(wordsBox)

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
            // With a word topic chosen, the new word goes into it: that is how a
            // ready set or one's own topic grows. Never into an alphabet.
            learnFilter?.takeIf { it.isNotEmpty() && !isAlphaFilter(it) }?.let { cat ->
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
    wordsSec.addView(mineHead)
    val mineSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    mineSection.addView(small(t("learn_my_body")))
    val myBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    mineSection.addView(myBox)
    wordsSec.addView(mineSection)

    // ---- Alphabets ----
    val alphaSec = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(alphaSec)
    // No heading: the mode switch above already names the alphabets, and the
    // wheels below show them (owner 261121).
    val alphaBody = small(t("learn_alpha_body"))
    alphaSec.addView(alphaBody)
    // The wheels stay on the screen (owner 261127: the alphabets need them);
    // under them the pairs taken, as tiles like the word topics, each with its
    // cards due and in all.
    val fromWheel = wheel()
    val toWheel = wheel()
    val alphaWheels = wheelRow(fromWheel, toWheel)
    alphaSec.addView(alphaWheels)
    val alphaGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    alphaSec.addView(alphaGrid)
    val sheetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    alphaSec.addView(sheetBox)
    val lettersHead = fold { learnShowLetters = !learnShowLetters; refresh() }
    alphaSec.addView(lettersHead)
    val lettersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    alphaSec.addView(lettersBox)

    /** The letters' language and its scripts, as the wheels show them now. */
    // The letters come from Interslavic, the one language written in Latin,
    // Cyrillic and Glagolitic alike: ABC, АБВ, ⰀⰁⰂ, the runes, Morse.
    fun alphaSrc() = Lang.ISV
    fun alphaAll() = Alphabets.scripts(alphaSrc())
    fun alphaFrom() = alphaAll().let { all -> all.firstOrNull { it == learnAlphaFrom } ?: all.first() }
    // Both wheels hold every script (owner 261121): a right wheel without the
    // left one's script shifted under the finger when the left one turned,
    // and runes -> Latin landed on Morse.
    fun alphaTos() = alphaAll()
    fun alphaTo() = alphaAll().let { all -> all.firstOrNull { it == learnAlphaTo }
        ?: all.firstOrNull { it != alphaFrom() } ?: all.first() }
    @Suppress("UNUSED_PARAMETER")
    fun shownAs(src: String, name: String) = name
    fun taken(src: String, name: String): Int {
        val t = shownAs(src, name)
        return langCards().count { topic(it) == t }
    }

    fun setWheel(p: android.widget.NumberPicker, labels: Array<String>, v: Int) {
        if (!(p.displayedValues?.contentEquals(labels) ?: false)) {
            p.displayedValues = null
            p.minValue = 0
            p.maxValue = labels.size - 1
            p.displayedValues = labels
        }
        if (p.value != v) p.value = v
    }

    /** A ready set or an alphabet into the cards, under [from]'s language
     *  (an alphabet of another language stays that language's). */
    fun take(name: String, pairs: List<Pair<String, String>>, from: String) {
        for ((w, tr) in pairs) {
            val c = Learn.card(this, from, w)
            c.deck = true
            if (c.tr.isEmpty()) c.tr = tr
            if (c.cat.isEmpty()) c.cat = name
            Learn.update(this, c)
        }
        val shown = shownAs(from, name)
        toast("${t("learn_added")}: $shown")
        pick(shown)
    }

    /** The cheat sheet under the wheels: the script translated INTO (the right
     *  wheel) with the Latin letters under each sign, whatever the left wheel
     *  says, redrawn as the wheel turns (owner 261127). With ABC on the right,
     *  the left wheel's script. Runes big: two of them can differ only by the
     *  slant of one twig (ᛅ a / ᚾ n). */
    fun drawSheet() {
        sheetBox.removeAllViews()
        val src = alphaSrc()
        val to = alphaTo().let { if (it == Script.Mode.LATIN) alphaFrom() else it }
        if (to == Script.Mode.LATIN) return
        var pairs = Alphabets.pair(src, Script.Mode.LATIN, to)
        // Glagolitic: then the Polish letters Interslavic has not (ą ę ł ń ó
        // ś ź ż), in the Polish spelling the keyboard writes (owner 261127).
        if (to == Script.Mode.GLAGOLITIC) {
            val known = pairs.flatMap { it.second.split(" / ") }.toSet()
            pairs = pairs + Alphabets.pair(Lang.PL, Script.Mode.LATIN, to)
                .filter { p -> p.second.split(" / ").none { it in known } }
        }
        val morse = to == Script.Mode.MORSE
        val runes = to == Script.Mode.RUNES_A || to == Script.Mode.RUNES_B || to == Script.Mode.RUNES_E
        // Five columns for every script but Morse, so the sheets look alike
        // (owner 261127); Morse codes are wider, four fit.
        val cols = if (morse) 4 else 5
        for (chunk in pairs.chunked(cols)) sheetBox.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * d).toInt(), 0, 0)
            for ((sign, letter) in chunk) addView(LinearLayout(this@buildLearnTab).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(this@buildLearnTab).apply {
                    text = sign
                    textSize = if (morse) 20f else if (runes) 36f else 30f
                    if (morse) setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(cText)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                addView(TextView(this@buildLearnTab).apply {
                    text = letter
                    textSize = 15f
                    setTextColor(cMuted)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            for (i in chunk.size until cols) addView(View(this@buildLearnTab), LinearLayout.LayoutParams(0, 0, 1f))
        })
    }

    // A turned wheel landing on a pair already taken studies that pair; one
    // not taken is offered as the "＋" tile. Waits until the wheel stops.
    val follow = Runnable {
        val src = alphaSrc()
        val name = Alphabets.title(alphaFrom(), alphaTo())
        pick(shownAs(src, name))
    }
    fun followSoon() { handler.removeCallbacks(follow); handler.postDelayed(follow, 350) }
    fromWheel.setOnValueChangedListener { _, _, n ->
        learnAlphaFrom = alphaAll().getOrNull(n) ?: return@setOnValueChangedListener
        if (learnAlphaTo == null) learnAlphaTo = alphaTo()
        drawSheet()
        followSoon()
    }
    toWheel.setOnValueChangedListener { _, _, n ->
        learnAlphaFrom = alphaFrom()
        learnAlphaTo = alphaTos().getOrNull(n) ?: return@setOnValueChangedListener
        drawSheet()
        followSoon()
    }

    /** The name of the deck being studied, as its tile says it. */
    fun deckName(): String = when (val f = learnFilter) {
        null, ALL_ALPHA -> t("learn_all")
        "" -> t("learn_nocat")
        else -> f
    }

    fun stopPass() {
        learnQueue = null
        learnCurrent = null
        learnLine.clear()
        learnRevealed = false
        refresh()
    }
    learnBack = { if (learnQueue != null && cardBox.isShown) { stopPass(); true } else false }

    fun showCard() {
        cardBox.removeAllViews()
        val q = learnQueue
        // A pass has the screen to itself: what to learn waits under it only
        // while no pass runs (it took 53 % of the screen under the card).
        val running = q != null
        learnPassing(running)
        modeRow.visibility = if (running) View.GONE else View.VISIBLE
        dueLine.visibility = if (running) View.GONE else View.VISIBLE
        if (running) { wordsSec.visibility = View.GONE; alphaSec.visibility = View.GONE }
        else {
            wordsSec.visibility = if (learnAlphaMode == true) View.GONE else View.VISIBLE
            alphaSec.visibility = if (learnAlphaMode == true) View.VISIBLE else View.GONE
        }
        if (q == null) {
            val shown = filtered(langCards())
            val due = Learn.due(shown)
            if (learnJustDone) cardBox.addView(TextView(this).apply {
                text = t("learn_done")
                textSize = 17f
                setTextColor(cText)
            })
            // Review takes the cards due now; study goes through every card of the
            // chosen topic in random order, so there is always something to do
            // after the review is over - except what was known today already.
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((key, cards, study) in listOf(
                Triple("learn_start", due, false),
                Triple("learn_study", shown.filter { !Learn.knownToday(this, it) }.shuffled(), true),
            )) buttons.addView(Button(this).apply {
                text = t(key)
                isAllCaps = false
                textSize = 17f
                maxLines = 1
                isEnabled = cards.isNotEmpty()
                if (!study) accent(this) else plain(this)
                setOnClickListener {
                    // One sign with one answer is one card to the learner, whatever
                    // language it was taken under (Morse `··` = i in isv, nb, es...).
                    learnStart(learnOnce(cards))
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
        // Where the pass is, and the way out of it.
        val left = q.size + learnLine.size + 1
        cardBox.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@buildLearnTab).apply {
                text = "${maxOf(1, learnPassTotal - left + 1)} / $learnPassTotal · ${deckName()}"
                textSize = 15f
                setTextColor(cMuted)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@buildLearnTab).apply {
                text = "✕"
                isAllCaps = false
                textSize = 16f
                plain(this)
                setOnClickListener { stopPass() }
            })
        })
        cardBox.addView(small(lname(card.lang) + if (card.cat.isNotEmpty()) " · ${card.cat}" else ""))
        // Reading practice: a word card can be shown in another script of its
        // language (Interslavic in Cyrillic or Glagolitic, Polish in Glagolitic).
        // Alphabet cards are already in their script.
        val scripts = Script.offered(card.lang)
        val alphabetCard = isAlphabet(card)
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
        val morse = card.cat.endsWith(Script.name(Script.Mode.MORSE))
        cardBox.addView(TextView(this).apply {
            text = if (mode == null) card.word else Script.convert(card.word, mode, card.lang)
            // A sign to learn is drawn big: a Morse "−−" at 32 sp was 11 px
            // high. 56 sp is a step on a ladder (48/56/64) for the eye to settle.
            textSize = if (alphabetCard) 56f else 32f
            if (alphabetCard && morse) letterSpacing = 0.15f
            setTypeface(if (morse) Typeface.MONOSPACE else typeface, Typeface.BOLD)
            setTextColor(cText)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        })
        if (!learnRevealed) {
            cardBox.addView(Button(this).apply {
                text = t("learn_show")
                isAllCaps = false
                textSize = 17f
                accent(this)
                setOnClickListener { learnRevealed = true; showCard() }
            })
            return
        }
        // The back: the translation first (what is checked), the word as
        // normally written under it when it was read in another script (iOS
        // 261118; owner: Latin first is no gain for a Pole).
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
        if (mode != null) cardBox.addView(TextView(this).apply {
            text = card.word
            textSize = 18f
            setTextColor(cGloss)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (4 * d).toInt(), 0, 0)
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
            when (g) { 0 -> plain(this, cMiss, cMiss); 1 -> plain(this); else -> accent(this) }
            setOnClickListener {
                val key = learnKey(card)
                val first = learnFirstGrade[key]
                // Only the first answer of a pass sets the schedule; the repeats
                // inside the pass only bring a forgotten card back sooner.
                // Studying ahead of the schedule must not stretch the intervals,
                // so there only "don't know" counts - but a card already due is
                // not studied ahead, so its answer counts (iOS 261118: "h" known
                // in study, again and again).
                // The same sign with the same answer under other languages
                // (owner 261121: "i" known several times today, shown again).
                val twins = if (isAlphabet(card)) Learn.cards(this@buildLearnTab)
                    .filter { it !== card && isAlphabet(it) && it.word == card.word && it.tr == card.tr }
                    else emptyList()
                if (first == null) {
                    learnFirstGrade[key] = g
                    if (!learnStudy || g == 0 || card.due <= System.currentTimeMillis())
                        for (c in listOf(card) + twins) Learn.grade(this@buildLearnTab, c, g)
                } else if (g == 0) for (c in listOf(card) + twins) Learn.grade(this@buildLearnTab, c, 0)
                learnRecent.add(g)
                learnCurrent = null
                // "Know" ends the card's pass: it comes back the next day at
                // the earliest, then less and less often - and not today in
                // "study" either.
                if (g == 2) {
                    for (c in listOf(card) + twins) Learn.markKnown(this@buildLearnTab, c)
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

    /** One line of a list: the word, its translation under it, the topic small
     *  and grey at the right (not glued to the word: a Morse word "·" next to
     *  " · ABC → ·−" read as one string), buttons last. */
    fun line(word: String, tr: String?, cat: String, morse: Boolean, onTap: (() -> Unit)?, vararg buttons: Button) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(this@buildLearnTab).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@buildLearnTab).apply {
                    text = word
                    textSize = 17f
                    setTextColor(cText)
                    if (morse) setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                })
                addView(TextView(this@buildLearnTab).apply {
                    text = tr ?: "…"
                    textSize = 14.5f
                    setTextColor(if (tr != null) cText else cGloss)
                    alpha = if (tr != null) 0.8f else 1f
                })
                if (onTap != null) setOnClickListener { onTap() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (cat.isNotEmpty()) addView(TextView(this@buildLearnTab).apply {
                text = cat
                textSize = 12f
                setTextColor(cGloss)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = (130 * d).toInt()
                setPadding((6 * d).toInt(), 0, (4 * d).toInt(), 0)
            })
            for (b in buttons) addView(b)
        }

    fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        narrow(this)
        setOnClickListener { onClick() }
    }

    fun unlearn(c: Learn.Card) {
        Learn.drop(this, c)
        toast("${t("learn_unlearn")}: ${c.word}")
        learnQueue?.removeAll { it.lang == c.lang && it.word == c.word }
        learnLine.removeAll { it.lang == c.lang && it.word == c.word }
        if (learnCurrent?.let { it.lang == c.lang && it.word == c.word } == true) learnCurrent = null
        refresh()
        showCard()
    }

    refresh = {
        val every = Learn.cards(this)
        val langs = Lang.enabled(this)
        learnLang = null
        val alphaMode = learnAlphaMode == true
        fun switchTo(l: String) {
            learnFilter = null
            learnQueue = null
            learnJustDone = false
            val ime = ImeService.running
            if (ime == null || !ime.switchToLanguage(l)) Lang.setCurrent(this, l)
            if (Ui.current(this) != ui) recreate() else refresh()
        }
        fun counted(l: String) = lname(l) + every.count { it.lang == l && !isAlphabet(it) }.let { if (it > 0) " ($it)" else "" }
        // The keyboard's languages, Interslavic first as in the keyboard. A
        // wheel is narrow: the name in the screen's language only ("Español
        // (hiszpański)" was cut on both sides).
        fun short(l: String): String {
            val n = lname(l)
            val s = if (n.endsWith(")") && "(" in n) n.substringAfterLast("(").removeSuffix(")") else n
            return s.replaceFirstChar { it.uppercase() }
        }
        wheelLangs = langs.sortedWith(compareBy({ it != Lang.ISV }, { it }))
        onLangWheel = { switchTo(it) }
        setWheel(langWheel, wheelLangs.map { l ->
            short(l) + every.count { it.lang == l && !isAlphabet(it) }.let { if (it > 0) " ($it)" else "" }
        }.toTypedArray(),
            wheelLangs.indexOf(curLang()).coerceAtLeast(0))
        wheelTrs = if (curLang() == Lang.PL) Gloss.targets(this, Lang.PL) else listOf(Lang.PL)
        val trNow = Learn.target(this, curLang()) ?: wheelTrs.firstOrNull()
        if (wheelTrs.isNotEmpty()) setWheel(trWheel, wheelTrs.map { short(it) }.toTypedArray(),
            wheelTrs.indexOf(trNow).coerceAtLeast(0))

        val all = langCards()
        val lang = curLang()
        // Every topic in use, the language's own first, alphabetically.
        val topics = all.sortedWith(compareBy({ if (it.lang != lang) 1 else 0 }, { topic(it).lowercase() }))
            .map { topic(it) }.filter { it.isNotEmpty() }.distinct()
        val f = learnFilter
        if (f != null && f != "" && !isAlphaFilter(f) && f !in topics) learnFilter = null
        // The filter belongs to the section shown.
        // Alphabets: the pair on the wheels is the one studied (iOS 261122).
        if (alphaMode && !isAlphaFilter(learnFilter)) {
            learnFilter = shownAs(alphaSrc(), Alphabets.title(alphaFrom(), alphaTo()))
            learnQueue = null
        }
        if (!alphaMode && isAlphaFilter(learnFilter)) { learnFilter = null; learnQueue = null }
        val shown = filtered(all)
        // The count says which deck it is about (it jumped 243 -> 23 between
        // the modes with nothing saying why).
        dueLine.text = SpannableStringBuilder().apply {
            append(deckName())
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" · ")
            val due = t("learn_due").replace("{к}", "{k}").split("{k}")
            append(due[0])
            val n = Learn.due(learnOnce(shown)).size.toString()
            val at = length
            append(n)
            setSpan(ForegroundColorSpan(cCount), at, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), at, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (due.size > 1) append(due[1])
        }
        for ((i, b) in modeButtons.withIndex()) {
            if ((i == 1) == alphaMode) chosen(b) else plain(b, ink = col("#37474F", "#B0BEC5"))
        }
        val running = learnQueue != null
        wordsSec.visibility = if (alphaMode || running) View.GONE else View.VISIBLE
        alphaSec.visibility = if (alphaMode && !running) View.VISIBLE else View.GONE

        class Tile(val label: CharSequence, val on: Boolean, val add: Boolean = false, val tap: () -> Unit)
        fun fill(grid: LinearLayout, tiles: List<Tile>) {
            grid.removeAllViews()
            for (row in tiles.chunked(2)) grid.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                for (tile in row) addView(Button(this@buildLearnTab).apply {
                    text = tile.label
                    isAllCaps = false
                    textSize = 15f
                    maxLines = 3
                    when {
                        tile.on -> chosen(this)
                        tile.add -> plain(this, ink = cMuted)
                        else -> plain(this)
                    }
                    setTypeface(typeface, if (tile.on) Typeface.BOLD else Typeface.NORMAL)
                    setOnClickListener { tile.tap() }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (row.size == 1) addView(View(this@buildLearnTab), LinearLayout.LayoutParams(0, 0, 1f))
            })
        }

        if (!alphaMode) {
            // Tiles: all words, every word topic in use, no topic, then the ready sets not taken.
            val words = all.filter { !isAlphabet(it) }
            val target = Learn.target(this, lang)?.takeIf { it != lang } ?: if (lang == "en") Lang.PL else "en"
            val tiles = ArrayList<Tile>()
            fun tile(name: String, cards: List<Learn.Card>) = SpannableStringBuilder(name + "\n").append(counts(cards))
            if (words.isNotEmpty()) tiles.add(Tile(tile(t("learn_all"), words), learnFilter == null) { pick(null) })
            for (c in topics) if (!c.contains(" → "))
                tiles.add(Tile(tile(c, all.filter { topic(it) == c }), learnFilter == c) { pick(c) })
            if (topics.any { !it.contains(" → ") } && words.any { it.cat.isEmpty() })
                tiles.add(Tile(tile(t("learn_nocat"), words.filter { it.cat.isEmpty() }), learnFilter == "") { pick("") })
            for (set in Sets.names(this, lang)) {
                val name = Sets.title(this, set, ui)
                if (name in topics) continue
                tiles.add(Tile("＋ $name", false, true) { take(name, Sets.words(this, set, lang, target), lang) })
            }
            fill(topicGrid, tiles)
            // A language with no word set and no cards yet: no empty heading.
            topicsHead.visibility = if (tiles.isEmpty()) View.GONE else View.VISIBLE
            wordsBody.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE

            // "My words": only word cards, never letters; with an alphabet chosen, every word.
            val wordList = (if (isAlphaFilter(learnFilter)) all else shown).filter { !isAlphabet(it) }
            wordsHead.text = (if (learnShowWords) "▾ " else "▸ ") + t("words_head") + " (${wordList.size})"
            wordsBox.visibility = if (learnShowWords) View.VISIBLE else View.GONE
            deckBox.removeAllViews()
            if (words.isEmpty()) deckBox.addView(small(t("learn_empty")))
            for (c in wordList) {
                val tr = trOf(c.lang, c.word, c.tr) ?: if ("${c.lang}\t${c.word}" in learnGlosses) t("learn_notr") else null
                deckBox.addView(line(c.word, tr, if (learnFilter == topic(c)) "" else topic(c), false, { learnEdit(c, refresh) },
                    smallButton("✎") { learnEdit(c, refresh) },
                    smallButton("✕") { unlearn(c) }))
            }

            // "Words from the keyboard": the ＋ words, each with its translation;
            // ＋ Learn puts one among the cards.
            myBox.removeAllViews()
            val inDeck = all.map { "${it.lang}\t${it.word}" }.toSet()
            val mine = UserWords.all(this, lang).sorted().map { lang to it }
            if (mine.isEmpty()) myBox.addView(small(t("words_none")))
            mineHead.text = (if (learnShowMine) "▾ " else "▸ ") + t("learn_kbd_words") + " (${mine.size})"
            mineSection.visibility = if (learnShowMine) View.VISIBLE else View.GONE
            for ((l, w) in mine) {
                val key = "$l\t$w"
                val tr = trOf(l, w) ?: if (key in learnGlosses) "—" else null
                val buttons = ArrayList<Button>()
                if (key !in inDeck) buttons.add(smallButton(t("learn_add")) {
                    Learn.add(this, l, w, null)
                    toast("${t("learn_added")}: $w")
                    refresh()
                })
                buttons.add(smallButton(t("words_remove")) {
                    UserWords.remove(this, l, w)
                    toast("${t("words_removed")}: $w")
                    refresh()
                })
                myBox.addView(line(w, tr, "", false, null, *buttons.toTypedArray()))
            }
            lookUp(words.filter { it.tr.isEmpty() }.map { it.lang to it.word } + mine)
        } else {
            // Tiles: every alphabet card, then each pair taken (of whatever
            // language), then ＋ for the wheels.
            val alphaCards = all.filter { isAlphabet(it) }
            val alphaCount = alphaCards.size
            alphaBody.visibility = if (alphaCount == 0) View.VISIBLE else View.GONE
            val src = alphaSrc()
            val allS = alphaAll()
            val from = alphaFrom()
            val to = alphaTo()
            setWheel(fromWheel, allS.map { Script.name(it) }.toTypedArray(), allS.indexOf(from))
            setWheel(toWheel, allS.map { m ->
                Script.name(m) + if (taken(src, Alphabets.title(from, m)) > 0) "  ✓" else ""
            }.toTypedArray(), allS.indexOf(to))
            // With every alphabet chosen, the wheels are not what is studied.
            val everyOn = learnFilter == ALL_ALPHA && alphaCount > 0
            alphaWheels.alpha = if (everyOn) 0.45f else 1f
            // No tiles (owner 261127): the pair on the wheels is studied; under
            // them ＋ for a pair not taken yet and "all" for every alphabet,
            // then the pair's whole table as a cheat sheet.
            alphaGrid.removeAllViews()
            val name = Alphabets.title(from, to)
            val pairs = Alphabets.pair(src, from, to)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (from == to) row.addView(small("${Script.name(from)} = ${Script.name(to)}"),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            else if (taken(src, name) == 0 && pairs.isNotEmpty()) row.addView(Button(this).apply {
                text = "＋ ${shownAs(src, name)} (${pairs.size})"
                isAllCaps = false
                textSize = 15f
                accent(this)
                setOnClickListener { take(name, pairs, src) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (alphaCount > 0) row.addView(Button(this).apply {
                text = SpannableStringBuilder(t("learn_all") + " ").append(counts(alphaCards))
                isAllCaps = false
                textSize = 15f
                if (everyOn) chosen(this) else plain(this)
                setOnClickListener { if (everyOn) pick(shownAs(src, name)) else pick(ALL_ALPHA) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (row.childCount > 0) alphaGrid.addView(row)
            drawSheet()

            // "My letters": the cards of the pair chosen, or all of them.
            val letterList = (if (isAlphaFilter(learnFilter)) shown else all).filter { isAlphabet(it) }
                .sortedWith(compareBy({ topic(it) }, { it.tr }))
            lettersHead.text = (if (learnShowLetters) "▾ " else "▸ ") + t("learn_my_letters") + " (${letterList.size})"
            lettersBox.visibility = if (learnShowLetters) View.VISIBLE else View.GONE
            lettersBox.removeAllViews()
            if (letterList.isEmpty()) lettersBox.addView(small(t("learn_empty")))
            for (c in letterList) lettersBox.addView(line(c.word, c.tr,
                if (learnFilter == topic(c)) "" else topic(c), c.cat.endsWith(Script.name(Script.Mode.MORSE)), null,
                smallButton("✕") { unlearn(c) }))
        }

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
