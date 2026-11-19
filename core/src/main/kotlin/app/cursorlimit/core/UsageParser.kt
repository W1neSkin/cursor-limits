package app.cursorlimit.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Цифры карточки.
 *
 * Недельный процент берём из GetCurrentPeriodUsage: у тарифа Super сводка
 * /api/usage-summary иногда показывает месячное окно и 0%, хотя в редакторе
 * уже потрачена недельная квота. Сводка остаётся запасным источником.
 * Дополнительные кредиты — остаток предоплаты в центах (usdRemainingCents).
 */
data class UsageCard(
    val planName: String,
    val limitLabel: String,
    val usedPercent: Double,
    val resetAtEpochMs: Long?,
    val creditCents: Long?,
    val fetchedAtEpochMs: Long = 0,
)

object UsageParser {
    fun parse(
        periodJson: String?,
        planJson: String?,
        complatJson: String?,
        grantsJson: String?,
        stripeJson: String?,
        summaryJson: String?,
        visibleGrantsJson: String? = null,
        sandJson: String? = null,
    ): UsageCard? {
        val period = jsonObject(periodJson)
        val plan = jsonObject(planJson)
        val summary = jsonObject(summaryJson)
        val planUsage = period?.obj("planUsage")
        val summaryPlan = summary?.obj("individualUsage")?.obj("plan")
        val used = percent(planUsage) ?: percent(summaryPlan) ?: return null
        val reset = epoch(period, "billingCycleEnd") ?: epoch(summary, "billingCycleEnd")
        val start = epoch(period, "billingCycleStart") ?: epoch(summary, "billingCycleStart")
        return UsageCard(
            planName = planName(plan, summary),
            limitLabel = limitLabel(start, reset),
            usedPercent = used,
            resetAtEpochMs = reset,
            creditCents = credits(
                plan,
                jsonObject(complatJson),
                jsonObject(grantsJson),
                jsonObject(stripeJson),
                jsonObject(visibleGrantsJson),
                jsonObject(sandJson),
            ),
        )
    }

    fun email(json: String?): String? {
        val email = jsonObject(json)?.str("email") ?: return null
        return email.takeIf { it.contains('@') }
    }

    private fun percent(plan: JsonObject?): Double? {
        if (plan == null) return null
        number(plan, "totalPercentUsed")?.let { if (it.isFinite()) return it }
        val limit = number(plan, "limit") ?: return null
        if (limit <= 0.0) return null
        val spent = number(plan, "totalSpend")
            ?: number(plan, "includedSpend")
            ?: number(plan, "remaining")?.let { limit - it }
            ?: number(plan, "used")
            ?: return null
        return spent / limit * 100.0
    }

    private fun planName(plan: JsonObject?, summary: JsonObject?): String {
        val raw = plan?.obj("planInfo")?.str("planName")
            ?: plan?.str("planName")
            ?: summary?.str("membershipType")
            ?: ""
        return raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
    }

    /** Окно около 7 дней — недельный лимит, около месяца — месячный. */
    private fun limitLabel(start: Long?, end: Long?): String {
        if (start == null || end == null || end <= start) return "Limit"
        val days = (end - start) / 86_400_000.0
        return when {
            days <= 10 -> "Weekly limit"
            days <= 40 -> "Monthly limit"
            else -> "Limit"
        }
    }

    /**
     * Остаток дополнительных кредитов лежит в разных ответах, и нулевой из них
     * не должен прятать ненулевой. Берём наибольший: предоплату объединённого
     * аккаунта, гранты со страницы трат, prepaid из Grok и баланс Stripe.
     */
    private fun credits(
        plan: JsonObject?,
        complat: JsonObject?,
        grants: JsonObject?,
        stripe: JsonObject?,
        visible: JsonObject?,
        sand: JsonObject?,
    ): Long? {
        val specific = listOfNotNull(
            usdRemainingCents(complat),
            visibleGrantCents(visible),
            namedCents(sand, "prepaidAvailableCents"),
        ).filter { it > 0L }
        if (specific.isNotEmpty()) return specific.max()
        val legacy = legacyCredits(grants, stripe)
        if (legacy != null && legacy > 0L) return legacy
        if (usdRemainingCents(complat) != null || visibleGrantCents(visible) != null ||
            namedCents(sand, "prepaidAvailableCents") != null || legacy != null || isMerged(plan, stripe)
        ) {
            return 0L
        }
        return null
    }

    private fun visibleGrantCents(visible: JsonObject?): Long? {
        val grants = visible?.get("grants") ?: return null
        if (!grants.isJsonArray) return null
        var sum = 0L
        for (item in grants.asJsonArray) {
            if (!item.isJsonObject) continue
            val grant = item.asJsonObject
            val remaining = number(grant, "remainingCents") ?: number(grant, "remaining_cents") ?: continue
            if (remaining < 0.0) continue
            sum += remaining.roundToLong()
        }
        return sum.coerceAtLeast(0)
    }

    private fun namedCents(obj: JsonObject?, key: String): Long? {
        return number(obj, key)?.roundToLong()?.coerceAtLeast(0)
    }

    private fun isMerged(plan: JsonObject?, stripe: JsonObject?): Boolean {
        if (bool(plan, "isMerged")) return true
        if (bool(plan?.obj("planAccess"), "isMerged")) return true
        if (bool(stripe?.obj("planAccess"), "isMerged")) return true
        val membership = stripe?.str("membershipType")?.lowercase() ?: return false
        return membership.startsWith("unified")
    }

    private fun usdRemainingCents(obj: JsonObject?): Long? {
        if (obj == null) return null
        number(obj, "usdRemainingCents")?.let { return it.roundToLong().coerceAtLeast(0) }
        for (entry in obj.entrySet()) {
            val value = entry.value
            if (value.isJsonObject) {
                usdRemainingCents(value.asJsonObject)?.let { return it }
            }
        }
        return null
    }

    private fun legacyCredits(grants: JsonObject?, stripe: JsonObject?): Long? {
        if (grants == null && stripe == null) return null
        val hasGrants = bool(grants, "hasCreditGrants")
        val total = if (hasGrants) number(grants, "totalCents") ?: 0.0 else 0.0
        val validGrant = hasGrants && total > 0.0
        val used = if (validGrant) number(grants, "usedCents") ?: 0.0 else 0.0
        val prepaid = number(stripe, "customerBalance")?.let { if (it < 0) -it else 0.0 } ?: 0.0
        val remaining = (if (validGrant) total else 0.0) + prepaid - used
        return remaining.roundToLong().coerceAtLeast(0)
    }

    private fun bool(obj: JsonObject?, key: String): Boolean {
        val element = obj?.get(key) ?: return false
        return element.isJsonPrimitive && element.asJsonPrimitive.isBoolean && element.asBoolean
    }

    private fun epoch(obj: JsonObject?, key: String): Long? {
        val value = number(obj, key)
        if (value != null) return normalizeEpoch(value)
        val text = obj?.str(key) ?: return null
        return try {
            Instant.parse(text).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeEpoch(value: Double): Long? {
        if (!value.isFinite() || value <= 0.0) return null
        val whole = value.toLong()
        return when {
            whole > 10_000_000_000L -> whole
            whole > 1_000_000_000L -> whole * 1000
            else -> null
        }
    }

    private fun jsonObject(json: String?): JsonObject? {
        if (json.isNullOrBlank()) return null
        return try {
            val element = JsonParser.parseString(json)
            if (element.isJsonObject) element.asJsonObject else null
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? {
        val element = get(key) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.str(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive || element.isJsonNull) return null
        return element.asString.trim().takeIf { it.isNotEmpty() }
    }

    private fun number(obj: JsonObject?, key: String): Double? {
        if (obj == null) return null
        val element = obj.get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asDouble
            primitive.isString -> primitive.asString.toDoubleOrNull()
            else -> null
        }
    }
}
