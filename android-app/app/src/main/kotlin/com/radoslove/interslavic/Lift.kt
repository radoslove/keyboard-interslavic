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
    private const val MIN = 0f
    private const val MAX = 0.6f

    /** How hard one kept word moves the value: a tenth of the gap. */
    private const val RATE = 0.1f

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
        val next = (now + RATE * (measured - now)).coerceIn(MIN, MAX)
        prefs(context).edit().putFloat(KEY, next).apply()
        return next
    }
}
