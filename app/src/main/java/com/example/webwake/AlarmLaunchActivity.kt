package com.example.webwake

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class AlarmLaunchActivity : AppCompatActivity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""
        android.util.Log.d("AlarmLaunch", "onCreate URL: $alarmUrl")
        handleUrl(alarmUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""
        android.util.Log.d("AlarmLaunch", "onNewIntent URL: $alarmUrl")
        handleUrl(alarmUrl)
    }

    private fun handleUrl(url: String) {
        if (url.isEmpty()) {
            finish()
            return
        }

        // 外部ブラウザで開く（全パターン対応）
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
            android.util.Log.d("AlarmLaunch", "Browser launched: $url")
        } catch (e: Exception) {
            android.util.Log.e("AlarmLaunch", "Browser failed, using WebView: ${e.message}")
            // ブラウザが開けない場合はWebViewにフォールバック
            loadInWebView(url)
            return
        }

        finish()
    }

    private fun loadInWebView(url: String) {
        val wv = WebView(this).also { wv ->
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            wv.webViewClient = WebViewClient()
            wv.webChromeClient = WebChromeClient()
            wv.loadUrl(url)
        }
        webView = wv
        setContentView(wv)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
