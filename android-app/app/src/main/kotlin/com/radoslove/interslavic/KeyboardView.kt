package com.radoslove.interslavic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioManager
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.sqrt

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

    private var shift = false          // uppercase active now (one-shot OR locked)
    private var capsLocked = false     // caps-lock: stays until turned off
    private var lastShiftTapMs = 0L    // double-tap-to-lock window
    private val doubleTapMs = 300L
    private var symbols = false

    private val flickHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMs = 300L

    // Background thread for the swipe decode so the glide never janks.
    private val decodeThread = HandlerThread("swipe-decode").apply { start() }
    private val decodeHandler = Handler(decodeThread.looper)

    // Prediction bar (M2). The strip is a single persistent view whose three
    // slots are updated on every keystroke; render() only re-parents it.
    private val suggestionViews = ArrayList<TextView>(3)
    private val suggestionStrip: android.view.View by lazy { buildSuggestionStrip() }
    private var currentWord = ""
    // Per-slot state: the word to act on, and whether the slot is a "save this
    // MISS" chip (true) rather than an ordinary prediction (false).
    /** Chips in the suggestion strip. ONE number governs the views and every
     *  array that describes them - when these drifted apart (6 views, 3-slot
     *  arrays) the keyboard threw out of bounds on every refresh, which the
     *  system shows as the keyboard switching itself off after each word. */
    private val SLOTS = 6
    private val slotWord = arrayOfNulls<String>(SLOTS)
    private val slotIsSave = BooleanArray(SLOTS)
    private val slotIsSwipeAlt = BooleanArray(SLOTS)

    // Swipe / glide decoding (M4). letterKeyViews lets us hit-test which key the
    // finger is over as it crosses the board.
    private val letterKeyViews = ArrayList<Pair<Char, TextView>>()
    private var swiping = false
    private var swipeStartKey: Char? = null
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private val swipePath = StringBuilder()
    private var lastSwipeWord = ""
    private var lastSwipeCandidates: List<String> = emptyList()
    private var lastPreviewMs = 0L            // throttle the live in-glide preview
    private var backspaceInterval = 200L      // backspace-hold repeat, accelerates
    private var swipeJustCommitted = false   // true right after a glide, until any other key
    // Smart space: a committed word carries NO trailing space; the space is
    // inserted BEFORE the next word instead, and punctuation attaches with no
    // space before it (Gboard-style).
    /** Whether the glide just committed was capitalised. Shift is one-shot and
     *  is spent the moment the word lands, so by the time an alternative is
     *  picked from the strip it is long gone - and the replacement came out
     *  lowercase even though the user had deliberately pressed shift. */
    private var lastSwipeCapitalized = false
    private var pendingSpace = false
    // Glued to the word, no space before it. Apostrophes and CLOSING quotes
    // belong here too: without them smart space read `'` as the start of a new
    // word and pushed a space in front of it.
    private val ATTACH_PUNCT = ".,!?:;\u2026'\u2019\u201D\")]".toSet()

    /** Smart space (default) owes the space to the NEXT thing typed, so
     *  punctuation lands tight against the word. Classic mode instead puts the
     *  space in immediately after a committed word, which is what people who
     *  learned to type on a desktop expect - and what someone editing in the
     *  middle of a sentence finds less surprising.
     *
     *  Read per use rather than cached: SharedPreferences keeps its values in
     *  memory after the first load, and a stale cache would leave the keyboard
     *  behaving one way while the setting screen says the other. */
    private val smartSpace: Boolean
        get() = context.getSharedPreferences("isv_collector", Context.MODE_PRIVATE)
            .getBoolean("smart_space", true)
    private val SWIPE_RESAMPLE = 32
    private val SWIPE_CANDIDATES = SLOTS   // the strip scrolls, so every candidate is reachable
    private val ANCHOR_KEYS = 3       // start-key candidates; 1 hid `pisati` behind `ležati`
    private val DWELL_N = 28          // measured: same 99% accuracy as 40, ~35% cheaper
    // Adaptive-ranking weights: how hard one prior use lifts a word.
    private val USAGE_W_SUGGEST = 8000       // in freq*64 units (max freq term ~16k)
    private val USAGE_W_SWIPE = 8.0          // in px-equivalent of the shape score

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
        previewText?.let { t ->
            val w = maxOf(previewKeyW * 1.3f, dp(46).toFloat())
            val h = dp(54).toFloat()
            val left = previewCx - w / 2f
            val right = previewCx + w / 2f
            var bottom = previewKeyTop - dp(4)
            var top = bottom - h
            if (top < 0f) { top = 0f; bottom = h }          // keep it on-screen for the top row
            val r = dp(8).toFloat()
            canvas.drawRoundRect(left, top, right, bottom, r, r, previewBg)
            canvas.drawRoundRect(left, top, right, bottom, r, r, previewBorder)
            previewTxt.textSize = dp(28).toFloat()
            val fm = previewTxt.fontMetrics
            canvas.drawText(t, previewCx, (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f, previewTxt)
        }
    }

    private fun addTrailPoint(x: Float, y: Float) {
        trailPts.add(x); trailPts.add(y)
    }

    // Key-press preview bubble (shows the pressed letter enlarged above the key).
    private var previewText: String? = null
    private var previewCx = 0f
    private var previewKeyTop = 0f
    private var previewKeyW = 0f
    private val previewBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val previewBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val previewTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C2529"); textAlign = Paint.Align.CENTER
    }

    private fun showKeyPreview(tv: TextView) {
        val row = tv.parent as? android.view.View ?: return
        previewText = tv.text?.toString()
        previewCx = row.left + tv.left + tv.width / 2f
        previewKeyTop = (row.top + tv.top).toFloat()
        previewKeyW = tv.width.toFloat()
        invalidate()
    }

    private fun hideKeyPreview() {
        if (previewText != null) { previewText = null; invalidate() }
    }

    /** Opt-in click sound + haptic (no permission needed — system feedback). */
    private fun feedback() {
        val on = context.getSharedPreferences("isv_collector", Context.MODE_PRIVATE)
            .getBoolean("feedback", false)
        if (!on) return
        performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }

    private fun render() {
        removeAllViews()
        addView(suggestionStrip)
        if (symbols) renderSymbols() else renderLetters()
    }

    /**
     * Shift key state machine — standard soft-keyboard behaviour:
     *  - OFF  → tap → ONE-SHOT (next letter is capital, then auto-reverts)
     *  - ONE-SHOT → quick 2nd tap → CAPS-LOCK (stays until turned off)
     *  - ONE-SHOT → slow 2nd tap → OFF
     *  - CAPS-LOCK → tap → OFF
     * `shift` stays the single "is uppercase active now" flag every key reads;
     * `capsLocked` only decides whether it survives typing a letter.
     */
    private fun onShiftTap() {
        val now = SystemClock.uptimeMillis()
        when {
            capsLocked -> { capsLocked = false; shift = false }
            shift && now - lastShiftTapMs <= doubleTapMs -> { capsLocked = true; shift = true }
            shift -> shift = false
            else -> shift = true
        }
        lastShiftTapMs = now
        render()
    }

    /** A one-shot capital is consumed by the character it capitalised; caps-lock
     *  and plain lowercase are left untouched. Called after every committed glyph. */
    private fun consumeOneShotShift() {
        if (shift && !capsLocked) { shift = false; render() }
    }

    private fun renderLetters() {
        letterKeyViews.clear()
        Layout.letterRows.forEachIndexed { index, row ->
            val rv = makeRow()
            if (index == 2) {
                val glyph = if (capsLocked) "⇪" else "⇧"
                val sk = functionKey(glyph, 1.5f) { onShiftTap() }
                when {
                    capsLocked -> sk.setBackgroundColor(Color.parseColor("#78909C"))  // locked: stronger
                    shift -> sk.setBackgroundColor(Color.parseColor("#B0BEC5"))         // one-shot: light
                }
                rv.addView(sk)
            }
            row.forEach { ch -> rv.addView(letterKey(ch)) }
            if (index == 2) {
                rv.addView(backspaceKey())
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
                rv.addView(backspaceKey())
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
                    showKeyPreview(tv)
                    if (hasAccent) flickHandler.postDelayed(longPress, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.x - downX) > touchSlop || abs(e.y - downY) > touchSlop) {
                        flickHandler.removeCallbacks(longPress)
                        hideKeyPreview()          // becoming a flick/glide, not a tap
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    hideKeyPreview()
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
                    hideKeyPreview()
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

    /** Backspace with press-and-hold: one tap deletes normally; holding deletes
     *  word by word, a little faster the longer you hold (never whole lines). */
    private fun backspaceKey(): TextView {
        val tv = baseKey("⌫", 1.5f)
        // The view's own long-press machinery competes with ours for the same
        // hold and buys nothing here.
        tv.isLongClickable = false
        val repeat = object : Runnable {
            override fun run() {
                deleteWordBackward()
                feedback()
                // Speeds up, but nowhere near as far as it used to (55 ms was
                // roughly eight words a second - a held thumb wiped a whole
                // message before the eye caught up). A floor of 130 ms stays
                // fast while leaving time to let go.
                backspaceInterval = maxOf(130L, backspaceInterval - 20L)
                flickHandler.postDelayed(this, backspaceInterval)
            }
        }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tv.isPressed = true
                    // A finger resting on a key is never perfectly still, and
                    // this view watches the whole keyboard for glides - so the
                    // parent used to steal the gesture the moment the thumb
                    // twitched, cancelling the repeat before it ever fired.
                    // That is why holding backspace appeared to do nothing.
                    (tv.parent as? android.view.ViewGroup)
                        ?.requestDisallowInterceptTouchEvent(true)
                    backspace()                       // a tap is still one character
                    backspaceInterval = 260L
                    flickHandler.postDelayed(repeat, 500L)   // then whole words; 350 ms fired on ordinary taps
                    true
                }
                // Consume movement too, so a twitch cannot hand the gesture
                // upward mid-hold.
                MotionEvent.ACTION_MOVE -> true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tv.isPressed = false
                    (tv.parent as? android.view.ViewGroup)
                        ?.requestDisallowInterceptTouchEvent(false)
                    flickHandler.removeCallbacks(repeat)
                    true
                }
                else -> false
            }
        }
        return tv
    }

    private fun baseKey(text: String, weight: Float): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        tv.setTextColor(Color.parseColor("#1C2529"))
        tv.setBackgroundColor(Color.WHITE)
        val lp = LayoutParams(0, dp(52), weight)
        lp.setMargins(dp(1), dp(1), dp(1), dp(1))   // bigger touch area, fewer dead gaps
        tv.layoutParams = lp
        tv.isClickable = true
        tv.isLongClickable = true
        return tv
    }

    private fun commit(s: String) {
        swipeJustCommitted = false
        feedback()
        val ic = service.currentInputConnection ?: return
        if (s.isEmpty()) return
        val c0 = s[0]

        // Adaptive ranking: count a hand-typed known word when a terminator lands.
        if (!c0.isLetter()) {
            val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
            val w = before.takeLastWhile { it.isLetter() }.lowercase()
            if (w.length >= 2 && Dictionary.contains(w)) {
                Usage.record(context, w)
                Popularity.record(context, w)
            }
        }

        // Smart space: a word carries no trailing space, so the space is owed to
        // the NEXT thing typed.
        if (pendingSpace) {
            when {
                c0 == ' ' -> { ic.commitText(" ", 1); pendingSpace = false }   // user's own space, no double
                c0 in ATTACH_PUNCT -> { ic.commitText(s, 1); pendingSpace = true }  // glue punct, stay armed
                else -> {
                    val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                    if (prev.isNotEmpty() && !prev[0].isWhitespace()) ic.commitText(" $s", 1)
                    else ic.commitText(s, 1)   // guard: never a leading/double space
                    pendingSpace = false
                }
            }
        } else {
            ic.commitText(s, 1)
        }
        refreshSuggestions()
        consumeOneShotShift()          // a tapped capital lasts exactly one letter
    }

    private fun backspace() {
        feedback()
        val ic = service.currentInputConnection ?: return
        // A backspace immediately after a glide means "that wasn't the word I
        // wanted" — wipe the WHOLE word in one press so the user can re-swipe,
        // instead of tapping backspace letter by letter.
        if (swipeJustCommitted && lastSwipeWord.isNotEmpty()) {
            // Classic mode put a space after the word; wipe that with it.
            ic.deleteSurroundingText(lastSwipeWord.length + if (smartSpace) 0 else 1, 0)
            // Deleting it whole is a verdict on the guess, so take back the
            // count the commit just added - otherwise being wrong trains the
            // ranking exactly as hard as being right.
            Usage.unrecord(context, lastSwipeWord)
            Popularity.unrecord(context, lastSwipeWord)
            val rejected = lastSwipeWord
            swipeJustCommitted = false
            lastSwipeWord = ""
            pendingSpace = false
            // Deleting a wrong glide? Offer the OTHER candidates first - a
            // finger drifts, and the word actually wanted is often fourth or
            // fifth, so the front slots should hold words not yet seen.
            //
            // But the rejected one goes to the BACK rather than away: a
            // backspace is often reflex, pressed before the eye has read what
            // landed, and a guess that was right must stay one tap away.
            lastSwipeCandidates =
                lastSwipeCandidates.filter { it != rejected } + listOf(rejected)
            if (lastSwipeCandidates.isNotEmpty()) {
                showSwipeAlts(lastSwipeCandidates, highlight = -1)
            } else refreshSuggestions()
            return
        }
        ic.deleteSurroundingText(1, 0)
        refreshSuggestions()
    }

    /** Delete the whitespace + the word before the cursor (for backspace-hold). */
    /**
     * Delete one unit backwards: a run of letters, or a run of punctuation -
     * never both at once.
     *
     * Stopping only at whitespace was wrong in exactly the case smart space
     * creates: punctuation glues to the word, so `napisati"` is one
     * whitespace-delimited token and removing the quote took the whole word
     * with it. Letters and marks are separate units, so a stray quote can be
     * taken back without losing the word in front of it.
     */
    private fun deleteWordBackward() {
        val ic = service.currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i > 0) {
            val lettersFirst = before[i - 1].isLetterOrDigit()
            while (i > 0 && !before[i - 1].isWhitespace() &&
                   before[i - 1].isLetterOrDigit() == lettersFirst) i--
        }
        val del = before.length - i
        ic.deleteSurroundingText(if (del > 0) del else 1, 0)
        refreshSuggestions()
    }

    // ---- Prediction bar (M2) --------------------------------------------

    /**
     * The strip scrolls. The decoder returns more candidates than fit on a
     * phone, and the one actually wanted is often not in the first three when
     * the finger drifted - so the extras have to be reachable rather than
     * merely computed.
     *
     * Wrapped in a HorizontalScrollView and, crucially, the scroll view claims
     * the gesture on touch: this view watches the WHOLE keyboard for glides, so
     * dragging across the strip was being read as the start of a swipe. That is
     * the same trap that made holding backspace appear dead.
     */
    private fun buildSuggestionStrip(): android.view.View {
        val strip = LinearLayout(context)
        strip.orientation = HORIZONTAL
        strip.setBackgroundColor(Color.parseColor("#E0E4E7"))
        repeat(SLOTS) { i ->
            val tv = TextView(context)
            tv.gravity = Gravity.CENTER
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            tv.setTextColor(Color.parseColor("#1C2529"))
            // Fixed width, not weight: weights collapse inside a scroll view,
            // and a stable width keeps the chips from jumping as words change.
            val lp = LayoutParams(dp(112), LayoutParams.MATCH_PARENT)
            lp.setMargins(dp(1), dp(1), dp(1), dp(1))
            tv.layoutParams = lp
            tv.isClickable = true
            tv.setOnClickListener { onSlotTap(i) }
            suggestionViews.add(tv)
            strip.addView(tv)
        }
        val scroller = HorizontalScrollView(context)
        scroller.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(42))
        scroller.isHorizontalScrollBarEnabled = false
        scroller.setBackgroundColor(Color.parseColor("#E0E4E7"))
        scroller.addView(strip)
        scroller.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                (v.parent as? android.view.ViewGroup)
                    ?.requestDisallowInterceptTouchEvent(true)
            }
            false        // let the scroll view and the chips do their own work
        }
        return scroller
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

        val preds = Dictionary.suggest(lower, if (canSave) 2 else 3) { w ->
            Usage.count(context, w) * USAGE_W_SUGGEST
        }
        // Follow the case of what is ALREADY typed. The dictionary is lowercase,
        // so a word begun with a deliberate capital was offered back in lower
        // case and the capital was lost on the tap - shift is one-shot and long
        // spent by then, which is why reading it here would not help either.
        // What the user typed is the reliable signal, and it survives.
        val capital = word.first().isUpperCase()
        for (i in preds.indices) {
            val w = if (capital) preds[i].replaceFirstChar { it.uppercaseChar() } else preds[i]
            setSlot(i, w, w, save = false)
        }
        if (canSave) setSlot(SLOTS - 1, "＋ $lower", lower, save = true)
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
                suggestionViews[i].text = "✓"          // language-neutral: no PL/MS wording needed
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
        // Replacing a half-typed word leaves whatever preceded it, spacing and
        // all. Inserting a FRESH word does not - and this was the one commit
        // path that forgot it, so a tapped suggestion glued itself to the
        // previous word (`možemožemo`). Smart space owes the space backwards,
        // so it has to be paid here exactly as `commit()` and the glide do.
        val replacing = currentWord.isNotEmpty()
        if (replacing) ic.deleteSurroundingText(currentWord.length, 0)
        val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val lead = if (!replacing && (pendingSpace ||
                (prev.isNotEmpty() && !prev[0].isWhitespace()))) " " else ""
        ic.commitText(if (smartSpace) "$lead$word" else "$lead$word ", 1)
        ic.endBatchEdit()
        Usage.record(context, word)
        Popularity.record(context, word)
        currentWord = ""
        // Smart space owes it forward; classic already wrote it.
        pendingSpace = smartSpace
        clearSlots()
    }

    private fun enter() {
        feedback()
        val ic = service.currentInputConnection ?: return
        swipeJustCommitted = false
        pendingSpace = false
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
     * Trigger the glide on DISTANCE, not on "entered another key". The old
     * key-crossing test lost short/fast swipes whose sampled points landed in a
     * key margin or the same key; a plain move past the touch-slop is far more
     * reliable. We only yield to a short UPWARD move on an accent key, so the
     * flick (up = diacritic) still works — everything else beyond slop is a glide.
     */
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (symbols) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Fall back to the NEAREST key when the press lands in a margin or
                // just off a key (common on edge keys like `a`); without this the
                // whole glide was dead because the start key was null.
                swipeStartKey = keyAt(e.x, e.y) ?: nearestCenterKey(e.x, e.y, computeCenters())
                swipeDownX = e.x
                swipeDownY = e.y
                swiping = false
                swipePath.setLength(0)
                trailPts.clear()
                addTrailPoint(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                addTrailPoint(e.x, e.y)
                val start = swipeStartKey ?: return false
                val dx = e.x - swipeDownX
                val dy = e.y - swipeDownY
                val dist = sqrt(dx * dx + dy * dy)
                val kNow = keyAt(e.x, e.y)
                val crossed = kNow != null && kNow != start
                // Eager: fire on enough movement OR the moment another key is
                // entered — whichever comes first.
                if (dist < touchSlop && !crossed) return false
                val hasAccent = Layout.longPress(start, false) != null
                val isUpFlick = hasAccent && dy < 0 && abs(dy) >= abs(dx) && dist < dp(48)
                if (isUpFlick) return false                   // let the child's flick handler take it
                swiping = true
                hideKeyPreview()
                swipePath.setLength(0)
                swipePath.append(start)
                if (crossed) swipePath.append(kNow!!)
                invalidate()
                return true
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
                // Live preview: show the word forming so far, throttled so the
                // per-move decode never janks the glide.
                val now = SystemClock.uptimeMillis()
                if (now - lastPreviewMs > 80 && trailPts.size >= 8) {
                    lastPreviewMs = now
                    previewSwipe(ArrayList(trailPts))
                }
            }
            MotionEvent.ACTION_UP -> {
                val pts = ArrayList(trailPts)          // snapshot BEFORE clearing
                swiping = false
                swipeStartKey = null
                trailPts.clear()
                invalidate()
                if (pts.size >= 6) decodeSwipe(pts)    // >= 3 points = a real glide
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

    /**
     * The route-shape decode, returning the top candidates WITHOUT committing —
     * shared by the live preview (during the glide) and the final commit (on
     * lift). Resample the finger trajectory and compare it, point-for-point, to
     * each candidate word's ideal route; first key + length cap come from the
     * whole trail so fast swipes are not truncated.
     */
    /** Key centres in KeyboardView coordinates. Built on the UI thread, then
     *  handed to the (possibly background) decode so no View is touched off-UI. */
    private fun computeCenters(): HashMap<Char, FloatArray> {
        val centers = HashMap<Char, FloatArray>(letterKeyViews.size)
        for ((ch, v) in letterKeyViews) {
            val row = v.parent as? android.view.View ?: continue
            centers[ch] = floatArrayOf(
                row.left + v.left + v.width / 2f,
                row.top + v.top + v.height / 2f,
            )
        }
        return centers
    }

    private fun decodeCandidates(rawPts: List<Float>, centers: Map<Char, FloatArray>): List<String> {
        val pointList = ArrayList<FloatArray>(rawPts.size / 2)
        var i = 0
        while (i + 1 < rawPts.size) {
            pointList.add(floatArrayOf(rawPts[i], rawPts[i + 1])); i += 2
        }
        // Index-resample keeps the finger's DWELL: the trail is time-sampled, so
        // a letter the finger paused on gets more points. Compared against each
        // word's ideal route (given equal dwell at every letter), the pause
        // disambiguates colinear letters a plain shape-match cannot.
        val rpTrail = indexResample(pointList, DWELL_N) ?: return emptyList()

        val score: (String) -> Double = fold@{ folded ->
            val ideal = idealWithDwell(folded, centers) ?: return@fold Double.NEGATIVE_INFINITY
            val idealRp = indexResample(ideal, DWELL_N) ?: return@fold Double.NEGATIVE_INFINITY
            -shapeDist(rpTrail, idealRp)
        }

        val ks = keyPathFromTrail(pointList, centers)
        val starts = nearestKeys(pointList.first(), centers, ANCHOR_KEYS)
            .ifEmpty { listOfNotNull(ks.firstOrNull() ?: swipeStartKey) }
            .toSet()
        if (starts.isEmpty()) return emptyList()
        // More candidates than the strip can show: the extras are the pool a
        // rejected guess falls back on, so a second try offers NEW words rather
        // than the same three with the wrong one still on top.
        // Anchor the LAST letter as well. Three start keys tripled the number of
        // words reaching the expensive shape comparison, which is what made
        // recognition drag; requiring the final letter to sit near where the
        // finger stopped throws out the overwhelming majority for the price of
        // one character lookup.
        val ends = nearestKeys(pointList.last(), centers, ANCHOR_KEYS).toSet()
        return Dictionary.decodeSwipeGeo(starts, ends, ks.size + 2, SWIPE_CANDIDATES, score) { w ->
            Usage.count(context, w) * USAGE_W_SWIPE
        }
    }

    /**
     * Live preview during the glide, OFF the UI thread. Decoding the whole
     * first-letter bucket every 80 ms was blocking the main thread and made long
     * words stutter; now the heavy decode runs on [decodeThread] and only the
     * strip update is posted back to the UI. Stale requests are dropped so only
     * the newest trail is ever decoded. View geometry is read here (UI thread)
     * and snapshotted, so the background decode touches no View.
     */
    private fun previewSwipe(rawPts: List<Float>) {
        val centers = computeCenters()
        decodeHandler.removeCallbacksAndMessages(null)   // drop stale previews
        decodeHandler.post {
            val words = decodeCandidates(rawPts, centers)
            post { if (swiping) showPreview(words) }      // ignore if the glide already ended
        }
    }

    private fun showPreview(words: List<String>) {
        for (i in suggestionViews.indices) {
            val w = words.getOrNull(i)
            suggestionViews[i].text = w.orEmpty()
            suggestionViews[i].setTextColor(Color.parseColor("#1C2529"))
            suggestionViews[i].setBackgroundColor(
                if (i == 0 && w != null) Color.parseColor("#DCE3E7") else Color.TRANSPARENT
            )
        }
    }

    private fun decodeSwipe(rawPts: List<Float>) {
        // Decode OFF the UI thread so lifting the finger never freezes the
        // keyboard (the dwell decode is heavier); the commit is posted back.
        val centers = computeCenters()
        decodeHandler.removeCallbacksAndMessages(null)   // drop any pending preview
        decodeHandler.post {
            val words = decodeCandidates(rawPts, centers)
            post { commitSwipeResult(words) }
        }
    }

    private fun commitSwipeResult(words: List<String>) {
        if (words.isEmpty()) { clearSlots(); return }
        val ic = service.currentInputConnection ?: return
        lastSwipeCapitalized = shift
        val best = if (shift) words[0].replaceFirstChar { it.uppercaseChar() } else words[0]
        // Smart space: a glide is a fresh word, so put the space BEFORE it (unless
        // at field start or already after a space), and none after it.
        val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val lead = if (pendingSpace || (prev.isNotEmpty() && !prev[0].isWhitespace())) " " else ""
        ic.commitText(if (smartSpace) "$lead$best" else "$lead$best ", 1)
        Usage.record(context, best)
        Popularity.record(context, best)
        lastSwipeWord = best
        lastSwipeCandidates = words
        swipeJustCommitted = true
        pendingSpace = smartSpace
        currentWord = ""
        showSwipeAlts(words, highlight = 0)   // a wrong guess is one tap from fixed
        consumeOneShotShift()                 // one-shot capital ends with this word too
    }

    /** Put the swipe candidates in the strip as tappable alternatives. */
    private fun showSwipeAlts(words: List<String>, highlight: Int) {
        clearSlots()
        // The decoder now returns more words than there are slots.
        // Show them cased the way they will actually be inserted - a strip of
        // lowercase words after a deliberate shift is a lie about the outcome.
        val shown = words.take(suggestionViews.size).map {
            if (lastSwipeCapitalized) it.replaceFirstChar { c -> c.uppercaseChar() } else it
        }
        for (i in shown.indices) {
            slotWord[i] = shown[i]
            slotIsSwipeAlt[i] = true
            suggestionViews[i].text = shown[i]
            suggestionViews[i].setTextColor(Color.parseColor("#1C2529"))
            suggestionViews[i].setBackgroundColor(
                if (i == highlight) Color.parseColor("#DCE3E7") else Color.TRANSPARENT
            )
        }
    }

    /** Replace the word the swipe just committed with a chosen alternative. If
     *  nothing is currently committed (e.g. after a whole-word backspace), just
     *  insert the chosen word fresh. */
    private fun replaceLastSwipeWord(word: String) {
        val ic = service.currentInputConnection ?: return
        ic.beginBatchEdit()
        if (lastSwipeWord.isNotEmpty()) {
            // Choosing another word means the committed one was wrong.
            Usage.unrecord(context, lastSwipeWord)
            Popularity.unrecord(context, lastSwipeWord)
            // Classic mode wrote a trailing space with the word, so take it too.
            ic.deleteSurroundingText(lastSwipeWord.length + if (smartSpace) 0 else 1, 0)
        }
        // Honour the shift that produced the ORIGINAL word, not the (already
        // spent) shift state now.
        val out = if (shift || lastSwipeCapitalized) {
            word.replaceFirstChar { it.uppercaseChar() }
        } else word
        val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val lead = if (lastSwipeWord.isEmpty() && prev.isNotEmpty() && !prev[0].isWhitespace()) " " else ""
        ic.commitText(if (smartSpace) "$lead$out" else "$lead$out ", 1)  // leading space before the old word stays
        ic.endBatchEdit()
        Usage.record(context, out)
        Popularity.record(context, out)
        lastSwipeWord = out
        swipeJustCommitted = true    // still a swipe result — backspace wipes it whole
        pendingSpace = smartSpace
        clearSlots()
    }

    /** Downsample the interleaved (x,y) trail to at most [cap] points, keeping the last. */
    private fun samplePoints(raw: List<Float>, cap: Int): List<FloatArray> {
        val total = raw.size / 2
        if (total == 0) return emptyList()
        val out = ArrayList<FloatArray>(minOf(total, cap) + 1)
        val step = maxOf(1, total / cap)
        var i = 0
        while (i < total) {
            out.add(floatArrayOf(raw[i * 2], raw[i * 2 + 1])); i += step
        }
        val lx = raw[(total - 1) * 2]; val ly = raw[(total - 1) * 2 + 1]
        if (out.isEmpty() || out.last()[0] != lx || out.last()[1] != ly) {
            out.add(floatArrayOf(lx, ly))
        }
        return out
    }

    /** Resample a polyline to [n] points evenly spaced by arc length. */
    private fun resamplePath(pts: List<FloatArray>, n: Int): List<FloatArray>? {
        if (pts.size < 2) return null
        val cum = FloatArray(pts.size)
        for (i in 1 until pts.size) {
            val dx = pts[i][0] - pts[i - 1][0]; val dy = pts[i][1] - pts[i - 1][1]
            cum[i] = cum[i - 1] + sqrt(dx * dx + dy * dy)
        }
        val total = cum[pts.size - 1]
        val out = ArrayList<FloatArray>(n)
        if (total <= 0f) {
            for (k in 0 until n) out.add(floatArrayOf(pts[0][0], pts[0][1]))
            return out
        }
        val step = total / (n - 1)
        var j = 0
        for (k in 0 until n) {
            val t = k * step
            while (j < pts.size - 2 && cum[j + 1] < t) j++
            val seg = cum[j + 1] - cum[j]
            val f = if (seg <= 0f) 0f else (t - cum[j]) / seg
            out.add(floatArrayOf(
                pts[j][0] + (pts[j + 1][0] - pts[j][0]) * f,
                pts[j][1] + (pts[j + 1][1] - pts[j][1]) * f,
            ))
        }
        return out
    }

    /** Resample by INDEX (time), keeping dense regions dense — preserves dwell. */
    private fun indexResample(pts: List<FloatArray>, n: Int): List<FloatArray>? {
        if (pts.size < 2) return null
        val out = ArrayList<FloatArray>(n)
        val step = (pts.size - 1).toFloat() / (n - 1)
        for (i in 0 until n) {
            val idx = Math.round(i * step).coerceIn(0, pts.size - 1)
            out.add(pts[idx])
        }
        return out
    }

    /** A word's ideal route with equal dwell (repeated points) at each letter. */
    private fun idealWithDwell(
        folded: String,
        centers: Map<Char, FloatArray>,
        dwell: Int = 3,
        perSeg: Int = 5,
    ): List<FloatArray>? {
        val seq = ArrayList<FloatArray>(folded.length)
        for (c in folded) seq.add(centers[c] ?: return null)
        if (seq.size < 2) return null
        val pts = ArrayList<FloatArray>(seq.size * (dwell + perSeg))
        for (i in 0 until seq.size - 1) {
            val a = seq[i]; val b = seq[i + 1]
            repeat(dwell) { pts.add(a) }
            for (k in 0 until perSeg) {
                val t = k.toFloat() / perSeg
                pts.add(floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            }
        }
        repeat(dwell) { pts.add(seq.last()) }
        return pts
    }

    /** Mean point-for-point distance between two equal-length resampled curves. */
    private fun shapeDist(a: List<FloatArray>, b: List<FloatArray>): Double {
        var s = 0.0
        for (i in a.indices) {
            val dx = a[i][0] - b[i][0]; val dy = a[i][1] - b[i][1]
            s += sqrt(dx * dx + dy * dy)
        }
        return s / a.size
    }

    /** Nearest key whose centre is closest to a point. */
    private fun nearestCenterKey(x: Float, y: Float, centers: Map<Char, FloatArray>): Char? {
        var best = Float.MAX_VALUE
        var bk: Char? = null
        for ((ch, c) in centers) {
            val dx = x - c[0]; val dy = y - c[1]
            val d = dx * dx + dy * dy
            if (d < best) { best = d; bk = ch }
        }
        return bk
    }

    /** Deduped sequence of nearest keys along the trail — robust to fast swipes. */
    private fun keyPathFromTrail(pts: List<FloatArray>, centers: Map<Char, FloatArray>): List<Char> {
        if (pts.isEmpty()) return emptyList()
        val step = maxOf(1, pts.size / 24)
        val seq = ArrayList<Char>()
        var i = 0
        while (i < pts.size) {
            val k = nearestCenterKey(pts[i][0], pts[i][1], centers)
            if (k != null && (seq.isEmpty() || seq.last() != k)) seq.add(k)
            i += step
        }
        return seq
    }

    /**
     * The keys a glide could plausibly have STARTED on. A finger aiming at a
     * key lands within about a key of its centre, and the neighbour above or
     * below is often closer than the intended key itself - so the first letter
     * gets several candidates, not one.
     */
    private fun nearestKeys(
        p: FloatArray,
        centers: Map<Char, FloatArray>,
        limit: Int,
    ): List<Char> {
        if (centers.isEmpty()) return emptyList()
        // A key's own width, taken from the live layout rather than assumed.
        val w = letterKeyViews.firstOrNull()?.second?.width?.toFloat() ?: return emptyList()
        val reach = w * 1.3f
        return centers.entries
            .map { it.key to dist2(p, it.value) }
            .filter { it.second <= reach }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Euclidean distance between a key centre and a trail point. */
    private fun dist2(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]
        return sqrt(dx * dx + dy * dy)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
