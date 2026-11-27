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

/** The app's privacy policy (website, section "privacy"). */
internal const val PRIVACY_URL = "https://interslove.vercel.app/#privacy"

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

    /** The screen follows the SAME choice the keyboard draws by, not the
     *  phone's mode - picking "always dark" for the keyboard and getting a
     *  white settings screen was the whole complaint. Read once, so the radio
     *  below rebuilds the screen with `recreate()`. The system widgets
     *  (buttons, radios, switches) come from the window theme, our own text
     *  from the pairs under it. */
    private var dark = false
    internal fun col(light: String, night: String) =
        Color.parseColor(if (dark) night else light)
    private val cBg get() = col("#FAFAFA", "#1B1F22")      // keyboard board colour
    internal val cText get() = col("#1C2529", "#E8EAED")    // 13.8:1 on the dark bg
    private val cBody get() = col("#37474F", "#B0BEC5")    // 8.7:1
    internal val cGloss get() = col("#78909C", "#90A4AE")   // the EN aid, below the body

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = Theme.isDark(this)
        setTheme(if (dark) android.R.style.Theme_DeviceDefault
                 else android.R.style.Theme_DeviceDefault_Light_DarkActionBar)
        super.onCreate(savedInstanceState)
        // One bar, ours (icon, "Interslove", the tab's name): the system's
        // action bar above it showed the app's name a second time.
        actionBar?.hide()
        ui = Ui.current(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(cBg)
        }
        // Three tabs in the one app: writing and translating,
        // learning the user's own words, and the keyboard's settings. The try
        // tab lives in TryTab.kt, the learning tab in LearnTab.kt.
        val tryRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val learnRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(tabBar(tryRoot, learnRoot, root))
        page.addView(tryRoot)
        page.addView(learnRoot)
        page.addView(root)
        buildTryTab(tryRoot)
        buildLearnTab(learnRoot)

        // Which build is this? The released app showed its version nowhere at
        // all - only a test build carried it, and only inside its label - so a
        // tester who had installed it could not answer "which one do you have?",
        // and a bug report could not say which build it was about. Not
        // translated on purpose: a version and a package id read the same in all
        // four screen languages, and the `.debug` suffix is itself the answer to
        // "is this the test build or the published one?".

        section(root, "title", null)
        root.addView(body(t("intro")))
        glossOf("intro")?.let { root.addView(gloss(it)) }

        // Languages: ticked HERE (and in the keyboard's own panel), one
        // keyboard in the system (parity with iOS 0.6.3).
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

        // The app's own language: the screen everything else is read in (iOS
        // 261118). "Automatic" keeps the old way: the keyboard's language, else the phone's.
        if (Ui.PICK.isNotEmpty()) {
            section(root, "ui_lang_pick", null)
            val now = Ui.chosen(this)
            root.addView(Button(this).apply {
                isAllCaps = false
                textSize = 17f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                text = (Ui.PICK.firstOrNull { it.first == now }?.second ?: t("theme_auto")) + "  ▾"
                setOnClickListener {
                    val codes = listOf("") + Ui.PICK.map { it.first }
                    val labels = (listOf(t("theme_auto")) + Ui.PICK.map { it.second }).toTypedArray()
                    android.app.AlertDialog.Builder(this@SetupActivity)
                        .setSingleChoiceItems(labels, codes.indexOf(now).coerceAtLeast(0)) { dlg, k ->
                            dlg.dismiss()
                            if (codes[k] != now) { Ui.choose(this@SetupActivity, codes[k]); recreate() }
                        }.show()
                }
            })
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

        // The user's own words (the `＋ word` chip) moved to the learning tab:
        // they gather where they are reviewed.

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
        // keyboards. Read when the keyboard next draws.
        section(root, "layout_head", "layout_body")
        for (lang in Lang.ALL.filter { KeyRows.offered(it) }) {
            root.addView(Switch(this).apply {
                text = "${langName(lang)} · ${KeyRows.nationalName(lang)}"
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

        // The privacy policy, linked from inside the app as Google Play asks
        // ("on your store listing and within your app", 2026-11-18). The page
        // opens in the browser: the keyboard itself has no network permission.
        root.addView(Button(this).apply {
            text = t("privacy_link")
            setOnClickListener { openPrivacyPolicy() }
        })

        // Gesture log for error reports (iOS 0.6.11): not for
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
            addView(page)
        }
        // From API 35 the window is drawn edge to edge: without these insets
        // the top of the page sat under the status bar and the bottom under
        // the navigation bar. The background still fills the whole screen.
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            // Without the system's action bar the status bar lies on our own
            // background: the page stops under it (not scrolling behind the
            // clock), and on a light screen its icons are dark.
            scroll.clipToPadding = true
            val light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() or
                        android.view.WindowInsets.Type.displayCutout())
                // Edge to edge, the window is no longer resized for the
                // keyboard: the keyboard's height goes into the bottom padding,
                // so the page can still scroll the focused box above it.
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                // Keyboard just appeared: once the new padding is laid out,
                // bring the focused box (its top, where the caret is) into view.
                val f = v.findFocus()
                if (ime.bottom > 0 && f != null && f !== v) {
                    val sv = v as ScrollView
                    sv.viewTreeObserver.addOnGlobalLayoutListener(object :
                        android.view.ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            val r = android.graphics.Rect(0, 0, f.width, f.height)
                            sv.offsetDescendantRectToMyCoords(f, r)
                            // Bottom edge of what the keyboard leaves visible, in content px.
                            val bottomVisible = sv.scrollY + sv.height - sv.paddingBottom
                            val room = (resources.displayMetrics.density * 140).toInt()
                            if (r.top + room > bottomVisible) {
                                val target = r.top + room - (sv.height - sv.paddingBottom)
                                sv.smoothScrollTo(0, minOf(target, r.top - sv.paddingTop).coerceAtLeast(0))
                            }
                        }
                    })
                }
                insets
            }
        }
        setContentView(scroll)
    }

    // The language switched on the keyboard (space bar, 🌐) while this screen
    // is open: the screen, its language and the text in ✎ follow (owner 261121).
    private val langListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // The script key on the keyboard (next to 🌐): the text in the box
        // and the alphabet row follow it at once (owner 261127).
        if (key != null && key.startsWith("script") && !trySwitching) {
            val cur = Lang.current(this)
            if (key == (if (cur == Lang.ISV) "script" else "script_$cur"))
                tryScriptFollow?.invoke(Script.chosen(this, cur))
            return@OnSharedPreferenceChangeListener
        }
        if (key != "language" || trySwitching) return@OnSharedPreferenceChangeListener
        val now = Lang.current(this)
        if (tryBuiltSig?.substringAfter('|')?.substringBefore('|') == now) return@OnSharedPreferenceChangeListener
        val f = tryFollow
        if (f != null) f(now) else { tryKeptText = tryText(); recreate() }
    }

    /** While a learning pass runs, Back ends the pass, not the app. With
     *  targetSdk 36 the system calls no onBackPressed on API 33+, so a
     *  callback is registered for the pass only. */
    private var backCb: Any? = null
    internal fun learnPassing(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (on && backCb == null) {
            val cb = android.window.OnBackInvokedCallback {
                if (learnBack?.invoke() != true) { learnPassing(false); moveTaskToBack(true) }
            }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCb = cb
        } else if (!on && backCb != null) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCb as android.window.OnBackInvokedCallback)
            backCb = null
        }
    }

    /** Back ends a learning pass first; only then does it leave the app. */
    @Deprecated("Activity, not ComponentActivity: no dispatcher here")
    override fun onBackPressed() {
        if (learnBack?.invoke() == true) return
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("isv_collector", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(langListener)
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences("isv_collector", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(langListener)
        if (Ui.current(this) != ui) { recreate(); return }
        if (batchHandedOver) {
            batchHandedOver = false
            Collector.clear(this)
            Popularity.clear(this)
        }
        refreshStatus()
        refreshLangs()
        learnRefresh()
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

    internal fun t(key: String) = Ui.t(ui, key)
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
    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL)))
        } catch (_: android.content.ActivityNotFoundException) {
            // No browser at all: show the address, so it can be typed elsewhere.
            Toast.makeText(this, PRIVACY_URL, Toast.LENGTH_LONG).show()
        }
    }

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
        // into the database (by mail and not over HTTPS: the
        // app gets no INTERNET permission, the user sends from their own mail
        // app, and the mail itself is the notice that something came in).
        // Subject, a one-line body and the batch as a .json ATTACHMENT, all
        // filled in (3.9.6: Gmail left the subject empty - ACTION_SENDTO
        // drops the extras). ACTION_SEND, one intent per mail app, so the
        // choice still offers only mail apps.
        val stamp = java.text.SimpleDateFormat("yyMMdd_HHmm", java.util.Locale.ROOT).format(java.util.Date())
        mailFile("interslove_${Collector.contributorId(this)}_$stamp.json", json,
            t("sub_batch") + " · " + Collector.contributorId(this) + " · " + stamp)
        batchHandedOver = true
    }

    /** The batch went to the mail app: on the way back the queue is emptied
     *  (sent words should go from the list at once). The
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

    internal fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 20f
        setTextColor(cText)
        setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 4)
        gravity = Gravity.START
    }

    /** Descriptions: smaller and quieter than the headers and the controls. */
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



    /** ⚙ (keyboard settings) | ✎ (write, translate) | 🎓 (learn own words); settings
     *  first (the main purpose of the app), big enough to see, equal
     *  widths as on iOS 0.6.36; the last one chosen stays chosen. */
    private fun tabBar(tryRoot: View, learnRoot: View, setRoot: View): View {
        val prefs = getSharedPreferences("isv_collector", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (8 * d).toInt())
        }
        // Above the tabs: the app's icon with a small "Interslove" at the side,
        // and the shown tab's name as the title in the ordinary size (iOS
        // `brandTitle`, owner 261118: panel names instead of "Interslove", which
        // goes small at the side).
        val title = TextView(this).apply {
            textSize = 18f
            setTextColor(cText)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // The icon's hearts on a grey tile, the screen's own surface: the
            // launcher's white tile (Samsung) hid the white heart (owner 261127).
            addView(android.widget.FrameLayout(this@SetupActivity).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 10 * d
                    setColor(col("#DDE1E4", "#3A4045"))
                }
                clipToOutline = true
                contentDescription = "Interslove"
                // The hearts as a vector without the icon's field (the
                // launcher foreground PNG has an opaque white field baked in).
                addView(android.widget.ImageView(this@SetupActivity).apply {
                    setImageResource(R.drawable.hearts_mark)
                }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()))
            addView(TextView(this@SetupActivity).apply {
                text = "Interslove"
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(cGloss)
                setPadding((8 * d).toInt(), 0, 0, 0)
            })
        }
        val head = android.widget.FrameLayout(this).apply {
            setPadding(0, 0, 0, (6 * d).toInt())
            addView(brand, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (44 * d).toInt(), Gravity.START or Gravity.CENTER_VERTICAL))
            addView(title, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (44 * d).toInt(), Gravity.CENTER))
        }
        val names = mapOf("set" to "settings_head", "try" to "tab_write", "learn" to "learn_head")
        val tabs = listOf("set" to "⚙", "try" to "✎", "learn" to "🎓")
        val buttons = ArrayList<Button>()
        fun show(which: String) {
            // Languages ticked in settings (or switched on the keyboard) since
            // the write tab was built: build it again, text kept (owner 261121:
            // Interslavic ticked, missing in the row).
            if (which == "try" && tryRoot is LinearLayout && tryRoot.childCount > 0 && trySig() != tryBuiltSig) {
                tryKeptText = tryText()
                tryRoot.removeAllViews()
                buildTryTab(tryRoot)
            }
            title.text = t(names[which] ?: "settings_head")
            tryRoot.visibility = if (which == "try") View.VISIBLE else View.GONE
            learnRoot.visibility = if (which == "learn") View.VISIBLE else View.GONE
            setRoot.visibility = if (which == "set") View.VISIBLE else View.GONE
            for ((i, btn) in buttons.withIndex()) btn.alpha = if (tabs[i].first == which) 1f else 0.45f
            prefs.edit().putString("app_tab", which).apply()
        }
        for ((key, label) in tabs) buttons.add(Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 26f
            setOnClickListener { show(key) }
            bar.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        show(prefs.getString("app_tab", "set") ?: "set")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(head)
            addView(bar)
        }
    }

    internal fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
