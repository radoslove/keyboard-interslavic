package com.radoslove.interslavic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * "It types" milestone (android_PLAN.md M1). Two layers:
 *  - letters: three rows + shift + backspace + space + return + globe, with
 *    `.` and `,` on the bottom row and `?123` to reach the symbol layer;
 *  - symbols: digits + common symbols, with the canonical MS punctuation
 *    „ ” – — as direct keys (docs/ms-latin-table.md).
 *
 * A base letter reaches its accent (c/s/z/e) or digraph (d/l/n) two ways, per
 * android_PLAN.md M1: LONGPRESS (hold) *and* FLICK (a quick upward swipe on the
 * key). Both commit the same thing; the flick is the fast habit that also has
 * to work on iOS, where it is the only accent gesture the OS allows. Shift
 * controls the case of the letter and of the accent/digraph.
 *
 * Deliberately a plain View: the headless build gets no Compose / resource
 * surprises, and behaviour is verified on a real device, never assumed —
 * flick thresholds especially have behaved differently on hardware.
 */
@SuppressLint("ViewConstructor", "SetTextI18n", "ClickableViewAccessibility")
class KeyboardView(
    context: Context,
    private val service: ImeService,
) : LinearLayout(context) {

    private var shift = false
    private var symbols = false

    private val flickHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMs = 300L

    // Prediction bar (M2). The strip is a single persistent view whose three
    // slots are updated on every keystroke; render() only re-parents it.
    private val suggestionViews = ArrayList<TextView>(3)
    private val suggestionStrip: LinearLayout by lazy { buildSuggestionStrip() }
    private var currentWord = ""
    // Per-slot state: the word to act on, and whether the slot is a "save this
    // MISS" chip (true) rather than an ordinary prediction (false).
    private val slotWord = arrayOfNulls<String>(3)
    private val slotIsSave = BooleanArray(3)
    private val slotIsSwipeAlt = BooleanArray(3)

    // Swipe / glide decoding (M4). letterKeyViews lets us hit-test which key the
    // finger is over as it crosses the board.
    private val letterKeyViews = ArrayList<Pair<Char, TextView>>()
    private var swiping = false
    private var swipeStartKey: Char? = null
    private val swipePath = StringBuilder()
    private var lastSwipeWord = ""

    // Visible trail so the user can SEE the glide as it happens.
    private val trailPts = ArrayList<Float>(128)
    private val trailPath = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x904FC3F7.toInt()   // semi-transparent light blue smudge
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#ECEFF1"))
        setPadding(dp(3), dp(6), dp(3), dp(6))
        setWillNotDraw(false)        // a ViewGroup must be told to draw
        trailPaint.strokeWidth = dp(9).toFloat()
        render()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (swiping && trailPts.size >= 4) {
            trailPath.reset()
            trailPath.moveTo(trailPts[0], trailPts[1])
            var i = 2
            while (i + 1 < trailPts.size) {
                trailPath.lineTo(trailPts[i], trailPts[i + 1]); i += 2
            }
            canvas.drawPath(trailPath, trailPaint)
        }
    }

    private fun addTrailPoint(x: Float, y: Float) {
        trailPts.add(x); trailPts.add(y)
    }

    private fun render() {
        removeAllViews()
        addView(suggestionStrip)
        if (symbols) renderSymbols() else renderLetters()
    }

    private fun renderLetters() {
        letterKeyViews.clear()
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
        // Whether this key has an accent/digraph at all (c/s/z/e, d/l/n).
        val hasAccent = Layout.longPress(ch, false) != null
        val flickThreshold = dp(22)

        var downX = 0f
        var downY = 0f
        var handled = false
        val longPress = Runnable {
            val out = Layout.longPress(ch, shift)
            if (out != null) {
                commit(out)
                handled = true
                tv.isPressed = false
            }
        }

        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; handled = false
                    tv.isPressed = true
                    if (hasAccent) flickHandler.postDelayed(longPress, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.x - downX) > touchSlop || abs(e.y - downY) > touchSlop) {
                        flickHandler.removeCallbacks(longPress)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    if (!handled) {
                        val dx = e.x - downX
                        val dy = e.y - downY
                        // Upward flick (mostly vertical) => the accent/digraph.
                        val flickUp = hasAccent && dy < -flickThreshold && abs(dy) >= abs(dx)
                        if (flickUp) {
                            Layout.longPress(ch, shift)?.let { commit(it) }
                        } else {
                            commit(if (shift) ch.uppercaseChar().toString() else ch.toString())
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    true
                }
                else -> false
            }
        }
        letterKeyViews.add(ch to tv)
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
        refreshSuggestions()
    }

    private fun backspace() {
        service.currentInputConnection?.deleteSurroundingText(1, 0)
        refreshSuggestions()
    }

    // ---- Prediction bar (M2) --------------------------------------------

    private fun buildSuggestionStrip(): LinearLayout {
        val strip = LinearLayout(context)
        strip.orientation = HORIZONTAL
        strip.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(42))
        strip.setBackgroundColor(Color.parseColor("#E0E4E7"))
        repeat(3) { i ->
            val tv = TextView(context)
            tv.gravity = Gravity.CENTER
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            tv.setTextColor(Color.parseColor("#1C2529"))
            val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(dp(1), dp(1), dp(1), dp(1))
            tv.layoutParams = lp
            tv.isClickable = true
            tv.setOnClickListener { onSlotTap(i) }
            suggestionViews.add(tv)
            strip.addView(tv)
        }
        return strip
    }

    /**
     * Recompute the strip: predictions in the left slots, and — when collection
     * is on and the typed word is a MISS — an explicit "＋ save" chip on the
     * right. Saving is a deliberate tap, never silent, so the user always sees
     * the option android_PLAN.md's collection loop needs.
     */
    private fun refreshSuggestions() {
        val before = service.currentInputConnection
            ?.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val word = before.takeLastWhile { it.isLetter() }
        currentWord = word
        clearSlots()
        if (word.isEmpty()) return

        val lower = word.lowercase()
        val canSave = Collector.isEnabled(context) &&
            Dictionary.isReady() &&
            word.length >= 3 &&
            !word.first().isUpperCase() &&
            !Dictionary.contains(lower)

        val preds = Dictionary.suggest(lower, if (canSave) 2 else 3)
        for (i in preds.indices) setSlot(i, preds[i], preds[i], save = false)
        if (canSave) setSlot(2, "＋ $lower", lower, save = true)
    }

    private fun setSlot(i: Int, display: String, word: String, save: Boolean) {
        slotWord[i] = word
        slotIsSave[i] = save
        slotIsSwipeAlt[i] = false
        val tv = suggestionViews[i]
        tv.text = display
        tv.setTextColor(if (save) Color.parseColor("#1B5E20") else Color.parseColor("#1C2529"))
        tv.setBackgroundColor(if (save) Color.parseColor("#C8E6C9") else Color.TRANSPARENT)
    }

    private fun clearSlots() {
        for (i in suggestionViews.indices) {
            slotWord[i] = null
            slotIsSave[i] = false
            slotIsSwipeAlt[i] = false
            suggestionViews[i].text = ""
            suggestionViews[i].setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun onSlotTap(i: Int) {
        val w = slotWord[i] ?: return
        when {
            slotIsSave[i] -> {
                Collector.record(context, w)
                suggestionViews[i].text = "✓ zapisano"
                flickHandler.postDelayed({ refreshSuggestions() }, 700)
            }
            slotIsSwipeAlt[i] -> replaceLastSwipeWord(w)
            else -> applySuggestion(w)
        }
    }

    /** Replace the word under the cursor with the tapped suggestion. */
    private fun applySuggestion(word: String) {
        val ic = service.currentInputConnection ?: return
        ic.beginBatchEdit()
        if (currentWord.isNotEmpty()) ic.deleteSurroundingText(currentWord.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        currentWord = ""
        clearSlots()
    }

    private fun enter() {
        val ic = service.currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    // ---- Swipe / glide (M4) ---------------------------------------------

    /** Which letter key, if any, is under a point in KeyboardView coordinates. */
    private fun keyAt(x: Float, y: Float): Char? {
        for ((ch, v) in letterKeyViews) {
            val row = v.parent as? android.view.View ?: continue
            val left = row.left + v.left
            val top = row.top + v.top
            if (x >= left && x < left + v.width && y >= top && y < top + v.height) return ch
        }
        return null
    }

    /**
     * Steal the gesture from the child keys the moment the finger crosses from
     * its start key into a DIFFERENT letter key — that unambiguously separates a
     * glide from a tap, longpress or (vertical, same-key) flick.
     */
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (symbols) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartKey = keyAt(e.x, e.y)
                swiping = false
                swipePath.setLength(0)
                trailPts.clear()
                addTrailPoint(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                addTrailPoint(e.x, e.y)
                val start = swipeStartKey ?: return false
                val k = keyAt(e.x, e.y)
                if (k != null && k != start) {
                    swiping = true
                    swipePath.append(start).append(k)
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!swiping) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                addTrailPoint(e.x, e.y)
                val k = keyAt(e.x, e.y)
                if (k != null && (swipePath.isEmpty() || swipePath.last() != k)) {
                    swipePath.append(k)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val path = swipePath.toString()
                swiping = false
                swipeStartKey = null
                trailPts.clear()
                invalidate()
                if (path.length >= 2) decodeSwipe(path)
            }
            MotionEvent.ACTION_CANCEL -> {
                swiping = false
                swipeStartKey = null
                trailPts.clear()
                invalidate()
            }
        }
        return true
    }

    private fun decodeSwipe(path: String) {
        val words = Dictionary.decodeSwipe(path, 3)
        if (words.isEmpty()) return
        val ic = service.currentInputConnection ?: return
        val best = if (shift) words[0].replaceFirstChar { it.uppercaseChar() } else words[0]
        ic.commitText("$best ", 1)
        lastSwipeWord = best
        currentWord = ""
        // Offer the alternatives so a wrong guess is one tap from fixed.
        clearSlots()
        for (i in words.indices) {
            slotWord[i] = words[i]
            slotIsSwipeAlt[i] = true
            suggestionViews[i].text = words[i]
            suggestionViews[i].setTextColor(Color.parseColor("#1C2529"))
            suggestionViews[i].setBackgroundColor(
                if (i == 0) Color.parseColor("#DCE3E7") else Color.TRANSPARENT
            )
        }
    }

    /** Replace the word the swipe just committed with a chosen alternative. */
    private fun replaceLastSwipeWord(word: String) {
        val ic = service.currentInputConnection ?: return
        if (lastSwipeWord.isEmpty()) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(lastSwipeWord.length + 1, 0)   // word + trailing space
        val out = if (shift) word.replaceFirstChar { it.uppercaseChar() } else word
        ic.commitText("$out ", 1)
        ic.endBatchEdit()
        lastSwipeWord = out
        clearSlots()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
