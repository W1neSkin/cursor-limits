package app.cursorlimit

import app.cursorlimit.core.SessionToken
import app.cursorlimit.core.UsageCard
import app.cursorlimit.core.UsageParser
import java.net.HttpURLConnection
import java.net.URL

sealed class FetchResult {
    data class Ok(val card: UsageCard, val account: String?) : FetchResult()
    data object Unauthorized : FetchResult()
    data object BadToken : FetchResult()
    data class Failed(val message: String) : FetchResult()
}

/**
 * Те же неофициальные запросы, которыми пользуется личный кабинет Cursor.
 * Ответы и ключ в лог не пишем: это данные аккаунта.
 */
class UsageClient {
    fun fetch(rawToken: String): FetchResult {
        val jwt = SessionToken.accessToken(rawToken) ?: return FetchResult.BadToken
        val cookie = SessionToken.cookieHeader(rawToken)
        val period = post(PERIOD, jwt)
        val plan = post(PLAN, jwt)
        val complat = post(COMPLAT, jwt)
        val grants = post(GRANTS, jwt)
        val visible = post(VISIBLE, jwt)
        val sand = post(SAND, jwt)
        val stripe = cookie?.let { get(STRIPE, it) }
        val summary = cookie?.let { get(SUMMARY, it) }
        val profile = cookie?.let { get(PROFILE, it) }

        val calls = listOf(period, plan, complat, grants)
        if (calls.all { it.status == 401 }) return FetchResult.Unauthorized
        if (calls.all { it.status == 0 } && (summary == null || summary.status == 0)) {
            return FetchResult.Failed("No network, or Cursor did not respond.")
        }

        val card = UsageParser.parse(
            period.body,
            plan.body,
            complat.body,
            grants.body,
            stripe?.body,
            summary?.body,
            visible.body,
            sand.body,
        ) ?: return FetchResult.Failed("Cursor responded, but no limit was found.")

        val account = UsageParser.email(profile?.body) ?: SessionToken.userId(rawToken)
        return FetchResult.Ok(card, account)
    }

    private fun post(url: String, jwt: String) = call(url, "POST", jwt, null)

    private fun get(url: String, cookie: String) = call(url, "GET", null, cookie)

    private fun call(url: String, method: String, jwt: String?, cookie: String?): Body {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CursorLimitWidget/1.0")
                if (jwt != null) {
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $jwt")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Connect-Protocol-Version", "1")
                }
                if (cookie != null) setRequestProperty("Cookie", cookie)
            }
            if (jwt != null) {
                connection.outputStream.use { it.write("{}".toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            connection.disconnect()
            Body(status, text)
        } catch (_: Exception) {
            Body(0, null)
        }
    }

    private data class Body(val status: Int, val body: String?)

    private companion object {
        const val PERIOD = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"
        const val PLAN = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetPlanInfo"
        const val COMPLAT = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetComplatCreditBalance"
        const val GRANTS = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCreditGrantsBalance"
        const val VISIBLE = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetClientVisibleCreditGrants"
        const val SAND = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetSandUsageStatus"
        const val STRIPE = "https://cursor.com/api/auth/stripe"
        const val SUMMARY = "https://cursor.com/api/usage-summary"
        const val PROFILE = "https://cursor.com/api/auth/me"
    }
}
