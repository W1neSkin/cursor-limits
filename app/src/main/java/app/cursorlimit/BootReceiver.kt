package app.cursorlimit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** После перезагрузки Samsung не всегда сам поднимает отложенную работу. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            WidgetRenderer.update(context)
            RefreshScheduler.apply(context)
        }
    }
}
