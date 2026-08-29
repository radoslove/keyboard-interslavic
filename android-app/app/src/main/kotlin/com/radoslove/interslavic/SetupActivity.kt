package com.radoslove.interslavic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * The container app (android_PLAN.md M3). Its whole job is the collection
 * consent surface the keyboard cannot show itself: an opt-in switch (default
 * OFF), the pending count, and a deliberate export. Everything here is on-device
 * — the export hands the owner a file; it does not send anything anywhere.
 */
class SetupActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }

        // MS-first + EN gloss (interslavic-tutor ruling, DB-verified :30020).
        root.addView(header("Medžuslovjansky"))
        root.addView(gloss("Interslavic"))
        root.addView(body(
            "Medžuslovjanska klaviatura. Standardne bukvy (č š ž ě) na dolgom " +
            "pritisku i mahu nagoru; pas prědloženj iz slovnika."
        ))
        root.addView(gloss(
            "Interslavic keyboard. Standard letters (č š ž ě) on long-press and " +
            "an upward flick; a suggestion bar from the dictionary."
        ))

        root.addView(header("Sbiranje novyh slov (opcionalno)"))
        root.addView(gloss("Collecting new words (optional)"))
        root.addView(body(
            "Kogda je vključeno, klaviatura lokalno zapisyvaje slova, ktoryh ně v " +
            "slovniku, da by pozdněje šli do revizije (nikoli ně avtomatično " +
            "popravjena). Ničto se ne posyla — izvoz je fajl, ktory sam prědaješ " +
            "dalje. Standardno vyključeno."
        ))
        root.addView(gloss(
            "When on, the keyboard saves words NOT in the dictionary LOCALLY, to " +
            "later go to review (never auto-corrected). Nothing is sent — the " +
            "export is a file you pass on yourself. Off by default."
        ))

        val sw = Switch(this).apply {
            text = "Sbiraj nove slova (Collect new words)"
            textSize = 17f
            isChecked = Collector.isEnabled(this@SetupActivity)
            setOnCheckedChangeListener { _, on ->
                Collector.setEnabled(this@SetupActivity, on)
                refreshStatus()
            }
        }
        root.addView(sw)

        root.addView(header("Poredok popularnosti (opcionalno)"))
        root.addView(gloss("Popularity ranking (optional)"))
        root.addView(body(
            "Kogda je vključeno, slova, ktora potvrdiš pri pisanju — svaipom, " +
            "izborom iz pasa, dovršenjem znanego slova — sut lokalno čislena za " +
            "spisok najpopularnějših medžuslovjanskih slov. Psevdonimno " +
            "(identifikator ustrojstva niže), bez ličnyh danyh. Ničto ne odhodi " +
            "avtomatično — do bazy dojde tolko pri tvojem izvozu. Standardno " +
            "vyključeno."
        ))
        root.addView(gloss(
            "When on, words you CONFIRM while typing — swipe, picking from the " +
            "bar, finishing a known word — are counted LOCALLY to feed a ranking " +
            "of the most popular Interslavic words. Pseudonymously (device id " +
            "below), no personal data. Nothing leaves automatically — it reaches " +
            "the database only on your export. Off by default."
        ))
        val swPop = Switch(this).apply {
            text = "Vklad do poredka popularnosti (Contribute to the popularity ranking)"
            textSize = 17f
            isChecked = Popularity.isEnabled(this@SetupActivity)
            setOnCheckedChangeListener { _, on ->
                Popularity.setEnabled(this@SetupActivity, on)
                refreshStatus()
            }
        }
        root.addView(swPop)

        root.addView(header("Zvuk i vibracija (opcionalno)"))
        root.addView(gloss("Sound and vibration (optional)"))
        root.addView(body(
            "Kratky zvuk i vibracija pri každoj klaviši. Standardno vyključeno."
        ))
        root.addView(gloss(
            "A short sound and vibration on each key. Off by default."
        ))
        val prefs = getSharedPreferences("isv_collector", MODE_PRIVATE)
        root.addView(Switch(this).apply {
            text = "Zvuk i vibracija klaviš (Key sound and vibration)"
            textSize = 17f
            isChecked = prefs.getBoolean("feedback", false)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("feedback", on).apply()
            }
        })

        root.addView(header("Razstup medžu slovami"))
        root.addView(gloss("Spacing between words"))
        root.addView(body(
            "Standardno razstup jest dodavany PRED slědujuće slovo, tako že " +
            "interpunkcija drži se slova bez razstupa. Iz-ključi, ako hoćeš " +
            "stary sposob: razstup naide srazu po slovu."
        ))
        root.addView(gloss(
            "By default the space is added BEFORE the next word, so punctuation " +
            "stays tight against the word. Turn this off for the classic way: " +
            "the space follows the word immediately."
        ))
        root.addView(Switch(this).apply {
            text = "Umny razstup (Smart space)"
            textSize = 17f
            isChecked = prefs.getBoolean("smart_space", true)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("smart_space", on).apply()
            }
        })

        status = body("")
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Pošli do bazy (grupa JSON) — Send to the database (JSON batch)"
            setOnClickListener { exportBatch() }
        })
        root.addView(Button(this).apply {
            text = "Izvezi do revizije (spisok) — Export for review (list)"
            setOnClickListener { exportQueue() }
        })
        root.addView(Button(this).apply {
            text = "Očisti red (Clear the queue)"
            setOnClickListener {
                Collector.clear(this@SetupActivity)
                refreshStatus()
                toast("Kolejka wyczyszczona")
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
    }

    private fun refreshStatus() {
        val n = Collector.pendingCount(this)
        val on = if (Collector.isEnabled(this)) "włączone" else "wyłączone"
        val pop = if (Popularity.isEnabled(this)) "on (${Popularity.pendingCount(this)})" else "off"
        status.text = "Nowe słowa: $on · w kolejce: $n · ranking: $pop · " +
            "id: ${Collector.contributorId(this)}"
    }

    private fun exportBatch() {
        if (Collector.pendingCount(this) == 0 && Popularity.pendingCount(this) == 0) {
            toast("Kolejka jest pusta")
            return
        }
        val json = Collector.exportBatchJson(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "medžuslovjansky — batch do bazy")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(send, "Wyślij batch do bazy"))
    }

    private fun exportQueue() {
        val text = Collector.exportInboxText(this)
        if (text.isBlank()) {
            toast("Kolejka jest pusta")
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "medžuslovjansky — słowa do rewizji")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Eksportuj kolejkę"))
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
