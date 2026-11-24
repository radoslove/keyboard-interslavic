package com.radoslove.interslavic

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build

/**
 * Noto Sans Glagolitic 2.004 inside the app (owner 261124: "wbudować font"),
 * so the keyboard draws Glagolitic and the titlo the same on every phone.
 *
 * The a55's system font is Noto Sans Glagolitic 1.04: no titlo of its own and
 * none of the titlo halves U+FE2E / U+FE2F, which the hold popup drew as
 * empty boxes (measured 261124). 2.004 has U+0483, U+FE26, U+FE2E, U+FE2F.
 *
 * File: `assets/fonts/NotoSansGlagolitic-Regular.ttf`, 40 812 B,
 * md5 d44bf6770028a2aec7e015d914f8421c, from
 * https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansGlagolitic/hinted/ttf/NotoSansGlagolitic-Regular.ttf
 * (the same file measured on `hp`, `mc` and `com`), SIL OFL 1.1 -
 * `assets/fonts/OFL_NotoSansGlagolitic.txt`.
 *
 * ⚠ It only changes what THIS app draws (keys, the hold popup, the
 * suggestion bar, the app's own screens). Text typed into another app is
 * drawn by that app with the phone's fonts.
 *
 * The font has no Latin and no U+0301 (Polish ó = ⱁ + acute): everything it
 * lacks falls back to the system sans-serif.
 */
object GlagFont {
    @Volatile private var face: Typeface? = null
    @Volatile private var failed = false

    fun get(context: Context): Typeface? {
        face?.let { return it }
        if (failed) return null
        return try {
            val am = context.applicationContext.assets
            val t = if (Build.VERSION.SDK_INT >= 29) {
                val family = FontFamily.Builder(Font.Builder(am, "fonts/NotoSansGlagolitic-Regular.ttf").build()).build()
                Typeface.CustomFallbackBuilder(family).setSystemFallback("sans-serif").build()
            } else {
                Typeface.createFromAsset(am, "fonts/NotoSansGlagolitic-Regular.ttf")
            }
            face = t
            t
        } catch (_: Throwable) {
            failed = true
            null
        }
    }

    /** The typeface to draw with now: the bundled one in Glagolitic, else the default. */
    fun forScript(context: Context): Typeface =
        (if (Script.chosen(context) == Script.Mode.GLAGOLITIC) get(context) else null) ?: Typeface.DEFAULT
}
