package app.cursorlimit

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

/** The network call cannot run inside the widget callback. That callback has about 10 seconds. */
class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext
        try {
            val token = Prefs.token(app)
            if (token.isNullOrBlank()) {
                Prefs.saveError(app, "Sign in from the app")
            } else {
                when (val result = UsageClient().fetch(token)) {
                    is FetchResult.Ok -> {
                        val saved = result.card.copy(fetchedAtEpochMs = System.currentTimeMillis())
                        Prefs.saveCard(app, saved)
                        Prefs.saveAccount(app, result.account)
                        Prefs.saveError(app, null)
                        LimitAlerts.onCard(app, saved)
                    }
                    FetchResult.Unauthorized -> Prefs.saveError(app, "Session expired. Sign in again.")
                    FetchResult.BadToken -> Prefs.saveError(app, "Session expired. Sign in again.")
                    is FetchResult.Failed -> Prefs.saveError(app, result.message)
                }
            }
        } catch (_: Exception) {
            Prefs.saveError(app, "Could not refresh")
        } finally {
            WidgetRenderer.update(app)
            app.sendBroadcast(Intent(RefreshScheduler.ACTION_UPDATED).setPackage(app.packageName))
        }
        // A retry does not fix a bad login. The next try is the button or the timer.
        return Result.success()
    }
}
