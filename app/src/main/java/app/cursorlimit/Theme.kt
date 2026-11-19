package app.cursorlimit

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Light or dark theme for the app. Widgets use the same choice, painted on their own. */
object Theme {
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.isLight(context)) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES,
        )
    }
}
