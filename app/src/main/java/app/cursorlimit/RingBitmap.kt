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
        sublabel: String,
        arcColor: Int,
        trackColor: Int,
        labelColor: Int,
        typeface: Typeface,
    ): Bitmap {
        // Cap the pixels so the widget update stays under the binder limit.
        val safe = size.coerceIn(32, 360)
        val bitmap = Bitmap.createBitmap(safe, safe, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = safe * 0.11f
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
            drawLabel(canvas, safe, label, sublabel, arcColor, labelColor, typeface)
        }
        return bitmap
    }

    /** Percent sits in the middle. On the large card, LEFT is a second line under it. */
    private fun drawLabel(
        canvas: Canvas,
        size: Int,
        label: String,
        sublabel: String,
        arcColor: Int,
        labelColor: Int,
        typeface: Typeface,
    ) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
            textSize = size * if (sublabel.isNotEmpty()) 0.26f else if (label.length > 3) 0.28f else 0.34f
        }
        val cx = size / 2f
        if (sublabel.isEmpty()) {
            val y = size / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText(label, cx, y, text)
            return
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = arcColor
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
            textSize = size * 0.11f
            letterSpacing = 0.14f
        }
        val block = text.textSize * 0.9f + sub.textSize
        val top = size / 2f - block / 2f
        canvas.drawText(label, cx, top - text.ascent(), text)
        canvas.drawText(sublabel, cx, top + text.textSize * 0.95f - sub.ascent(), sub)
    }
}
