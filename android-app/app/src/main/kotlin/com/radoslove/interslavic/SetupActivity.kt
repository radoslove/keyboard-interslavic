package com.radoslove.interslavic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * The container app (android_PLAN.md M3). Its whole job is the collection
 * consent surface the keyboard cannot show itself: an opt-in switch (default
 * OFF), the pending count, and one deliberate send: the batch goes out only as
 * a mail the user sends from their own mail app — this app never touches the network.
 *
 * Every text comes from [Ui], in the language chosen at the top: Interslavic
 * with an English gloss (the original), or English, Polish or Norwegian alone.
 * MS texts are checked against medzuslove (DB-verified :30020 / host canon):
 * izberi, ili, latinica, kirilica, glagolica, jezyk (język), klaviatura;
 * `pismo` is NOT in the dictionary, so the script header names the alphabets.
 */
class SetupActivity : Activity() {

    private lateinit var status: TextView

    /** The gesture-log section: hidden until the version line is tapped five
     *  times, shown for good while recording is on (iOS 0.6.11). */
    private lateinit var logBox: LinearLayout
    private lateinit var logCount: TextView
    private var versionTaps = 0
    private lateinit var ui: String

    /** The screen follows the SAME choice the keyboard draws by (owner
     *  2026-11-07: "jak motyw ciemny to tutaj tez by mogl byc"), not the
     *  phone's mode - picking "always dark" for the keyboard and getting a
     *  white settings screen was the whole complaint. Read once, so the radio
     *  below rebuilds the screen with `recreate()`. The system widgets
     *  (buttons, radios, switches) come from the window theme, our own text
     *  from the pairs under it. */
    private var dark = false
    private fun col(light: String, night: String) =
        Color.parseColor(if (dark) night else light)
    private val cBg get() = col("#FAFAFA", "#1B1F22")      // keyboard board colour
    private val cText get() = col("#1C2529", "#E8EAED")    // 13.8:1 on the dark bg
    private val cBody get() = col("#37474F", "#B0BEC5")    // 8.7:1
    private val cGloss get() = col("#78909C", "#90A4AE")   // the EN aid, below the body

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = Theme.isDark(this)
        setTheme(if (dark) android.R.style.Theme_DeviceDefault
                 else android.R.style.Theme_DeviceDefault_Light_DarkActionBar)
        super.onCreate(savedInstanceState)
        ui = Ui.current(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(cBg)
        }

        // Which build is this? The released app showed its version nowhere at
        // all - only a test build carried it, and only inside its label - so a
        // tester who had installed it could not answer "which one do you have?",
        // and a bug report could not say which build it was about. Not
        // translated on purpose: a version and a package id read the same in all
        // four screen languages, and the `.debug` suffix is itself the answer to
        // "is this the test build or the published one?".
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
        root.addView(tryBox.apply {
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
        addScriptMirror(mirrors, tryBox)
        // A language switched from this screen rebuilds it; the text typed so
        // far comes back, with the keyboard up (owner 2026-11-15).
        keptText?.let {
            tryBox.setText(it)
            tryBox.setSelection(it.length)
            tryBox.requestFocus()
            tryBox.postDelayed({
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(tryBox, 0)
            }, 250)
        }
        keptText = null
        tryText = { tryBox.text?.toString().orEmpty() }

        section(root, "title", null)
        root.addView(body(t("intro")))
        glossOf("intro")?.let { root.addView(gloss(it)) }

        // Languages: ticked HERE (and in the keyboard's own panel), one
        // keyboard in the system (owner 2026-11-11, parity with iOS 0.6.3).
        // At least one stays ticked.
        section(root, "langs_head", "langs_body")
        langsNow = body("")
        root.addView(langsNow)
        langBoxes.clear()
        for (lang in Lang.ALL) {
            val box = CheckBox(this).apply {
                text = langName(lang)
                textSize = 17f
                setOnClickListener {
                    val on = Lang.enabled(this@SetupActivity).toMutableList()
                    if (isChecked) on.add(lang) else if (on.size > 1) on.remove(lang)
                    Lang.setEnabled(this@SetupActivity, on)
                    refreshLangs()
                }
            }
            langBoxes[lang] = box
            root.addView(box)
        }

        val hasScripts = Lang.ISV in Lang.ALL
        if (hasScripts) section(root, "script_head", "script_body")
        val scripts = listOf(
            Script.Mode.LATIN to "s_latin",
            Script.Mode.CYRILLIC to "s_cyrillic",
            Script.Mode.GLAGOLITIC to "s_glagolitic",
        )
        val prefs = getSharedPreferences("isv_collector", MODE_PRIVATE)
        val current = prefs.getString("script", "latin")
        if (hasScripts) root.addView(RadioGroup(this).apply {
            for ((mode, key) in scripts) {
                addView(RadioButton(this@SetupActivity).apply {
                    id = android.view.View.generateViewId()
                    text = labelled(key)
                    textSize = 17f
                    isChecked = mode.name.lowercase() == current
                    setOnClickListener { Script.setMode(this@SetupActivity, mode) }
                })
            }
        })

        // Polish in Glagolitic: how the add-on alphabet types (iOS 0.6.30). The
        // text exists in Polish only, so the section shows on a Polish screen.
        if (ui == "pl" && Lang.PL in Lang.ALL && t("glag_pl_head") != "glag_pl_head") {
            section(root, "glag_pl_head", "glag_pl_body")
        }

        section(root, "collect_head", "collect_body")
        root.addView(Switch(this).apply {
            text = labelled("collect_sw")
            textSize = 17f
            isChecked = Collector.isEnabled(this@SetupActivity)
            setOnCheckedChangeListener { _, on ->
                Collector.setEnabled(this@SetupActivity, on)
                refreshStatus()
            }
        })

        section(root, "pop_head", "pop_body")
        root.addView(Switch(this).apply {
            text = labelled("pop_sw")
            textSize = 17f
            isChecked = Popularity.isEnabled(this@SetupActivity)
            setOnCheckedChangeListener { _, on ->
                Popularity.setEnabled(this@SetupActivity, on)
                refreshStatus()
            }
        })

        // The user's own words (the `＋ word` chip), per language, each removable.
        section(root, "words_head", "words_body")
        wordsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(wordsBox)

        // Light or dark keyboard; automatic follows the phone.
        section(root, "theme_head", null)
        val theme = Theme.choice(this)
        root.addView(RadioGroup(this).apply {
            for ((value, key) in listOf(Theme.AUTO to "theme_auto", Theme.LIGHT to "theme_light", Theme.DARK to "theme_dark")) {
                addView(RadioButton(this@SetupActivity).apply {
                    id = android.view.View.generateViewId()
                    text = labelled(key)
                    textSize = 17f
                    isChecked = value == theme
                    setOnClickListener {
                        Theme.set(this@SetupActivity, value)
                        // The screen is drawn from `dark`, which is read in
                        // onCreate - without this the keyboard went dark and
                        // the screen around the radio stayed light.
                        if (Theme.isDark(this@SetupActivity) != dark) recreate()
                    }
                })
            }
        })

        // QWERTY or QWERTZ per language; the default is that language's own
        // keyboards (owner 2026-11-10). Read when the keyboard next draws.
        section(root, "layout_head", "layout_body")
        for (lang in Lang.ALL.filter { KeyRows.offered(it) }) {
            root.addView(Switch(this).apply {
                text = "${langName(lang)} · QWERTZ"
                textSize = 17f
                isChecked = KeyRows.isQwertz(this@SetupActivity, lang)
                setOnCheckedChangeListener { _, on -> KeyRows.set(this@SetupActivity, lang, on) }
            })
        }

        // Which marks are typed how often - the measurement behind key placement.
        section(root, "sym_head", "sym_body")
        symbolsNow = body("")
        root.addView(symbolsNow)

        section(root, "sound_head", "sound_body")
        root.addView(Switch(this).apply {
            text = labelled("sound_sw")
            textSize = 17f
            isChecked = prefs.getBoolean("feedback", false)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("feedback", on).apply()
            }
        })

        section(root, "space_head", "space_body")
        root.addView(Switch(this).apply {
            text = labelled("space_sw")
            textSize = 17f
            isChecked = prefs.getBoolean("smart_space", true)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("smart_space", on).apply()
            }
        })

        section(root, "caps_head", "caps_body")
        root.addView(Switch(this).apply {
            text = labelled("caps_sw")
            textSize = 17f
            isChecked = prefs.getBoolean("auto_cap", true)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("auto_cap", on).apply()
            }
        })
        root.addView(Switch(this).apply {
            text = labelled("de_nouns_sw")
            textSize = 17f
            isChecked = prefs.getBoolean("de_nouns", true)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("de_nouns", on).apply()
            }
        })

        status = body("")
        root.addView(status)

        root.addView(Button(this).apply {
            text = button("btn_batch")
            setOnClickListener { exportBatch() }
        })
        root.addView(Button(this).apply {
            text = labelled("btn_clear")
            setOnClickListener {
                Collector.clear(this@SetupActivity)
                refreshStatus()
                toast(t("t_cleared"))
            }
        })

        // Gesture log for error reports (iOS 0.6.11, owner 2026-11-13): not for
        // everyday use, so hidden behind five taps on the version line; kept on
        // the phone, sent only by the user's own mail.
        logBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section(logBox, "log_head", "log_body_android")
        logBox.addView(body(t("log_privacy")))
        logBox.addView(Switch(this).apply {
            text = t("log_toggle")
            textSize = 17f
            isChecked = GestureLog.fileOn(this@SetupActivity)
            setOnCheckedChangeListener { _, on -> GestureLog.setFileOn(this@SetupActivity, on) }
        })
        logCount = body("")
        logBox.addView(logCount)
        logBox.addView(Button(this).apply {
            text = t("log_send")
            setOnClickListener { sendGestureLog() }
        })
        logBox.addView(Button(this).apply {
            text = t("log_clear")
            setOnClickListener { GestureLog.clear(this@SetupActivity); refreshLog() }
        })
        logBox.visibility = if (GestureLog.fileOn(this) && hasLogTexts()) View.VISIBLE else View.GONE
        root.addView(logBox)

        // The build line last: it is for bug reports, not for reading first.
        root.addView(buildLine().apply {
            setOnClickListener {
                versionTaps++
                if (versionTaps >= 5 && ::logBox.isInitialized && hasLogTexts()) logBox.visibility = View.VISIBLE
            }
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(cBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(root)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (Ui.current(this) != ui) { recreate(); return }
        if (batchHandedOver) {
            batchHandedOver = false
            Collector.clear(this)
            Popularity.clear(this)
        }
        refreshStatus()
        refreshLangs()
        refreshWords()
        refreshLog()
        val top = Symbols.top(this, 15)
        symbolsNow.text = if (top.isEmpty()) t("sym_none")
            else top.joinToString("   ") { (mark, n) -> "$mark $n" }
        symbolsNow.textSize = 17f
    }

    private lateinit var langsNow: TextView
    private val langBoxes = LinkedHashMap<String, android.widget.CheckBox>()

    /** The ticks as the keyboard has them - its own panel may have changed them. */
    private fun refreshLangs() {
        val on = Lang.enabled(this)
        langsNow.text = "${t("langs_now")}: " + on.joinToString(", ") { langName(it) }
        for ((lang, box) in langBoxes) box.isChecked = lang in on
    }
    private lateinit var symbolsNow: TextView
    private lateinit var wordsBox: LinearLayout

    /** One block per language that has words: its name, then each word with a remove button. */
    private fun refreshWords() {
        wordsBox.removeAllViews()
        var any = false
        for (lang in Lang.ALL) {
            val words = UserWords.all(this, lang).sorted()
            if (words.isEmpty()) continue
            any = true
            wordsBox.addView(body(langName(lang)).apply { textSize = 16f })
            for (w in words) {
                wordsBox.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@SetupActivity).apply {
                        text = w
                        textSize = 17f
                        setTextColor(cText)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(Button(this@SetupActivity).apply {
                        text = t("words_remove")
                        setOnClickListener {
                            UserWords.remove(this@SetupActivity, lang, w)
                            toast("${t("words_removed")}: $w")
                            refreshWords()
                        }
                    })
                })
            }
        }
        if (!any) wordsBox.addView(body(t("words_none")))
    }

    private fun t(key: String) = Ui.t(ui, key)
    private fun glossOf(key: String) = Ui.gloss(ui, key)

    /** `ms` shows `Interslavic (English)`, the other languages their text alone. */
    private fun labelled(key: String, sep: String = " "): String =
        glossOf(key)?.let { "${t(key)}$sep($it)" } ?: t(key)

    /** Buttons as before the translation: `Pošli do bazy (grupa JSON) — Send to the
     *  database (JSON batch)`, no brackets around the English. */
    private fun button(key: String): String =
        glossOf(key)?.let { if (it != t(key)) "${t(key)} — $it" else null } ?: t(key)

    /** Language names in their own language, and in the screen's in brackets. */
    private fun langName(lang: String): String = Ui.langName(ui, lang)

    /** Header, its English gloss in `ms`, then the body and its gloss. */
    private fun section(root: LinearLayout, head: String, text: String?) {
        root.addView(header(t(head)))
        glossOf(head)?.let { if (it != t(head)) root.addView(gloss(it)) }
        if (text != null) {
            root.addView(body(t(text)))
            // A key with no Interslavic text falls back to English: no gloss twice.
            glossOf(text)?.let { if (it != t(text)) root.addView(gloss(it)) }
        }
    }

    private fun refreshStatus() {
        val n = Collector.pendingCount(this)
        val on = if (Collector.isEnabled(this)) t("st_on") else t("st_off")
        val pop = if (Popularity.isEnabled(this)) "${t("st_on")} (${Popularity.pendingCount(this)})" else t("st_off")
        status.text = "${t("st_new")}: $on · ${t("st_queue")}: $n · ${t("st_rank")}: $pop · " +
            "id: ${Collector.contributorId(this)}"
    }

    private val BATCH_MAILBOX = "interslove.keyboard@gmail.com"

    /** The isc app has no gesture-log texts; it never shows the section. */
    private fun hasLogTexts() = t("log_head") != "log_head"

    private fun refreshLog() {
        if (!::logCount.isInitialized) return
        val n = GestureLog.lines(this).size
        logCount.text = if (n == 0) t("log_empty") else "${t("log_count")} $n"
    }

    /** The log as ONE file `interslove-gesty_<RRMMDD_HHMM>.json` - a hyphen
     *  after `interslove`, so the word-batch reader on the server does not take
     *  it for a batch of words (iOS `sendLog`). */
    private fun sendGestureLog() {
        if (GestureLog.lines(this).isEmpty()) { toast(t("log_empty")); return }
        val stamp = java.text.SimpleDateFormat("yyMMdd_HHmm", java.util.Locale.ROOT).format(java.util.Date())
        val json = GestureLog.exportJson(this, BuildConfig.VERSION_NAME, Collector.contributorId(this))
        mailFile("interslove-gesty_$stamp.json", json, "${App.MAIN_NAME} Android · ${t("log_subject")} · $stamp")
    }

    private fun exportBatch() {
        if (Collector.pendingCount(this) == 0 && Popularity.pendingCount(this) == 0) {
            toast(t("t_empty"))
            return
        }
        val json = Collector.exportBatchJson(this)
        // To one fixed mailbox, read on the server side, which loads the batch
        // into the database (owner 2026-11-12, by mail and not over HTTPS: the
        // app gets no INTERNET permission, the user sends from their own mail
        // app, and the mail itself is the notice that something came in).
        // Subject, a one-line body and the batch as a .json ATTACHMENT, all
        // filled in (owner 3.9.6: Gmail left the subject empty - ACTION_SENDTO
        // drops the extras). ACTION_SEND, one intent per mail app, so the
        // choice still offers only mail apps.
        val stamp = java.text.SimpleDateFormat("yyMMdd_HHmm", java.util.Locale.ROOT).format(java.util.Date())
        mailFile("interslove_${Collector.contributorId(this)}_$stamp.json", json,
            t("sub_batch") + " · " + Collector.contributorId(this) + " · " + stamp)
        batchHandedOver = true
    }

    /** The batch went to the mail app: on the way back the queue is emptied
     *  (owner 2026-11-15: sent words should go from the list at once). The
     *  words themselves stay in the user's dictionary ([UserWords]). */
    private var batchHandedOver = false

    /** [json] as the attachment [name] of a mail to [BATCH_MAILBOX], sent from
     *  the user's own mail app (the app has no INTERNET). */
    private fun mailFile(name: String, json: String, subject: String) {
        val dir = java.io.File(cacheDir, "batches").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // only the file being sent
        val file = java.io.File(dir, name)
        file.writeText(json, Charsets.UTF_8)
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.batches", file)
        fun sendVia(pkg: String?) = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(BATCH_MAILBOX))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "${App.MAIN_NAME} ${BuildConfig.VERSION_NAME} · ${file.name}")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) setPackage(pkg)
        }
        val mailApps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")), 0
        ).map { it.activityInfo.packageName }.distinct()
        val chooser = if (mailApps.isEmpty()) Intent.createChooser(sendVia(null), t("chooser"))
        else Intent.createChooser(sendVia(mailApps.first()), t("chooser")).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, mailApps.drop(1).map { sendVia(it) }.toTypedArray())
        }
        startActivity(chooser)
    }

    /** `<version> · com.radoslove.interslavic.debug` - the two facts every
     *  bug report needs, at the top so they are read without scrolling. */
    private fun buildLine() = TextView(this).apply {
        text = "${BuildConfig.VERSION_NAME} · $packageName"
        textSize = 13f
        // Content colour, not the gloss grey it first had: that grey is 3.2:1 on
        // this background, and it is used for text the eye may skip. This line is
        // the opposite - it exists to be READ and copied into a bug report, so it
        // gets the body colour at 9.2:1. Measured on a screenshot of the phone,
        // not judged in the editor.
        setTextColor(cBody)
        setPadding(0, 0, 0, 2)
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 20f
        setTextColor(cText)
        setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 4)
        gravity = Gravity.START
    }

    /** Descriptions: smaller and quieter than the headers and the controls
     *  (owner 2026-11-15). */
    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(col("#546E7A", "#9AABB4"))   // 5.3:1 light, quieter than cBody
        setPadding(0, 4, 0, 8)
    }

    /** EN gloss under an MS header/paragraph — smaller and lighter, so MS reads
     *  as primary and English as the aid (MS-first + EN gloss). */
    private fun gloss(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(cGloss)
        setPadding(0, 0, 0, 10)
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
    private fun narrow(b: Button) {
        val d = resources.displayMetrics.density
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding((14 * d).toInt(), b.paddingTop, (14 * d).toInt(), b.paddingBottom)
    }

    private var tryText: () -> String = { "" }

    private companion object {
        /** The try box's text across a language switch (the screen is rebuilt). */
        var keptText: String? = null
    }

    private fun addLanguageRow(root: LinearLayout) {
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
                if (l != Lang.current(this@SetupActivity)) {
                    // Through the running keyboard when there is one, so its
                    // view and dictionary follow at once - setting the stored
                    // choice alone left the open keyboard on the old language.
                    val ime = ImeService.running
                    if (ime == null || !ime.switchToLanguage(l)) Lang.setCurrent(this@SetupActivity, l)
                    keptText = tryText()
                    recreate()
                }
            }
        })
        root.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })
    }

    private fun addScriptMirror(root: LinearLayout, box: android.widget.EditText) {
        val lang = Lang.current(this)
        val modes = Script.offered(lang)
        if (modes.size < 2) return
        val prefs = getSharedPreferences("isv_collector", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        fun label(m: Script.Mode) = when (m) {
            Script.Mode.LATIN -> "ABC"; Script.Mode.CYRILLIC -> "АБВ"; Script.Mode.GLAGOLITIC -> "ⰀⰁⰂ"
        }
        var target = modes.firstOrNull { it.name == prefs.getString("try_target", null) }
            ?: modes.first { it != Script.mode(this, lang) }
        val out = TextView(this).apply {
            textSize = 20f
            setTextColor(cText)
            setTextIsSelectable(true)
            minLines = 2
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14 * d
                setColor(col("#EEF2F5", "#1E2A31"))
            }
        }
        fun update() {
            val typed = box.text?.toString().orEmpty()
            val latin = Script.back(typed, Script.mode(this, lang), lang)
            out.text = Script.convert(latin, target, lang)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * d).toInt(), 0, (4 * d).toInt())
        }
        val chips = modes.map { m ->
            Button(this).apply {
                text = label(m)
                isAllCaps = false
            narrow(this)
                textSize = 17f
                setOnClickListener {
                    target = m
                    prefs.edit().putString("try_target", m.name).apply()
                    update()
                    (parent as? LinearLayout)?.let { p ->
                        for (i in 0 until p.childCount) (p.getChildAt(i) as? Button)?.let { c ->
                            c.alpha = if (c === this) 1f else 0.45f
                        }
                    }
                }
                alpha = if (m == target) 1f else 0.45f
            }
        }
        chips.forEach { row.addView(it) }
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(Button(this).apply {
            text = "⧉"
            textSize = 18f
            narrow(this)
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Interslove", out.text))
                toast("✓ ${label(target)}")
            }
        })
        root.addView(out)
        root.addView(row)
        box.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = update()
        })
        update()
    }

    /**
     * Under the try box: the typed text glossed word by word into another
     * language (owner 2026-11-15), offline, from Wiktionary ([Gloss]). Polish
     * glosses into the other keyboard languages, every other language into
     * Polish. Not for Interslavic yet (its dictionary's licence is open).
     */
    private fun addGlossMirror(root: LinearLayout, box: android.widget.EditText) {
        val from = Lang.current(this)
        val targets = Gloss.targets(this, from)
        if (targets.isEmpty()) return
        val prefs = getSharedPreferences("isv_collector", MODE_PRIVATE)
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
                if (back !in Lang.enabled(this@SetupActivity))
                    Lang.setEnabled(this@SetupActivity, Lang.enabled(this@SetupActivity) + back)
                prefs.edit().putString("gloss_target_$back", from).apply()
                val ime = ImeService.running
                if (ime == null || !ime.switchToLanguage(back)) Lang.setCurrent(this@SetupActivity, back)
                keptText = out.text.toString()
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
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
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

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
