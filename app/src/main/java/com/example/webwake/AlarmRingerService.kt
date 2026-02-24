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
        const val CHANNEL_ID     = "alarm_ringer_v4"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP    = "com.example.webwake.ACTION_STOP_RINGER"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

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

        // タップでブラウザ起動
        val launchPi = PendingIntent.getActivity(
            this, alarmId.toInt(),
            Intent(this, AlarmLaunchActivity::class.java).apply {
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_URL", alarmUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // フォアグラウンド通知（サウンド・バイブは通知チャンネルで完全無効化済み）
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ アラーム")
            .setContentText("タップしてURLを開く")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(null)
            .setVibrate(longArrayOf(0L))   // バイブ無効（0ms=実質なし）
            .setContentIntent(launchPi)
            .addAction(android.R.drawable.ic_delete, "⏹ 停止", stopPi)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 画面を起こしてロック画面に通知を見せる
        val launchIntent = Intent(this, AlarmLaunchActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            android.util.Log.e("AlarmRinger", "startActivity failed: ${e.message}")
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
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // 過去の全チャンネルを削除
        listOf(
            "alarm_channel", "alarm_channel2",
            "alarm_ringer_channel", "alarm_ringer_channel_v2", "alarm_ringer_channel_v3"
        ).forEach { nm.deleteNotificationChannel(it) }

        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        // IMPORTANCE_HIGH だが setSound(null) でチャンネル音を完全無効
        NotificationChannel(CHANNEL_ID, "アラーム着信", NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)          // チャンネルサウンド無効
            enableVibration(false)        // チャンネルバイブ無効
        }.also { nm.createNotificationChannel(it) }
    }
}
