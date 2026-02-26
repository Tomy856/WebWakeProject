package com.example.webwake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class AlarmRingerService : Service() {

    companion object {
        const val CHANNEL_ID        = "alarm_ringer_v5"   // 新IDでチャンネル設定をリセット
        const val CHANNEL_ID_SILENT = "alarm_ringer_silent_v1"
        const val NOTIFICATION_ID   = 2001
        const val ACTION_STOP       = "com.example.webwake.ACTION_STOP_RINGER"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmId  = intent?.getLongExtra("ALARM_ID", -1) ?: -1
        val alarmUrl = intent?.getStringExtra("ALARM_URL") ?: ""

        // 停止ボタン
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, AlarmRingerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // WakeLockで画面を強制点灯
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "webwake:alarmwakelock"
        ).also { it.acquire(60_000L) }

        // AlarmOverlayActivity を起動
        val overlayIntent = Intent(this, AlarmOverlayActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val overlayPi = PendingIntent.getActivity(
            this, (alarmId + 100).toInt(), overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isAppForeground = (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager)
            .runningAppProcesses?.any {
                it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                it.processName == packageName
            } ?: false

        android.util.Log.d("AlarmRinger", "=== onStartCommand ===")
        android.util.Log.d("AlarmRinger", "isAppForeground=$isAppForeground alarmId=$alarmId")

        // fullScreenIntent を使えるか確認（Android 14+）
        val nm2 = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val canFsi = nm2.canUseFullScreenIntent()
            android.util.Log.d("AlarmRinger", "canUseFullScreenIntent=$canFsi")
        }

        // チャンネルの重要度をログ
        val ch = nm2.getNotificationChannel(CHANNEL_ID)
        android.util.Log.d("AlarmRinger", "channel=$CHANNEL_ID importance=${ch?.importance} exists=${ch != null}")

        if (isAppForeground) {
            android.util.Log.d("AlarmRinger", "-> startActivity directly")
            startActivity(overlayIntent)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_SILENT)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ アラーム")
                .setContentText("タップしてURLを開く")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setSound(null)
                .setVibrate(longArrayOf(0L))
                .setContentIntent(overlayPi)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            android.util.Log.d("AlarmRinger", "-> background alarm")

            // サイレント通知（バナーなし）でサービスを維持
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_SILENT)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ アラーム")
                .setContentText("タップしてURLを開く")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setSound(null)
                .setVibrate(longArrayOf(0L))
                .setContentIntent(overlayPi)
                .build()
            startForeground(NOTIFICATION_ID, notification)

            val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
            val canOverlay = android.provider.Settings.canDrawOverlays(this)
            android.util.Log.d("AlarmRinger", "canDrawOverlays=$canOverlay")

            if (canOverlay) {
                // SYSTEM_ALERT_WINDOW で直接オーバーレイ表示
                overlayIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(overlayIntent)
                android.util.Log.d("AlarmRinger", "startActivity with SYSTEM_ALERT_WINDOW")
            } else {
                // 権限なしの場合は fullScreenIntent でフォールバック
                val fsiNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("⏰ アラーム")
                    .setContentText("タップしてURLを開く")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setSound(null)
                    .setVibrate(longArrayOf(0L))
                    .setContentIntent(overlayPi)
                    .setFullScreenIntent(overlayPi, true)
                    .build()
                startForeground(NOTIFICATION_ID, fsiNotification)
                android.util.Log.d("AlarmRinger", "fallback to fullScreenIntent")
            }
        }

        startRinging()

        return START_NOT_STICKY
    }

    private fun startRinging() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val alarmVolume  = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val musicVolume  = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val ringVolume   = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        android.util.Log.d("AlarmRinger", "alarm=$alarmVolume music=$musicVolume ring=$ringVolume")

        val ringerMode = audioManager.ringerMode

        // Galaxy独自のサウンドモードを読む (0=サウンド, 1=バイブ, 2=ミュート)
        val galaxySoundMode = try {
            android.provider.Settings.System.getInt(contentResolver, "sound_mode", -1)
        } catch (e: Exception) { -1 }

        android.util.Log.d("AlarmRinger", "ringerMode=$ringerMode galaxySoundMode=$galaxySoundMode alarm=$alarmVolume")

        // 音を鳴らす条件:
        // - Android標準: ringerModeがNORMAL(2)
        // - Galaxy: sound_modeが0(サウンド)
        // - アラーム音量が1以上
        val isAndroidNormal = ringerMode == AudioManager.RINGER_MODE_NORMAL
        val isGalaxySoundOn = galaxySoundMode == 0 || galaxySoundMode == -1 // -1=Galaxy以外の端末
        val shouldPlaySound = alarmVolume > 0 && isAndroidNormal && isGalaxySoundOn
        if (shouldPlaySound) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(applicationContext, uri)
                    isLooping = true
                    prepare()
                    // 再度音量を確認（prepare中に変わった場合の保险）
                    val volumeCheck = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (volumeCheck > 0) {
                        start()
                        android.util.Log.d("AlarmRinger", "MediaPlayer started")
                    } else {
                        android.util.Log.d("AlarmRinger", "Volume changed to 0 during prepare, skip")
                        release()
                        mediaPlayer = null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AlarmRinger", "MediaPlayer error: ${e.message}")
            }
        } else {
            android.util.Log.d("AlarmRinger", "Volume=0 → no sound at all")
        }

        // バイブ（音量に関わらず常に動作）
        try {
            vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 400)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmRinger", "Vibrator error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // 過去の全チャンネルを削除
        listOf(
            "alarm_channel", "alarm_channel2",
            "alarm_ringer_channel", "alarm_ringer_channel_v2", "alarm_ringer_channel_v3"
        ).forEach { nm.deleteNotificationChannel(it) }

        // 古いv4チャンネルも削除してリセット
        nm.deleteNotificationChannel("alarm_ringer_v4")

        // メインチャンネル（画面OFF時・fullScreenIntent用・新ID）
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel(CHANNEL_ID, "アラーム着信", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }.also { nm.createNotificationChannel(it) }
        }

        // サイレントチャンネル（アプリ起動中・バナー完全非表示用）
        if (nm.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            NotificationChannel(CHANNEL_ID_SILENT, "アラーム（サイレント）", NotificationManager.IMPORTANCE_MIN).apply {
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setSound(null, null)
                enableVibration(false)
            }.also { nm.createNotificationChannel(it) }
        }
    }
}
