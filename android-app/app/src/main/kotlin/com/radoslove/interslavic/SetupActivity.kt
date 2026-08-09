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

        root.addView(header("Medžuslovjansky"))
        root.addView(body(
            "Klawiatura międzysłowiańska. Litery ze standardu (č š ž ě) na " +
            "long-press i machnięciu w górę; pasek podpowiedzi ze słownika."
        ))

        root.addView(header("Zbieranie nowych słów (opcjonalne)"))
        root.addView(body(
            "Gdy włączone, klawiatura zapisuje LOKALNIE słowa, których nie ma w " +
            "słowniku, żeby później trafiły do rewizji (nie są poprawiane " +
            "automatycznie). Nic nie jest wysyłane — eksport to plik, który " +
            "przekazujesz sam. Domyślnie wyłączone."
        ))

        val sw = Switch(this).apply {
            text = "Zbieraj nowe słowa"
            textSize = 17f
            isChecked = Collector.isEnabled(this@SetupActivity)
            setOnCheckedChangeListener { _, on ->
                Collector.setEnabled(this@SetupActivity, on)
                refreshStatus()
            }
        }
        root.addView(sw)

        status = body("")
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Wyślij do bazy (batch JSON)"
            setOnClickListener { exportBatch() }
        })
        root.addView(Button(this).apply {
            text = "Eksportuj do rewizji (lista)"
            setOnClickListener { exportQueue() }
        })
        root.addView(Button(this).apply {
            text = "Wyczyść kolejkę"
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
        status.text = "Zbieranie: $on · w kolejce: $n słów · id: ${Collector.contributorId(this)}"
    }

    private fun exportBatch() {
        val json = Collector.exportBatchJson(this)
        if (Collector.pendingCount(this) == 0) {
            toast("Kolejka jest pusta")
            return
        }
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

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
