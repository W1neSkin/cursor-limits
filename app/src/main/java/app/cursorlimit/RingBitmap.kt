package app.cursorlimit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** The mint arc is the share of the week still left, drawn from 12 o'clock. */
object RingBitmap {
    fun create(
        size: Int,
        leftFraction: Float,
        label: String,
        arcColor: Int,
        trackColor: Int,
        labelColor: Int,
    ): Bitmap {
        val safe = size.coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(safe, safe, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = safe * 0.1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        val inset = stroke / 2f + 1f
        val oval = RectF(inset, inset, safe - inset, safe - inset)
        paint.color = trackColor
        canvas.drawArc(oval, 0f, 360f, false, paint)
        val fraction = leftFraction.coerceIn(0f, 1f)
        if (fraction > 0f) {
            paint.color = arcColor
            // A tiny remainder still shows a cap, so 1% is not invisible.
            val sweep = (360f * fraction).coerceAtLeast(8f)
            canvas.drawArc(oval, -90f, sweep, false, paint)
        }
        if (label.isNotEmpty()) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = safe * if (label.length > 3) 0.26f else 0.32f
            }
            val y = safe / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText(label, safe / 2f, y, text)
        }
        return bitmap
    }
}
