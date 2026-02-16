package com.example.webwake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra("ALARM_ID", -1) ?: -1
        val alarmUrl = intent?.getStringExtra("ALARM_URL") ?: ""

        // URLが設定されていればブラウザで開く
        if (alarmUrl.isNotEmpty()) {
            openUrl(alarmUrl)
        }

        // フォアグラウンドサービスとして通知を表示（システム要件）
        val notification = buildNotification(alarmId)
        startForeground(NOTIFICATION_ID, notification)

        // URL起動後すぐにサービスを終了
        stopSelf()

        return START_NOT_STICKY
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(alarmId: Long): Notification {
        val stopIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = "STOP_ALARM"
            putExtra("ALARM_ID", alarmId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("アラーム")
            .setContentText("URLを開きました")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "閉じる", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "アラーム通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "アラームの通知チャンネル"
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
