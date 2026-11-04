package com.radoslove.interslavic

import android.content.Context

/**
 * Where this phone's touches sit ABOVE the key they mean, in key widths.
 *
 * A thumb is not a stylus: the contact patch is the pad of the finger, the
 * digitizer reports its centroid, and the thumb arrives from below — so the
 * point the phone hands us sits consistently higher than the letter the user
 * aimed at. It costs nothing while typing (the key under the touch is still
 * the right one, the keys are tall) and everything while gliding, because the
 * decoder anchors a word on the keys NEAREST the first and last point: lift
 * the ends by a third of a key and the real first letter drops out of the
 * anchor set, the word is never even looked at, and the glide produces
 * NOTHING. That is the owner's standing complaint — "the keyboard does not
 * catch, I have to swipe two or three times" (`KOLEJKA_klawiatura_261005.md`,
 * 2026-10-14) — and the repeat works because the second attempt lands lower.
 *
 * iOS has had this since 0.1.4: 0.35 key widths, measured on 34 labelled real
 * gestures on an iPhone 13 (2026-10-07), where compensating for it alone moved
 * top-1 from 16/34 to 23/34. Android had no offset at all until 3.5.31.
 * `tools/swipe_eval.py lift` shows the mechanism on synthetic paths.
 *
 * [DEFAULT] is only the starting point. Every glide the user KEEPS is a
 * measurement of their own thumb on their own phone, so the value is nudged
 * toward what was measured ([learn]) and the keyboard converges on its owner.
 * One value per phone, not per language — it is a property of the finger.
 */
object Lift {

    /** Starting value, as measured on iOS. */
    const val DEFAULT = 0.35f

    /** Learned values stay in a band a thumb can actually produce. */
    // A thumb that lands BELOW the key it aims at is just as real as one that
    // lands above, and `a55` is one: after 195 glides the value sat on this
    // floor with the estimator still pulling down, so compensation was simply
    // switched off on the phone that needed it. Measured in swipe_eval.py: an
    // offset of -0.35 costs 6 points of top-1 while clamped and none when
    // compensated, and -0.50 costs 13. The floor stays rather than opening to
    // -MAX, because the same sweep shows compensating with the WRONG sign is
    // worth 45 % against 78 % - far worse than not compensating at all.
    private const val MIN = -0.5f
    private const val MAX = 0.6f

    /** How hard one measured word moves the value, once the value has settled. */
    private const val RATE = 0.1f

    /**
     * Until this many words have been measured the value moves by 1/(n+1) of the
     * gap, i.e. the first word counts for half and the tenth for a tenth. The old
     * flat tenth needed dozens of KEPT glides to walk away from a wrong default -
     * and a wrong default is exactly the state in which nothing gets kept
     * (owner 2026-11-01 on `a55`: the value had crawled 0.35 -> 0.276 and was
     * still over-compensating, which drags a top-row start down onto the home row:
     * `wyjść` came back as `semtex`, `wysoko` as `do`).
     */
    private const val WARMUP = 20
    private const val KEY_N = "touchLiftN"

    /** Readings outside this are noise (a glide that wandered, a mis-decode). */
    private val SANE = -0.3f..0.9f

    private const val PREFS = "isv_collector"
    private const val KEY = "touchLift"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): Float = prefs(context).getFloat(KEY, DEFAULT)

    /**
     * One kept glide: [measured] is how far above the word's first and last key
     * centres the path actually began and ended, in key widths. One sloppy word
     * must not drag the keyboard around, so a wild reading is dropped and the
     * rest only nudge. Returns the value now in force.
     */
    fun learn(context: Context, measured: Float): Float {
        val now = get(context)
        if (measured !in SANE) return now
        val p = prefs(context)
        val n = p.getInt(KEY_N, 0)
        val rate = if (n < WARMUP) maxOf(RATE, 1f / (n + 1)) else RATE
        val next = (now + rate * (measured - now)).coerceIn(MIN, MAX)
        p.edit().putFloat(KEY, next).putInt(KEY_N, n + 1).apply()
        return next
    }
}
