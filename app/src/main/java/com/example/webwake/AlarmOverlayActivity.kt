package com.example.webwake

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat

/**
 * アラーム発火時にロック画面上に表示されるオーバーレイ画面
 * × ボタンタップ → RingerService停止 → ブラウザでURL起動
 */
class AlarmOverlayActivity : AppCompatActivity() {

    private var alarmUrl: String = ""
    private var alarmId: Long = -1
    private var alarmHour: Int = -1
    private var alarmMinute: Int = -1
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { onTimeout() }
    private val TIMEOUT_MS = 60_000L  // 1分
    private var rippleView: View? = null
    private var snoozeMinutes = 5

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

        // Edge-to-Edge: ナビゲーションバー領域まで描画
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

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

        // ナビゲーションバーの高さを考慮してスヌーズバーの余白を設定
        val rootView = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val navBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            val snoozeBar = findViewById<android.widget.LinearLayout>(R.id.snoozeBar)
            val params = snoozeBar.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomMargin = navBarHeight + (32 * resources.displayMetrics.density).toInt()
            snoozeBar.layoutParams = params
            insets
        }

        alarmId  = intent.getLongExtra("ALARM_ID", -1)
        alarmUrl = intent.getStringExtra("ALARM_URL") ?: ""

        // ラベル・時刻を取得
        val storage = AlarmStorage(this)
        val alarm   = storage.loadAlarms().find { it.id == alarmId }
        if (alarm != null) {
            if (alarm.label.isNotEmpty()) {
                findViewById<TextView>(R.id.alarmLabel).text = alarm.label
            }
            alarmHour   = alarm.hour
            alarmMinute = alarm.minute
        }

        // ボタン: RingerService停止 → ブラウザ起動 → この画面を閉じる
        val stopButton = findViewById<FrameLayout>(R.id.stopButton)
        stopButton.setOnClickListener {
            handler.removeCallbacks(timeoutRunnable)
            launchAndStop()
        }

        // XMLのrippleViewでアニメーション開始
        val ripple = findViewById<View>(R.id.rippleView)
        ripple.post { startRippleAnimation(ripple) }

        // スヌーズバーの初期化
        setupSnoozeBar()

        // 1分後にタイムアウト
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
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

    private fun onTimeout() {
        // RingerService を停止
        stopService(Intent(this, AlarmRingerService::class.java))

        // 「設定した時間にアラームが鳴りました」通知を残す
        showMissedNotification()

        finish()
    }

    private fun showMissedNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "alarm_missed_v1"

        // 既存チャンネルを削除して重要度をリセット（一度作成すると変更不可のため）
        nm.deleteNotificationChannel(channelId)
        NotificationChannel(channelId, "アラーム履歴", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }.also { nm.createNotificationChannel(it) }

        // タップで MainActivity を開く
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 「設定した時間」を表示
        val timeText = if (alarmHour >= 0 && alarmMinute >= 0) {
            val period = if (alarmHour < 12) "午前" else "午後"
            val displayHour = when {
                alarmHour == 0 || alarmHour == 12 -> 12
                alarmHour > 12 -> alarmHour - 12
                else -> alarmHour
            }
            "${period}${displayHour}:${String.format("%02d", alarmMinute)}"
        } else ""

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ アラームが鳴りました")
            .setContentText("${timeText}に設定したアラームが鳴りました")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(alarmId.toInt(), notification)
    }

    private fun setupSnoozeBar() {
        val snoozeLabel    = findViewById<TextView>(R.id.snoozeLabel)
        val snoozeDecrease = findViewById<FrameLayout>(R.id.snoozeDecrease)
        val snoozeIncrease = findViewById<FrameLayout>(R.id.snoozeIncrease)
        val snoozeCenter   = findViewById<FrameLayout>(R.id.snoozeCenter)

        fun updateUI() {
            snoozeLabel.text = "スヌーズ：${snoozeMinutes}分"
            // − ボタン: 5分の時はグレーアウト
            snoozeDecrease.alpha = if (snoozeMinutes <= 5) 0.3f else 1.0f
            snoozeDecrease.isClickable = snoozeMinutes > 5
            // ＋ボタン: 60分の時はグレーアウト
            snoozeIncrease.alpha = if (snoozeMinutes >= 60) 0.3f else 1.0f
            snoozeIncrease.isClickable = snoozeMinutes < 60
        }

        // 初期状態を反映
        updateUI()

        // ＋ボタン
        snoozeIncrease.setOnClickListener {
            if (snoozeMinutes < 60) {
                snoozeMinutes += 5
                updateUI()
            }
        }

        // －ボタン
        snoozeDecrease.setOnClickListener {
            if (snoozeMinutes > 5) {
                snoozeMinutes -= 5
                updateUI()
            }
        }

        // スヌーズボタン（中央）タップ → スヌーズ実行
        snoozeCenter.setOnClickListener {
            handler.removeCallbacks(timeoutRunnable)
            stopService(Intent(this, AlarmRingerService::class.java))
            scheduleSnooze(snoozeMinutes)
            finish()
        }
    }

    private fun scheduleSnooze(minutes: Int) {
        val triggerMs = System.currentTimeMillis() + minutes * 60 * 1000L

        // AlarmReceiver にスヌーズ発火をセット
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
        }
        val pi = PendingIntent.getBroadcast(
            this,
            (alarmId + 9000).toInt(),  // スヌーズ用のリクエストコード
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(AlarmManager::class.java)
        val showIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showIntent), pi)

        // 「アラームはXX時XX分に鳴動します」通知を残す
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "alarm_snooze_v1"
        if (nm.getNotificationChannel(channelId) == null) {
            NotificationChannel(channelId, "スヌーズ予告", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            }.also { nm.createNotificationChannel(it) }
        }

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = triggerMs }
        val hour   = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val period = if (hour < 12) "午前" else "午後"
        val displayHour = when {
            hour == 0 || hour == 12 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val timeText = "${period}${displayHour}:${String.format("%02d", minute)}"

        val tapPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ アラームがスヌーズ")
            .setContentText("アラームは${timeText}に鳴動します。")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapPi)
            .setAutoCancel(true)
            .build()
        nm.notify((alarmId + 9000).toInt(), notification)
    }

    private fun startRippleAnimation(ripple: View) {
        // 内側円(80dp) / 外側円(120dp) = 0.667f をスタート地点にする
        val startScale = 80f / 120f
        val endScale   = 1.8f

        fun loop() {
            ripple.scaleX = startScale
            ripple.scaleY = startScale
            ripple.alpha  = 0.8f
            val scaleX = ObjectAnimator.ofFloat(ripple, "scaleX", startScale, endScale)
            val scaleY = ObjectAnimator.ofFloat(ripple, "scaleY", startScale, endScale)
            val alpha  = ObjectAnimator.ofFloat(ripple, "alpha", 0.8f, 0f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 1500
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        loop()
                    }
                })
                start()
            }
        }
        loop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeoutRunnable)
        rippleView = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }
}
