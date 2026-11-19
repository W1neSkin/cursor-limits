package app.cursorlimit

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Кнопка обновляет сразу. Период — через WorkManager.
 * Система не даёт повторять работу чаще чем раз в 15 минут, и во сне
 * Samsung может сдвинуть запуск ещё дальше. Поэтому кнопка на виджете обязательна.
 */
object RefreshScheduler {
    const val ACTION_UPDATED = "app.cursorlimit.UPDATED"
    private const val PERIODIC = "cursor-limit-periodic"
    private const val ONCE = "cursor-limit-now"

    fun enqueueNow(context: Context) {
        WidgetRenderer.update(context, loading = true)
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
    }

    fun apply(context: Context) {
        val manager = WorkManager.getInstance(context)
        val minutes = Prefs.intervalMinutes(context)
        if (!WidgetRenderer.hasAny(context) || minutes <= 0 || Prefs.token(context).isNullOrBlank()) {
            manager.cancelUniqueWork(PERIODIC)
            return
        }
        // Ниже 15 минут Android запрос не примет, поэтому поднимаем до минимума.
        val safe = minutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(safe, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
