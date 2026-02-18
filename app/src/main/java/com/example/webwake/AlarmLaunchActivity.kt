package com.example.webwake

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class AlarmLaunchActivity : AppCompatActivity() {

    private var alarmUrl: String = ""
    private var isWaitingForUnlock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面上に表示 & 画面ON
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""
        android.util.Log.d("AlarmLaunch", "onCreate URL: $alarmUrl")

        // 透明なActivityを表示（ユーザーには見えない）
        setContentView(android.view.View(this))

        handleUrl()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""
        android.util.Log.d("AlarmLaunch", "onNewIntent URL: $alarmUrl")
        handleUrl()
    }

    private fun handleUrl() {
        if (alarmUrl.isEmpty()) {
            finish()
            return
        }

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val isLocked = keyguardManager.isKeyguardLocked

        android.util.Log.d("AlarmLaunch", "isLocked=$isLocked")

        if (isLocked) {
            // ロック中 → ロック解除を待つ
            isWaitingForUnlock = true
            android.util.Log.d("AlarmLaunch", "Waiting for unlock...")
        } else {
            // ロック解除済み → 即座にブラウザ起動
            launchBrowser()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (hasFocus && isWaitingForUnlock) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (!keyguardManager.isKeyguardLocked) {
                // ロック解除された
                android.util.Log.d("AlarmLaunch", "Unlocked detected, launching browser")
                isWaitingForUnlock = false
                launchBrowser()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // onResumeでもロック状態を確認
        if (isWaitingForUnlock) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (!keyguardManager.isKeyguardLocked) {
                android.util.Log.d("AlarmLaunch", "Unlocked in onResume, launching browser")
                isWaitingForUnlock = false
                launchBrowser()
            }
        }
    }

    private fun launchBrowser() {
        if (alarmUrl.isEmpty()) {
            finish()
            return
        }

        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(alarmUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
            android.util.Log.d("AlarmLaunch", "Browser launched: $alarmUrl")
            
            // ブラウザが開いてから少し待ってfinish
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 500)
        } catch (e: Exception) {
            android.util.Log.e("AlarmLaunch", "Browser launch failed: ${e.message}")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("AlarmLaunch", "Activity destroyed")
    }
}
