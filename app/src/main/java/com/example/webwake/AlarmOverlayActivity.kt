package com.example.webwake

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * アラーム発火時にロック画面上に表示されるオーバーレイ画面
 * × ボタンタップ → RingerService停止 → ブラウザでURL起動
 */
class AlarmOverlayActivity : AppCompatActivity() {

    private var alarmUrl: String = ""
    private var alarmId: Long = -1
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Activity自身でも画面を点灯・維持するWakeLockを取得
        val pm = getSystemService(android.os.PowerManager::class.java)
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "webwake:overlaylock"
        ).also { it.acquire(300_000L) } // 最大5分

        // ロック画面上に表示・画面ON・画面を消さない
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_alarm_overlay)

        alarmId  = intent.getLongExtra("ALARM_ID", -1)
        alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""

        // ラベルがあれば表示
        val storage = AlarmStorage(this)
        val alarm   = storage.loadAlarms().find { it.id == alarmId }
        if (alarm != null && alarm.label.isNotEmpty()) {
            findViewById<TextView>(R.id.alarmLabel).text = alarm.label
        }

        // × ボタン: RingerService停止 → ブラウザ起動 → この画面を閉じる
        findViewById<FrameLayout>(R.id.stopButton).setOnClickListener {
            launchAndStop()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        alarmId  = intent.getLongExtra("ALARM_ID", alarmId)
        alarmUrl = intent.getStringExtra("ALARM_URL") ?: alarmUrl
    }

    private fun launchAndStop() {
        // 1. RingerService を停止
        stopService(Intent(this, AlarmRingerService::class.java))

        // 2. キーガード解除してからブラウザを起動
        val km = getSystemService(KeyguardManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    openBrowser()
                }
                override fun onDismissError() {
                    // 解除失敗しても生起を試みる
                    openBrowser()
                }
                override fun onDismissCancelled() {
                    // ユーザーがキャンセルした場合は何もしない
                }
            })
        } else {
            // Android 8未満は直接開く
            openBrowser()
        }
    }

    private fun openBrowser() {
        if (alarmUrl.isNotEmpty()) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(alarmUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("AlarmOverlay", "Browser launch failed: ${e.message}")
            }
        }
        finish()
    }

    // バックキーでは閉じない（意図しない閉じ防止）
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 何もしない
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }
}
