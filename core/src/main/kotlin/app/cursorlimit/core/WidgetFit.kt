package app.cursorlimit.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Picks sizes for the two home-screen widgets that show a ring, a date, and a dollar amount.
 * The pieces are measured before they are drawn, so a bigger ring cannot push the dollars off the edge.
 */
object WidgetFit {
    data class Card(
        val ringDp: Int,
        val captionSp: Float,
        val daySp: Float,
        val timeSp: Float,
        /** How much to enlarge EXTRA and the dollar. Never wider than [moneySlotDp]. */
        val moneyScale: Float,
        val moneySlotDp: Int,
    )

    data class Wide(
        val ringDp: Int,
        val percentSp: Float,
        val leftSp: Float,
        val daySp: Float,
        val moneySp: Float,
    )

    fun card(widthDp: Int, heightDp: Int): Card {
        val width = if (widthDp > 0) widthDp else 320
        val height = if (heightDp > 0) heightDp else 120
        val captionSp = 11f
        val daySp = 20f
        val timeSp = 13f
        // Wider than the real letters. A large system font still has to stay in this column.
        val dateDp = ceil(
            maxOf(
                glyphs(captionSp, "RESETS", 0.64f, 0.14f),
                glyphs(daySp, "NOV 20", 0.80f, 0f),
                glyphs(timeSp, "09:38", 0.64f, 0f),
            ),
        ).toInt()
        // "$13.94" at the bitmap's 26sp size, before [moneyScale].
        val moneyNatural = max(
            glyphs(12f, "EXTRA", 0.74f, 0.12f),
            glyphs(26f, "$13.94", 0.74f, 0f),
        )
        // Padding 8+8, row end 4, gap after the ring 10, gap before the dollar 8.
        val chrome = 38
        val minScale = 0.85f
        val minMoney = ceil(moneyNatural * minScale).toInt()
        val wantedRing = (height - 16).coerceIn(64, 120)
        val ring = min(wantedRing, width - chrome - dateDp - minMoney).coerceAtLeast(36)
        val slot = (width - chrome - dateDp - ring).coerceAtLeast(1)
        val scale = min(1.25f, slot / moneyNatural).coerceAtLeast(minScale)
        val fitted = if (moneyNatural * scale > slot) slot / moneyNatural else scale
        return Card(ring, captionSp, daySp, timeSp, fitted, slot)
    }

    fun wide(widthDp: Int, heightDp: Int, percent: String, day: String, amount: String): Wide {
        val width = if (widthDp > 0) widthDp else 320
        val height = if (heightDp > 0) heightDp else 48
        // Pads, the gaps between words, the gap before the dollar, and the 28dp refresh arrow.
        val chrome = 10 + 8 + 8 + 5 + 10 + 8 + 4 + 28
        var ring = (height - 10).coerceIn(28, 44)
        var scale = 1.1f
        while (scale > 0.55f && wideUsed(ring, scale, percent, day, amount) + chrome > width) {
            scale -= 0.02f
        }
        while (ring > 20 && wideUsed(ring, scale, percent, day, amount) + chrome > width) {
            ring -= 2
        }
        return Wide(ring, 18f * scale, 12f * scale, 14f * scale, 18f * scale)
    }

    /** Width the card draws, in dp. The spacer may add more, but the glyphs never pass the cell. */
    fun cardUsed(widthDp: Int, heightDp: Int): Int {
        val fit = card(widthDp, heightDp)
        val dateDp = ceil(
            maxOf(
                glyphs(fit.captionSp, "RESETS", 0.64f, 0.14f),
                glyphs(fit.daySp, "NOV 20", 0.80f, 0f),
                glyphs(fit.timeSp, "09:38", 0.64f, 0f),
            ),
        ).toInt()
        val moneyDp = ceil(
            max(
                glyphs(12f * fit.moneyScale, "EXTRA", 0.74f, 0.12f),
                glyphs(26f * fit.moneyScale, "$13.94", 0.74f, 0f),
            ),
        ).toInt()
        return 38 + fit.ringDp + dateDp + moneyDp
    }

    fun wideUsed(widthDp: Int, heightDp: Int, percent: String, day: String, amount: String): Int {
        val fit = wide(widthDp, heightDp, percent, day, amount)
        val chrome = 10 + 8 + 8 + 5 + 10 + 8 + 4 + 28
        return wideUsed(fit.ringDp, fit.percentSp / 18f, percent, day, amount) + chrome
    }

    private fun wideUsed(ring: Int, scale: Float, percent: String, day: String, amount: String): Int {
        return ring + ceil(
            glyphs(18f * scale, percent.ifBlank { "—" }, 0.74f, 0f) +
                glyphs(12f * scale, "LEFT", 0.66f, 0.08f) +
                glyphs(14f * scale, day.ifBlank { "—" }, 0.68f, 0f) +
                glyphs(18f * scale, amount.ifBlank { "—" }, 0.76f, 0f),
        ).toInt()
    }

    /** A high estimate of one line, so the real letters stay inside it. */
    private fun glyphs(sp: Float, text: String, em: Float, letterSpacing: Float): Float {
        if (text.isEmpty()) return 0f
        val gaps = (text.length - 1).coerceAtLeast(0)
        return text.length * em * sp + gaps * letterSpacing * sp
    }
}
