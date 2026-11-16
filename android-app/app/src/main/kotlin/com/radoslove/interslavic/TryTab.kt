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

/** The try box's text across a language switch (the screen is rebuilt). */
internal var tryKeptText: String? = null

/** Reads the try box; set when the tab is built. */
internal var tryText: () -> String = { "" }

/** Builds the try tab into [root]. */
internal fun SetupActivity.buildTryTab(root: LinearLayout) {
        // A box to try the keyboard right here (iOS `try_head`, owner
    // 2026-11-15): it tells "the keyboard is broken" from "it is not
    // switched on", and is a place to write without opening another app -
    // so it comes FIRST, large and framed, inviting to write.
    // What the text becomes (gloss, other alphabet) comes FIRST, at the
    // very top: below the box the keyboard covered it (owner 2026-11-15).
    // No header: the box's own hint says what it is for.
    val mirrors = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(mirrors)
    addLanguageRow(root)
    val tryBox = android.widget.EditText(this)
    val frame = FrameLayout(this)
    root.addView(frame)
    frame.addView(tryBox.apply {
        hint = t("try_hint")
        textSize = 20f
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

    addGlossMirror(mirrors, tryBox)
    addLearnChips(mirrors, tryBox)
    addScriptRow(root, tryBox, root.indexOfChild(frame))
    // A language switched from this screen rebuilds it; the text typed so
    // far comes back, with the keyboard up (owner 2026-11-15).
    tryKeptText?.let {
        tryBox.setText(it)
        tryBox.setSelection(it.length)
        tryBox.requestFocus()
        tryBox.postDelayed({
            (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(tryBox, 0)
        }, 250)
    }
    tryKeptText = null
    tryText = { tryBox.text?.toString().orEmpty() }
}


/**
 * Under the try box: the same text, live, in another alphabet of the
 * keyboard's language - ABC / АБВ / ⰀⰁⰂ - with a copy button, to write in
 * one script and paste the other (owner 2026-11-15). Offline, exact: it is
 * the keyboard's own transliteration. Shown only for languages with more
 * than one alphabet (Interslavic, Serbian, Polish).
 */
/** The keyboard's ticked languages as buttons right above the try box: a
 *  tap makes that language the one typed (owner 2026-11-15: switching had
 *  to be easier here), and the screen follows. */
/** Buttons as wide as their label: the default minimum width pushed the
 *  copy button off the screen. */
internal fun SetupActivity.narrow(b: Button) {
    val d = resources.displayMetrics.density
    b.minWidth = 0
    b.minimumWidth = 0
    b.setPadding((14 * d).toInt(), b.paddingTop, (14 * d).toInt(), b.paddingBottom)
}


internal fun SetupActivity.addLanguageRow(root: LinearLayout) {
    val on = Lang.enabled(this)
    if (on.size < 2) return
    val cur = Lang.current(this)
    val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    for (l in on) chips.addView(Button(this).apply {
        text = l.uppercase()
        isAllCaps = false
        narrow(this)
        textSize = 16f
        alpha = if (l == cur) 1f else 0.45f
        setOnClickListener {
            if (l != Lang.current(this@addLanguageRow)) {
                // Through the running keyboard when there is one, so its
                // view and dictionary follow at once - setting the stored
                // choice alone left the open keyboard on the old language.
                val ime = ImeService.running
                if (ime == null || !ime.switchToLanguage(l)) Lang.setCurrent(this@addLanguageRow, l)
                tryKeptText = tryText()
                recreate()
            }
        }
    })
    root.addView(android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(chips)
    })
}


/**
 * The language's alphabets as buttons right above the try box (owner
 * 2026-11-15): a tap turns the text IN the box into that alphabet and the
 * keyboard switches with it, so writing goes on in the new script - Polish in
 * Glagolitic, Interslavic in Cyrillic. Only for languages with more than one.
 */
internal fun SetupActivity.addScriptRow(root: LinearLayout, box: android.widget.EditText, at: Int) {
    val lang = Lang.current(this)
    val modes = Script.offered(lang)
    if (modes.size < 2) return
    fun label(m: Script.Mode) = when (m) {
        Script.Mode.LATIN -> "ABC"; Script.Mode.CYRILLIC -> "АБВ"; Script.Mode.GLAGOLITIC -> "ⰀⰁⰂ"
    }
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val buttons = ArrayList<Button>()
    for (m in modes) buttons.add(Button(this).apply {
        text = label(m)
        isAllCaps = false
        textSize = 16f
        narrow(this)
        alpha = if (m == Script.mode(this@addScriptRow, lang)) 1f else 0.45f
        setOnClickListener {
            val old = Script.mode(this@addScriptRow, lang)
            if (m == old) return@setOnClickListener
            val latin = Script.back(box.text?.toString().orEmpty(), old, lang)
            Script.setMode(this@addScriptRow, m)
            box.setText(Script.convert(latin, m, lang))
            box.setSelection(box.text?.length ?: 0)
            ImeService.running?.rebuildView(false)
            for (b in buttons) b.alpha = if (b === this) 1f else 0.45f
        }
        row.addView(this)
    })
    root.addView(row, at)
}

/**
 * Under the try box: the typed text glossed word by word into another
 * language (owner 2026-11-15), offline, from Wiktionary ([Gloss]). Polish
 * glosses into the other keyboard languages, every other language into
 * Polish. Not for Interslavic yet (its dictionary's licence is open).
 */
internal fun SetupActivity.addGlossMirror(root: LinearLayout, box: android.widget.EditText) {
    val from = Lang.current(this)
    val targets = Gloss.targets(this, from)
    if (targets.isEmpty()) return
    val prefs = getSharedPreferences("isv_collector", android.content.Context.MODE_PRIVATE)
    val d = resources.displayMetrics.density
    var to = targets.firstOrNull { it == prefs.getString("gloss_target_$from", null) }
        ?: targets.firstOrNull { it in Lang.enabled(this) && it != from } ?: targets.first()
    var table: Map<String, String>? = null
    val out = TextView(this).apply {
        textSize = 20f
        setTextColor(cText)
        setTextIsSelectable(true)
        minLines = 2
        text = "…"
        setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 14 * d
            setColor(col("#EEF2F5", "#1E2A31"))
        }
    }
    fun update() {
        val t = table ?: return
        var typed = box.text?.toString().orEmpty()
        // Serbian entries are Cyrillic; Latin typing is read as Cyrillic.
        if (from == Lang.SR && Script.mode(this, from) == Script.Mode.LATIN)
            typed = Script.convert(typed, Script.Mode.CYRILLIC, Lang.SR)
        // Polish written in Glagolitic is read back to Latin first.
        if (from == Lang.PL && Script.mode(this, from) == Script.Mode.GLAGOLITIC)
            typed = Script.back(typed, Script.Mode.GLAGOLITIC, Lang.PL)
        val sb = android.text.SpannableStringBuilder()
        for ((piece, known) in Gloss.pieces(t, typed, from, to)) {
            val a = sb.length
            sb.append(piece)
            if (!known) sb.setSpan(android.text.style.ForegroundColorSpan(cGloss), a, sb.length, 0)
        }
        out.text = sb
    }
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
    }
    // Every target in sight, five to a row (a scrolling row hid Norwegian).
    val chips = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val allChips = ArrayList<Button>()
    // ⇄ turns the direction round (owner 2026-11-15): the keyboard switches
    // to the language translated into, the translation becomes the text,
    // and the old source becomes the target.
    row.addView(Button(this).apply {
        text = "${from.uppercase()} ⇄"
        isAllCaps = false
        textSize = 15f
        narrow(this)
        setOnClickListener {
            val back = to
            if (back !in Lang.enabled(this@addGlossMirror))
                Lang.setEnabled(this@addGlossMirror, Lang.enabled(this@addGlossMirror) + back)
            prefs.edit().putString("gloss_target_$back", from).apply()
            val ime = ImeService.running
            if (ime == null || !ime.switchToLanguage(back)) Lang.setCurrent(this@addGlossMirror, back)
            tryKeptText = out.text.toString()
            recreate()
        }
    })
    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    for (l in targets) allChips.add(Button(this).apply {
        text = l.uppercase()
        isAllCaps = false
        narrow(this)
        textSize = 16f
        alpha = if (l == to) 1f else 0.45f
        setOnClickListener {
            to = l
            prefs.edit().putString("gloss_target_$from", l).apply()
            for (c in allChips) c.alpha = if (c === this) 1f else 0.45f
            update()
        }
    })
    for (c in allChips) { c.textSize = 14f; c.setPadding((9 * d).toInt(), 0, (9 * d).toInt(), 0) }
    for (part in allChips.chunked(7)) {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        part.forEach { line.addView(it) }
        chips.addView(line)
    }
    row.addView(Button(this).apply {
        text = "⧉"
        textSize = 18f
        narrow(this)
        setOnClickListener {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Interslove", out.text))
            toast("✓ ${to.uppercase()}")
        }
    })
    root.addView(out)
    root.addView(row)
    root.addView(chips)
    // Attribution the licence asks for.
    root.addView(TextView(this).apply {
        text = "Wiktionary · CC BY-SA 4.0"
        textSize = 11f
        setTextColor(cGloss)
        setPadding(0, (2 * d).toInt(), 0, 0)
    })
    box.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = update()
    })
    // The table is large: read it off the UI thread, then gloss what is there.
    Thread {
        val t = Gloss.table(this, from)
        runOnUiThread { table = t; update() }
    }.start()
}


/** Under the translation, every word of the box as a button: a tap puts it
 *  among the learning cards (owner 2026-11-15: unknown words are met while
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
                Learn.add(this@addLearnChips, lang, word, null)
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
