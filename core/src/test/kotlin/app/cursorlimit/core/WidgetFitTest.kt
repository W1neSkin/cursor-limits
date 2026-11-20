package app.cursorlimit.core

import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFitTest {
    @Test
    fun cardKeepsTheDollarInside() {
        val sizes = listOf(
            280 to 100,
            320 to 120,
            340 to 140,
            380 to 160,
            420 to 180,
            460 to 200,
        )
        for ((width, height) in sizes) {
            val fit = WidgetFit.card(width, height)
            val used = WidgetFit.cardUsed(width, height)
            assertTrue("ring ${fit.ringDp} on ${width}x$height", fit.ringDp >= 36)
            assertTrue("slot ${fit.moneySlotDp} on ${width}x$height", fit.moneySlotDp >= 48)
            assertTrue("$used dp of $width on ${width}x$height", used <= width)
        }
    }

    @Test
    fun wideKeepsTheRefreshAndAGap() {
        val texts = listOf(
            Triple("5%", "NOV 20", "$13.94"),
            Triple("100%", "SEP 30", "$13.94"),
            Triple("100%", "NOV 20", "$1234.56"),
        )
        val sizes = listOf(280 to 40, 320 to 48, 360 to 64, 420 to 72)
        for ((width, height) in sizes) {
            for ((percent, day, amount) in texts) {
                val used = WidgetFit.wideUsed(width, height, percent, day, amount)
                assertTrue("$used dp of $width for $percent $day $amount", used <= width)
            }
        }
    }
}
