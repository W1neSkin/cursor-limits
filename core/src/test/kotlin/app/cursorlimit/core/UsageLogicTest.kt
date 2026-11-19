package app.cursorlimit.core

import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageLogicTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    @Test
    fun cardMatchesTheChatScreenshot() {
        val card = UsageCard(
            planName = "Super",
            limitLabel = "Weekly limit",
            usedPercent = 50.9,
            resetAtEpochMs = Instant.parse("2026-11-20T06:38:00Z").toEpochMilli(),
            creditCents = 1395,
        )
        val text = CardText.from(card, moscow)
        assertEquals("Cursor · Super", text.title)
        assertEquals("Weekly limit", text.limitLabel)
        assertEquals("49% left", text.remaining)
        assertEquals("49%", text.percent)
        assertEquals("Used 50.9%", text.used)
        assertEquals("Fri, Nov 20, 09:38", text.reset)
        assertEquals("$13.95", text.credits)
        assertEquals(509, text.bar)
    }

    @Test
    fun weeklyPercentComesFromPeriodAndCreditsIgnoreStripeWhenUnified() {
        val end = Instant.parse("2026-11-20T06:38:00Z")
        val start = end.minusSeconds(7 * 24 * 3600)
        val card = UsageParser.parse(
            periodJson = """
                {"billingCycleStart":"${start.toEpochMilli()}","billingCycleEnd":"${end.toEpochMilli()}",
                 "planUsage":{"totalPercentUsed":50.9}}
            """.trimIndent(),
            planJson = """{"isMerged":true,"planInfo":{"planName":"super"}}""",
            complatJson = """{"cursorCreditInFlightCents":"0","usdRemainingCents":"1395"}""",
            grantsJson = """{"hasCreditGrants":true,"totalCents":999999,"usedCents":0}""",
            stripeJson = """{"customerBalance":-999999}""",
            summaryJson = """{"membershipType":"free","individualUsage":{"plan":{"totalPercentUsed":0}}}""",
        )!!
        assertEquals("Super", card.planName)
        assertEquals("Weekly limit", card.limitLabel)
        assertEquals(50.9, card.usedPercent, 0.001)
        assertEquals(1395L, card.creditCents)
        assertEquals(end.toEpochMilli(), card.resetAtEpochMs)
    }

    @Test
    fun zeroUnifiedBalanceDoesNotHideStripeCredits() {
        val end = Instant.parse("2026-11-20T06:38:00Z")
        val start = end.minusSeconds(7 * 24 * 3600)
        val card = UsageParser.parse(
            periodJson = """
                {"billingCycleStart":"${start.toEpochMilli()}","billingCycleEnd":"${end.toEpochMilli()}",
                 "planUsage":{"totalPercentUsed":53.2}}
            """.trimIndent(),
            planJson = """{"isMerged":false,"planInfo":{"planName":"Super"}}""",
            complatJson = """{"cursorCreditInFlightCents":"0","usdRemainingCents":"0"}""",
            grantsJson = """{"hasCreditGrants":false}""",
            stripeJson = """{"customerBalance":"-1395"}""",
            summaryJson = null,
        )!!
        assertEquals(1395L, card.creditCents)
    }

    @Test
    fun spendingPageGrantIsTheCreditBalanceWhenPrepaidIsZero() {
        val end = Instant.parse("2026-11-20T06:38:00Z")
        val start = end.minusSeconds(7 * 24 * 3600)
        val card = UsageParser.parse(
            periodJson = """
                {"billingCycleStart":"${start.toEpochMilli()}","billingCycleEnd":"${end.toEpochMilli()}",
                 "planUsage":{"totalPercentUsed":54.2}}
            """.trimIndent(),
            planJson = """{"isMerged":true,"planInfo":{"planName":"Super"}}""",
            complatJson = """{"usdRemainingCents":"0","cursorCreditInFlightCents":"0"}""",
            grantsJson = """{"hasCreditGrants":false}""",
            stripeJson = """{"customerBalance":0,"membershipType":"unified_super","planAccess":{"isMerged":true}}""",
            summaryJson = null,
            visibleGrantsJson = """{"grants":[{"remainingCents":"1395","totalCents":"2000","displayName":"Additional"}]}""",
            sandJson = """{"prepaidAvailableCents":"0"}""",
        )!!
        assertEquals(1395L, card.creditCents)
    }

    @Test
    fun summaryFallbackIsMonthlyAndGrantsPlusStripe() {
        val end = Instant.parse("2026-12-01T00:00:00Z")
        val start = end.minusSeconds(30 * 24 * 3600)
        val card = UsageParser.parse(
            periodJson = """{"enabled":true}""",
            planJson = null,
            complatJson = "{}",
            grantsJson = """{"hasCreditGrants":true,"totalCents":"10000","usedCents":"1000"}""",
            stripeJson = """{"customerBalance":"-500"}""",
            summaryJson = """
                {"membershipType":"super",
                 "billingCycleStart":"$start","billingCycleEnd":"$end",
                 "individualUsage":{"plan":{"totalPercentUsed":10}}}
            """.trimIndent(),
        )!!
        assertEquals("Super", card.planName)
        assertEquals("Monthly limit", card.limitLabel)
        assertEquals(10.0, card.usedPercent, 0.001)
        assertEquals(9500L, card.creditCents)
    }

    @Test
    fun missingNumbersProduceNoCard() {
        assertNull(UsageParser.parse("{}", null, null, null, null, null))
    }

    @Test
    fun tokenForms() {
        val jwt = jwt("""{"sub":"google-oauth2|user_abc"}""")
        assertEquals(jwt, SessionToken.accessToken(jwt))
        assertEquals("user_abc", SessionToken.userId(jwt))
        assertEquals("WorkosCursorSessionToken=user_abc%3A%3A$jwt", SessionToken.cookieHeader(jwt))
        assertEquals(jwt, SessionToken.accessToken("user_abc%3A%3A$jwt"))
        assertEquals(jwt, SessionToken.accessToken("WorkosCursorSessionToken=user_other::$jwt"))
        assertEquals("user_other", SessionToken.userId("WorkosCursorSessionToken=user_other::$jwt"))
        assertNull(SessionToken.accessToken("not a token"))
    }

    private fun jwt(payload: String): String {
        fun part(text: String) = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(text.toByteArray())
        return part("""{"alg":"none"}""") + "." + part(payload) + ".sig"
    }
}
