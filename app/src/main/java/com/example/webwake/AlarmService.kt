package com.example.webwake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID   = "alarm_channel"
        const val CHANNEL_ID2  = "alarm_channel2"   // stopSelf後も残る通知用
        const val NOTIFICATION_ID  = 1001
        const val NOTIFICATION_ID2 = 1002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId  = intent?.getLongExtra("ALARM_ID", -1) ?: -1
        val alarmUrl = intent?.getStringExtra("ALARM_URL") ?: ""

        android.util.Log.d("AlarmService", "onStartCommand: alarmId=$alarmId url=$alarmUrl")

        val launchIntent = Intent(this, AlarmLaunchActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Step1: フォアグラウンドサービス用の通知（必須・即削除される） ---
        val fgsNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("アラーム")
            .setContentText("起動中...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, fgsNotification)

        // --- Step2: fullScreenIntent付きの本命通知を別IDで発行 ---
        // stopSelf()後もこの通知は残り、fullScreenIntentが発火する
        val alarmNotification = NotificationCompat.Builder(this, CHANNEL_ID2)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("アラーム")
            .setContentText("タップしてURLを開く")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID2, alarmNotification)

        android.util.Log.d("AlarmService", "fullScreenIntent notification posted (ID=$NOTIFICATION_ID2)")

        stopSelf()
        return START_NOT_STICKY
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // FGS用（最小限）
        notificationManager.deleteNotificationChannel(CHANNEL_ID)
        NotificationChannel(CHANNEL_ID, "アラームサービス", NotificationManager.IMPORTANCE_MIN).also {
            notificationManager.createNotificationChannel(it)
        }

        // fullScreenIntent用（最高優先度）
        notificationManager.deleteNotificationChannel(CHANNEL_ID2)
        NotificationChannel(CHANNEL_ID2, "アラーム通知", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "アラームの通知チャンネル"
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
            enableVibration(true)
        }.also {
            notificationManager.createNotificationChannel(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
