package com.example.webwake

import android.content.Intent
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

        // 画面をONにしてロック画面上でも表示
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""
        android.util.Log.d("AlarmLaunch", "onCreate URL: $alarmUrl")

        loadUrl(alarmUrl)
    }

    // 既に起動中のActivityに新しいIntentが来た場合
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""
        android.util.Log.d("AlarmLaunch", "onNewIntent URL: $alarmUrl")
        loadUrl(alarmUrl)
    }

    private fun loadUrl(url: String) {
        if (url.isEmpty()) {
            finish()
            return
        }

        val wv = WebView(this).also { wv ->
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            wv.webViewClient = WebViewClient()
            wv.webChromeClient = WebChromeClient()
            wv.loadUrl(url)
        }

        webView = wv
        setContentView(wv)
        android.util.Log.d("AlarmLaunch", "WebView loading: $url")
    }

    // 戻るボタンでWebViewの履歴を戻る
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
