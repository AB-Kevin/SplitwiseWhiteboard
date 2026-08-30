package com.kevin.splitwisewhiteboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kevin.splitwisewhiteboard.storage.SecureStore

/**
 * Logs the user into Splitwise the same way a browser would (an embedded
 * WebView on the real login page), then harvests the resulting session
 * cookies. There's no OAuth flow for this because the feature this app needs
 * (the group whiteboard) isn't part of Splitwise's public API — only the
 * website has it.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start with a clean cookie jar so a stale/expired session from a
        // previous attempt can't get mistaken for a fresh login.
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.setAcceptCookie(true)

        webView = WebView(this)
        setContentView(webView)

        // The WebView fills the whole edge-to-edge window, so without this
        // the top of the login form renders right under the status
        // bar/camera cutout (and the bottom under gesture nav). Pad it by
        // the system bar insets instead, same fix as MainActivity's.
        webView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                checkForSession(url)
            }
        }

        webView.loadUrl(LOGIN_URL)
    }

    private fun checkForSession(url: String?) {
        if (finished || url == null) return
        // Splitwise sets _splitwise_session for anonymous visitors too, as
        // soon as the login page itself loads — its presence alone isn't
        // proof of a completed login. Only trust it once the WebView has
        // navigated away from the login/signup page, which Splitwise does
        // via redirect on a successful form submission.
        if (url.contains("/login") || url.contains("/signup")) return
        val cookies = CookieManager.getInstance().getCookie(url) ?: return
        if (cookies.contains("_splitwise_session=")) {
            finished = true
            SecureStore.saveCookieHeader(this, cookies)
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val LOGIN_URL = "https://secure.splitwise.com/login"
    }
}
