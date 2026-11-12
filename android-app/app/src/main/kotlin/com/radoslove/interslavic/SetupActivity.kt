package com.radoslove.interslavic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
        root.addView(buildLine())

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
        refreshStatus()
        refreshLangs()
        refreshWords()
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
        val dir = java.io.File(cacheDir, "batches").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // only the batch being sent
        val stamp = java.text.SimpleDateFormat("yyMMdd_HHmm", java.util.Locale.ROOT).format(java.util.Date())
        val file = java.io.File(dir, "interslove_${Collector.contributorId(this)}_$stamp.json")
        file.writeText(json, Charsets.UTF_8)
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.batches", file)
        val subject = t("sub_batch") + " · " + Collector.contributorId(this) + " · " + stamp
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

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(cBody)
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

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
