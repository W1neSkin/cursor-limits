package app.cursorlimit

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

open class LimitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRenderer.update(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetRenderer.update(context)
    }

    override fun onEnabled(context: Context) {
        RefreshScheduler.apply(context)
    }

    override fun onDisabled(context: Context) {
        RefreshScheduler.apply(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            if (Prefs.token(context).isNullOrBlank()) {
                WidgetRenderer.update(context)
            } else {
                RefreshScheduler.enqueueNow(context)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "app.cursorlimit.REFRESH"
    }
}

class WideWidgetProvider : LimitWidgetProvider()

class TallWidgetProvider : LimitWidgetProvider()
