package com.radoslove.interslavic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
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
 * OFF), the pending count, and a deliberate export. Everything here is on-device
 * — the export hands the owner a file; it does not send anything anywhere.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui.current(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }

        // Language of this screen. The heading carries all four names, so it can
        // be found whichever language the screen is in now.
        root.addView(header(Ui.LANG_HEAD))
        // One per line: four names in a row did not fit a phone (a55: "Norsk" ran
        // off the screen, "Polski" broke in two).
        root.addView(RadioGroup(this).apply {
            for (lang in Ui.ALL) {
                addView(RadioButton(this@SetupActivity).apply {
                    id = android.view.View.generateViewId()
                    text = Ui.NAMES.getValue(lang)
                    textSize = 17f
                    isChecked = lang == ui
                    setOnClickListener {
                        if (lang != ui) { Ui.set(this@SetupActivity, lang); recreate() }
                    }
                })
            }
        })

        section(root, "title", null)
        root.addView(body(t("intro")))
        glossOf("intro")?.let { root.addView(gloss(it)) }

        // Languages, as on iOS where each one is its own keyboard (7801f4b):
        // on Android they are the keyboard's entries in the SYSTEM list, ticked
        // on the system's Languages page - this screen shows them and opens it.
        section(root, "langs_head", "langs_body")
        langsNow = body("")
        root.addView(langsNow)
        root.addView(Button(this).apply {
            text = labelled("btn_langs", " — ")
            setOnClickListener {
                val imi = Lang.imi(this@SetupActivity)
                val i = Intent(android.provider.Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS)
                if (imi != null) i.putExtra(android.provider.Settings.EXTRA_INPUT_METHOD_ID, imi.id)
                // Not yet a keyboard at all: the list where it is switched on.
                try { startActivity(if (imi != null) i else Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                catch (_: Exception) { startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            }
        })

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
            text = labelled("btn_batch", " — ")
            setOnClickListener { exportBatch() }
        })
        root.addView(Button(this).apply {
            text = labelled("btn_export", " — ")
            setOnClickListener { exportQueue() }
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
        langsNow.text = "${t("langs_now")}: " + Lang.enabled(this).joinToString(", ") { langName(it) }
        val top = Symbols.top(this, 15)
        symbolsNow.text = if (top.isEmpty()) t("sym_none")
            else top.joinToString("   ") { (mark, n) -> "$mark $n" }
        symbolsNow.textSize = 17f
    }

    private lateinit var langsNow: TextView
    private lateinit var symbolsNow: TextView

    private fun t(key: String) = Ui.t(ui, key)
    private fun glossOf(key: String) = Ui.gloss(ui, key)

    /** `ms` shows `Interslavic (English)`, the other languages their text alone. */
    private fun labelled(key: String, sep: String = " "): String =
        glossOf(key)?.let { "${t(key)}$sep($it)" } ?: t(key)

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

    private fun exportBatch() {
        if (Collector.pendingCount(this) == 0 && Popularity.pendingCount(this) == 0) {
            toast(t("t_empty"))
            return
        }
        val json = Collector.exportBatchJson(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, t("sub_batch"))
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(send, t("chooser")))
    }

    private fun exportQueue() {
        val text = Collector.exportInboxText(this)
        if (text.isBlank()) {
            toast(t("t_empty"))
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, t("sub_review"))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, t("chooser")))
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 20f
        setTextColor(Color.parseColor("#1C2529"))
        setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 4)
        gravity = Gravity.START
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(Color.parseColor("#37474F"))
        setPadding(0, 4, 0, 8)
    }

    /** EN gloss under an MS header/paragraph — smaller and lighter, so MS reads
     *  as primary and English as the aid (MS-first + EN gloss). */
    private fun gloss(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(Color.parseColor("#78909C"))
        setPadding(0, 0, 0, 10)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
