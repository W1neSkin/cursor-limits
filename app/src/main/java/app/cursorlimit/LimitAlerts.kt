package app.cursorlimit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.cursorlimit.core.CardText
import app.cursorlimit.core.UsageCard

/** Одно уведомление на пересечение порога. Повтор — после того как цифра снова выше. */
object LimitAlerts {
    private const val CHANNEL = "limits"
    private const val PERCENT_ID = 41
    private const val DOLLARS_ID = 42

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "Limit alerts", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Weekly limit and extra credits"
        manager.createNotificationChannel(channel)
    }

    fun onCard(context: Context, card: UsageCard) {
        ensureChannel(context)
        val left = CardText.percentLeft(card.usedPercent)
        if (Prefs.percentAlerts(context)) {
            val threshold = Prefs.percentThreshold(context)
            if (left <= threshold) {
                if (!Prefs.percentAlerted(context)) {
                    notify(
                        context,
                        PERCENT_ID,
                        "Weekly limit is low",
                        "$left% left. Your alert is set at $threshold%.",
                    )
                    Prefs.savePercentAlerted(context, true)
                }
            } else {
                Prefs.savePercentAlerted(context, false)
            }
        }
        val cents = card.creditCents
        if (Prefs.dollarAlerts(context) && cents != null) {
            val dollars = Prefs.dollarThreshold(context)
            val leftDollars = cents / 100.0
            if (cents <= dollars * 100L) {
                if (!Prefs.dollarsAlerted(context)) {
                    notify(
                        context,
                        DOLLARS_ID,
                        "Extra credits are low",
                        String.format(java.util.Locale.US, "$%.2f left. Your alert is set at $%d.", leftDollars, dollars),
                    )
                    Prefs.saveDollarsAlerted(context, true)
                }
            } else {
                Prefs.saveDollarsAlerted(context, false)
            }
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notify(context: Context, id: Int, title: String, body: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_limit)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Разрешение сняли между проверкой и отправкой.
        }
    }
}
