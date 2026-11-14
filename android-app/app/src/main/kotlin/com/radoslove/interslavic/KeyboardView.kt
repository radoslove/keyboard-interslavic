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
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
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
    /** Shift set by the KEY rather than by the text. While it is on, the
     *  automatic re-arm ([rearmShift]) keeps its hands off. */
    private var shiftByHand = false
    /** The text the shift key last re-cased ([cycleCase]), so the next press can
     *  go on from `Word` to `WORD`. Any other key ends the run. */
    private var recased: String? = null
    private val doubleTapMs = 300L
    private var symbols = false
    /** The `#+=` page of the symbol layer (iOS 559a71f). */
    private var symbolsPage2 = false
    /** The emoji panel, in place of the letter rows (iOS 0.4.2). */
    private var emoji = false

    /** The keyboard's own settings, in place of the letter rows (iOS 0.5.3).
     *  Opened by the same gesture on both phones: hold `123`, slide to the gear. */
    private var settingsOpen = false
    private val EMOJI_PER_ROW = 8

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
    private val slotIsUndo = BooleanArray(SLOTS)

    // Swipe / glide decoding (M4). letterKeyViews lets us hit-test which key the
    // finger is over as it crosses the board.
    private val letterKeyViews = ArrayList<Pair<Char, TextView>>()
    private var swiping = false
    private var swipeStartKey: Char? = null
    /** Shift as it stood when the GESTURE BEGAN.
     *
     *  A one-shot capital belongs to the moment the finger went down, not to the
     *  moment the word is finally decoded. Reading `shift` at commit time is the
     *  same mistake the typed-prediction path already documents ("shift is
     *  one-shot and long spent by then") - and a glide cannot use that path's
     *  remedy, because there is no typed prefix whose case could be read back.
     *  So the answer is captured up front and carried through the gesture. */
    private var swipeShift = false
    /** Caps-lock as it stood when the gesture began - same reasoning as
     *  [swipeShift]. Without it a locked keyboard still produced `Velmi`
     *  instead of `VELMI`, because the glide only ever touched the first
     *  character. */
    private var swipeCaps = false
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    /** The finger a glide follows. Two-thumb typing overlaps touches: when the
     *  first thumb lifted, "pointer 0" became the second one, the position jumped
     *  `m`->`a`, and a run of hand-typed letters was decoded as ONE glide -
     *  `master` typed by hand landed as `mastet` (owner 2026-10-15). */
    private var swipePointerId = -1
    /** When a letter was last TYPED (tapped); a touch right after is a tap too. */
    private var lastLetterTapMs = 0L
    private val swipePath = StringBuilder()
    private var lastSwipeWord = ""
    private var lastSwipeCandidates: List<String> = emptyList()
    /**
     * Words this user has just deleted after a glide, and how many times.
     *
     * Swiping the same shape ten times and getting the same wrong word ten times
     * is the keyboard refusing to take a hint. A backspace straight after a
     * glide is an explicit "not that one", so the word steps DOWN for the next
     * few attempts - repeatedly, if it keeps coming back.
     *
     * It is demoted, never banned: the guess may have been right and the
     * backspace a reflex, so it must stay reachable in the strip. That is why
     * this is a score penalty and not a filter.
     */
    private val rejectedStrikes = LinkedHashMap<String, Int>()
    private val REJECT_W = 12.0        // px-equivalent, per strike
    private val REJECT_MEMORY = 16     // words remembered, oldest dropped
    private var lastPreviewMs = 0L            // throttle the live in-glide preview
    private var backspaceInterval = 200L      // backspace-hold repeat, accelerates
    private var swipeJustCommitted = false   // true right after a glide, until any other key
    /**
     * When the glide that armed [swipeJustCommitted] landed.
     *
     * Used only to tell a letter that EXTENDS the glided word (`pisem` + `s`)
     * from one that starts the next word. Backspace no longer looks at it: the
     * first press after a glide takes the word whatever the delay, and an
     * ending is fixed by holding backspace (owner, 2026-10-08).
     */
    private var swipeCommitAtMs = 0L
    /** Norwegian compounds are long (`søtningsstoff`) and take longer to read,
     *  so a letter typed after a Norwegian glide extends it for longer (isc 0.1.3). */
    private val SWIPE_WIPE_WINDOW_MS: Long
        get() = if (Lang.current(context) in setOf(Lang.NB, Lang.ISC)) 2500L else 1200L
    /** How long after a glide a letter may still complete a MORE frequent word. */
    private val LATE_EXTEND_MS = 8000L
    /** What the last whole-word wipe took out; read by a held backspace. */
    private var lastWipeRemoved = ""
    /** Set when a backspace press began with a wipe: holding trims instead. */
    private var trimAfterWipe = ""
    private var trimming = false

    /**
     * One-shot undo for a whole-word delete (glide-reject or held backspace).
     *
     * Deleting a whole word in one press is only safe if it is one press back.
     * [undoText] is the EXACT run of characters removed - word plus whatever
     * whitespace went with it - so putting it back is a verbatim restore
     * rather than a re-guess at the spacing. No stack: one action, offered as
     * the first chip, gone at the next key.
     */
    private var undoText = ""
    private var undoWord = ""
    private var undoArmed = false         // a WHOLE-WORD delete happened in this run
    private var undoMore = false          // the run holds more than one word
    private var undoRestoreRank = false   // the delete also un-counted the word
    // Smart space: a committed word carries NO trailing space; the space is
    // inserted BEFORE the next word instead, and punctuation attaches with no
    // space before it (Gboard-style).
    /** Whether the glide just committed was capitalised. Shift is one-shot and
     *  is spent the moment the word lands, so by the time an alternative is
     *  picked from the strip it is long gone - and the replacement came out
     *  lowercase even though the user had deliberately pressed shift. */
    private var lastSwipeCapitalized = false
    private var lastSwipeLocked = false
    private var pendingSpace = false
    // Glued to the word, no space before it. Apostrophes and CLOSING quotes
    // belong here too: without them smart space read `'` as the start of a new
    // word and pushed a space in front of it.
    private val ATTACH_PUNCT = ".,!?:;\u2026'\u2019\u201D\")]".toSet()

    /** A character of a word: a letter, and in Ukrainian also the apostrophe
     *  inside one (`\u043F'\u044F\u0442\u044C`, `\u0441\u0456\u043C'\u044F`), or completions, `\uFF0B` and backspace by
     *  word would break the word in two at it (iOS 0.6.6, owner 2026-11-11). */
    private fun wordChar(c: Char, lang: String = Lang.current(context)): Boolean =
        c.isLetter() || Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
            (c == '\'' && lang in Lang.APOSTROPHE_IN_WORD)

    /** The editor, seen through Polish Glagolitic's wide word space: there a
     *  word space goes out as the en space U+2002 (phone fonts draw Glagolitic
     *  wide and the plain space narrow, so words ran together - owner
     *  2026-11-14) and reads back as " ", one char for one, so every count of
     *  characters to delete stays right (iOS 0.6.14 `put` / `contextBefore`). */
    private val conn: InputConnection?
        get() {
            val ic = service.currentInputConnection ?: return null
            return if (Script.rewritesWord(context)) WideSpace(ic) else ic
        }

    private class WideSpace(target: InputConnection) : InputConnectionWrapper(target, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
            super.commitText(text?.toString()?.replace(' ', Script.WIDE_SPACE), newCursorPosition)
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? =
            super.getTextBeforeCursor(n, flags)?.toString()?.replace(Script.WIDE_SPACE, ' ')
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? =
            super.getTextAfterCursor(n, flags)?.toString()?.replace(Script.WIDE_SPACE, ' ')
    }

    /** Polish Glagolitic: the word being typed, in the keyboard's Latin (with
     *  the markers) and as it stands on screen. Kept from our own writes rather
     *  than read back from the text every time (iOS 0.6.23: some apps report
     *  the text before the cursor late, and a fast second letter joined wrong -
     *  "jak", "dzień"); any other key, a glide, a suggestion or a cursor move
     *  clears it. */
    private var glagWord: Pair<String, String>? = null

    /** In the wordlist or the user's own words; false while no list is loaded,
     *  so od-/pod-/nad-/przed- + z is never split on a guess (iOS `knowsWord`). */
    private fun knowsWord(word: String): Boolean {
        val lower = word.lowercase()
        if (Dictionary.contains(lower)) return true
        return UserWords.all(context).any { it.lowercase() == lower }
    }

    /** A whole word in the current script; in Polish Glagolitic with the
     *  od-/pod-/nad-/przed- + z split applied first (iOS `outWord`). */
    private fun outWord(latin: String): String =
        if (!Script.rewritesWord(context)) Script.out(context, latin)
        else Script.out(context, Script.splitPrefixD(latin) { knowsWord(it) })

    /** Polish Glagolitic: a typed letter may join the ones before it (c + z =
     *  ⱍ), so the word under the cursor is written again. The word so far is
     *  our own record if the text still ends with it, otherwise it is read
     *  back - only the part after the last glyph typed as such (ⰷ ⱉ ⰴⱐ), so
     *  those stay as the user chose them. Returns inserted minus deleted. */
    private fun glagType(ic: InputConnection, latin: String): Int {
        val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val w = glagWord
        val prev: Pair<String, String> = if (w != null && before.endsWith(w.second)) w else {
            val word = trailingWord(before)
            var cut = 0
            for (i in word.indices) if (Script.endsAtomic(word, i)) cut = i + 1
            val shown = word.substring(cut)
            Script.toLatin(context, shown) to shown
        }
        if (prev.second.isNotEmpty()) ic.deleteSurroundingText(prev.second.length, 0)
        val joined = Script.softenPL(prev.first + latin)
        val now = Script.out(context, joined)
        ic.commitText(now, 1)
        glagWord = joined to now
        return now.length - prev.second.length
    }

    /** Polish Glagolitic: the word is finished - split d + z after a prefix
     *  where it is two sounds (od-zyskać), then whatever ends it is written.
     *  Returns inserted minus deleted. */
    private fun finishGlagWord(ic: InputConnection): Int {
        val w = glagWord ?: return 0
        glagWord = null
        val fixed = Script.splitPrefixD(w.first) { knowsWord(it) }
        if (fixed == w.first) return 0
        if (ic.getTextBeforeCursor(w.second.length, 0)?.toString() != w.second) return 0
        ic.deleteSurroundingText(w.second.length, 0)
        val now = Script.out(context, fixed)
        ic.commitText(now, 1)
        return now.length - w.second.length
    }

    /** The word right before the cursor, as shown; a leading apostrophe is a
     *  quote mark, not part of it (iOS `trailingWord`). */
    private fun trailingWord(text: String): String {
        val lang = Lang.current(context)
        return text.takeLastWhile { wordChar(it, lang) }.trimStart('\'')
    }

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
    // Resolution of the cheap first pass. Eight points is enough to throw out
    // the thousands of words whose route goes nowhere near the finger.
    private val COARSE_RESAMPLE = 8
    private val SWIPE_CANDIDATES = SLOTS   // the strip scrolls, so every candidate is reachable
    /** Soft end gate (mirrors swipe_eval END_SOFT / END_SOFT_REACH, 2026-11-12). */
    private val END_SOFT = 20.0
    private val END_SOFT_REACH = 1.8f
    /** Strip after a glide: alternatives at most this many key widths behind
     *  the winner (measured, see [swipeStrip]); farther ones only when their
     *  frequency byte is at least [STRIP_FAR_FREQ] (iOS `farAlternativeFreq`). */
    private val STRIP_MARGIN = 0.25f
    private val STRIP_FAR_FREQ = 150
    private val ANCHOR_KEYS = 3       // start-key candidates; 1 hid `pisati` behind `ležati`
    private val ANCHOR_MS = 120f      // how far back from each end anchorPoint looks
    // Velocity minima (see velocityPivots). Tuned in tools/swipe_eval.py against
    // synthetic glides at three dwell profiles, so no single style of swiper
    // sets them.
    private val V_SMOOTH = 5          // moving-average width over the speed curve
    private val V_RADIUS = 3          // a minimum must be the slowest sample within +/- this
    private val V_REL = 0.80f         // ...and slower than this share of the local mean
    private val V_MIN_SEP = 4         // samples; closer minima collapse into the slower one
    private val PIVOT_CAP = 1.6f      // in key widths - one bad pivot must not swamp the score
    private val W_LETTER = 0.55       // weight: every letter wants a pivot near it
    private val W_PIVOT = 0.30        // weight: every pivot wants a letter near it
    // Adaptive-ranking weights: how hard one prior use lifts a word.
    private val USAGE_W_SUGGEST = 8000       // in freq*64 units (max freq term ~16k)
    private val USAGE_W_SWIPE = 8.0          // in px-equivalent of the shape score

    /** Shortest real glide, in key widths. A tap that rolls over a key edge
     *  travels a fraction of a key; the shortest real word glide, between two
     *  neighbouring keys, travels about one. */
    private val MIN_GLIDE_KEYS = 0.8f
    /** A touch this soon after a typed letter belongs to the typing run, not a glide. */
    private val TAP_RUN_MS = 250L

    /** How far above the intended key this thumb's touches land, in key widths
     *  ([Lift]). Read once per view and kept here, because the decode runs off
     *  the UI thread and must not touch preferences per gesture. */
    private var touchLift = Lift.get(context)
    /** The gesture that produced the word now sitting in the text: its raw (NOT
     *  lift-corrected) first and last point, and the key width they were
     *  measured at. Kept so that a word the user KEEPS can teach [Lift] where
     *  this thumb really sits; dropped the moment the word is rejected or
     *  replaced, because then the gesture says nothing about the thumb. */
    private var liftSample: FloatArray? = null
    private var liftSampleWord = ""

    /**
     * Light or dark ([Theme]: automatic with the phone, or chosen in the
     * settings). Every colour the keyboard draws comes from here. The view is
     * rebuilt when the system mode or the choice changes, so this is read once.
     */
    private val dark = Theme.isDark(context)
    private fun c(light: String, night: String) = Color.parseColor(if (dark) night else light)
    private val cBoard = c("#ECEFF1", "#1B1F22")
    private val cKey = c("#FFFFFF", "#3A3F44")
    private val cText = c("#1C2529", "#E8EAED")
    private val cStrip = c("#E0E4E7", "#24292D")
    private val cChipOn = c("#DCE3E7", "#3A444B")
    private val cBorder = c("#90A4AE", "#5F6B73")
    private val cShiftOne = c("#B0BEC5", "#56626B")
    private val cShiftLock = c("#78909C", "#8A9AA5")
    private val cUndoText = c("#4E342E", "#FFE0B2")
    private val cUndoBg = c("#FFE0B2", "#5D4037")
    private val cSaveText = c("#1B5E20", "#C8E6C9")
    private val cSaveBg = c("#C8E6C9", "#2E5E33")

    // Visible trail so the user can SEE the glide as it happens. Flat triples
    // (x, y, t) - the timestamp is what makes velocityPivots possible, and the
    // trail is the only place the raw gesture exists.
    private val trailPts = ArrayList<Float>(192)
    private var swipeT0 = 0L
    private val trailPath = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x904FC3F7.toInt()   // semi-transparent light blue smudge
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(cBoard)
        setPadding(dp(3), dp(6), dp(3), dp(6))
        setWillNotDraw(false)        // a ViewGroup must be told to draw
        trailPaint.strokeWidth = dp(9).toFloat()
        // A theme chosen in the panel rebuilds the whole view; come back to the
        // panel, not to the letters, or the choice looks like it closed itself.
        if (service.reopenSettings) { service.reopenSettings = false; settingsOpen = true }
        render()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (swiping && trailPts.size >= 6) {
            trailPath.reset()
            trailPath.moveTo(trailPts[0], trailPts[1])
            var i = 3
            while (i + 2 < trailPts.size) {
                trailPath.lineTo(trailPts[i], trailPts[i + 1]); i += 3
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
        popupItems?.let { items ->
            val r = dp(8).toFloat()
            val right = popupLeft + popupCell * items.size
            canvas.drawRoundRect(popupLeft, popupTop, right, popupBottom, r, r, previewBg)
            canvas.drawRoundRect(popupLeft, popupTop, right, popupBottom, r, r, previewBorder)
            // Scaled with the cell: a shrunk cell with full-size glyphs
            // would overlap its neighbours.
            previewTxt.textSize = minOf(dp(24).toFloat(), popupCell * 0.62f)
            val fm = previewTxt.fontMetrics
            val baseline = (popupTop + popupBottom) / 2f - (fm.ascent + fm.descent) / 2f
            val fullSize = previewTxt.textSize
            for (slot in popupOrder.indices) {
                val i = popupOrder[slot]
                val l = popupLeft + popupCell * slot
                // A thin line between cells, so several letters in one cell
                // do not read as part of the next (iOS 0.6.19).
                if (slot > 0) canvas.drawLine(l, popupTop + dp(6), l, popupBottom - dp(6), previewBorder)
                if (i == popupSel) {
                    canvas.drawRoundRect(l + 2f, popupTop + 2f, l + popupCell - 2f, popupBottom - 2f, r, r, popupSelBg)
                    previewTxt.color = Color.WHITE
                } else previewTxt.color = cText
                val label = Script.out(context, items[i])
                // Shrink a label that does not fit its cell instead of letting it
                // spill over (down to 0.4, as on iOS).
                previewTxt.textSize = fullSize
                val wTxt = previewTxt.measureText(label)
                val fit = popupCell * 0.88f
                if (wTxt > fit) previewTxt.textSize = maxOf(fullSize * 0.4f, fullSize * fit / wTxt)
                canvas.drawText(label, l + popupCell / 2f, baseline, previewTxt)
            }
            previewTxt.textSize = fullSize
            previewTxt.color = cText
        }
        langItems?.let { items ->
            val r = dp(10).toFloat()
            val cols = (items.size + langRows - 1) / langRows
            val right = langLeft + langColW * cols
            val bottom = langTop + langRowH * langRows
            canvas.drawRoundRect(langLeft, langTop, right, bottom, r, r, previewBg)
            canvas.drawRoundRect(langLeft, langTop, right, bottom, r, r, previewBorder)
            val base = minOf(dp(19).toFloat(), langRowH * 0.48f)
            for (i in items.indices) {
                val l = langLeft + langColW * (i / langRows)
                val t = langTop + langRowH * (i % langRows)
                if (i == langSel) {
                    canvas.drawRoundRect(l + 2f, t + 2f, l + langColW - 2f, t + langRowH - 2f, r, r, popupSelBg)
                    previewTxt.color = Color.WHITE
                } else previewTxt.color = cText
                // Full names, shrunk to the cell if one is too long for it.
                val label = if (items[i] == LANG_GEAR) "⚙" else Lang.label(items[i])
                previewTxt.textSize = base
                val room = langColW - dp(12)
                val wide = previewTxt.measureText(label)
                if (wide > room) previewTxt.textSize = base * room / wide
                val fm = previewTxt.fontMetrics
                canvas.drawText(label, l + langColW / 2f, t + langRowH / 2f - (fm.ascent + fm.descent) / 2f, previewTxt)
            }
            previewTxt.color = cText
        }
    }

    // Hold on SPACE (iOS 0.6.3, owner 2026-11-11): the ticked languages as a
    // VERTICAL list of full names over the space bar - a horizontal strip of
    // codes was hard to notice. The keyboard cannot draw above its own top, so
    // when the rows do not fit above the space bar the list breaks into columns.
    private var langItems: List<String>? = null
    /** Last row of the space-bar list (owner 2026-11-11, iOS 0.6.10: "more
     *  intuitive than looking under 123"): lifting on it opens the panel. */
    private val LANG_GEAR = " gear"
    private var langLeft = 0f
    private var langTop = 0f
    private var langRowH = 0f
    private var langColW = 0f
    private var langRows = 1
    private var langSel = 0

    private fun showLangList(tv: TextView, langs: List<String>) {
        val row = tv.parent as? android.view.View ?: return
        hideKeyPreview()
        val keyTop = (row.top + tv.top).toFloat()
        val centre = row.left + tv.left + tv.width / 2f
        val n = langs.size
        langRowH = dp(40).toFloat()
        val maxRows = maxOf(1, ((keyTop - dp(6)) / langRowH).toInt())
        val cols = (n + maxRows - 1) / maxRows
        langRows = (n + cols - 1) / cols
        langColW = minOf(dp(170).toFloat(), (width - dp(4)).toFloat() / cols)
        val w = langColW * cols
        langLeft = (centre - w / 2f).coerceAtMost(width - w - dp(2)).coerceAtLeast(dp(2).toFloat())
        langTop = (keyTop - dp(4) - langRowH * langRows).coerceAtLeast(0f)
        langItems = langs
        langSel = langs.indexOf(Lang.current(context)).coerceAtLeast(0)
        invalidate()
    }

    /** The cell under the finger - the nearest one when it strays outside the
     *  list, so the pick does not need to be precise. [x] [y] in the key's
     *  coordinates. */
    private fun moveLangList(tv: TextView, x: Float, y: Float) {
        val items = langItems ?: return
        val row = tv.parent as? android.view.View ?: return
        val cols = (items.size + langRows - 1) / langRows
        val col = ((row.left + tv.left + x - langLeft) / langColW).toInt().coerceIn(0, cols - 1)
        val r = ((row.top + tv.top + y - langTop) / langRowH).toInt().coerceIn(0, langRows - 1)
        val i = minOf(col * langRows + r, items.size - 1)
        if (i != langSel) { langSel = i; invalidate() }
    }

    private fun closeLangList(): String? {
        val items = langItems ?: return null
        langItems = null
        invalidate()
        return items.getOrNull(langSel)
    }

    // Hold popup, as on iOS (7801f4b): every letter or mark the key holds, the
    // keyboard's own first. The finger slides along it to choose and the choice
    // lands on lift. It replaced a hold that committed its one letter at once -
    // which showed nothing but the key's own letter (`з`, not `ж`) above the
    // finger. Items are kept Latin and drawn through Script, like key labels.
    private var popupItems: List<String>? = null
    private var popupLeft = 0f
    private var popupTop = 0f
    private var popupBottom = 0f
    private var popupCell = 0f
    private var popupSel = 0
    /** The strip shows the items mirrored: first one on the right, over a key
     *  on the right half (iOS 0.5.1). */
    private var popupMirrored = false
    /** Which ITEM each slot of the strip holds, left to right. Mirroring and
     *  the overhanging cell are both just a different order, so drawing and
     *  hit-testing read the same array and cannot drift apart. */
    private var popupOrder = IntArray(0)
    private val popupSelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5"); style = Paint.Style.FILL
    }

    /** Open the popup over [tv]; [fingerX] (in the key's coordinates) picks the
     *  first selection. [mayOverhang] lets ONE cell hang past the key - only the
     *  mark keys ask for it, so holding a LETTER still lays every variant out on
     *  one side, the way the thumb learned it. */
    private fun showPopup(tv: TextView, items: List<String>, fingerX: Float,
                          mayOverhang: Boolean = false) {
        val row = tv.parent as? android.view.View ?: return
        hideKeyPreview()
        val keyLeft = (row.left + tv.left).toFloat()
        val keyTop = (row.top + tv.top).toFloat()
        // The first item sits over the key and the rest run AWAY from the nearer
        // edge, so a key on the right half opens leftward (iOS 0.5.1, owner
        // 2026-11-07): the `.` strip used to be pushed against the edge and its
        // first item, the comma, landed far from the thumb.
        val centre = keyLeft + tv.width / 2f
        popupMirrored = centre > width / 2f
        // THE FIRST ITEM MUST BE OVER THE KEY ON EVERY PHONE (owner 2026-11-07:
        // "zeby sie na innych modelach dobrze pokazywal przecinek nad zawsze").
        // It is not enough to pick the roomier side: if the strip is wider than
        // that side, the old code shoved it back and the first cells fell off
        // the screen - on his phone (1080 px at density 510 = 339 dp) eleven
        // 38 dp marks asked for 418 dp, so `,` and `:` were not on screen at
        // all. So the cells SHRINK to the room the chosen side really has.
        // The first cell is half over the key, hence n - 0.5 cells to fit.
        val room = (if (popupMirrored) centre else width - centre) - dp(2)
        popupCell = maxOf(tv.width.toFloat(), dp(38).toFloat())
        // Polish Glagolitic: one variant can be two or three letters (ⰴⰸⱐ, ⱎⱍ),
        // which ran into the next cell (owner 2026-11-14) - a cell is as wide as
        // the longest variant (iOS 0.6.19: 14 + 18 per letter).
        if (Script.rewritesWord(context)) {
            val longest = items.maxOf { Script.out(context, it).length }
            if (longest > 1) popupCell = maxOf(popupCell, dp(14 + 18 * longest).toFloat())
        }
        popupCell = minOf(popupCell, room / (items.size - 0.5f))
        // A floor, so a long list cannot shrink into unreadable slivers; below
        // it the strip is clamped again and the first item may slide off the
        // key - with 8 marks that needs a screen under ~230 dp, which no phone
        // has.
        popupCell = maxOf(popupCell, dp(24).toFloat())
        val n = items.size
        val w = popupCell * n
        // ONE cell may hang PAST the key, over whatever is on the other side -
        // on the `.` key that is the backspace of the row above, and while a
        // popup is up no other key takes a touch anyway, so the space is free
        // (owner 2026-11-07: "zeby byl na miejscu znaku kasowania ktory i tak
        // jest nieaktywny"). The SECOND item goes there, which is why `?` sits
        // to the right of the comma instead of queueing on its left.
        val roomPast = (if (popupMirrored) width - (centre + popupCell / 2f) else centre - popupCell / 2f) - dp(2)
        val overhang = mayOverhang && n >= 2 && roomPast >= popupCell
        var left = if (popupMirrored) centre + popupCell / 2f - w + (if (overhang) popupCell else 0f)
                   else centre - popupCell / 2f - (if (overhang) popupCell else 0f)
        left = left.coerceAtMost(width - w - dp(2)).coerceAtLeast(dp(2).toFloat())
        popupLeft = left
        // Slots left to right. Mirrored and no overhang: items n-1 .. 0, so
        // item 0 lands over the key. With the overhang item 1 takes the cell
        // past it; unmirrored is the same picture flipped.
        popupOrder = IntArray(n) { slot ->
            val fromKey = if (popupMirrored) n - 1 - slot else slot
            if (!overhang) fromKey
            else when (fromKey) { 0 -> 1; 1 -> 0; else -> fromKey }
        }
        popupBottom = keyTop - dp(2)
        popupTop = popupBottom - dp(52)
        if (popupTop < 0f) { popupTop = 0f; popupBottom = dp(52).toFloat() }   // top row: overlap, stay visible
        popupItems = items
        popupSel = popupIndex(keyLeft + fingerX)
        (tv.parent as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(true)
        invalidate()
    }

    private fun popupIndex(x: Float): Int {
        val n = popupOrder.size
        if (n == 0) return 0
        val slot = ((x - popupLeft) / popupCell).toInt().coerceIn(0, n - 1)
        return popupOrder[slot]
    }

    /** Finger moved while the popup is up; [x] in the key's coordinates. */
    private fun movePopup(tv: TextView, x: Float) {
        val row = tv.parent as? android.view.View ?: return
        val i = popupIndex(row.left + tv.left + x)
        if (i != popupSel) { popupSel = i; invalidate() }
    }

    /** Close the popup and return the chosen item (Latin), or null if none was up. */
    private fun closePopup(): String? {
        val items = popupItems ?: return null
        popupItems = null
        invalidate()
        return items.getOrNull(popupSel)
    }

    private fun addTrailPoint(x: Float, y: Float, tMs: Float) {
        trailPts.add(x); trailPts.add(y); trailPts.add(tMs)
    }

    // Key-press preview bubble (shows the pressed letter enlarged above the key).
    private var previewText: String? = null
    private var previewCx = 0f
    private var previewKeyTop = 0f
    private var previewKeyW = 0f
    private val previewBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cKey; style = Paint.Style.FILL
    }
    private val previewBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cBorder; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val previewTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cText; textAlign = Paint.Align.CENTER
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
        when {
            settingsOpen -> renderSettings()
            emoji -> renderEmoji()
            symbols -> renderSymbols()
            else -> renderLetters()
        }
    }

    /**
     * Shift key state machine — three presses, no stopwatch:
     *  - OFF → press → ONE-SHOT (next word/letter gets a capital, then reverts)
     *  - ONE-SHOT → press → CAPS-LOCK (everything upper until turned off)
     *  - CAPS-LOCK → press → OFF
     *
     * The lock used to need a QUICK second tap (<300 ms). That made the third
     * state unreachable by anyone pressing at a normal pace: a calm second press
     * fell through to "shift -> off", so caps-lock looked broken rather than
     * strict. Timing is not a thing to guess at on a key with three meanings.
     * `shift` stays the single "is uppercase active now" flag every key reads;
     * `capsLocked` only decides whether it survives typing.
     */
    private fun onShiftTap() {
        val now = SystemClock.uptimeMillis()
        // With text selected (or right after this key re-cased something) the key
        // changes THAT text instead of arming the next letter - iOS 0.4.2.
        if (cycleCase()) return
        when {
            capsLocked -> { capsLocked = false; shift = false }
            shift -> capsLocked = true      // stays true; shift is already on
            else -> shift = true
        }
        // Whatever the text says, a shift set by hand wins until it is spent:
        // [rearmShift] keeps out of the way while this is true.
        shiftByHand = shift
        lastShiftTapMs = now
        render()
    }

    /**
     * Shift on selected text: small -> Capital -> CAPS -> small, as on the
     * system keyboard (iOS 0.4.2, owner 2026-10-25). This is how an ACRONYM gets
     * typed - write `sša`, select it, press shift twice - and how a word is
     * fixed after the fact without retyping it.
     *
     * With nothing selected it still works on the text this key itself last
     * re-cased: inserting drops the selection (an input connection cannot
     * select), so the run is remembered and found again right before the caret.
     * Any other key ends the run. Returns true when it handled the press.
     */
    private fun cycleCase(): Boolean {
        val ic = conn ?: return false
        val selected = if (selStart != selEnd) ic.getSelectedText(0)?.toString() else null
        val last = recased
        val text: String
        when {
            selected != null && selected.any { it.isLetter() } -> text = selected
            last != null && selStart == selEnd &&
                ic.getTextBeforeCursor(last.length, 0)?.toString() == last -> {
                ic.deleteSurroundingText(last.length, 0)
                afterSelfEdit(-last.length)
                text = last
            }
            else -> { recased = null; return false }
        }
        val next = when {
            text == text.lowercase() -> text.split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            text == text.uppercase() -> text.lowercase()
            else -> text.uppercase()
        }
        ic.commitText(next, 1)
        afterSelfEdit(next.length - if (selected != null) selected.length else 0)
        recased = next
        swipeJustCommitted = false
        lastSwipeWord = ""
        liftSample = null
        clearUndo()
        feedback()
        refreshSuggestions()
        return true
    }

    /** A one-shot capital is consumed by the character it capitalised; caps-lock
     *  and plain lowercase are left untouched. Called after every committed glyph. */
    private fun consumeOneShotShift() {
        // The capital is spent, so the hand that asked for it has had its way
        // and the text may arm shift again (iOS: `shift` didSet).
        shiftByHand = false
        if (shift && !capsLocked) { shift = false; render() }
    }

    /** Polish Glagolitic is on; read by the glide decoder off the UI thread. */
    @Volatile private var polishGlag = false

    private fun renderLetters() {
        letterKeyViews.clear()
        val lang = Lang.current(context)
        val script = Script.mode(context)
        // The Latin rows are what the GRID is calibrated to. Cyrillic and
        // Glagolitic have no `q w x`, and the keys that stay keep their Latin
        // WIDTH: the freed space becomes padding at the ends of the row, and in
        // row 3 shift and backspace grow instead. So everything the decoder
        // expresses in key widths - the learned touch offset (`Lift`), the pivot
        // cap, the glide minimum - stays calibrated across a script change, and
        // no key changes size under the thumb. Measured 2026-11-08: letting the
        // eight keys of row 1 fill the width instead (the plain Android way)
        // costs four points of top-1, because the columns of the alphabet stop
        // lining up with the rows below (`docs/script-tables.md`).
        val qwertz = KeyRows.isQwertz(context, lang)
        val full = Layout.letterRows(lang, qwertz = qwertz)
        // Both non-Latin scripts glide `lj` and `nj` over one key each: `љ`/`њ` in
        // Cyrillic, `ⰾⱐ`/`ⱀⱐ` in Glagolitic. The route over `l`+`j` stays too.
        // Not where every key writes ONE letter (mk ru uk bg sla, iOS `singleLetterKeys`).
        // Not in Polish Glagolitic either: its keys write Polish letters (iOS
        // `singleLetterKeys` + "pl").
        Dictionary.digraphKeys = script != Script.Mode.LATIN && lang !in Lang.SINGLE_LETTER_KEYS &&
            lang != Lang.PL
        polishGlag = lang == Lang.PL && script == Script.Mode.GLAGOLITIC
        Layout.letterRows(lang, script, qwertz).forEachIndexed { index, row ->
            val rv = makeRow()
            val pad = (full[index].length - row.length) / 2f
            if (index == 2) {
                val glyph = if (capsLocked) "⇪" else "⇧"
                val sk = functionKey(glyph, 1.5f + pad) { onShiftTap() }
                when {
                    capsLocked -> sk.setBackgroundColor(cShiftLock)  // locked: stronger
                    shift -> sk.setBackgroundColor(cShiftOne)         // one-shot: light
                }
                rv.addView(sk)
            } else if (pad > 0f) {
                rv.addView(spacer(pad))
            }
            row.forEach { ch -> rv.addView(letterKey(ch)) }
            if (index == 2) {
                rv.addView(backspaceKey(1.5f + pad))
            } else if (pad > 0f) {
                rv.addView(spacer(pad))
            }
            addView(rv)
        }
        // Bottom row: `123`, 🌐, 😀, space, `.`, ⏎ - one mark key, on the right
        // of space, holding the rest (`,` first). `?` and `!` live on the `123`
        // page next to backspace.
        val isv = Lang.current(context) == Lang.ISV
        // The script key belongs to every language written in more than one
        // alphabet - Interslavic (three) and Serbian (two) so far.
        val scripts = Script.offered(Lang.current(context)).size > 1
        val b = makeRow()
        b.addView(numericKey(1.2f))
        b.addView(globeKey(0.95f))
        // Script key: like 🌐 switches the keyboard, this switches the alphabet,
        // Latin -> Cyrillic -> Glagolitic. It names the current alphabet; the
        // key labels and everything written from now on follow it. Label: ABC / АБВ / ⰀⰁⰂ.
        // Interslavic only - Polish is written in Latin.
        if (scripts) b.addView(functionKey(Script.keyLabel(context), 1.2f) {
            Script.next(context)
            render()
            refreshSuggestions()
        })
        // Only `.` beside space, on its right; 😀 is the one key on the left
        // (owner 2026-11-07, after using the iOS row: the comma comes back as
        // the FIRST item held on `.`, "a obok te pozostale znaki jak wczesniej").
        // This undoes the 2026-11-01 comma key (92b3ad4) and buys its 0.8 plus
        // 0.1 off 😀 for the space bar: the weights are now the iOS ones to the
        // decimal, so the same thumb travel works on both phones. Nothing moved
        // out of reach - `: ; ( ) -` sit on the `123` page and `–` is held on
        // its `-`.
        b.addView(functionKey("😀", 0.9f) { emoji = true; symbols = false; render() })
        b.addView(spaceKey(if (scripts) 3.45f else 4.65f))
        b.addView(symbolKey('.', 0.9f, Layout.periodLetterHold))
        b.addView(functionKey("⏎", 1.4f) { enter() })
        addView(b)
    }

    /** `123`: tap = the symbol page. HOLD opens a one-item strip with the gear,
     *  which draws this keyboard's own settings in place of the letters - the
     *  same gesture as on the iPhone, where the globe belongs to the system and
     *  a keyboard may not open the picker itself, so `123` had to carry it
     *  (owner 2026-11-10). Android keeps its own extra route as well: the gear
     *  held on the GLOBE still opens the app, which is where everything this
     *  panel does not carry lives - the collector, the mark counter, the test
     *  build number, the language list. */
    private fun numericKey(weight: Float): TextView {
        val gear = "⚙"
        val tv = baseKey("123", weight)
        tv.isLongClickable = false
        var downX = 0f
        var downY = 0f
        val longPress = Runnable { showPopup(tv, listOf(gear), downX); tv.isPressed = false }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    tv.isPressed = true
                    flickHandler.postDelayed(longPress, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popupItems != null) movePopup(tv, e.x)
                    else if (abs(e.x - downX) > touchSlop * 2 || abs(e.y - downY) > touchSlop * 2) {
                        flickHandler.removeCallbacks(longPress)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    if (closePopup() == gear) {
                        settingsOpen = true; symbols = false; emoji = false
                    } else {
                        symbols = true; symbolsPage2 = false
                    }
                    render()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    closePopup()
                    true
                }
                else -> false
            }
        }
        return tv
    }

    /**
     * The keyboard's own settings, drawn in place of the letters - the panel
     * iOS has carried since 0.5.3, ported here 2026-11-10 so that the same
     * gesture answers with the same screen on both phones. Same four sections
     * in the same order: theme, key layout, script (Interslavic only) and the
     * words added with the `+` chip. Every choice here belongs to the KEYBOARD,
     * so the panel works although the app is never opened; the headings go
     * through [Script] like every other surface, so in Cyrillic they read in
     * Cyrillic.
     */
    private fun renderSettings() {
        letterKeyViews.clear()        // nothing here is a letter key: no glide can start
        val lang = Lang.current(context)
        val blue = Color.parseColor("#1E88E5")
        val scroll = android.widget.ScrollView(context)
        scroll.isFillViewport = true
        val column = LinearLayout(context)
        column.orientation = VERTICAL

        fun header(text: String) {
            val tv = TextView(context)
            tv.text = Script.out(context, text)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tv.setTextColor(cBorder)
            tv.setPadding(dp(8), dp(8), dp(8), dp(2))
            column.addView(tv)
        }

        fun choices(items: List<Triple<String, Boolean, () -> Unit>>) {
            val rv = makeRow()
            for ((label, on, act) in items) {
                val key = functionKey(label, 1f) { act() }
                if (on) { key.setBackgroundColor(blue); key.setTextColor(Color.WHITE) }
                rv.addView(key)
            }
            column.addView(rv)
        }

        // Languages (owner 2026-11-11, iOS 0.6.3): one keyboard in the system,
        // the languages ticked HERE, four codes to a row; at least one stays.
        // A language just ticked becomes the one typed, as on iOS.
        header("Jezyki")
        val on = Lang.enabled(context)
        // isv first, the rest by code (owner 2026-11-11, iOS 0.6.9: "hard to find");
        // only the panel is sorted - the space bar and 🌐 keep Lang.ALL order.
        (listOf(Lang.ISV) + Lang.ALL.filter { it != Lang.ISV }.sorted()).chunked(4).forEach { chunk ->
            choices(chunk.map { code ->
                Triple(code, code in on, {
                    val next = on.toMutableList()
                    val adding = code !in next
                    if (adding) next.add(code) else if (next.size > 1) next.remove(code)
                    Lang.setEnabled(context, next)
                    if (adding) Lang.setCurrent(context, code)
                    Dictionary.use(context, Lang.current(context))
                    service.rebuildView(true)
                })
            })
        }

        header("Tema")
        choices(listOf(
            Triple("◐", Theme.choice(context) == Theme.AUTO,
                { Theme.set(context, Theme.AUTO); service.rebuildView(true) }),
            Triple("☀", Theme.choice(context) == Theme.LIGHT,
                { Theme.set(context, Theme.LIGHT); service.rebuildView(true) }),
            Triple("☾", Theme.choice(context) == Theme.DARK,
                { Theme.set(context, Theme.DARK); service.rebuildView(true) }),
        ))

        if (KeyRows.offered(lang)) {
            header("Razloženje klaviatury")
            val z = KeyRows.isQwertz(context, lang)
            choices(listOf(
                Triple("QWERTY", !z, { KeyRows.set(context, lang, false); render() }),
                Triple("QWERTZ", z, { KeyRows.set(context, lang, true); render() }),
            ))
        }

        val canScripts = Script.offered(lang)
        if (canScripts.size > 1) {
            header("Pismo")
            val m = Script.mode(context)
            choices(canScripts.map { mode ->
                val label = when (mode) {
                    Script.Mode.LATIN -> "ABC"
                    Script.Mode.CYRILLIC -> "АБВ"
                    Script.Mode.GLAGOLITIC -> "ⰀⰁⰂ"
                }
                Triple(label, m == mode,
                    { Script.setMode(context, mode); render(); refreshSuggestions() })
            })
        }

        header("Moje slova")
        val words = UserWords.all(context, lang).sorted()
        if (words.isEmpty()) {
            val tv = TextView(context)
            tv.text = "—"
            tv.setTextColor(cBorder)
            tv.setPadding(dp(8), dp(4), dp(8), dp(4))
            column.addView(tv)
        }
        for (w in words) {
            val rv = makeRow()
            val label = TextView(context)
            label.text = Script.out(context, w)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            label.setTextColor(cText)
            label.gravity = Gravity.CENTER_VERTICAL
            label.setPadding(dp(8), 0, dp(8), 0)
            label.layoutParams = LayoutParams(0, dp(44), 1f)
            rv.addView(label)
            rv.addView(functionKey("✕", 0.9f) {
                UserWords.remove(context, lang, w)
                Dictionary.use(context, lang)      // `remove` dropped the loaded list
                render()
            })
            column.addView(rv)
        }

        scroll.addView(column)
        // Exactly the three letter rows it replaces, as the emoji panel does:
        // the keyboard must not jump in height when the panel opens.
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(52) * 3))
        val b = makeRow()
        b.addView(functionKey("ABC", 1.6f) { settingsOpen = false; render() })
        b.addView(globeKey(1.2f))
        b.addView(spaceKey(4.2f))
        b.addView(backspaceKey())
        addView(b)
    }

    /**
     * The emoji panel (iOS 0.4.2): lately used first, then the common set, in
     * rows of eight, scrolling. Its bottom row is the one from `123` - ABC to
     * come back, 🌐, space - plus a backspace, because the letter row that
     * carries one is not on screen.
     */
    private fun renderEmoji() {
        letterKeyViews.clear()        // nothing here is a letter key: no glide can start
        val scroll = android.widget.ScrollView(context)
        scroll.isFillViewport = true
        val column = LinearLayout(context)
        column.orientation = VERTICAL
        val all = Emoji.panel(context)
        for (start in all.indices step EMOJI_PER_ROW) {
            val rv = makeRow()
            for (i in start until start + EMOJI_PER_ROW) {
                val e = all.getOrNull(i)
                if (e == null) {
                    rv.addView(functionKey("", 1f) {})
                } else {
                    rv.addView(functionKey(e, 1f) { tapEmoji(e) })
                }
            }
            column.addView(rv)
        }
        scroll.addView(column)
        // Exactly the three letter rows it replaces: the keyboard must not jump
        // in height when the panel opens, and a weighted height would collapse
        // in an input view that sizes itself to its content.
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(52) * 3))
        val b = makeRow()
        b.addView(functionKey("ABC", 1.6f) { emoji = false; render() })
        b.addView(globeKey(1.2f))
        b.addView(spaceKey(4.2f))
        b.addView(backspaceKey())
        addView(b)
    }

    /** After a glide the owed space comes first: `pisem 😀`, not `pisem😀`. */
    private fun tapEmoji(e: String) {
        glagWord = null
        feedback()
        val ic = conn ?: return
        if (pendingSpace) {
            ic.commitText(" ", 1)
            afterSelfEdit(1)
            pendingSpace = false
        }
        swipeJustCommitted = false
        lastSwipeWord = ""
        liftSample = null
        clearUndo()
        ic.commitText(e, 1)
        afterSelfEdit(e.length)
        currentWord = ""
        Emoji.used(context, e)
        clearSlots()
    }

    /** Names the language when there is more than one to tell apart. */
    private fun spaceKey(weight: Float): TextView {
        val many = Lang.enabled(context).size > 1
        val tv = functionKey(if (many) Lang.label(Lang.current(context)) else "space", weight) { commit(" ") }
        if (many) {
            // Smaller than a letter, still shrinking to fit (setTextSize would switch auto-size off).
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                tv.setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, TypedValue.COMPLEX_UNIT_SP)
            } else tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        // The hold list ends with ⚙, so it is offered with ONE language too
        // (iOS 0.6.10); sliding between languages still needs two.
        switchesLanguages(tv)
        return tv
    }

    /**
     * With more than one language ticked the space bar also switches them
     * (iOS 0.6.3, owner 2026-11-11): slide right = the next ticked language,
     * left = the previous, round the list (never out to the system - that stays
     * 🌐's job); HOLD = the vertical list, slide onto one and lift. So the space
     * is typed on LIFT, inside or outside the key - a thumb that slid a little
     * still gets its space - and a recognised gesture types nothing.
     */
    private fun switchesLanguages(tv: TextView) {
        tv.setOnClickListener(null)
        tv.isLongClickable = false
        var downX = 0f
        var downY = 0f
        var moved = false
        val hold = Runnable {
            showLangList(tv, Lang.enabled(context) + LANG_GEAR)
            tv.isPressed = false
        }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; moved = false
                    tv.isPressed = true
                    // This view watches the whole keyboard for glides; without
                    // this the slide would be taken from the space bar.
                    (tv.parent as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    flickHandler.postDelayed(hold, 400L)   // iOS minimumPressDuration 0.4 s
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (langItems != null) moveLangList(tv, e.x, e.y)
                    else if (!moved && (abs(e.x - downX) > touchSlop * 2 || abs(e.y - downY) > touchSlop * 2)) {
                        moved = true
                        flickHandler.removeCallbacks(hold)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    flickHandler.removeCallbacks(hold)
                    tv.isPressed = false
                    (tv.parent as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                    val listed = langItems != null
                    val pick = closeLangList()
                    val on = Lang.enabled(context)
                    val now = Lang.current(context)
                    val dx = e.x - downX
                    when {
                        listed && pick == LANG_GEAR -> {
                            // The keyboard's own panel, as from 123's hold (iOS openSettings).
                            settingsOpen = true; symbols = false; emoji = false
                            render()
                        }
                        listed -> if (pick != null && pick != now) switchLanguage(pick)
                        abs(dx) >= dp(30) && on.size > 1 -> {
                            val i = on.indexOf(now).coerceAtLeast(0)
                            switchLanguage(on[(i + if (dx > 0) 1 else on.size - 1) % on.size])
                        }
                        else -> commit(" ")
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    flickHandler.removeCallbacks(hold)
                    tv.isPressed = false
                    closeLangList()
                    true
                }
                else -> false
            }
        }
    }

    /** The glide state of the word before belongs to the old language and is
     *  closed (iOS `switchLanguage`); the system then rebuilds the view. */
    private fun switchLanguage(lang: String) {
        feedback()
        pendingSpace = false
        swipeJustCommitted = false
        lastSwipeWord = ""
        trimAfterWipe = ""
        clearUndo()
        service.switchToLanguage(lang)
    }

    /** 🌐: tap = the next ticked language (Gboard's habit), or the system
     *  keyboard picker when only one is ticked. HOLD opens a two-item strip:
     *  ⚙ this keyboard's own settings, 🌐 the system keyboard picker.
     *
     *  The settings were reachable only from the app icon, which meant leaving
     *  whatever you were typing - and a tester does not necessarily know the
     *  keyboard has an app at all (owner, 2026-11-04). Icons and not words on
     *  purpose: the keyboard surface carries no translated strings, and a gear
     *  reads the same in all six languages. The strip is the same one the
     *  letter keys use, so a hold-and-slide works exactly as it does there;
     *  The PICKER sits first, over the key, so a plain hold-and-release does
     *  what holding the globe always did - and it is the only way back from
     *  a foreign keyboard, so it must not need aiming. The gear is one slide
     *  to the right (owner, 2026-11-04). */
    private fun globeKey(weight: Float): TextView {
        val gear = "⚙"
        val picker = "🌐"
        val tv = baseKey("🌐", weight)
        // The view's own long-press machinery competes with ours for the hold.
        tv.isLongClickable = false
        var downX = 0f
        var downY = 0f
        val longPress = Runnable { showPopup(tv, listOf(picker, gear), downX); tv.isPressed = false }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    tv.isPressed = true
                    flickHandler.postDelayed(longPress, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popupItems != null) movePopup(tv, e.x)
                    else if (abs(e.x - downX) > touchSlop * 2 || abs(e.y - downY) > touchSlop * 2) {
                        flickHandler.removeCallbacks(longPress)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    // The system rotates the languages (subtypes) and tells the
                    // service, which rebuilds the keyboard for the new one.
                    when (closePopup()) {
                        gear -> service.openSettings()
                        picker -> service.switchIme()
                        else -> if (!service.nextLanguage()) service.switchIme()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    closePopup()
                    true
                }
                else -> false
            }
        }
        return tv
    }

    /** `123` and `#+=` pages, laid out as on iOS (ab921f2, 559a71f): the third
     *  row runs between the page key and backspace, and the bottom row is
     *  ABC, 🌐, space, return. */
    private fun renderSymbols() {
        val rows = if (symbolsPage2) Layout.symbolRows else Layout.numericRows
        rows.forEachIndexed { index, row ->
            val rv = makeRow()
            if (index == 2) {
                rv.addView(functionKey(if (symbolsPage2) "123" else "#+=", 1.5f) {
                    symbolsPage2 = !symbolsPage2; render()
                })
            }
            // `,` and `?` hold nothing on these pages; the rest follow iOS.
            row.forEach { ch -> rv.addView(symbolKey(ch, 1f, Layout.numericHold[ch])) }
            if (index == 2) {
                rv.addView(backspaceKey())
            }
            addView(rv)
        }
        val b = makeRow()
        b.addView(functionKey("ABC", 1.6f) { symbols = false; symbolsPage2 = false; render() })
        b.addView(globeKey(1.2f))
        b.addView(spaceKey(5.6f))
        b.addView(functionKey("⏎", 1.6f) { enter() })
        addView(b)
    }

    private fun makeRow(): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        // A row lines its keys up by text BASELINE by default, so a label that
        // wrapped onto two lines (`ⰀⰁⰂ`, `Medžuslovjansky`, Glagolitic `y` = `ⱐⰹ`)
        // pushed its key down out of the row (owner 2026-10-14, Android test phone).
        row.isBaselineAligned = false
        return row
    }

    /** The Latin text a letter key writes, with the shift applied: its own
     *  character, or in Cyrillic the digraph behind `љ` / `њ` (`Layout.keyText`).
     *  `Lj` titlecases the way `Script.cased` expects, so `Љ` comes out right. */
    private fun keyText(ch: Char): String {
        val base = Layout.keyText(ch, Script.mode(context), Lang.current(context))
        return if (shift) base.replaceFirstChar { it.uppercaseChar() } else base
    }

    private fun letterKey(ch: Char): TextView {
        // Label in the chosen script; the key itself stays the Latin letter `ch`.
        val label = Script.out(context, keyText(ch))
        val tv = baseKey(label, 1f)
        val lang = Lang.current(context)
        val latin = Script.mode(context) == Script.Mode.LATIN
        // Whether this key has a letter of the keyboard's OWN on it (flick target).
        val hasAccent = Layout.longPress(ch, false, lang) != null
        val holds = Layout.variants(ch, false, lang, latin).isNotEmpty()
        val flickThreshold = dp(22)

        var downX = 0f
        var downY = 0f
        var handled = false
        val longPress = Runnable {
            val items = Layout.variants(ch, shift, lang, latin)
            if (items.isNotEmpty()) {
                showPopup(tv, items, downX)
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
                    if (holds) flickHandler.postDelayed(longPress, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popupItems != null) { movePopup(tv, e.x); return@setOnTouchListener true }
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
                    closePopup()?.let { commit(Script.out(context, it), it) }
                    if (!handled) {
                        val dx = e.x - downX
                        val dy = e.y - downY
                        // Upward flick (mostly vertical) => the keyboard's own letter.
                        val flickUp = hasAccent && dy < -flickThreshold && abs(dy) >= abs(dx)
                        if (flickUp) {
                            Layout.longPress(ch, shift, lang)?.let { commit(Script.out(context, it), it) }
                        } else {
                            keyText(ch).let { commit(Script.out(context, it), it) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    hideKeyPreview()
                    closePopup()
                    true
                }
                else -> false
            }
        }
        letterKeyViews.add(ch to tv)
        return tv
    }

    /** A mark key. With [hold] it opens the popup like a letter (iOS 559a71f):
     *  tap = the mark, hold and slide = one of [hold]. */
    private fun symbolKey(ch: Char, weight: Float = 1f, hold: List<String>? = null): TextView {
        val tv = baseKey(ch.toString(), weight)
        if (hold == null) {
            tv.setOnClickListener {
                Symbols.record(context, ch.toString(), Symbols.TAP)
                commit(ch.toString())
            }
            return tv
        }
        tv.isLongClickable = false
        var downX = 0f
        var downY = 0f
        val longPress = Runnable { showPopup(tv, hold, downX, mayOverhang = true); tv.isPressed = false }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    tv.isPressed = true
                    flickHandler.postDelayed(longPress, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popupItems != null) movePopup(tv, e.x)
                    else if (abs(e.x - downX) > touchSlop * 2 || abs(e.y - downY) > touchSlop * 2) {
                        flickHandler.removeCallbacks(longPress)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    val chosen = closePopup()
                    Symbols.record(context, chosen ?: ch.toString(), if (chosen != null) Symbols.HOLD else Symbols.TAP)
                    commit(chosen ?: ch.toString())
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    flickHandler.removeCallbacks(longPress)
                    tv.isPressed = false
                    closePopup()
                    true
                }
                else -> false
            }
        }
        return tv
    }

    private fun functionKey(text: String, weight: Float, onTap: () -> Unit): TextView {
        val tv = baseKey(text, weight)
        tv.setOnClickListener { onTap() }
        return tv
    }

    /** Backspace with press-and-hold: one tap deletes normally; holding deletes
     *  word by word, a little faster the longer you hold (never whole lines).
     *  A hold that STARTED by wiping a glided word trims it instead: the word
     *  comes back one letter short and loses letters while held - the way to
     *  fix an ending without first tapping space (owner, 2026-10-08). */
    private fun backspaceKey(weight: Float = 1.5f): TextView {
        val tv = baseKey("⌫", weight)
        // The view's own long-press machinery competes with ours for the same
        // hold and buys nothing here.
        tv.isLongClickable = false
        val repeat = object : Runnable {
            override fun run() {
                // Held right after a glide (iOS 0.5.14, owner 2026-11-11): the tap
                // already took the last letter, so the first held step takes the
                // rest of that word and offers the glide's other guesses; then
                // whole words, as for any held backspace.
                if (trimAfterWipe.isNotEmpty()) {
                    val stem = trimAfterWipe
                    trimAfterWipe = ""
                    val ic = conn
                    val before = ic?.getTextBeforeCursor(stem.length, 0)?.toString().orEmpty()
                    if (ic != null && before == stem) {
                        ic.deleteSurroundingText(stem.length, 0)
                        afterSelfEdit(-stem.length)
                    }
                    clearUndo()
                    if (lastSwipeCandidates.isNotEmpty()) {
                        showSwipeAlts(lastSwipeCandidates, highlight = -1)
                    } else refreshSuggestions()
                    feedback()
                    flickHandler.postDelayed(this, backspaceInterval)
                    return
                }
                // deleteWordBackward says whether anything is left to delete.
                val more = deleteWordBackward()
                feedback()
                if (!more) return
                // Deleting whole WORDS at the old floor of 130 ms was about eight
                // words a second - far past the point where a thumb can react to
                // what it sees. Slower ramp, higher floor.
                backspaceInterval = maxOf(200L, backspaceInterval - 15L)
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
                    lastWipeRemoved = ""
                    trimming = false
                    backspace()                       // a tap is still one character
                    trimAfterWipe = ""
                    // Right after a glide `backspace()` took the whole word; a TAP
                    // means "fix the ending" (owner 2026-11-11, parity with iOS
                    // 0.5.14), so it comes straight back one letter short. A hold
                    // then takes the stem as well (see `repeat`).
                    val stem = lastWipeRemoved.trimEnd().dropLast(1)
                    if (lastWipeRemoved.isNotEmpty() && stem.isNotEmpty()) {
                        val ic = conn
                        if (ic != null) {
                            ic.commitText(stem, 1)
                            afterSelfEdit(stem.length)
                            trimAfterWipe = stem
                        }
                        clearUndo()
                        refreshSuggestions()
                    }
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
                    trimAfterWipe = ""
                    trimming = false
                    true
                }
                else -> false
            }
        }
        return tv
    }

    /** Dead space in a letter row, built exactly like a key so the columns of
     *  the keys beside it land where the Latin grid puts them. Used where a
     *  script has no letter for a key (Cyrillic, Glagolitic: no `q w x`). */
    private fun spacer(weight: Float): android.view.View {
        val v = android.view.View(context)
        val lp = LayoutParams(0, dp(52), weight)
        lp.setMargins(dp(1), dp(1), dp(1), dp(1))
        v.layoutParams = lp
        return v
    }

    private fun baseKey(text: String, weight: Float): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        // One line, shrinking to fit: a wide label never wraps onto a second line.
        tv.maxLines = 1
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            tv.setAutoSizeTextTypeUniformWithConfiguration(10, 20, 1, TypedValue.COMPLEX_UNIT_SP)
        } else {
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tv.setTextColor(cText)
        tv.setBackgroundColor(cKey)
        val lp = LayoutParams(0, dp(52), weight)
        lp.setMargins(dp(1), dp(1), dp(1), dp(1))   // bigger touch area, fewer dead gaps
        tv.layoutParams = lp
        tv.isClickable = true
        tv.isLongClickable = true
        return tv
    }

    /** [s] as shown; [latin] = the same in the keyboard's Latin, given by the
     *  letter keys so Polish Glagolitic can re-write the word (iOS 0.6.12). */
    private fun commit(s: String, latin: String? = null) {
        // A letter typed inside the post-glide window EXTENDS the word that was
        // just glided - the very case the backspace path below already handles.
        // Smart space must not fire there, or `pisem` + `s` lands as `pisem s`
        // instead of `pisems`, which in an inflected language is the common case,
        // not the edge one. Read it BEFORE swipeJustCommitted is cleared.
        // The window is now only a CEILING. Inside it, the wordlist decides: a
        // letter extends the glided word only if the two together can still
        // grow into a word. Time alone glued the start of the next word onto
        // the last one whenever the user typed quickly (`wyjsc` + `s` ->
        // `wyjscs`), and that is the frequent case - extending a glide is the
        // rare one. Price: extending into a word the list does NOT know gets a
        // space instead; adding it once with the + chip fixes it for good,
        // because UserWords is part of the list this asks.
        // Past the window, one more case still extends: the letter COMPLETES a
        // word that is more frequent than the glided one (`her` + `e` = `here`,
        // owner 2026-11-12: picked `her`, paused, tapped `e`, got `her e`).
        // A whole, MORE common word guards the frequent case the window exists
        // for - the next word starting right after a glide - because `her` + `s`
        // (`hers`) or `the` + `y` (`they`) is a rarer word and keeps its space.
        val sinceGlide = SystemClock.uptimeMillis() - swipeCommitAtMs
        val glidedLatin = Script.toLatin(context, lastSwipeWord).lowercase()
        val joinedLatin = (glidedLatin + Script.toLatin(context, s)).lowercase()
        val extendingGlide = swipeJustCommitted && s.isNotEmpty() && s[0].isLetter() && (
            (sinceGlide <= SWIPE_WIPE_WINDOW_MS && Dictionary.hasPrefix(joinedLatin)) ||
                (sinceGlide <= LATE_EXTEND_MS &&
                    Dictionary.frequency(joinedLatin) > Dictionary.frequency(glidedLatin).coerceAtLeast(0))
            )
        if (extendingGlide) pendingSpace = false

        // Ukrainian: an apostrophe right after a letter is INSIDE the word
        // (`п'ять`), also right after a glide - the owed space is dropped and
        // the word goes on (iOS 0.6.6). A leading one stays a quote mark.
        val lang = Lang.current(context)
        val apostropheInWord = s == "'" && lang in Lang.APOSTROPHE_IN_WORD &&
            conn?.getTextBeforeCursor(1, 0)
                ?.lastOrNull()?.isLetter() == true
        if (apostropheInWord) pendingSpace = false

        // A glide the user KEPT is a measurement of their own thumb: the word
        // stood in the text until a space or a mark closed it. A letter means
        // the word is being EXTENDED, so the text no longer holds the word that
        // was decoded and the gesture says nothing about where the thumb sits.
        if (swipeJustCommitted && !extendingGlide && !apostropheInWord &&
            s.isNotEmpty() && !s[0].isLetter()) {
            learnLift()
        }
        recased = null       // any other key ends a shift-key re-casing run
        swipeJustCommitted = false
        clearUndo()          // one-shot: the undo chip dies on the next key
        feedback()
        val ic = conn ?: return
        if (s.isEmpty()) return
        val c0 = s[0]
        if (c0.isLetter()) lastLetterTapMs = SystemClock.uptimeMillis()

        // Adaptive ranking: count a hand-typed known word when a terminator lands.
        if (!c0.isLetter()) {
            val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
            // A Ukrainian apostrophe is not a terminator: `п'` is half a word.
            val w = if (apostropheInWord) ""
                else Script.toLatin(context, trailingWord(before)).lowercase()
            if (w.length >= 2 && Dictionary.contains(w)) {
                Usage.record(context, w)
                Popularity.record(context, w)
            }
        }

        // Polish Glagolitic: letters re-write the word under the cursor; any
        // other key first finishes it (the od-/pod- + z split).
        val glag = Script.rewritesWord(context)
        if (glag && latin != null && c0.isLetter()) {
            var lead = 0
            if (pendingSpace) {
                val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                if (prev.isNotEmpty() && !prev[0].isWhitespace()) { ic.commitText(" ", 1); lead = 1 }
                pendingSpace = false
            }
            afterSelfEdit(lead + glagType(ic, latin))
            refreshSuggestions()
            consumeOneShotShift()
            return
        }
        var finished = 0
        if (glag) finished = finishGlagWord(ic) else glagWord = null

        // Smart space: a word carries no trailing space, so the space is owed to
        // the NEXT thing typed.
        var inserted = s.length + finished
        if (pendingSpace) {
            when {
                c0 == ' ' -> { ic.commitText(" ", 1); inserted = 1 + finished; pendingSpace = false }   // user's own space, no double
                c0 in ATTACH_PUNCT -> { ic.commitText(s, 1); pendingSpace = true }  // glue punct, stay armed
                else -> {
                    val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                    if (prev.isNotEmpty() && !prev[0].isWhitespace()) {
                        ic.commitText(" $s", 1); inserted = s.length + 1 + finished
                    } else {
                        ic.commitText(s, 1)    // guard: never a leading/double space
                    }
                    pendingSpace = false
                }
            }
        } else {
            ic.commitText(s, 1)
        }
        afterSelfEdit(inserted)
        refreshSuggestions()
        consumeOneShotShift()          // a tapped capital lasts exactly one letter
    }

    /**
     * True when the editor currently holds a SELECTION.
     *
     * This has to be asked before every delete, because `deleteSurroundingText`
     * does not do what its name suggests when text is selected: it removes
     * characters AROUND the selection and leaves the selection itself in place.
     * So selecting a paragraph and pressing backspace ate the text next to it
     * while the selected block stayed - and holding backspace chewed backwards
     * through the rest of the document. Losing the user's writing is the worst
     * thing a keyboard can do, so every delete path checks this first.
     */
    private var selStart = 0
    private var selEnd = 0

    /**
     * Cursor positions OUR OWN edits produced, newest last.
     *
     * The editor reports every caret change and nothing in that report says who
     * caused it. Predicting where our own edit leaves the cursor is what
     * separates "the keyboard just typed" from "the user tapped somewhere
     * else" - and only the second may cancel an owed space or a just-glided
     * word. A RING rather than one value because the reports are asynchronous:
     * swiping a second word before the first word's report arrives would
     * otherwise read that late report as a user jump and cancel smart space
     * mid-sentence.
     */
    private val expectedCursors = ArrayDeque<Int>()

    /** Record where our own edit left the cursor. [delta] = inserted - deleted. */
    private fun afterSelfEdit(delta: Int) = afterSelfEditTo(maxOf(0, selEnd + delta))

    private fun afterSelfEditTo(at: Int) {
        selStart = at
        selEnd = at
        expectedCursors.addLast(at)
        while (expectedCursors.size > 4) expectedCursors.removeFirst()
    }

    /**
     * The keyboard came up, or came back from another app. The text decides the
     * shift: a keyboard that opens with shift off in the middle of a sentence
     * used to type the rest of it lowercase forever, and one that opens armed
     * puts a capital in the middle of a word (iOS `viewWillAppear`, `byh Dobra`).
     */
    fun onInputStarted() {
        shiftByHand = false
        recased = null
        liftSample = null
        touchLift = Lift.get(context)
        rearmShift()
    }

    /** Called by the service whenever the editor reports cursor/selection. */
    fun onSelectionChanged(start: Int, end: Int) {
        // Anything but a position one of our own edits produced means the user
        // moved the caret - tapped into an older word, dragged a handle,
        // selected something. A space owed to the next word and a glide that
        // backspace may still wipe whole both belong to the place they were
        // created; carried anywhere else they fire in the wrong place, which is
        // exactly how a space appeared "out of nowhere" in the middle of a
        // sentence that had been edited.
        val ours = start == end && expectedCursors.contains(start)
        selStart = start
        selEnd = end
        if (ours) {
            // Retire the predictions this report overtook, so a position the
            // cursor merely PASSED THROUGH a moment ago cannot later be mistaken
            // for our own work when the user taps back into the same word. The
            // matched one stays: editors sometimes report the same edit twice.
            while (expectedCursors.size > 1 && expectedCursors.first() != start) {
                expectedCursors.removeFirst()
            }
        }
        if (!ours) {
            val hadUndo = canUndo()
            pendingSpace = false
            swipeJustCommitted = false
            lastSwipeWord = ""
            liftSample = null          // the caret moved: that word is no longer ours to measure
            glagWord = null
            clearUndo()
            expectedCursors.clear()
            expectedCursors.addLast(start)   // resync: judge the next edit from here
            // The undo chip offers to put text back WHERE IT WAS; once the
            // caret has moved it would put it somewhere else, so it goes.
            if (hadUndo) refreshSuggestions()
            // A caret moved by hand lands wherever it lands - including the
            // middle of a word - so the shift the user had set by hand means
            // nothing here and the text decides again.
            shiftByHand = false
        }
        // The editor reports after OUR edits too, and that is the one moment the
        // text is settled enough to ask it about the sentence.
        rearmShift()
    }

    /**
     * Shift armed where a sentence begins (iOS ab921f2): at the start of the
     * field, after a new line, and after `. ! ?` once a space has followed.
     *
     * Android armed shift NOWHERE until 3.5.31 - it only ever came from the key,
     * so every sentence after the first had to be capitalised by hand. Which
     * state is wanted is read from the TEXT, never remembered, because the
     * keyboard is also rebuilt under us (language, script, theme) and any
     * remembered flag would survive that as a lie.
     *
     * Caps Lock is deliberate and never touched. A shift the user set BY HAND
     * is left alone too, until the caret moves or the capital is spent.
     */
    private fun rearmShift() {
        if (capsLocked || shiftByHand) return
        val ic = conn ?: return
        val before = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        // An empty answer is not proof of an empty field: editors report nothing
        // for a moment after an edit, and acting on that armed shift in the
        // middle of a word. Empty counts only with the caret at position 0.
        if (before.isEmpty() && selStart > 0) return
        // A bare new line is NOT a sentence start (iOS 0.5.1, owner 2026-11-07):
        // a word per line got a capital on every line, and deleting a mark at
        // the start of a line armed it again each time. A new line after
        // `.` `!` `?` still is, and so is the empty field.
        val lines = before.trimEnd(' ', '\n', '\t')
        val atStart = lines.isEmpty()
        val gap = before.lastOrNull().let { it == ' ' || it == '\n' }
        val afterStop = (lines.lastOrNull() in listOf('.', '!', '?')) && gap
        val wanted = atStart || afterStop
        // Only act on a real transition, or every keystroke rebuilds the view.
        if (wanted != shift) {
            shift = wanted
            render()
        }
    }

    /**
     * Add what a delete just removed to the undo buffer.
     *
     * Deletes run backwards, so the newest characters go in FRONT of the ones
     * taken before them and the buffer is always the contiguous run this delete
     * gesture has eaten, in reading order. Every single-character delete feeds
     * it too, even though only a WHOLE-WORD delete ([arm]) offers the chip -
     * because holding backspace begins with one plain character and then eats
     * the rest of the word, so a buffer that started only at the word delete
     * would put `napisat` back where `napisati` had been.
     */
    private fun pushUndo(removed: String, arm: Boolean, restoreRank: Boolean = false) {
        if (removed.isEmpty()) return
        undoText = removed + undoText
        if (arm) undoArmed = true
        if (restoreRank) undoRestoreRank = true
        val trimmed = undoText.trim()
        undoWord = trailingWord(trimmed)
        undoMore = trimmed.any { it.isWhitespace() }
        if (undoWord.isEmpty()) undoArmed = false   // a stray mark is not a word
    }

    private fun clearUndo() {
        undoText = ""
        undoWord = ""
        undoArmed = false
        undoMore = false
        undoRestoreRank = false
    }

    /** Is there something to offer as the `↶` chip? */
    private fun canUndo(): Boolean = undoArmed && undoWord.isNotEmpty()

    /** Put back exactly what the last whole-word delete removed. */
    private fun undoLastDelete() {
        val ic = conn ?: return
        if (!canUndo()) return
        val text = undoText
        val word = undoWord
        val restoreRank = undoRestoreRank
        clearUndo()
        feedback()
        ic.commitText(text, 1)
        afterSelfEdit(text.length)
        if (restoreRank) {
            // The delete un-counted the word and demoted it. Taking the delete
            // back has to take that verdict back too, or an undo still teaches
            // the ranking that the word was wrong.
            val latin = Script.toLatin(context, word)
            Usage.record(context, latin)
            Popularity.record(context, latin)
            rejectedStrikes.remove(latin)
        }
        // It is a committed word again, exactly as it was before the delete.
        lastSwipeWord = word
        swipeJustCommitted = true
        swipeCommitAtMs = SystemClock.uptimeMillis()
        // Smart space owes the space forward only if the restored run does not
        // already end in one (classic mode, and held-backspace, take it along).
        pendingSpace = smartSpace && !text.last().isWhitespace()
        currentWord = ""
        clearSlots()
    }

    private fun hasSelection(): Boolean {
        if (selEnd != selStart) return true
        // Belt and braces: some editors report the span, some answer this, and
        // the ones that do neither are why the first attempt at this failed.
        val ic = conn ?: return false
        return !ic.getSelectedText(0).isNullOrEmpty()
    }

    /** Replace the selection with nothing - the correct way to delete one. */
    private fun deleteSelection() {
        val ic = conn ?: return
        val at = selStart
        ic.commitText("", 1)
        afterSelfEditTo(at)
        swipeJustCommitted = false
        lastSwipeWord = ""
        currentWord = ""
        pendingSpace = false
        clearUndo()
        refreshSuggestions()
    }

    private fun backspace() {
        glagWord = null
        feedback()
        val ic = conn ?: return
        // A selection means "delete THIS", and nothing else - not the word
        // before it, and certainly not the glide committed earlier.
        if (hasSelection()) { deleteSelection(); return }
        // A backspace immediately after a glide means "that wasn't the word I
        // wanted" — wipe the WHOLE word in one press so the user can re-swipe,
        // instead of tapping backspace letter by letter.
        //
        // No time limit (owner, 2026-10-08: "nobody can be made to wait"):
        // the first press after a glide always takes the word, however long
        // the eye took to read it. Fixing only the ENDING is a hold instead -
        // see backspaceKey: the word comes back one letter short and trims.
        // Any other key or a cursor jump still closes the glide state.
        if (swipeJustCommitted && lastSwipeWord.isNotEmpty()) {
            // Classic mode put a space after the word; wipe that with it.
            val n = lastSwipeWord.length + if (smartSpace) 0 else 1
            // Read it before it is gone: undo restores the exact characters.
            val removed = ic.getTextBeforeCursor(n, 0)?.toString().orEmpty()
            ic.deleteSurroundingText(n, 0)
            afterSelfEdit(-n)
            lastWipeRemoved = removed
            pushUndo(removed, arm = true, restoreRank = true)
            // Deleting it whole is a verdict on the guess, so take back the
            // count the commit just added - otherwise being wrong trains the
            // ranking exactly as hard as being right.
            Usage.unrecord(context, Script.toLatin(context, lastSwipeWord))
            Popularity.unrecord(context, Script.toLatin(context, lastSwipeWord))
            liftSample = null   // a rejected guess measures nothing about the thumb
            val rejected = Script.toLatin(context, lastSwipeWord)
            GestureLog.resolve("deleted", null)
            rejectedStrikes[rejected] = (rejectedStrikes[rejected] ?: 0) + 1
            while (rejectedStrikes.size > REJECT_MEMORY) {
                rejectedStrikes.remove(rejectedStrikes.keys.first())
            }
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
        // An ordinary one-character delete. It also closes the swipe state: an
        // owed space belongs to the word as it was committed, and once a letter
        // has been taken off the end it would be paid in the wrong place.
        swipeJustCommitted = false
        lastSwipeWord = ""
        pendingSpace = false
        // Feed the undo buffer without arming the chip: a single character is
        // not "I deleted too much", but it IS part of the run a held backspace
        // is about to finish eating.
        pushUndo(ic.getTextBeforeCursor(1, 0)?.toString().orEmpty(), arm = false)
        ic.deleteSurroundingText(1, 0)
        afterSelfEdit(-1)
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
    private fun deleteWordBackward(): Boolean {
        glagWord = null
        val ic = conn ?: return false
        // Held backspace over a selection was the dangerous one: each repeat
        // deleted another word BEFORE the untouched selection, walking backwards
        // through the text the user meant to keep.
        if (hasSelection()) { deleteSelection(); return false }
        val before = ic.getTextBeforeCursor(400, 0)?.toString().orEmpty()
        if (before.isEmpty()) return false

        // A step stops at the start of a line; the next one takes the line break
        // and a hold that goes on carries on into the line above (owner
        // 2026-11-11). This reverses the earlier rule that a
        // held backspace never crossed a line break; the pause at each break is
        // what is left of it - the word pace (200 ms floor) is the guard now.
        val floor = before.lastIndexOf('\n') + 1

        var i = before.length
        while (i > floor && before[i - 1].isWhitespace()) i--
        if (i > floor) {
            // `wordChar`: a Ukrainian `п'ять` goes as one word, not two and a mark.
            val lang = Lang.current(context)
            fun inWord(c: Char) = wordChar(c, lang) || c.isDigit()
            val lettersFirst = inWord(before[i - 1])
            while (i > floor && !before[i - 1].isWhitespace() &&
                   inWord(before[i - 1]) == lettersFirst) i--
        }
        val del = before.length - i
        if (del > 0) {
            ic.deleteSurroundingText(del, 0)
            afterSelfEdit(-del)
            // A whole word going in one press is only safe while it is one
            // press back — offer it as the first chip. pushUndo disarms runs
            // with no letters in them (a stray quote is not a word to restore).
            pushUndo(before.substring(i), arm = true)
            swipeJustCommitted = false
            lastSwipeWord = ""
            pendingSpace = false
            refreshSuggestions()
            return i > 0
        }
        // Cursor sits right after the line break: take the break, then go on above.
        if (floor > 0) {
            clearUndo()
            ic.deleteSurroundingText(1, 0)
            afterSelfEdit(-1)
            refreshSuggestions()
        }
        return floor > 1
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
        strip.setBackgroundColor(cStrip)
        repeat(SLOTS) { i ->
            val tv = TextView(context)
            tv.gravity = Gravity.CENTER
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            tv.setTextColor(cText)
            // Fixed width, not weight: weights collapse inside a scroll view,
            // and a stable width keeps the chips from jumping as words change.
            // Long words (`søtningsstoff`, `najpopularniejszych`) wrapped onto a
            // second line, cut mid-word (isc 0.1.3). One line, shrinking to fit.
            tv.maxLines = 1
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                tv.setAutoSizeTextTypeUniformWithConfiguration(10, 17, 1, TypedValue.COMPLEX_UNIT_SP)
            } else {
                tv.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
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
        scroller.setBackgroundColor(cStrip)
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
     * Recompute the strip: predictions in the left slots, and — when the typed
     * word is a MISS — an explicit "＋ save" chip on the right that adds it to
     * the user's dictionary (and to the collection queue when that is on).
     * Saving is a deliberate tap, never silent.
     */
    private fun refreshSuggestions() {
        val before = conn
            ?.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val word = trailingWord(before)
        currentWord = word
        clearSlots()
        // The undo chip takes the front slot: a word that has just vanished is
        // more urgent than a prediction for a word not yet finished.
        val first = if (canUndo()) { setUndoSlot(0); 1 } else 0
        if (word.isEmpty()) {
            // Right after the space that finished an unknown word, offer it once
            // more - that is when the eye notices nothing was suggested (as on
            // iOS, refreshCompletions). Without this the chip vanished with the space.
            if (before.endsWith(" ")) {
                val finished = trailingWord(before.dropLast(1))
                val ahead = before.dropLast(1 + finished.length)
                saveForm(finished, ahead)?.let {
                    setSlot(first, "＋ ${Script.out(context, it)}", it, save = true)
                }
            }
            return
        }

        // `word` is what is on screen (any script); the dictionary is Latin.
        val lower = Script.toLatin(context, word).lowercase()
        // Offered whenever the word is unknown, not only with collection on: the
        // chip now saves into the user's own dictionary first (UserWords), and
        // collection stays opt-in inside Collector.record.
        val saved = saveForm(word, before.dropLast(word.length))
        val canSave = saved != null

        val preds = Dictionary.suggest(lower, if (canSave) 2 else 3) { w ->
            Usage.count(context, w) * USAGE_W_SUGGEST
        }
        // Follow the case of what is ALREADY typed. The dictionary is lowercase,
        // so a word begun with a deliberate capital was offered back in lower
        // case and the capital was lost on the tap - shift is one-shot and long
        // spent by then, which is why reading it here would not help either.
        // What the user typed is the reliable signal, and it survives.
        val capital = word.first().isUpperCase()
        // The `＋ word` chip goes FIRST, where it is seen. It used to sit in the
        // last of six slots, off the right edge of the screen, so it could only be
        // reached by scrolling the strip - which looked like no adding at all
        // (owner 2026-10-15).
        val off = if (saved != null) {
            setSlot(first, "＋ ${Script.out(context, saved)}", saved, save = true); first + 1
        } else first
        for (i in preds.indices) {
            val w = if (capital) preds[i].replaceFirstChar { it.uppercaseChar() } else preds[i]
            setSlot(off + i, Script.out(context, w), w, save = false)
        }
    }

    /**
     * What `＋` would save for [word], or null if it must not be offered.
     * [ahead] is the text in front of the word.
     *
     * Lowercase-initial words go in as they are. A capital is kept out, because
     * a capital mid-sentence is a NAME and names do not belong in a shared
     * wordlist - with one exception the owner hit on 2026-10-25 (iOS 0.4.0):
     * at the START of a sentence the capital was put there by shift, not by the
     * writer, so `Neznaneslovo` is the same word as `neznaneslovo` and used to
     * get no chip at all. There it is offered, and saved LOWERCASE.
     * A word with further capitals inside (`SŠA`, `PiS`) stays out either way.
     */
    private fun saveForm(word: String, ahead: String): String? {
        if (word.length < 3 || !Dictionary.isReady()) return null
        val latin = Script.toLatin(context, word)
        val lower = latin.lowercase()
        if (Dictionary.contains(lower)) return null
        val first = latin.first()
        if (!first.isUpperCase()) return lower
        if (latin.drop(1).any { it.isUpperCase() }) return null
        return if (sentenceStart(ahead)) lower else null
    }

    /** True when [before] (the text ahead of a word) ends a sentence: nothing at
     *  all, a new line, or `. ? ! …` and then spaces (iOS `sentenceStart`). */
    private fun sentenceStart(before: String): Boolean {
        val rest = before.trimEnd(' ')
        val last = rest.lastOrNull() ?: return true
        return last == '\n' || last in ".?!…"
    }

    /** The "put it back" chip: `↶ word`, amber, always the leftmost slot. */
    private fun setUndoSlot(i: Int) {
        slotWord[i] = undoWord
        slotIsSave[i] = false
        slotIsSwipeAlt[i] = false
        slotIsUndo[i] = true
        val tv = suggestionViews[i]
        // The ellipsis is not decoration: a held backspace can have eaten more
        // than the word named here, and the chip puts ALL of it back.
        tv.text = if (undoMore) "↶ $undoWord …" else "↶ $undoWord"
        tv.setTextColor(cUndoText)
        tv.setBackgroundColor(cUndoBg)
    }

    private fun setSlot(i: Int, display: String, word: String, save: Boolean) {
        slotWord[i] = word
        slotIsSave[i] = save
        slotIsSwipeAlt[i] = false
        slotIsUndo[i] = false
        val tv = suggestionViews[i]
        tv.text = display
        tv.setTextColor(if (save) cSaveText else cText)
        tv.setBackgroundColor(if (save) cSaveBg else Color.TRANSPARENT)
    }

    private fun clearSlots() {
        for (i in suggestionViews.indices) {
            slotWord[i] = null
            slotIsSave[i] = false
            slotIsSwipeAlt[i] = false
            slotIsUndo[i] = false
            suggestionViews[i].text = ""
            suggestionViews[i].setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun onSlotTap(i: Int) {
        val w = slotWord[i] ?: return
        // Taking another candidate from the strip is not a lost sample - it is a
        // LABELLED one: the gesture is the same and now the true word is known.
        // Dropping it was the deadlock behind "swipe nie łapie": while the offset
        // is wrong nothing is ever kept, so nothing could ever teach [Lift]
        // (owner 2026-11-01). Undo and completion still teach nothing, and `＋`
        // changes no text so it leaves the sample alone.
        if (slotIsSwipeAlt[i]) {
            liftSampleWord = Script.toLatin(context, w).lowercase()
            learnLift()
        } else if (!slotIsSave[i]) liftSample = null
        when {
            slotIsUndo[i] -> undoLastDelete()
            slotIsSave[i] -> {
                Collector.record(context, w)       // before UserWords: it only queues a MISS
                UserWords.add(context, w)
                suggestionViews[i].text = "✓"          // language-neutral: no PL/MS wording needed
                flickHandler.postDelayed({ refreshSuggestions() }, 700)
            }
            slotIsSwipeAlt[i] -> replaceLastSwipeWord(w)
            else -> applySuggestion(w)
        }
    }

    /** Replace the word under the cursor with the tapped suggestion. */
    private fun applySuggestion(word: String) {
        val ic = conn ?: return
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
        glagWord = null
        val shownWord = outWord(word)
        val out = if (smartSpace) "$lead$shownWord" else "$lead$shownWord "
        ic.commitText(out, 1)
        ic.endBatchEdit()
        afterSelfEdit(out.length - if (replacing) currentWord.length else 0)
        clearUndo()
        Usage.record(context, word)
        Popularity.record(context, word)
        currentWord = ""
        // Smart space owes it forward; classic already wrote it.
        pendingSpace = smartSpace
        clearSlots()
    }

    private fun enter() {
        feedback()
        val ic = conn ?: return
        if (Script.rewritesWord(context)) finishGlagWord(ic) else glagWord = null
        swipeJustCommitted = false
        pendingSpace = false
        lastSwipeWord = ""
        clearUndo()
        // What Enter does is the editor's business — a newline, or sending the
        // message. Either way the position it leaves is not ours to predict, so
        // let the next report resync rather than guess and be wrong.
        expectedCursors.clear()
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
        // No glide on a page without letters - and the emoji panel SCROLLS, so
        // the parent must keep its hands off the drag entirely.
        if (symbols || emoji || popupItems != null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A glide may not START on the bottom row (iOS swipeCanStart): a
                // thumb sliding on space or on the held `.` was read as a word.
                if (e.y > lettersBottom() + dp(2)) { swipeStartKey = null; return false }
                // Nor from ABOVE the letters: dragging the suggestion strip to reach
                // a chip was read as a glide - words flickered in the strip and `ore`
                // was committed while scrolling to `＋ farmacevt` (owner 2026-10-15).
                // The strip's own scroll listener cannot prevent this: the chips
                // consume the touch, so the listener never sees ACTION_DOWN.
                if (e.y < lettersTop() - dp(2)) { swipeStartKey = null; return false }
                // Mid-word hand typing: a touch landing right after a typed letter is
                // the next tap, even if the finger rolls. What is typed by hand stays
                // as typed - nothing gets decoded over it.
                if (SystemClock.uptimeMillis() - lastLetterTapMs < TAP_RUN_MS) {
                    swipeStartKey = null; return false
                }
                swipePointerId = e.getPointerId(0)
                // Fall back to the NEAREST key when the press lands in a margin or
                // just off a key (common on edge keys like `a`); without this the
                // whole glide was dead because the start key was null.
                swipeShift = shift
                swipeCaps = capsLocked
                swipeStartKey = keyAt(e.x, e.y) ?: nearestCenterKey(e.x, e.y, computeCenters())
                swipeDownX = e.x
                swipeDownY = e.y
                swiping = false
                swipePath.setLength(0)
                trailPts.clear()
                swipeT0 = e.eventTime
                addTrailPoint(e.x, e.y, 0f)
            }
            // A second finger before any glide began = two-thumb typing, never a
            // glide: give this whole touch sequence to the keys.
            MotionEvent.ACTION_POINTER_DOWN -> { swipeStartKey = null; return false }
            MotionEvent.ACTION_MOVE -> {
                val start = swipeStartKey ?: return false
                if (e.pointerCount > 1) { swipeStartKey = null; return false }
                val pi = e.findPointerIndex(swipePointerId)
                if (pi < 0) { swipeStartKey = null; return false }
                val x = e.getX(pi)
                val y = e.getY(pi)
                addTrailPoint(x, y, (e.eventTime - swipeT0).toFloat())
                val dx = x - swipeDownX
                val dy = y - swipeDownY
                val dist = sqrt(dx * dx + dy * dy)
                val kNow = keyAt(x, y)
                val crossed = kNow != null && kNow != start
                // Eager: fire on enough movement OR the moment another key is
                // entered — whichever comes first.
                if (dist < touchSlop && !crossed) return false
                val hasAccent = Layout.longPress(start, false, Lang.current(context)) != null
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
                // Only the glide's own finger draws; another one touching down
                // mid-glide must not teleport the trail.
                val pi = e.findPointerIndex(swipePointerId)
                if (pi < 0) return true
                addTrailPoint(e.getX(pi), e.getY(pi), (e.eventTime - swipeT0).toFloat())
                val k = keyAt(e.getX(pi), e.getY(pi))
                if (k != null && (swipePath.isEmpty() || swipePath.last() != k)) {
                    swipePath.append(k)
                }
                invalidate()
                // Live preview: show the word forming so far, throttled so the
                // per-move decode never janks the glide.
                val now = SystemClock.uptimeMillis()
                if (now - lastPreviewMs > 80 && trailPts.size >= 12) {
                    lastPreviewMs = now
                    previewSwipe(ArrayList(trailPts))
                }
            }
            MotionEvent.ACTION_UP -> {
                val pts = ArrayList(trailPts)          // snapshot BEFORE clearing
                val start = swipeStartKey
                swiping = false
                swipeStartKey = null
                trailPts.clear()
                invalidate()
                // The glide fires the moment the finger enters a neighbouring key,
                // so a tap that rolls a few pixels over a key edge became a "glide"
                // and the decoder committed a whole unrelated word (`opreděl`,
                // `prošlo`, ` ili `) in place of one letter. A real glide travels;
                // a tap does not. Below a fraction of a key width it was a tap:
                // type the key it started on, exactly as the child would have.
                if (start != null && trailLength(pts) < keyWidth(computeCenters()) * MIN_GLIDE_KEYS) {
                    val up = swipeShift || swipeCaps
                    // EXACTLY what the key itself writes, which is not the same
                    // as its Latin character: outside Latin the label and the
                    // committed text go through `Layout.keyText` (the digraph
                    // keys `q` `w` `x` carry `lj` `nj` `dž`) and `Script.out`.
                    // Committing `start` raw typed a LATIN letter whenever a tap
                    // drifted enough to reach this branch - on the Serbian
                    // Cyrillic keyboard the `џ` key put an `x` in the text
                    // (owner 2026-11-10), and the same hole swallowed every
                    // other letter of both non-Latin scripts.
                    val base = Layout.keyText(start, Script.mode(context), Lang.current(context))
                    val cased = if (up) base.replaceFirstChar { it.uppercaseChar() } else base
                    commit(Script.out(context, cased), cased)
                } else if (pts.size >= 9) {
                    decodeSwipe(pts)                         // >= 3 points = a real glide
                } else {
                    // It travelled far enough to be a glide but left too few samples
                    // to decode: still a gesture that produced nothing.
                    glideMissed()
                }
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

    /** Upper edge of the letter rows, in KeyboardView coordinates. */
    private fun lettersTop(): Float {
        var t = Float.MAX_VALUE
        for ((_, v) in letterKeyViews) {
            val row = v.parent as? android.view.View ?: continue
            t = minOf(t, (row.top + v.top).toFloat())
        }
        return if (t == Float.MAX_VALUE) 0f else t
    }

    /** Lower edge of the letter rows, in KeyboardView coordinates. */
    private fun lettersBottom(): Float {
        var b = 0f
        for ((_, v) in letterKeyViews) {
            val row = v.parent as? android.view.View ?: continue
            b = maxOf(b, (row.top + v.top + v.height).toFloat())
        }
        return if (b == 0f) Float.MAX_VALUE else b
    }

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

    /**
     * The route-shape decode, returning the top candidates WITHOUT committing —
     * shared by the live preview (during the glide) and the final commit (on
     * lift). Resample the finger trajectory and compare it, point-for-point, to
     * each candidate word's ideal route; first key + length cap come from the
     * whole trail so fast swipes are not truncated.
     */
    private fun decodeCandidates(rawPts: List<Float>, centers: Map<Char, FloatArray>): List<String> =
        decodeScored(rawPts, centers).map { it.first }

    /** The candidates with their scores (px of path deviation + frequency,
     *  higher is better), best first. */
    private fun decodeScored(rawPts: List<Float>, centers: Map<Char, FloatArray>): List<Pair<String, Double>> {
        // TOUCH LIFT: push the whole trail back DOWN before anything reads it, so
        // the anchor keys and the shape are judged where the thumb AIMED, not
        // where the glass reported it ([Lift]; iOS SwipeDecoder has done this
        // since 0.1.4). Without it a thumb landing a third of a key high takes
        // the word's real first and last letters out of `starts`/`ends`, the
        // word is never looked at, and the glide commits nothing at all.
        val lift = keyWidth(centers) * touchLift
        val pointList = ArrayList<FloatArray>(rawPts.size / 3)
        var i = 0
        while (i + 2 < rawPts.size) {
            pointList.add(floatArrayOf(rawPts[i], rawPts[i + 1] + lift, rawPts[i + 2])); i += 3
        }
        // Shape is compared on ARC LENGTH - even spacing in distance, so the
        // comparison says only "did the finger go this way".
        //
        // Dwell used to be smuggled into that comparison by resampling the trail
        // on INDEX (time) instead, so a letter the finger lingered on
        // contributed more points. It cost more than it bought: the trail's dwell
        // is wildly uneven while the ideal route's was uniform by construction,
        // so the two curves drifted out of step and the shape distance became
        // noise. Dwell is now read explicitly, once, as velocity minima.
        val rpTrail = resamplePath(pointList, SWIPE_RESAMPLE) ?: return emptyList()
        val rpCoarse = resamplePath(pointList, COARSE_RESAMPLE) ?: return emptyList()
        val pivots = velocityPivots(pointList)
        val cap = keyWidth(centers) * PIVOT_CAP

        // Cheap: shape only, eight points, no pivots. Decides who gets looked at.
        val coarse: (String) -> Double = fold@{ folded ->
            val ideal = idealRoute(folded, centers) ?: return@fold Double.NEGATIVE_INFINITY
            val idealRp = resamplePath(ideal, COARSE_RESAMPLE) ?: return@fold Double.NEGATIVE_INFINITY
            -shapeDist(rpCoarse, idealRp)
        }
        // Full resolution plus the velocity-minima term, on the shortlist only.
        val fine: (String) -> Double = fold@{ folded ->
            val ideal = idealRoute(folded, centers) ?: return@fold Double.NEGATIVE_INFINITY
            val idealRp = resamplePath(ideal, SWIPE_RESAMPLE) ?: return@fold Double.NEGATIVE_INFINITY
            -shapeDist(rpTrail, idealRp) - pivotPenalty(pivots, folded, centers, cap)
        }

        val ks = keyPathFromTrail(pointList, centers)
        val spd = speedCurve(pointList)
        val starts = nearestKeys(anchorPoint(pointList, false, spd), centers, ANCHOR_KEYS)
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
        // The end anchor reads the SLOWEST sample near the end (3.6.1), which is
        // the right call when the thumb settles on the last letter - but when it
        // slowed on the TURN before it, the real lift point drops out of the set
        // and the word that ends there cannot even enter the race. That is how a
        // glide for `myslim` came back `myslimo` with `myslim` nowhere in the
        // strip (owner 2026-11-07). So the key under the actual last point is
        // always in the set as well; it costs one lookup and one more candidate
        // bucket.
        val ends = (nearestKeys(anchorPoint(pointList, true, spd), centers, ANCHOR_KEYS) +
            nearestKeys(pointList.last(), centers, 1)).toSet()
        // One key further out than [ends], for the soft end gate (see Dictionary.decodeSwipeGeo).
        val softEnds = nearestKeys(pointList.last(), centers, 6, END_SOFT_REACH).toSet() - ends
        // Polish Glagolitic (iOS 0.6.28/0.6.29, owner 2026-11-14): v carried Ⰲ
        // in layouts v02-v03, and the owner keeps v catching w now that w is
        // back on w - so a `w` of the word may run over the w key OR the v key.
        // The second route is built only when the finger came near v (0.6 of a
        // key width), and a glide starting or ending on v also looks at words
        // filed under w.
        val vc = centers['v']
        val vReach = keyWidth(centers) * 0.6f
        val wOverV = polishGlag && vc != null && pointList.any {
            val dx = it[0] - vc[0]; val dy = it[1] - vc[1]; dx * dx + dy * dy < vReach * vReach
        }
        val starts2 = if (wOverV && 'v' in starts) starts + 'w' else starts
        val ends2 = if (wOverV && 'v' in ends) ends + 'w' else ends
        val scores = ArrayList<Double>()
        val words = Dictionary.decodeSwipeGeo(
            starts2, ends2, ks.size + 2, SWIPE_CANDIDATES, coarse, fine,
            { w -> Usage.count(context, w) * USAGE_W_SWIPE - (rejectedStrikes[w] ?: 0) * REJECT_W },
            softEnds - ends2, END_SOFT, wOverV, scores,
        )
        return words.mapIndexed { i, w -> w to (scores.getOrNull(i) ?: Double.NEGATIVE_INFINITY) }
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
            // Cased the way it will actually be inserted. Showing the decoder's
            // raw lowercase here is a lie about the outcome, and it is the half
            // of the bug the user sees first - the strip contradicts the shift
            // they just pressed, mid-gesture, before anything is committed.
            val w = words.getOrNull(i)?.let { caseSwipe(it, swipeShift, swipeCaps) }
            suggestionViews[i].text = w?.let { Script.out(context, it) }.orEmpty()
            suggestionViews[i].setTextColor(cText)
            suggestionViews[i].setBackgroundColor(
                if (i == 0 && w != null) cChipOn else Color.TRANSPARENT
            )
        }
    }

    private fun decodeSwipe(rawPts: List<Float>) {
        // Decode OFF the UI thread so lifting the finger never freezes the
        // keyboard (the dwell decode is heavier); the commit is posted back.
        val centers = computeCenters()
        decodeHandler.removeCallbacksAndMessages(null)   // drop any pending preview
        val liftedAt = SystemClock.uptimeMillis()
        decodeHandler.post {
            val scored = decodeScored(rawPts, centers)
            val words = scored.map { it.first }
            // From the finger leaving the glass to a decoded list: what the user
            // waits through (queue + decode). Logged with the gesture so "it
            // grinds in Polish" becomes a number per language (owner 2026-11-12).
            val decodeMs = (SystemClock.uptimeMillis() - liftedAt).toInt()
            post { commitSwipeResult(words, rawPts, centers, decodeMs, scored.map { it.second }) }
        }
    }

    /** The one place that decides how a glided word is cased. Every surface the
     *  user sees - the strip during the gesture, the committed word, the list of
     *  alternatives, the replacement - has to answer this identically, or the
     *  keyboard contradicts itself in front of the user. */
    private fun caseSwipe(word: String, cap: Boolean, lock: Boolean): String = when {
        lock -> word.uppercase()
        cap -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    /**
     * A glide that produced NOTHING still ends the glide before it.
     *
     * The empty-decode branch used to return early, which left every piece of
     * glide state armed from the word committed EARLIER: the next backspace
     * wiped that word whole, the strip's `↶` chip and the old alternatives
     * still offered a word the user had already moved past, and a letter typed
     * inside the window could extend it. From the user's side the keyboard
     * pushed back a word from before the gesture (owner 2026-11-09: "po
     * nieznalezionym swipie jak sie wpisuje jeszcze raz to wpierdala sie wyraz
     * z przed"). Nothing is deleted here - the previous word stays exactly as
     * it was typed; it simply stops being "the word just glided".
     *
     * The owed space is NOT cleared: it belongs to the word that is standing in
     * the text, and the next thing typed still has to be separated from it.
     */
    private fun glideMissed() {
        // That earlier glide survived into a whole new gesture, so the user
        // kept it: it is a real measurement of this thumb, and the log entry
        // waiting on it is a `kept` one.
        if (swipeJustCommitted) learnLift()
        GestureLog.resolve("kept", null)
        swipeJustCommitted = false
        lastSwipeWord = ""
        lastSwipeCandidates = emptyList()
        liftSample = null
        currentWord = ""
        clearUndo()
        // Deliberately NOT refreshSuggestions(): the cursor sits after the
        // previous word, so predictions here would be continuations of a word
        // the user is not typing - and tapping one would replace it. An empty
        // strip is the honest answer to "nothing was decoded".
        clearSlots()
    }

    /**
     * What the strip shows after a glide (iOS 0.6.11 `SwipeInput.strip`, owner
     * 2026-11-13: `ide` -> `otče`, `osě` were shapes the finger never drew):
     * the winner; the shorter form that begins it (Android 2026-11-07,
     * `myslim` behind `myslimo`); alternatives within [STRIP_MARGIN] key widths
     * of the winner; longer forms of the winner, most frequent first (`ide` ->
     * `idemo`, `idete`); then farther shapes only if they are COMMON words.
     *
     * The margin is measured, not copied: the Android score is px of path
     * deviation plus frequency, and the harness (`tools/swipe_eval.py` scoring,
     * Android 3.9.13 settings, 300 most frequent forms x 2 seeds, key 108 px)
     * gives at 0.25 key widths: the right word, when it came 2nd/3rd, still
     * shown isv 100 %, pl 98.2 %, en 96.4 %, and 53-67 % of the other
     * alternatives gone - the same as the iOS figure. At 0.5 the right word
     * stays 100 % everywhere but only 15-27 % of the noise goes.
     */
    private fun swipeStrip(words: List<String>, scoreOf: Map<String, Double>, keyW: Float): List<String> {
        if (words.isEmpty()) return emptyList()
        // Without scores (or key geometry) nothing is filtered: the old strip.
        if (scoreOf.isEmpty() || keyW <= 0f) {
            val alts = words.toMutableList()
            Dictionary.bestPrefix(words[0])?.let { short ->
                if (short !in alts) alts.add(minOf(1, alts.size), short)
            }
            return alts
        }
        val best = words[0]
        val top = scoreOf[best] ?: return words
        val margin = keyW * STRIP_MARGIN
        val out = mutableListOf(best)
        fun add(w: String) {
            if (out.size < SWIPE_CANDIDATES && out.none { it.equals(w, ignoreCase = true) }) out.add(w)
        }
        Dictionary.bestPrefix(best)?.let { add(it) }
        val rest = words.drop(1)
        for (w in rest) if (top - (scoreOf[w] ?: Double.NEGATIVE_INFINITY) <= margin) add(w)
        val lower = best.lowercase()
        for (w in Dictionary.suggest(lower, SWIPE_CANDIDATES * 2)) {
            if (w.length > best.length && w.lowercase().startsWith(lower) &&
                !(Lang.current(context) == Lang.ISV && Dictionary.addsPastEnding(lower, w.lowercase()))) add(w)
        }
        for (w in rest) {
            if (top - (scoreOf[w] ?: Double.NEGATIVE_INFINITY) > margin &&
                Dictionary.frequency(w.lowercase()) >= STRIP_FAR_FREQ) add(w)
        }
        return out
    }

    private fun commitSwipeResult(
        words: List<String>,
        rawPts: List<Float> = emptyList(),
        centers: Map<Char, FloatArray> = emptyMap(),
        decodeMs: Int = -1,
        scores: List<Double> = emptyList(),
    ) {
        if (words.isEmpty()) { glideMissed(); return }
        val ic = conn ?: return
        // The aorist and the imperfect go to the BACK of the queue, committed
        // only when nothing else was decoded (owner 2026-11-08: a glide for
        // `myslimo` kept coming back `myslihmo` - the extra `h` sits right on
        // the way from `i` to `m`, so it costs the shape score almost nothing).
        // Stable, so everything else keeps the decoder's own order.
        val scoreOf = HashMap<String, Double>()
        for (i in words.indices) scores.getOrNull(i)?.let { scoreOf[words[i]] = it }
        @Suppress("NAME_SHADOWING")
        val words = words.sortedBy { if (Dictionary.isArchaicPast(it)) 1 else 0 }
        lastSwipeCapitalized = swipeShift
        lastSwipeLocked = swipeCaps
        val best = caseSwipe(words[0], swipeShift, swipeCaps)
        // Smart space: a glide is a fresh word, so put the space BEFORE it (unless
        // at field start or already after a space), and none after it.
        val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val lead = if (pendingSpace || (prev.isNotEmpty() && !prev[0].isWhitespace())) " " else ""
        glagWord = null
        val shownBest = outWord(best)
        val written = if (smartSpace) "$lead$shownBest" else "$lead$shownBest "
        ic.commitText(written, 1)
        afterSelfEdit(written.length)
        clearUndo()
        Usage.record(context, best)
        Popularity.record(context, best)
        lastSwipeWord = shownBest          // on-screen form: its length is what gets deleted
        // A glide cannot tell "I stopped here" from "I carried on": the path for
        // `myslim` IS the path for `myslimo` up to its last letter. So whenever
        // the winner has a shorter real word as its beginning, that word gets a
        // slot right behind it - the owner asked for exactly this ("przynajmniej
        // podac te krotka podstawowa forme, ktora zostala zrobiona gestem",
        // 2026-11-07). It is an OFFER, never the committed word: the decoder
        // still decides what gets typed.
        val alts = swipeStrip(words, scoreOf, if (centers.isEmpty()) 0f else keyWidth(centers))
        // The strip shows the filtered list; a REJECTED glide (backspace) falls
        // back on the whole pool, the shapes the filter hid included.
        lastSwipeCandidates = alts + words.filter { it !in alts }
        rememberLiftSample(rawPts, centers, words[0])
        swipeJustCommitted = true
        swipeCommitAtMs = SystemClock.uptimeMillis()
        pendingSpace = smartSpace
        currentWord = ""
        showSwipeAlts(alts, highlight = 0)    // a wrong guess is one tap from fixed
        GestureLog.committed(rawPts, centers, words, best, decodeMs, Lang.current(context),
            Script.mode(context).name.lowercase())
        consumeOneShotShift()                 // one-shot capital ends with this word too
    }

    /**
     * Keep the RAW (not lift-corrected) ends of the gesture that produced
     * [word], so that if the word survives, [learnLift] can read this thumb's
     * offset off it. Stored as `x0, y0, x1, y1, keyWidth`.
     */
    private fun rememberLiftSample(
        rawPts: List<Float>,
        centers: Map<Char, FloatArray>,
        word: String,
    ) {
        val kw = keyWidth(centers)
        if (rawPts.size < 6 || kw <= 0f) { liftSample = null; return }
        liftSample = floatArrayOf(
            rawPts[0], rawPts[1],
            rawPts[rawPts.size - 3], rawPts[rawPts.size - 2],
            kw,
        )
        liftSampleWord = word
    }

    /**
     * One kept glide teaches [Lift] where this thumb really lands: compare the
     * path's first and last point with the CENTRES of the word's first and last
     * key. A positive reading means the touches sat above the keys.
     *
     * One word only nudges the value (and a wild reading is dropped), so a
     * single sloppy glide cannot drag the keyboard around - but a few dozen
     * words of real typing move it to this phone and this thumb.
     */
    private fun learnLift() {
        val s = liftSample ?: return
        liftSample = null
        val word = liftSampleWord
        if (word.length < 2 || s[4] <= 0f) return
        val centers = computeCenters()
        // Outside Latin a word with `lj`/`nj` has TWO routes - over the digraph
        // key (`q`, `w`) or over the two letters - and the decoder accepts
        // either. Measure against the one the thumb actually drew: take, for
        // each end, whichever of the two keys lies nearer the point the finger
        // really touched. `ljudi` drawn from `l` but measured against `q` a row
        // higher teaches half a row of offset that was never there, and that
        // error goes into a value the whole keyboard then uses.
        val first = centers[nearerKey(
            Dictionary.foldFirst(word), Dictionary.foldBase(word.first()),
            s[0], s[1], centers,
        )] ?: return
        val last = centers[nearerKey(
            Dictionary.foldLast(word), Dictionary.foldBase(word.last()),
            s[2], s[3], centers,
        )] ?: return
        val measured = ((first[1] + last[1]) - (s[1] + s[3])) / 2f / s[4]
        touchLift = Lift.learn(context, measured)
    }

    /**
     * Of two keys that could have carried this end of the glide, the one whose
     * centre lies nearer the point the finger really touched. Equal keys (every
     * word in Latin, and every word without a digraph at that end) short-circuit,
     * so this costs nothing in the common case.
     */
    private fun nearerKey(
        a: Char,
        b: Char,
        x: Float,
        y: Float,
        centers: Map<Char, FloatArray>,
    ): Char {
        if (a == b) return a
        val ca = centers[a] ?: return b
        val cb = centers[b] ?: return a
        fun d2(c: FloatArray) = (c[0] - x) * (c[0] - x) + (c[1] - y) * (c[1] - y)
        return if (d2(cb) < d2(ca)) b else a
    }

    /** Put the swipe candidates in the strip as tappable alternatives. */
    private fun showSwipeAlts(words: List<String>, highlight: Int) {
        clearSlots()
        // The decoder now returns more words than there are slots.
        // Show them cased the way they will actually be inserted - a strip of
        // lowercase words after a deliberate shift is a lie about the outcome.
        // After a rejected glide the strip carries both offers at once: put the
        // word back untouched (leftmost), or take one of the other candidates.
        val off = if (canUndo()) { setUndoSlot(0); 1 } else 0
        val shown = words.take(suggestionViews.size - off).map {
            caseSwipe(it, lastSwipeCapitalized, lastSwipeLocked)
        }
        for (i in shown.indices) {
            slotWord[i + off] = shown[i]
            slotIsSwipeAlt[i + off] = true
            suggestionViews[i + off].text = Script.out(context, shown[i])
            suggestionViews[i + off].setTextColor(cText)
            suggestionViews[i + off].setBackgroundColor(
                if (i == highlight) cChipOn else Color.TRANSPARENT
            )
        }
    }

    /** Replace the word the swipe just committed with a chosen alternative. If
     *  nothing is currently committed (e.g. after a whole-word backspace), just
     *  insert the chosen word fresh. */
    private fun replaceLastSwipeWord(word: String) {
        // Picking it deliberately overrides every earlier rejection of it.
        rejectedStrikes.remove(word)
        // This is the label the harness cannot invent: what was actually wanted.
        GestureLog.resolve("corrected", word)
        val ic = conn ?: return
        ic.beginBatchEdit()
        // With a selection live, deleting "the word we just committed" would cut
        // characters next to the selection instead. Let the insertion simply
        // replace the selection.
        var removed = 0
        if (lastSwipeWord.isNotEmpty() && !hasSelection()) {
            // Choosing another word means the committed one was wrong.
            Usage.unrecord(context, Script.toLatin(context, lastSwipeWord))
            Popularity.unrecord(context, Script.toLatin(context, lastSwipeWord))
            // Classic mode wrote a trailing space with the word, so take it too.
            removed = lastSwipeWord.length + if (smartSpace) 0 else 1
            ic.deleteSurroundingText(removed, 0)
        }
        // Honour the shift that produced the ORIGINAL word, not the (already
        // spent) shift state now.
        val out = caseSwipe(word, shift || lastSwipeCapitalized, capsLocked || lastSwipeLocked)
        val prev = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val lead = if (lastSwipeWord.isEmpty() && prev.isNotEmpty() && !prev[0].isWhitespace()) " " else ""
        glagWord = null
        val shownOut = outWord(out)
        val written = if (smartSpace) "$lead$shownOut" else "$lead$shownOut "
        ic.commitText(written, 1)   // leading space before the old word stays
        ic.endBatchEdit()
        afterSelfEdit(written.length - removed)
        clearUndo()
        Usage.record(context, out)
        Popularity.record(context, out)
        lastSwipeWord = shownOut
        swipeJustCommitted = true    // still a swipe result — backspace wipes it whole
        swipeCommitAtMs = SystemClock.uptimeMillis()
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

    /** A word's ideal route: the polyline through its letter centres. */
    private fun idealRoute(folded: String, centers: Map<Char, FloatArray>): List<FloatArray>? {
        val seq = ArrayList<FloatArray>(folded.length)
        for (c in folded) seq.add(centers[c] ?: return null)
        return if (seq.size < 2) null else seq
    }

    /** Distance the finger actually travelled, over (x, y, t) triples. */
    private fun trailLength(pts: List<Float>): Float {
        var len = 0f
        var i = 3
        while (i + 1 < pts.size) {
            val dx = pts[i] - pts[i - 3]
            val dy = pts[i + 1] - pts[i - 2]
            len += sqrt(dx * dx + dy * dy)
            i += 3
        }
        return len
    }

    /** One key width, taken from the snapshotted centres so no View is read:
     *  the PITCH of the top letter row, as the median gap between neighbouring
     *  key centres (median, so a hole in the row cannot skew it).
     *
     *  It used to read `centers['q']` and `centers['w']` by name and fall back to
     *  `dp(36)` when either was missing. From 2026-11-08 Cyrillic and Glagolitic
     *  have no `q w x`, so that fallback would have fired on every glide in those
     *  scripts: the keyboard would look and feel identical while the lift
     *  compensation and the pivot cap were scaled by a width no key has. */
    private fun keyWidth(centers: Map<Char, FloatArray>): Float {
        if (centers.isEmpty()) return dp(36).toFloat()
        val topY = centers.values.minOf { it[1] }
        val xs = centers.values.filter { it[1] - topY < 1f }.map { it[0] }.sorted()
        if (xs.size < 2) return dp(36).toFloat()
        val gaps = (1 until xs.size).map { xs[it] - xs[it - 1] }.sorted()
        return gaps[gaps.size / 2]
    }

    /**
     * Where the finger slowed enough to mean something, and how far to trust it.
     * Returns (x, y, confidence) triples.
     *
     * A glide decelerates into the letters the writer means and cruises past the
     * ones it merely crosses, so a minimum of the speed curve is a vote for
     * "a letter is here". This is the same dwell signal the index-resample used
     * to smear across the whole comparison, read out properly instead: a few
     * points that say WHERE the finger hesitated, rather than a sampling density
     * that only says "somewhere around here was slow".
     *
     * [conf] is how DEEP the minimum is relative to the surrounding speed - 1 for
     * a dead stop, 0 for a dimple in an otherwise constant glide. It is the
     * safety valve. Someone who swipes fast and flat produces shallow minima,
     * their confidence collapses, and [pivotPenalty] fades out rather than
     * inventing structure that was never in the gesture. Measured: without the
     * confidence weight a no-dwell swiper LOST three points of top-1; with it,
     * the term is a wash there and still worth four to eight points for everyone
     * who does slow down.
     *
     * First and last sample always count at full confidence - a glide starts and
     * ends at rest, and those two are the anchors the decoder already trusts.
     */
    /**
     * Smoothed speed per sample, in px/ms. Raw touch deltas are far too noisy to
     * read directly - a single jittery sample looks like a pause - so the curve
     * is averaged over [V_SMOOTH] before anyone takes a minimum of it.
     *
     * Read by TWO callers now: [velocityPivots], which wants the minima in the
     * middle of a glide, and [anchorPoint], which wants the slowest sample at
     * each END. They must not drift apart, hence one function.
     */
    private fun speedCurve(pts: List<FloatArray>): FloatArray? {
        val n = pts.size
        if (n < 2) return null
        val raw = FloatArray(n)
        for (i in 1 until n) {
            var dt = pts[i][2] - pts[i - 1][2]
            if (dt <= 0f) dt = 8f              // a batched/duplicate timestamp
            val dx = pts[i][0] - pts[i - 1][0]; val dy = pts[i][1] - pts[i - 1][1]
            raw[i] = sqrt(dx * dx + dy * dy) / dt
        }
        raw[0] = raw[1]
        val out = FloatArray(n)
        val half = V_SMOOTH / 2
        for (i in 0 until n) {
            val lo = maxOf(0, i - half); val hi = minOf(n, i + half + 1)
            var acc = 0f
            for (j in lo until hi) acc += raw[j]
            out[i] = acc / (hi - lo)
        }
        return out
    }

    /**
     * Where to read the first and last letter from. NOT the first and last
     * sample: a thumb decelerates onto the letter it means and then carries
     * past it, so those two points are the approach and the overshoot. This
     * returns the SLOWEST sample within [ANCHOR_MS] of the chosen end.
     *
     * Both anchors are hard gates - the start picks the dictionary bucket, the
     * end throws away every word that does not finish there - so one noisy
     * sample used to decide whether the right word could compete at all.
     *
     * Measured in tools/swipe_eval.py across the whole touch-offset sweep:
     * +1 to +3 points of top-1 and never below baseline in eight cells. The
     * other obvious idea, widening the anchor set to every key that came within
     * reach during the same window, measured as nothing at all - so the gate
     * was never too narrow, only aimed at the wrong sample.
     */
    private fun anchorPoint(pts: List<FloatArray>, atEnd: Boolean, v: FloatArray?): FloatArray {
        val n = pts.size
        if (v == null || n < 3) return if (atEnd) pts[n - 1] else pts[0]
        val edge = (if (atEnd) pts[n - 1] else pts[0])[2]
        // Never look back more than a THIRD of the gesture: a quick flick can
        // last under 240 ms end to end, and with a flat window each side the
        // two would OVERLAP - the start anchor picking a sample from the end of
        // the path and the end anchor one from its start. Both are hard gates,
        // so that does not blur the result, it destroys it: measured at 90 ms,
        // `se` decoded as `resě` and `pisati` at 240 ms as `porasteš`.
        val span = pts[n - 1][2] - pts[0][2]
        val reach = if (span > 0f) minOf(ANCHOR_MS, span / 3f) else 0f
        var best = if (atEnd) n - 1 else 0
        var bestV = Float.MAX_VALUE
        val order = if (atEnd) (n - 1) downTo 0 else 0 until n
        for (i in order) {
            if (abs(pts[i][2] - edge) > reach) break
            if (v[i] < bestV) { bestV = v[i]; best = i }
        }
        return pts[best]
    }

    private fun velocityPivots(pts: List<FloatArray>): List<FloatArray> {
        val n = pts.size
        if (n < 2) return emptyList()
        val first = floatArrayOf(pts[0][0], pts[0][1], 1f)
        val last = floatArrayOf(pts[n - 1][0], pts[n - 1][1], 1f)
        if (n < 5) return listOf(first, last)

        val v = speedCurve(pts) ?: return listOf(first, last)

        val idx = ArrayList<Int>()
        val conf = ArrayList<Float>()
        for (i in 1 until n - 1) {
            val lo = maxOf(0, i - V_RADIUS); val hi = minOf(n, i + V_RADIUS + 1)
            var isMin = true
            for (j in lo until hi) if (v[j] < v[i]) { isMin = false; break }
            if (!isMin) continue
            val wlo = maxOf(0, i - V_SMOOTH * 2); val whi = minOf(n, i + V_SMOOTH * 2 + 1)
            var acc = 0f
            for (j in wlo until whi) acc += v[j]
            val local = acc / (whi - wlo)
            if (local <= 0f || v[i] > V_REL * local) continue
            val c = (1f - v[i] / local).coerceIn(0f, 1f)
            // A flat slow stretch fires a cluster of near-equal minima; they all
            // mean ONE letter, so keep the slowest of each run.
            if (idx.isNotEmpty() && i - idx.last() < V_MIN_SEP) {
                if (v[i] < v[idx.last()]) { idx[idx.size - 1] = i; conf[conf.size - 1] = c }
            } else {
                idx.add(i); conf.add(c)
            }
        }

        val out = ArrayList<FloatArray>(idx.size + 2)
        out.add(first)
        for (k in idx.indices) out.add(floatArrayOf(pts[idx[k]][0], pts[idx[k]][1], conf[k]))
        out.add(last)
        return out
    }

    /**
     * How badly a candidate word and the finger's pauses disagree, in px.
     *
     * Two directions, both capped so a single outlier cannot swamp the score:
     *
     *  - every LETTER wants a pivot near it. This is what separates `možemo`
     *    from `možehmo`: the detour through `h` lies close enough to the path
     *    that the shape distance barely notices it, but nothing in the finger's
     *    speed ever suggested a letter there.
     *  - every PIVOT wants a letter. A pause the word cannot explain is evidence
     *    against the word.
     *
     * Both are weighted by confidence, and the whole term is scaled by the mean
     * confidence, so a gesture with no real minima contributes nothing.
     */
    private fun pivotPenalty(
        pivots: List<FloatArray>,
        folded: String,
        centers: Map<Char, FloatArray>,
        cap: Float,
    ): Double {
        if (pivots.isEmpty()) return 0.0
        var confSum = 0f
        for (p in pivots) confSum += p[2]
        if (confSum <= 0f) return 0.0

        var letterPen = 0.0
        var letters = 0
        for (c in folded) {
            val cc = centers[c] ?: continue
            var best = Float.MAX_VALUE
            for (p in pivots) {
                val dx = cc[0] - p[0]; val dy = cc[1] - p[1]
                val d = sqrt(dx * dx + dy * dy)
                if (d < best) best = d
            }
            letterPen += minOf(best, cap).toDouble()
            letters++
        }
        if (letters == 0) return 0.0

        var pivotPen = 0.0
        for (p in pivots) {
            var best = Float.MAX_VALUE
            for (c in folded) {
                val cc = centers[c] ?: continue
                val dx = cc[0] - p[0]; val dy = cc[1] - p[1]
                val d = sqrt(dx * dx + dy * dy)
                if (d < best) best = d
            }
            if (best < Float.MAX_VALUE) pivotPen += p[2] * minOf(best, cap)
        }

        val trust = minOf(1.0, (confSum / pivots.size).toDouble())
        return trust * (W_LETTER * (letterPen / letters) + W_PIVOT * (pivotPen / confSum))
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
        reachKeys: Float = 1.3f,
    ): List<Char> {
        if (centers.isEmpty()) return emptyList()
        // A key's own width, taken from the live layout rather than assumed.
        val w = letterKeyViews.firstOrNull()?.second?.width?.toFloat() ?: return emptyList()
        val reach = w * reachKeys
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
