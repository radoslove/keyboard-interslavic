package com.radoslove.interslavic

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/*
 * The app's first tab (2026-11-15): write, see the text in the language's other
 * alphabets, translate it word by word, copy. Kept apart from SetupActivity so the
 * translator can grow in its own file; the second tab is the keyboard settings.
 */

/** The try box's text across a language switch (the screen is rebuilt);
 *  a translation keeps its greyed untranslated words. */
internal var tryKeptText: CharSequence? = null

/** Reads the try box; set when the tab is built. */
internal var tryText: () -> String = { "" }

/** What the try tab was built for (languages, current one, its script): the
 *  settings tab can change these without rebuilding the screen. */
internal var tryBuiltSig: String? = null

internal fun SetupActivity.trySig(): String {
    val cur = Lang.current(this)
    return Lang.enabled(this).sorted().joinToString(",") + "|" + cur + "|" + Script.chosen(this, cur)
}

/** The last translation in the box, to turn it back exactly instead of
 *  translating a translation: from [from] ([before]) into [to] ([after]). */
internal data class TryTurn(val from: String, val before: CharSequence, val to: String, val after: String,
    val script: Script.Mode)
internal var tryTurn: TryTurn? = null

/** Words of the last translation (lowercase) -> the word they came from, so a
 *  card made from the box gets its translation. */
internal var tryPairs: Map<String, String> = emptyMap()
internal var tryPairsLang: String? = null

/** Called when the keyboard switches language while this screen is open. */
internal var tryFollow: ((String) -> Unit)? = null
internal var trySwitching = false

/** Switches the write tab to a language as its row does (translating the box). */
internal var tryGo: ((String) -> Unit)? = null

/** The script the keyboard switched to (its key next to 🌐): the box and the
 *  alphabet row follow; set while the write tab is built. */
internal var tryScriptFollow: ((Script.Mode) -> Unit)? = null

/** The word-by-word translation in the write box (Wiktionary). On; Interslavic
 *  stays out of it until its dictionary's licence is settled (owner 261127).
 *  Off: the language row holds the keyboard's languages only and switches
 *  the keyboard, the text stays as typed; an alphabet the language has not
 *  goes to Interslavic. */
internal const val TRY_TRANSLATE = true

/** Builds the try tab into [root]. */
internal fun SetupActivity.buildTryTab(root: LinearLayout) {
    // Callbacks of the screen built before this one must not fire.
    tryGo = null
    tryFollow = null
    tryScriptFollow = null
    // A box to try the keyboard right here (iOS `try_head`): it tells "the
    // keyboard is broken" from "it is not switched on", and is a place to
    // write without opening another app.
    // Owner 261121: the translation goes INTO the box, as the alphabets do -
    // a translation shown above the box was hidden under the top of the
    // screen once the keyboard was up. So above the box: the languages (a tap
    // translates the text and switches the keyboard), then the alphabets.
    val mirrors = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(mirrors)
    val tryBox = android.widget.EditText(this)
    addLanguageRow(root, tryBox)
    val frame = FrameLayout(this)
    root.addView(frame)
    frame.addView(tryBox.apply {
        hint = t("try_hint")
        textSize = 20f
        // Glagolitic and the titlo from the bundled Noto 2.004 (GlagFont), the rest
        // from the system fonts it falls back to.
        GlagFont.get(context)?.let { typeface = it }
        minLines = 4
        maxLines = 10
        gravity = Gravity.TOP or Gravity.START
        val d = resources.displayMetrics.density
        setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 14 * d
            setColor(col("#F4F7F9", "#263238"))
            setStroke((2 * d).toInt(), col("#4F7FA8", "#7FA8CF"))
        }
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    })
    // Attribution the licence asks for, under the box the translation lands in.
    if (TRY_TRANSLATE && glossReach(Lang.current(this)).isNotEmpty()) root.addView(TextView(this).apply {
        text = "Wiktionary · CC BY-SA 4.0"
        textSize = 11f
        setTextColor(cGloss)
        setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, 0)
    })

    // No word chips above the box any more: they repeated the text in it
    // (owner 261127); words to learn are added in 🎓. Kept for later.
    if (false) addLearnChips(mirrors, tryBox)
    addScriptRow(root, tryBox, root.indexOfChild(frame))
    // A language switched from this screen rebuilds it; the text typed so
    // far comes back, with the keyboard up.
    tryKeptText?.takeIf { it.isNotEmpty() }?.let {
        tryBox.setText(it)
        tryBox.setSelection(tryBox.text?.length ?: 0)
        tryBox.requestFocus()
        tryBox.postDelayed({
            (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(tryBox, 0)
        }, 250)
    }
    tryKeptText = null
    tryText = { tryBox.text?.toString().orEmpty() }
    tryBuiltSig = trySig()
    trySwitching = false
}

/** The languages [from]'s text can be translated into: Polish into the 19
 *  of its Wiktionary, the others into Polish and on through Polish into the
 *  rest. None for a language without a gloss (Interslavic: its dictionary's
 *  licence is open). */
internal fun SetupActivity.glossReach(from: String): List<String> = when {
    from == Lang.PL -> Gloss.targets(this, Lang.PL)
    !Gloss.has(this, from) -> emptyList()
    else -> (Gloss.targets(this, Lang.PL) + Lang.PL).filter { it != from }
}

/** The alphabet a language's gloss entries are written in. */
private fun glossForm(lang: String) =
    if (lang == Lang.SR) Script.Mode.CYRILLIC else Script.offered(lang).first()

/** Box text in [lang]'s chosen script -> the alphabet of its gloss entries. */
private fun SetupActivity.toGlossForm(s: String, lang: String): String {
    val m = Script.chosen(this, lang)
    val g = glossForm(lang)
    return if (m == g) s else Script.convert(Script.back(s, m, lang), g, lang)
}

/** The other way: gloss text -> [lang]'s chosen script (runes, Morse,
 *  Glagolitic, Serbian Latin...). */
private fun SetupActivity.fromGlossForm(s: String, lang: String): String {
    val m = Script.chosen(this, lang)
    val g = glossForm(lang)
    return if (m == g) s else Script.convert(Script.back(s, g, lang), m, lang)
}

/** [text] (in [from]'s script) translated word by word into [to], in [to]'s
 *  script. Words with no gloss stay as typed, greyed while the script shows
 *  them as they are. Sets [tryPairs]. */
internal fun SetupActivity.translateBox(text: String, from: String, to: String): CharSequence {
    val src = toGlossForm(text, from)
    if (to !in glossReach(from)) {
        // No gloss (Interslavic): the words stay as typed. Only a code of
        // letters (Latin, Morse, runes) follows the new language; Cyrillic or
        // Glagolitic would spell another language's words in it (owner 261127:
        // English came out in Cyrillic under ISV).
        tryPairs = emptyMap(); tryPairsLang = null
        val latin = boxLatin(text, Script.chosen(this, from), from)
        val m = Script.chosen(this, to)
        return if (m == Script.Mode.LATIN || m == Script.Mode.MORSE || Script.runeRow(m) != null)
            Script.convert(latin, m, to) else latin
    }
    val tPl = if (from == Lang.PL || to != Lang.PL) Gloss.table(this, Lang.PL) else emptyMap()
    val tFrom = if (from != Lang.PL) Gloss.table(this, from) else tPl
    val direct = from == Lang.PL || to == Lang.PL
    fun one(w: String): String? =
        if (direct) Gloss.word(tFrom, w, from, to)
        else Gloss.word(tFrom, w, from, Lang.PL)?.let { pl ->
            val ps = Gloss.pieces(tPl, pl, Lang.PL, to)
            if (ps.any { (q, k) -> !k && q.any { it.isLetter() } }) null else ps.joinToString("") { it.first }
        }
    // Not Polish on either side: through Polish, word by word.
    val pieces: List<Pair<String, Boolean>> =
        if (direct) Gloss.pieces(tFrom, src, from, to)
        else Gloss.pieces(tFrom, src, from, Lang.PL).flatMap { (p, known) ->
            if (!known || p.none { it.isLetter() }) listOf(p to known)
            else Gloss.pieces(tPl, p, Lang.PL, to).let { ps ->
                // A Polish word with no gloss in [to] would stay Polish: the
                // typed word is honester then.
                if (ps.any { (q, k) -> !k && q.any { it.isLetter() } }) listOf(p to false) else ps
            }
        }
    val pairs = HashMap<String, String>()
    for (m in Regex("\\p{L}+").findAll(src)) {
        val g = one(m.value) ?: continue
        val w = if (from == Lang.DE) m.value else m.value.lowercase()
        val typed = if (from == Lang.SR) Script.back(w, Script.Mode.CYRILLIC, Lang.SR) else w
        pairs.putIfAbsent(g.lowercase(), typed)
        if (to == Lang.SR) pairs.putIfAbsent(Script.back(g, Script.Mode.CYRILLIC, Lang.SR).lowercase(), typed)
    }
    tryPairs = pairs; tryPairsLang = to
    val joined = pieces.joinToString("") { it.first }
    if (Script.chosen(this, to) != glossForm(to)) return fromGlossForm(joined, to)
    val sb = android.text.SpannableStringBuilder()
    for ((piece, known) in pieces) {
        val a = sb.length
        sb.append(piece)
        if (!known) sb.setSpan(android.text.style.ForegroundColorSpan(cGloss), a, sb.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return sb
}

/**
 * Under the try box: the same text, live, in another alphabet of the
 * keyboard's language - ABC / АБВ / ⰀⰁⰂ - with a copy button, to write in
 * one script and paste the other. Offline, exact: it is
 * the keyboard's own transliteration. Shown only for languages with more
 * than one alphabet (Interslavic, Serbian, Polish).
 */
/** The keyboard's ticked languages as buttons right above the try box: a
 *  tap makes that language the one typed (switching had
 *  to be easier here), and the screen follows. */
/** Buttons as wide as their label: the default minimum width pushed the
 *  copy button off the screen. */
internal fun SetupActivity.narrow(b: Button) {
    val d = resources.displayMetrics.density
    b.minWidth = 0
    b.minimumWidth = 0
    b.setPadding((14 * d).toInt(), b.paddingTop, (14 * d).toInt(), b.paddingBottom)
}


/** Box text written in [m] of [lang] back to Latin - unless it is not in [m]
 *  at all (a kept text in Latin under a Morse or rune language). */
private fun boxLatin(s: String, m: Script.Mode, lang: String): String = when {
    m == Script.Mode.MORSE && s.any { it.isLetter() } -> s
    Script.runeRow(m) != null && s.none { it in 'ᚠ'..'᛿' } -> s
    else -> Script.back(s, m, lang)
}

internal fun SetupActivity.addLanguageRow(root: LinearLayout, box: android.widget.EditText) {
    val d = resources.displayMetrics.density
    // Alphabetical, as every list of languages (owner 261118): the keyboard's
    // languages, then the ones the text can only be translated into.
    val on = Lang.enabled(this)
    val cur = Lang.current(this)
    // One row: Interslavic first, as in the keyboard's language list (the
    // project's own language), then the keyboard's languages and the ones
    // the text can only be translated into, all alphabetical together
    // (owner 261121); the second kind is paler.
    val all = (on + (if (TRY_TRANSLATE) glossReach(cur) else emptyList())).distinct()
        .sortedWith(compareBy({ it != Lang.ISV }, { it }))
    if (all.size < 2) return
    val chips = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    // [l] chosen here ([switch]: the keyboard follows) or on the keyboard
    // itself (the screen follows): the text is translated either way. From
    // the language the screen was built for, not the stored one - the
    // keyboard may have switched it already.
    fun go(l: String, switch: Boolean) {
            val from = cur
            if (l == from) return
            // The screen is about to be rebuilt for [l]: the keyboard's own
            // switch, made below, must not start a second one.
            tryFollow = null
            trySwitching = true
            val typed = box.text?.toString().orEmpty()
            // Back to the language just translated from, text untouched since:
            // the original comes back, not a translation of a translation.
            val turn = tryTurn
            val result: CharSequence =
                // No translation: the text stays as typed, never put into
                // another language's alphabet (words are not letters).
                if (!TRY_TRANSLATE) {
                    // ...but the letters follow the new language's alphabet
                    // when it is a code of letters (Latin, Morse, runes: owner
                    // 261127, PL Glagolitic -> EN Morse converted nothing).
                    // Cyrillic or Glagolitic for another language's words
                    // would misspell them: those stay in Latin.
                    val newM = Script.chosen(this@addLanguageRow, l)
                    val latin = boxLatin(typed, Script.chosen(this@addLanguageRow, from), from)
                    if (typed.isBlank()) ""
                    else if (newM == Script.Mode.LATIN || newM == Script.Mode.MORSE || Script.runeRow(newM) != null)
                        Script.convert(latin, newM, l)
                    else latin
                }
                else if (turn != null && turn.to == from && turn.from == l && turn.after == typed) {
                    // ...in the alphabet chosen for it now (Glagolitic picked in
                    // the row below brings Polish back in Glagolitic).
                    val now = Script.chosen(this@addLanguageRow, l)
                    if (now == turn.script) turn.before
                    else Script.convert(Script.back(turn.before.toString(), turn.script, l), now, l)
                }
                else if (typed.isBlank()) "" else translateBox(typed, from, l)
            tryTurn = if (typed.isBlank() || !TRY_TRANSLATE) null
                else TryTurn(from, android.text.SpannableString(box.text ?: typed), l, result.toString(),
                    Script.chosen(this@addLanguageRow, from))
            // The box is always in the keyboard's language: a translation
            // language not on the keyboard yet joins it.
            if (switch && l !in Lang.enabled(this@addLanguageRow)) {
                Lang.setEnabled(this@addLanguageRow, Lang.enabled(this@addLanguageRow) + l)
                toast("⌨ ＋ ${l.uppercase()}")
            }
            // Through the running keyboard when there is one, so its view and
            // dictionary follow at once - setting the stored choice alone
            // left the open keyboard on the old language.
            if (switch) {
                val ime = ImeService.running
                if (ime == null || !ime.switchToLanguage(l)) Lang.setCurrent(this@addLanguageRow, l)
            }
            tryKeptText = result
            recreate()
    }
    tryFollow = { go(it, false) }
    tryGo = { go(it, true) }
    fun chip(l: String, keyboard: Boolean) = Button(this).apply {
        text = l.uppercase()
        isAllCaps = false
        narrow(this)
        textSize = 16f
        alpha = if (l == cur) 1f else if (keyboard) 0.55f else 0.4f
        if (l == cur) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setOnClickListener { go(l, true) }
    }
    for (l in all) chips.addView(chip(l, l in on))
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val scroll = android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(chips)
    }
    row.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(Button(this).apply {
        text = "⧉"
        textSize = 18f
        narrow(this)
        setOnClickListener {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Interslove", box.text?.toString().orEmpty()))
            toast("✓ ${Lang.current(this@addLanguageRow).uppercase()}")
        }
    })
    root.addView(row)
    // The language on now in sight, also when it is far down the alphabet.
    scroll.post {
        for (i in 0 until chips.childCount) {
            val v = chips.getChildAt(i)
            if (v is Button && v.text.toString() == cur.uppercase()) {
                scroll.scrollTo(maxOf(0, v.left - scroll.width / 3), 0)
                break
            }
        }
    }
}


/**
 * Every alphabet as buttons right above the try box (owner 261121: always
 * all of them, as the languages above). One the language has turns the text
 * IN the box into it and the keyboard switches with it; one it has not
 * switches to a language that has it - the website's rule for its chips.
 */
internal fun SetupActivity.addScriptRow(root: LinearLayout, box: android.widget.EditText, at: Int) {
    val lang = Lang.current(this)
    val own = Script.choices(lang)
    val modes = listOf(Script.Mode.LATIN, Script.Mode.CYRILLIC, Script.Mode.GLAGOLITIC,
        Script.Mode.RUNES_E, Script.Mode.RUNES_B, Script.Mode.RUNES_A, Script.Mode.MORSE)
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val buttons = ArrayList<Button>()
    fun shade(m: Script.Mode, on: Script.Mode) = if (m == on) 1f else if (m in own) 0.55f else 0.3f
    // The script the box is written in now; the text is rewritten from it.
    var shown = Script.chosen(this, lang)
    fun rewrite(to: Script.Mode) {
        if (to == shown) return
        val latin = boxLatin(box.text?.toString().orEmpty(), shown, lang)
        shown = to
        box.setText(Script.convert(latin, to, lang))
        box.setSelection(box.text?.length ?: 0)
        for ((i, b) in buttons.withIndex()) b.alpha = shade(modes[i], to)
    }
    tryScriptFollow = { rewrite(it) }
    for (m in modes) buttons.add(Button(this).apply {
        text = Script.name(m)
        isAllCaps = false
        textSize = 16f
        narrow(this)
        alpha = shade(m, Script.chosen(this@addScriptRow, lang))
        setOnClickListener {
            if (m !in own) { otherScript(m, lang); return@setOnClickListener }
            val old = shown
            // Tapping the alphabet already on turns it off: back to the
            // language's own script, as a switch is expected to behave.
            val to = if (m == old) own.first() else m
            if (to == old) return@setOnClickListener
            rewrite(to)
            Script.setMode(this@addScriptRow, to)
            ImeService.running?.rebuildView(false)
        }
        row.addView(this)
    })
    root.addView(android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
    }, at)
}

/** Alphabet [m], which [lang] is not written in: to a language that has it -
 *  one on the keyboard the text can be translated into first (Norwegian ->
 *  Glagolitic goes to Polish), else one on the keyboard, else Interslavic
 *  (Cyrillic, Glagolitic) or Norwegian (runes), which then joins the keyboard. */
private fun SetupActivity.otherScript(m: Script.Mode, lang: String) {
    val has = Lang.ALL.filter { it != lang && m in Script.choices(it) }
    if (has.isEmpty()) return
    // Interslavic first among the keyboard's languages (owner 261121: АБВ on
    // Polish went to Belarusian).
    val on = Lang.enabled(this).sortedWith(compareBy({ it != Lang.ISV }, { it }))
    val reach = glossReach(lang)
    // A language for which [m] is an added alphabet (Polish and Glagolitic,
    // Interslavic and Cyrillic) before one written only in it (Belarusian):
    // the text changes alphabet, not people.
    fun added(l: String) = Script.offered(l).first() != m
    // Without translation: Interslavic, the safe place for an alphabet when
    // nothing says where else (owner 261127), the runes' Norwegian otherwise.
    val to = if (!TRY_TRANSLATE) (if (Lang.ISV in has) Lang.ISV else on.firstOrNull { it in has } ?: has.sorted().first())
        else on.firstOrNull { it in has && added(it) && it in reach }
        // With text in the box, a language it can be translated into: the
        // keyboard's, else Bulgarian for Cyrillic (owner 261127: English +
        // АБВ should change the text, not land untranslated on Interslavic).
        ?: (if (tryText().isBlank()) null else on.firstOrNull { it in has && it in reach }
            ?: Lang.BG.takeIf { m == Script.Mode.CYRILLIC && it in has && it in reach })
        ?: on.firstOrNull { it in has && added(it) }
        ?: on.firstOrNull { it in has }
        ?: (if (Lang.ISV in has) Lang.ISV else has.sorted().first())
    Script.setMode(this, to, m)
    val go = tryGo
    if (go != null) { go(to); return }
    if (to !in on) Lang.setEnabled(this, Lang.enabled(this) + to)
    val ime = ImeService.running
    if (ime == null || !ime.switchToLanguage(to)) Lang.setCurrent(this, to)
    tryKeptText = tryText()
    recreate()
}

/** Under the translation, every word of the box as a button: a tap puts it
 *  among the learning cards (unknown words are met while
 *  reading and writing, not in the `＋` chip, which only collects words the
 *  keyboard was missing). Works for every language, with or without a gloss. */
internal fun SetupActivity.addLearnChips(root: LinearLayout, box: android.widget.EditText) {
    val d = resources.displayMetrics.density
    val lang = Lang.current(this)
    val label = TextView(this).apply {
        text = t("try_learn")
        textSize = 13f
        setTextColor(cGloss)
        setPadding(0, (8 * d).toInt(), 0, 0)
    }
    val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val scroll = android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(chips)
    }
    root.addView(label)
    root.addView(scroll)
    val token = Regex("\\p{L}+")
    fun update() {
        chips.removeAllViews()
        var typed = box.text?.toString().orEmpty()
        // Decoded only when the text IS in that script: a language switched
        // with the text kept leaves Latin in the box of a Morse language, and
        // "jak sie pisze" decoded as Morse came out as one word.
        if (Script.chosen(this, lang) == Script.Mode.MORSE && typed.none { it.isLetter() })
            typed = Morse.decode(typed, lang)
        Script.runeRow(Script.chosen(this, lang))?.let { row ->
            if (typed.any { it in 'ᚠ'..'᛿' }) typed = Runes.decodeLossy(typed, row)
        }
        if (lang == Lang.PL && Script.mode(this, lang) == Script.Mode.GLAGOLITIC)
            typed = Script.back(typed, Script.Mode.GLAGOLITIC, Lang.PL)
        val words = token.findAll(typed).map { it.value }
            .distinctBy { it.lowercase() }.filter { it.length > 1 }.toList().takeLast(12)
        label.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
        val known = Learn.cards(this).filter { it.lang == lang }.map { it.word.lowercase() }.toSet()
        for (w in words) chips.addView(Button(this).apply {
            val inDeck = w.lowercase() in known
            text = if (inDeck) "✓ $w" else w
            isAllCaps = false
            textSize = 15f
            narrow(this)
            alpha = if (inDeck) 0.5f else 1f
            setOnClickListener {
                if (inDeck) return@setOnClickListener
                val word = if (lang == Lang.DE) w else w.lowercase()
                // A word the box was translated into carries the word it
                // came from as its translation.
                val from = if (tryPairsLang == lang) tryPairs[word.lowercase()] else null
                Learn.add(this@addLearnChips, lang, word, from)
                toast("${t("learn_added")}: $word")
                update()
                learnRefresh()
            }
        })
    }
    box.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = update()
    })
    update()
}
