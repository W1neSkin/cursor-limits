package app.cursorlimit

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Светлая или тёмная тема приложения. Виджеты красятся отдельно, тем же выбором. */
object Theme {
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.isLight(context)) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES,
        )
    }
}
