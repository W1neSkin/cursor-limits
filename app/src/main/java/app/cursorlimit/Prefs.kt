package app.cursorlimit

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cursorlimit.core.UsageCard

/** The login and the last card. The login is kept in encrypted settings. */
object Prefs {
    private const val TOKEN = "token"
    private const val INTERVAL = "interval"
    private const val ACCOUNT = "account"
    private const val ERROR = "error"
    private const val HAS_CARD = "has_card"
    private const val PLAN = "plan"
    private const val LIMIT = "limit"
    private const val USED = "used"
    private const val RESET = "reset"
    private const val CREDITS = "credits"
    private const val FETCHED = "fetched"
    private const val THEME = "theme"
    private const val ALERT_PERCENT_ON = "alert_percent_on"
    private const val ALERT_PERCENT = "alert_percent"
    private const val ALERT_DOLLARS_ON = "alert_dollars_on"
    private const val ALERT_DOLLARS = "alert_dollars"
    private const val PERCENT_ALERTED = "percent_alerted"
    private const val DOLLARS_ALERTED = "dollars_alerted"
    private const val NO_CREDITS = Long.MIN_VALUE

    @Volatile
    private var cached: SharedPreferences? = null

    fun token(context: Context): String? = prefs(context).getString(TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(TOKEN, token.trim()).apply()
    }

    fun intervalMinutes(context: Context): Int = prefs(context).getInt(INTERVAL, 60)

    fun saveInterval(context: Context, minutes: Int) {
        prefs(context).edit().putInt(INTERVAL, minutes).apply()
    }

    fun account(context: Context): String? = prefs(context).getString(ACCOUNT, null)

    fun saveAccount(context: Context, account: String?) {
        prefs(context).edit().putString(ACCOUNT, account).apply()
    }

    fun error(context: Context): String? = prefs(context).getString(ERROR, null)?.takeIf { it.isNotBlank() }

    fun saveError(context: Context, message: String?) {
        prefs(context).edit().putString(ERROR, message).apply()
    }

    fun card(context: Context): UsageCard? {
        val store = prefs(context)
        if (!store.getBoolean(HAS_CARD, false)) return null
        val credits = store.getLong(CREDITS, NO_CREDITS)
        val reset = store.getLong(RESET, -1L)
        return UsageCard(
            planName = store.getString(PLAN, "").orEmpty(),
            limitLabel = store.getString(LIMIT, "Limit").orEmpty(),
            usedPercent = store.getFloat(USED, 0f).toDouble(),
            resetAtEpochMs = reset.takeIf { it > 0L },
            creditCents = credits.takeIf { it != NO_CREDITS },
            fetchedAtEpochMs = store.getLong(FETCHED, 0L),
        )
    }

    fun saveCard(context: Context, card: UsageCard) {
        prefs(context).edit()
            .putBoolean(HAS_CARD, true)
            .putString(PLAN, card.planName)
            .putString(LIMIT, card.limitLabel)
            .putFloat(USED, card.usedPercent.toFloat())
            .putLong(RESET, card.resetAtEpochMs ?: -1L)
            .putLong(CREDITS, card.creditCents ?: NO_CREDITS)
            .putLong(FETCHED, card.fetchedAtEpochMs)
            .apply()
    }

    fun isLight(context: Context): Boolean = prefs(context).getString(THEME, "dark") == "light"

    fun saveTheme(context: Context, light: Boolean) {
        prefs(context).edit().putString(THEME, if (light) "light" else "dark").apply()
    }

    fun percentAlerts(context: Context): Boolean = prefs(context).getBoolean(ALERT_PERCENT_ON, false)

    fun savePercentAlerts(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ALERT_PERCENT_ON, enabled).apply()
    }

    fun percentThreshold(context: Context): Int = prefs(context).getInt(ALERT_PERCENT, 5).coerceIn(0, 100)

    fun savePercentThreshold(context: Context, percent: Int) {
        prefs(context).edit().putInt(ALERT_PERCENT, percent.coerceIn(0, 100)).apply()
    }

    fun dollarAlerts(context: Context): Boolean = prefs(context).getBoolean(ALERT_DOLLARS_ON, false)

    fun saveDollarAlerts(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ALERT_DOLLARS_ON, enabled).apply()
    }

    fun dollarThreshold(context: Context): Int = prefs(context).getInt(ALERT_DOLLARS, 5).coerceAtLeast(0)

    fun saveDollarThreshold(context: Context, dollars: Int) {
        prefs(context).edit().putInt(ALERT_DOLLARS, dollars.coerceAtLeast(0)).apply()
    }

    fun percentAlerted(context: Context): Boolean = prefs(context).getBoolean(PERCENT_ALERTED, false)

    fun savePercentAlerted(context: Context, alerted: Boolean) {
        prefs(context).edit().putBoolean(PERCENT_ALERTED, alerted).apply()
    }

    fun dollarsAlerted(context: Context): Boolean = prefs(context).getBoolean(DOLLARS_ALERTED, false)

    fun saveDollarsAlerted(context: Context, alerted: Boolean) {
        prefs(context).edit().putBoolean(DOLLARS_ALERTED, alerted).apply()
    }

    fun clearSession(context: Context) {
        prefs(context).edit()
            .remove(TOKEN)
            .remove(ACCOUNT)
            .remove(ERROR)
            .remove(HAS_CARD)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        val app = context.applicationContext
        val created = try {
            val master = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                "cursor_limit_secure",
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // Encryption fails on some phones. The file is still private, and backup is off.
            app.getSharedPreferences("cursor_limit_plain", Context.MODE_PRIVATE)
        }
        cached = created
        return created
    }
}
