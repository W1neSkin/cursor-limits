package app.cursorlimit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot, Samsung does not always start the scheduled refresh on its own. */
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
