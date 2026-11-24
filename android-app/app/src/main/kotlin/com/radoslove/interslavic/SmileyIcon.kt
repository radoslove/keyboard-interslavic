package com.radoslove.interslavic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The emoji key's face, drawn instead of the colour 😀 (owner 261123, iOS
 * d8538ba, spec `vault_002/Projects/INTERSLOVE/uklad/klawiatura_wyglad_261123_v02.md` §5):
 * dark theme = the outline alone, light theme = a grey disc with the outline
 * and dark features. Every measure is a fraction of [DC], the diameter of the
 * circle's centre line - the same numbers as iOS `SmileyIcon`. Set as the
 * key's foreground: it draws itself at a fixed size in the middle of
 * whatever bounds it gets.
 */
class SmileyIcon(context: Context, private val dark: Boolean, private val ink: Int) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = ink; strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun draw(canvas: Canvas) {
        val d = DC * density
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        if (!dark) {
            fill.color = DISC
            canvas.drawCircle(cx, cy, d / 2f, fill)
        }
        stroke.strokeWidth = 0.0967f * d
        canvas.drawCircle(cx, cy, d / 2f, stroke)
        fill.color = ink
        val r = 0.0643f * d
        for (side in listOf(-1f, 1f)) canvas.drawCircle(cx + side * 0.181f * d, cy - 0.155f * d, r, fill)
        // Stroked, never filled: a fill would close the chord into a crescent.
        val mr = 0.2488f * d
        val my = cy + 0.076f * d
        stroke.strokeWidth = 0.1043f * d
        canvas.drawArc(RectF(cx - mr, my - mr, cx + mr, my + mr), 90f - SWEEP / 2f, SWEEP, false, stroke)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** The circle, dp (iOS 20 pt; open question: 20 or 22). */
        const val DC = 20f
        /** The smile's sweep, symmetric about the bottom (owner 261123). */
        const val SWEEP = 160f
        private val DISC = Color.rgb(0xCF, 0xD4, 0xD8)
    }
}
