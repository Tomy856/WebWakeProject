package com.example.webwake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID2     = "alarm_channel2"
        const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("ALARM_ID", -1)

        val storage = AlarmStorage(context)
        val alarms  = storage.loadAlarms()
        val alarm   = alarms.find { it.id == alarmId }

        android.util.Log.d("AlarmReceiver", "onReceive: alarmId=$alarmId url=${alarm?.url}")

        // 日付指定 or 何も指定なし（一回限り）→ 発火後にOFF扱いにする
        if (alarm != null && alarm.repeatDays.isEmpty()) {
            val index = alarms.indexOfFirst { it.id == alarmId }
            if (index != -1) {
                alarms[index] = alarm.copy(isEnabled = false)
                storage.saveAlarms(alarms)
            }
        }

        val alarmUrl = alarm?.url ?: ""

        val launchIntent = Intent(context, AlarmLaunchActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        createNotificationChannel(context)

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // アプリがフォアグラウンドにあるか確認
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
        val isAppForegrounded = activityManager.runningAppProcesses?.any { proc ->
            proc.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            && proc.processName == context.packageName
        } ?: false

        android.util.Log.d("AlarmReceiver", "isAppForegrounded=$isAppForegrounded")

        if (isAppForegrounded) {
            // フォアグラウンド → startActivityで直接遷移（ポップアップなし）
            try {
                context.startActivity(launchIntent)
                android.util.Log.d("AlarmReceiver", "startActivity succeeded (foreground)")
                return
            } catch (e: Exception) {
                android.util.Log.e("AlarmReceiver", "startActivity failed: ${e.message}")
            }
        }

        // バックグラウンド・画面OFF → fullScreenIntent通知 + FGS経由でstartActivity
        val alarmNotification = NotificationCompat.Builder(context, CHANNEL_ID2)
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
        notificationManager.notify(NOTIFICATION_ID, alarmNotification)
        android.util.Log.d("AlarmReceiver", "fullScreenIntent notification posted")

        // FGS経由でもstartActivityを試みる（バックグラウンド画面ON用）
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
        }
        context.startForegroundService(serviceIntent)
        android.util.Log.d("AlarmReceiver", "FGS started for background activity launch")
    }

    private fun createNotificationChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID2) != null) return

        NotificationChannel(CHANNEL_ID2, "アラーム通知", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "アラームの通知チャンネル"
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
        }.also { nm.createNotificationChannel(it) }
    }
}
