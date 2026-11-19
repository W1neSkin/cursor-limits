package app.cursorlimit.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Card copy. Reset time looks like "Fri, Nov 20, 09:38". */
data class CardText(
    val title: String,
    val limitLabel: String,
    val remaining: String,
    val percent: String,
    val used: String,
    val reset: String,
    val credits: String,
    val bar: Int,
) {
    companion object {
        private val resetFormat = DateTimeFormatter.ofPattern("EEE, MMM d, HH:mm", Locale.US)
        private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        fun percentLeft(usedPercent: Double): Int {
            val shown = round1(usedPercent.coerceAtLeast(0.0))
            return (100.0 - shown).coerceIn(0.0, 100.0).roundToInt()
        }

        fun from(card: UsageCard, zone: ZoneId = ZoneId.systemDefault()): CardText {
            val shown = round1(card.usedPercent.coerceAtLeast(0.0))
            val left = percentLeft(card.usedPercent)
            return CardText(
                title = if (card.planName.isBlank()) "Cursor" else "Cursor · ${card.planName}",
                limitLabel = card.limitLabel,
                remaining = "$left% left",
                percent = "$left%",
                used = String.format(Locale.US, "Used %.1f%%", shown),
                reset = formatReset(card.resetAtEpochMs, zone),
                credits = formatCredits(card.creditCents),
                bar = (shown * 10).roundToInt().coerceIn(0, 1000),
            )
        }

        fun formatReset(epochMs: Long?, zone: ZoneId): String {
            if (epochMs == null || epochMs <= 0L) return "—"
            return Instant.ofEpochMilli(epochMs).atZone(zone).format(resetFormat)
        }

        /** "NOV 20". The date stays in the weekly half so it is not read as a credit reset. */
        fun resetDay(epochMs: Long?, zone: ZoneId): String {
            if (epochMs == null || epochMs <= 0L) return "—"
            return Instant.ofEpochMilli(epochMs).atZone(zone).format(dayFormat).uppercase(Locale.US)
        }

        /** "09:38". Empty when the cycle end is unknown, so the poster does not show a second dash. */
        fun resetClock(epochMs: Long?, zone: ZoneId): String {
            if (epochMs == null || epochMs <= 0L) return ""
            return Instant.ofEpochMilli(epochMs).atZone(zone).format(clockFormat)
        }

        /**
         * Alert money. Whole dollars stay "$5". The top step can be the exact balance, "$13.94".
         */
        fun formatAlertDollars(cents: Int): String {
            val abs = kotlin.math.abs(cents)
            val body = if (abs % 100 == 0) {
                "${abs / 100}"
            } else {
                "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
            }
            val sign = if (cents < 0) "-" else ""
            return "$sign$$body"
        }

        fun updatedLabel(
            epochMs: Long,
            zone: ZoneId = ZoneId.systemDefault(),
            today: LocalDate = LocalDate.now(zone),
        ): String {
            if (epochMs <= 0L) return "Not updated yet"
            val date = Instant.ofEpochMilli(epochMs).atZone(zone)
            val time = date.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
            if (date.toLocalDate() == today) return "Updated today at $time"
            return "Updated ${formatReset(epochMs, zone)}"
        }

        fun formatCredits(cents: Long?): String {
            if (cents == null) return "—"
            val negative = cents < 0
            val abs = kotlin.math.abs(cents)
            val sign = if (negative) "-" else ""
            return "$sign$${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
        }

        private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
    }
}
