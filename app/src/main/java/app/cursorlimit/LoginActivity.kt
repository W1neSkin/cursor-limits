package app.cursorlimit

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import app.cursorlimit.core.SessionToken
import com.google.android.material.button.MaterialButton

/**
 * Вход на cursor.com внутри приложения.
 * HttpOnly-куку WorkosCursorSessionToken страница из JavaScript не отдаёт,
 * но CookieManager её видит — поэтому ключ можно забрать после входа.
 * Google иногда запрещает вход во встроенном окне. Тогда ключ вставляют вручную.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var watching = true
    private val watch = object : Runnable {
        override fun run() {
            if (!watching) return
            if (!capture()) web.postDelayed(this, 700)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        web = findViewById(R.id.web)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Без обычного Chrome-агента Google часто пишет «браузер не поддерживается».
        web.settings.userAgentString = CHROME
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val bar = findViewById<ProgressBar>(R.id.login_progress)
                bar.progress = newProgress
                bar.visibility = if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        findViewById<MaterialButton>(R.id.close_login).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.done_login).setOnClickListener {
            if (!capture()) {
                Toast.makeText(this, "Sign-in is not finished", Toast.LENGTH_SHORT).show()
            }
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (web.canGoBack()) web.goBack() else finish()
                }
            },
        )
        web.loadUrl("https://cursor.com/dashboard")
        web.post(watch)
    }

    override fun onDestroy() {
        watching = false
        super.onDestroy()
    }

    private fun capture(): Boolean {
        val raw = CookieManager.getInstance().getCookie("https://cursor.com") ?: return false
        val token = raw.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("WorkosCursorSessionToken=") }
            ?.substringAfter('=')
            ?: return false
        if (SessionToken.accessToken(token) == null) return false
        Prefs.saveToken(this, token)
        RefreshScheduler.apply(this)
        RefreshScheduler.enqueueNow(this)
        finish()
        return true
    }

    private companion object {
        const val CHROME =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
    }
}
