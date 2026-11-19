package app.cursorlimit

import android.app.Application

class LimitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Theme.apply(this)
        LimitAlerts.ensureChannel(this)
    }
}
