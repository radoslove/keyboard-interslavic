package com.radoslove.interslavic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "It types" milestone (android_PLAN.md M1). Two layers:
 *  - letters: three rows + shift + backspace + space + return + globe, with
 *    `.` and `,` on the bottom row and `?123` to reach the symbol layer;
 *  - symbols: digits + common symbols, with the canonical MS punctuation
 *    „ ” – — as direct keys (docs/ms-latin-table.md).
 *
 * Longpress a base letter to reach its accent (c/s/z/e) or digraph (d/l/n);
 * shift controls the case of both the letter and the longpress output.
 * Flick is not here yet — it is the other half of M1 and the next increment.
 *
 * Deliberately a plain View: the headless build gets no Compose / resource
 * surprises, and behaviour is verified on a real device, never assumed.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class KeyboardView(
    context: Context,
    private val service: ImeService,
) : LinearLayout(context) {

    private var shift = false
    private var symbols = false

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#ECEFF1"))
        setPadding(dp(3), dp(6), dp(3), dp(6))
        render()
    }

    private fun render() {
        removeAllViews()
        if (symbols) renderSymbols() else renderLetters()
    }

    private fun renderLetters() {
        Layout.letterRows.forEachIndexed { index, row ->
            val rv = makeRow()
            if (index == 2) {
                val sk = functionKey("⇧", 1.5f) { shift = !shift; render() }
                if (shift) sk.setBackgroundColor(Color.parseColor("#B0BEC5"))
                rv.addView(sk)
            }
            row.forEach { ch -> rv.addView(letterKey(ch)) }
            if (index == 2) {
                rv.addView(functionKey("⌫", 1.5f) { backspace() })
            }
            addView(rv)
        }
        val b = makeRow()
        b.addView(functionKey("?123", 1.6f) { symbols = true; render() })
        b.addView(functionKey(",", 1f) { commit(",") })
        b.addView(functionKey("🌐", 1f) { service.switchIme() })
        b.addView(functionKey("space", 4f) { commit(" ") })
        b.addView(functionKey(".", 1f) { commit(".") })
        b.addView(functionKey("⏎", 1.6f) { enter() })
        addView(b)
    }

    private fun renderSymbols() {
        Layout.symbolRows.forEachIndexed { index, row ->
            val rv = makeRow()
            row.forEach { ch -> rv.addView(symbolKey(ch)) }
            if (index == 2) {
                rv.addView(functionKey("⌫", 1.5f) { backspace() })
            }
            addView(rv)
        }
        val b = makeRow()
        b.addView(functionKey("ABC", 1.6f) { symbols = false; render() })
        b.addView(functionKey(",", 1f) { commit(",") })
        b.addView(functionKey("🌐", 1f) { service.switchIme() })
        b.addView(functionKey("space", 4f) { commit(" ") })
        b.addView(functionKey(".", 1f) { commit(".") })
        b.addView(functionKey("⏎", 1.6f) { enter() })
        addView(b)
    }

    private fun makeRow(): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        return row
    }

    private fun letterKey(ch: Char): TextView {
        val label = if (shift) ch.uppercaseChar().toString() else ch.toString()
        val tv = baseKey(label, 1f)
        tv.setOnClickListener {
            commit(if (shift) ch.uppercaseChar().toString() else ch.toString())
        }
        tv.setOnLongClickListener {
            val out = Layout.longPress(ch, shift)
            if (out != null) { commit(out); true } else false
        }
        return tv
    }

    private fun symbolKey(ch: Char): TextView {
        val tv = baseKey(ch.toString(), 1f)
        tv.setOnClickListener { commit(ch.toString()) }
        return tv
    }

    private fun functionKey(text: String, weight: Float, onTap: () -> Unit): TextView {
        val tv = baseKey(text, weight)
        tv.setOnClickListener { onTap() }
        return tv
    }

    private fun baseKey(text: String, weight: Float): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        tv.setTextColor(Color.parseColor("#1C2529"))
        tv.setBackgroundColor(Color.WHITE)
        val lp = LayoutParams(0, dp(50), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        tv.layoutParams = lp
        tv.isClickable = true
        tv.isLongClickable = true
        return tv
    }

    private fun commit(s: String) {
        service.currentInputConnection?.commitText(s, 1)
    }

    private fun backspace() {
        service.currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun enter() {
        val ic = service.currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
