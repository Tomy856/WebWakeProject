package com.example.webwake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID_FGS  = "alarm_fgs_channel"
        const val NOTIFICATION_ID_FGS = 1003
    }

    override fun onCreate() {
        super.onCreate()
        createFgsChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId  = intent?.getLongExtra("ALARM_ID", -1) ?: -1
        val alarmUrl = intent?.getStringExtra("ALARM_URL") ?: ""

        android.util.Log.d("AlarmService", "onStartCommand: url=$alarmUrl")

        // FGSとして起動（必須）
        val fgsNotif = NotificationCompat.Builder(this, CHANNEL_ID_FGS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("アラーム起動中")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(NOTIFICATION_ID_FGS, fgsNotif)

        // FGS確立後にstartActivity（バックグラウンド画面ONで有効）
        val launchIntent = Intent(this, AlarmLaunchActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                startActivity(launchIntent)
                android.util.Log.d("AlarmService", "startActivity succeeded from FGS")
            } catch (e: Exception) {
                android.util.Log.e("AlarmService", "startActivity failed from FGS: ${e.message}")
            }
            stopSelf()
        }, 300)

        return START_NOT_STICKY
    }

    private fun createFgsChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID_FGS) != null) return
        NotificationChannel(CHANNEL_ID_FGS, "アラームサービス", NotificationManager.IMPORTANCE_MIN)
            .also { nm.createNotificationChannel(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
