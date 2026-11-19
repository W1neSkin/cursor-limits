package app.cursorlimit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.CookieManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import app.cursorlimit.core.CardText
import app.cursorlimit.core.UsageCard
import com.google.android.material.button.MaterialButton
import java.time.Instant

/** Экран настроек: вход, период и та же карточка, что стоит на рабочем столе. */
class MainActivity : AppCompatActivity() {
    private val intervals = intArrayOf(0, 15, 30, 60, 180, 360)
    private var ignoreSpinner = true
    private var ignoreTheme = true
    private var ignoreAlerts = true
    private var loading = false
    private var pendingAlert: Int = 0
    private val askNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val id = pendingAlert
        pendingAlert = 0
        if (id == 0) return@registerForActivityResult
        val switch = findViewById<SwitchCompat>(id)
        if (!granted) {
            ignoreAlerts = true
            switch.isChecked = false
            ignoreAlerts = false
            Toast.makeText(this, R.string.notifications_off, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        saveAlert(id, true)
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
        findViewById<MaterialButton>(R.id.refresh_now).setOnClickListener { refresh() }
        findViewById<MaterialButton>(R.id.login_button).setOnClickListener { loginOrLogout() }
        findViewById<MaterialButton>(R.id.battery_button).setOnClickListener { openBatterySettings() }
        setupSpinner()
        setupTheme()
        setupAlerts()
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
        unregisterReceiver(updated)
        super.onStop()
    }

    private fun refresh() {
        if (Prefs.token(this).isNullOrBlank()) {
            Toast.makeText(this, "Sign in first", Toast.LENGTH_SHORT).show()
            return
        }
        loading = true
        render()
        RefreshScheduler.enqueueNow(this)
    }

    private fun loginOrLogout() {
        if (Prefs.token(this).isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        Prefs.clearSession(this)
        CookieManager.getInstance().removeAllCookies(null)
        RefreshScheduler.apply(this)
        WidgetRenderer.update(this)
        loading = false
        render()
    }

    private fun openBatterySettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun setupSpinner() {
        val spinner = findViewById<Spinner>(R.id.interval)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.interval_labels,
            R.layout.spinner_item,
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown)
        spinner.adapter = adapter
        val current = Prefs.intervalMinutes(this)
        val index = intervals.indexOf(current).let { if (it >= 0) it else intervals.indexOf(60) }
        spinner.setSelection(index)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinner) return
                Prefs.saveInterval(this@MainActivity, intervals[position])
                RefreshScheduler.apply(this@MainActivity)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinner.post { ignoreSpinner = false }
    }

    private fun setupTheme() {
        val spinner = findViewById<Spinner>(R.id.theme)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.theme_labels,
            R.layout.spinner_item,
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown)
        spinner.adapter = adapter
        spinner.setSelection(if (Prefs.isLight(this)) 1 else 0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreTheme) return
                val light = position == 1
                if (light == Prefs.isLight(this@MainActivity)) return
                Prefs.saveTheme(this@MainActivity, light)
                Theme.apply(this@MainActivity)
                WidgetRenderer.update(this@MainActivity)
                recreate()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinner.post { ignoreTheme = false }
    }

    private fun setupAlerts() {
        val percent = findViewById<SwitchCompat>(R.id.alert_percent)
        val dollars = findViewById<SwitchCompat>(R.id.alert_dollars)
        percent.isChecked = Prefs.percentAlerts(this)
        dollars.isChecked = Prefs.dollarAlerts(this)
        percent.setOnCheckedChangeListener { _, checked -> onAlertToggled(R.id.alert_percent, checked) }
        dollars.setOnCheckedChangeListener { _, checked -> onAlertToggled(R.id.alert_dollars, checked) }
        bindNumber(findViewById(R.id.percent_input), Prefs.percentThreshold(this)) { value ->
            if (value != Prefs.percentThreshold(this)) {
                Prefs.savePercentThreshold(this, value.coerceIn(0, 100))
                Prefs.savePercentAlerted(this, false)
            }
        }
        bindNumber(findViewById(R.id.dollars_input), Prefs.dollarThreshold(this)) { value ->
            if (value != Prefs.dollarThreshold(this)) {
                Prefs.saveDollarThreshold(this, value.coerceAtLeast(0))
                Prefs.saveDollarsAlerted(this, false)
            }
        }
        ignoreAlerts = false
    }

    private fun onAlertToggled(id: Int, checked: Boolean) {
        if (ignoreAlerts) return
        if (checked && !LimitAlerts.canPost(this)) {
            pendingAlert = id
            if (Build.VERSION.SDK_INT >= 33) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        saveAlert(id, checked)
    }

    private fun saveAlert(id: Int, checked: Boolean) {
        if (id == R.id.alert_percent) {
            Prefs.savePercentAlerts(this, checked)
            if (checked) Prefs.savePercentAlerted(this, false)
        } else {
            Prefs.saveDollarAlerts(this, checked)
            if (checked) Prefs.saveDollarsAlerted(this, false)
        }
    }

    private fun bindNumber(field: EditText, value: Int, save: (Int) -> Unit) {
        field.setText(value.toString())
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val parsed = s?.toString()?.toIntOrNull() ?: return
                save(parsed)
            }
        })
    }

    private fun render() {
        val token = Prefs.token(this)
        val card = Prefs.card(this)
        val example = findViewById<TextView>(R.id.example_label)
        if (card != null) {
            example.visibility = View.GONE
            bind(CardText.from(card))
        } else if (token.isNullOrBlank()) {
            example.visibility = View.VISIBLE
            bind(CardText.from(sampleCard()))
        } else {
            example.visibility = View.GONE
            bind(CardText.from(UsageCard("", "Weekly limit", 0.0, null, null)))
            findViewById<TextView>(R.id.card_remaining).text = "—"
            findViewById<TextView>(R.id.card_used).text = "Waiting for the first update"
        }

        val status = findViewById<TextView>(R.id.status_line)
        val error = Prefs.error(this)
        status.text = when {
            loading -> "Updating…"
            !error.isNullOrBlank() -> error
            card != null -> CardText.updatedLabel(card.fetchedAtEpochMs)
            token.isNullOrBlank() -> "Your numbers show up after you sign in"
            else -> "Updating…"
        }
        status.setTextColor(
            ContextCompat.getColor(this, if (!loading && !error.isNullOrBlank()) R.color.error else R.color.muted),
        )
        findViewById<TextView>(R.id.account_line).text = when {
            !Prefs.account(this).isNullOrBlank() -> Prefs.account(this)
            token.isNullOrBlank() -> "Account not connected"
            else -> "Account connected"
        }
        findViewById<MaterialButton>(R.id.login_button).setText(
            if (token.isNullOrBlank()) R.string.login else R.string.logout,
        )
    }

    private fun bind(text: CardText) {
        findViewById<TextView>(R.id.card_title).text = text.title
        findViewById<TextView>(R.id.card_limit).text = text.limitLabel
        findViewById<TextView>(R.id.card_remaining).text = text.remaining
        findViewById<TextView>(R.id.card_used).text = text.used
        findViewById<TextView>(R.id.card_reset).text = text.reset
        findViewById<TextView>(R.id.card_credits).text = text.credits
        findViewById<ProgressBar>(R.id.card_bar).progress = text.bar
    }

    private fun sampleCard(): UsageCard = UsageCard(
        planName = "Super",
        limitLabel = "Weekly limit",
        usedPercent = 50.9,
        resetAtEpochMs = Instant.parse("2026-11-20T06:38:00Z").toEpochMilli(),
        creditCents = 1395,
    )
}
