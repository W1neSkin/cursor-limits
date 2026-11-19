package app.cursorlimit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.CookieManager
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import app.cursorlimit.core.CardText
import app.cursorlimit.core.UsageCard
import java.time.Instant
import java.time.ZoneId

/** The poster: weekly limit on the left, extra credits on the right. */
class MainActivity : AppCompatActivity() {
    private var ignoreWheels = false
    private var loading = false
    private var pendingAlert = 0
    private var toastedError: String? = null
    private lateinit var percentStepper: AlertStepper
    private lateinit var dollarStepper: AlertStepper

    private val askNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val kind = pendingAlert
        pendingAlert = 0
        if (kind == 0) return@registerForActivityResult
        if (!granted) {
            Toast.makeText(this, R.string.notifications_off, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        enableAlert(kind)
    }

    private val filter = IntentFilter(RefreshScheduler.ACTION_UPDATED)
    private val updated = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loading = false
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        paintBars()
        percentStepper = AlertStepper(
            minus = findViewById(R.id.percent_minus),
            value = findViewById(R.id.percent_value),
            plus = findViewById(R.id.percent_plus),
            step = 1,
            floor = { 1 },
            ceiling = { 100 },
            read = { Prefs.percentThreshold(this) },
            write = { savePercent(it) },
            format = { "$it%" },
            onOpen = { dollarStepper.close(); hideSheet() },
        )
        dollarStepper = AlertStepper(
            minus = findViewById(R.id.dollar_minus),
            value = findViewById(R.id.dollar_value),
            plus = findViewById(R.id.dollar_plus),
            step = 100,
            floor = { dollarFloor() },
            ceiling = { dollarCap() },
            read = { Prefs.dollarThresholdCents(this) },
            write = { saveDollars(it) },
            format = { CardText.formatAlertDollars(it) },
            onOpen = { percentStepper.close(); hideSheet() },
        )
        percentStepper.bind()
        dollarStepper.bind()
        bindWheels()
        findViewById<TextView>(R.id.hero_percent).setOnClickListener { onHeroTap() }
        findViewById<TextView>(R.id.interval_label).setOnClickListener { onIntervalTap() }
        findViewById<View>(R.id.battery_button).setOnClickListener { openBatterySettings() }
        findViewById<View>(R.id.sign_out).setOnClickListener { signOut() }
        findViewById<ImageButton>(R.id.theme_button).setOnClickListener { chooseTheme(!Prefs.isLight(this)) }
        paintThemeIcon()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updated, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updated, filter)
        }
        render()
    }

    override fun onStop() {
        percentStepper.stop()
        dollarStepper.stop()
        unregisterReceiver(updated)
        super.onStop()
    }

    private fun paintBars() {
        val light = Prefs.isLight(this)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = light
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = light
    }

    private fun onHeroTap() {
        percentStepper.close()
        dollarStepper.close()
        hideSheet()
        if (Prefs.token(this).isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            refresh()
        }
    }

    private fun onIntervalTap() {
        if (Prefs.token(this).isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        percentStepper.close()
        dollarStepper.close()
        val sheet = findViewById<View>(R.id.interval_sheet)
        if (sheet.visibility == View.VISIBLE) hideSheet() else showSheet()
    }

    private fun refresh() {
        if (Prefs.token(this).isNullOrBlank()) return
        loading = true
        render()
        RefreshScheduler.enqueueNow(this)
    }

    private fun signOut() {
        Prefs.clearSession(this)
        CookieManager.getInstance().removeAllCookies(null)
        RefreshScheduler.apply(this)
        WidgetRenderer.update(this)
        loading = false
        hideSheet()
        render()
    }

    private fun openBatterySettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    private fun chooseTheme(light: Boolean) {
        if (light == Prefs.isLight(this)) return
        Prefs.saveTheme(this, light)
        Theme.apply(this)
        WidgetRenderer.update(this)
        recreate()
    }

    private fun paintThemeIcon() {
        val icon = if (Prefs.isLight(this)) R.drawable.ic_sun else R.drawable.ic_moon
        findViewById<ImageButton>(R.id.theme_button).setImageResource(icon)
    }

    private fun savePercent(value: Int) {
        if (value == Prefs.percentThreshold(this)) return
        Prefs.savePercentThreshold(this, value)
        Prefs.savePercentAlerted(this, false)
        arm(PERCENT)
    }

    private fun saveDollars(cents: Int) {
        if (cents == Prefs.dollarThresholdCents(this)) return
        Prefs.saveDollarThresholdCents(this, cents)
        Prefs.saveDollarsAlerted(this, false)
        arm(DOLLARS)
    }

    /** The first edit arms the alert and asks for notification permission once. */
    private fun arm(kind: Int) {
        val already = if (kind == PERCENT) Prefs.percentAlerts(this) else Prefs.dollarAlerts(this)
        if (already) return
        if (!LimitAlerts.canPost(this)) {
            pendingAlert = kind
            if (Build.VERSION.SDK_INT >= 33) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        enableAlert(kind)
    }

    private fun enableAlert(kind: Int) {
        if (kind == PERCENT) Prefs.savePercentAlerts(this, true) else Prefs.saveDollarAlerts(this, true)
    }

    private fun render() {
        val signedIn = !Prefs.token(this).isNullOrBlank()
        findViewById<TextView>(R.id.example_label).visibility = if (signedIn) View.GONE else View.VISIBLE
        val card = shownCard()
        if (card == null) {
            findViewById<TextView>(R.id.hero_percent).text = "—"
            findViewById<TextView>(R.id.reset_day).text = "—"
            findViewById<TextView>(R.id.reset_time).text = ""
            findViewById<TextView>(R.id.hero_credits).text = "—"
        } else {
            val zone = ZoneId.systemDefault()
            findViewById<TextView>(R.id.hero_percent).text = "${CardText.percentLeft(card.usedPercent)}%"
            findViewById<TextView>(R.id.reset_day).text = CardText.resetDay(card.resetAtEpochMs, zone)
            findViewById<TextView>(R.id.reset_time).text = CardText.resetClock(card.resetAtEpochMs, zone)
            findViewById<TextView>(R.id.hero_credits).text = CardText.formatCredits(card.creditCents)
        }
        percentStepper.show()
        dollarStepper.show()
        renderBar()
        val error = Prefs.error(this)
        if (!loading && !error.isNullOrBlank() && error != toastedError) {
            toastedError = error
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
        if (error.isNullOrBlank()) toastedError = null
    }

    private fun renderBar() {
        val signedIn = !Prefs.token(this).isNullOrBlank()
        val phrase = intervalPhrase(Prefs.intervalMinutes(this))
        findViewById<TextView>(R.id.interval_label).text = when {
            loading -> getString(R.string.refreshing)
            !signedIn -> getString(R.string.sign_in)
            else -> phrase
        }
        findViewById<TextView>(R.id.interval_caption).text = phrase
    }

    private fun showSheet() {
        syncWheels()
        findViewById<View>(R.id.bottom_bar).visibility = View.GONE
        findViewById<View>(R.id.interval_sheet).visibility = View.VISIBLE
    }

    private fun hideSheet() {
        findViewById<View>(R.id.interval_sheet).visibility = View.GONE
        findViewById<View>(R.id.bottom_bar).visibility = View.VISIBLE
    }

    /**
     * Two wheels. Hours keep going past 6, up to 99. Minutes step by 15.
     * 00:00 is the default and means there is no timer.
     */
    private fun bindWheels() {
        val hours = findViewById<NumberPicker>(R.id.hour_picker)
        val minutes = findViewById<NumberPicker>(R.id.minute_picker)
        hours.minValue = 0
        hours.maxValue = 99
        hours.displayedValues = Array(100) { "%02d".format(it) }
        hours.wrapSelectorWheel = false
        minutes.minValue = 0
        minutes.maxValue = 3
        minutes.displayedValues = arrayOf("00", "15", "30", "45")
        minutes.wrapSelectorWheel = true
        val changed = NumberPicker.OnValueChangeListener { _, _, _ ->
            if (ignoreWheels) return@OnValueChangeListener
            Prefs.saveInterval(this, hours.value * 60 + minutes.value * 15)
            RefreshScheduler.apply(this)
            renderBar()
        }
        hours.setOnValueChangedListener(changed)
        minutes.setOnValueChangedListener(changed)
    }

    private fun syncWheels() {
        val total = Prefs.intervalMinutes(this).coerceAtLeast(0)
        val hours = findViewById<NumberPicker>(R.id.hour_picker)
        val minutes = findViewById<NumberPicker>(R.id.minute_picker)
        ignoreWheels = true
        hours.value = (total / 60).coerceIn(hours.minValue, hours.maxValue)
        minutes.value = ((total % 60) / 15).coerceIn(0, 3)
        ignoreWheels = false
    }

    private fun intervalPhrase(minutes: Int): String {
        if (minutes <= 0) return getString(R.string.tap_percent)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "Every $rest min"
            rest == 0 -> "Every $hours h"
            else -> "Every $hours h $rest min"
        }
    }

    /** Dollars stop at the extra credits on screen. $13.94 is the ceiling, not $14. */
    private fun dollarCap(): Int {
        val cents = shownCard()?.creditCents
        if (cents == null) return Prefs.dollarThresholdCents(this).coerceAtLeast(100)
        return cents.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Below one dollar the only step is the balance itself. Otherwise the floor is $1. */
    private fun dollarFloor(): Int {
        val cap = dollarCap()
        return if (cap < 100) cap else 100
    }

    private fun shownCard(): UsageCard? {
        Prefs.card(this)?.let { return it }
        if (Prefs.token(this).isNullOrBlank()) return sampleCard()
        return null
    }

    private fun sampleCard(): UsageCard = UsageCard(
        planName = "Super",
        limitLabel = "Weekly limit",
        usedPercent = 50.9,
        resetAtEpochMs = Instant.parse("2026-11-20T06:38:00Z").toEpochMilli(),
        creditCents = 1395,
    )

    companion object {
        private const val PERCENT = 1
        private const val DOLLARS = 2
    }
}
