package com.example.webwake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

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

        if (alarm == null) return

        // ---- 祝日除外チェック ----
        if (alarm.excludeHolidays) {
            val today = Calendar.getInstance()
            if (JapaneseHolidayChecker.isHoliday(today)) {
                android.util.Log.d("AlarmReceiver", "Today is a holiday, skipping alarm")
                // 曜日繰り返しの場合は次の発火をそのままAlarmManagerが持っているのでスキップのみ
                // 一回限り・特定日付の場合は翌平日に再スケジュール
                if (alarm.repeatDays.isEmpty()) {
                    rescheduleToNextWeekday(context, storage, alarms, alarm)
                }
                return
            }
        }

        // ---- 日付指定 or 何も指定なし（一回限り）→ 発火後にOFF ----
        if (alarm.repeatDays.isEmpty()) {
            val index = alarms.indexOfFirst { it.id == alarmId }
            if (index != -1) {
                alarms[index] = alarm.copy(isEnabled = false)
                storage.saveAlarms(alarms)
            }
        }

        val alarmUrl = alarm.url

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

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
        val isAppForegrounded = activityManager.runningAppProcesses?.any { proc ->
            proc.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            && proc.processName == context.packageName
        } ?: false

        android.util.Log.d("AlarmReceiver", "isAppForegrounded=$isAppForegrounded")

        if (isAppForegrounded) {
            try {
                context.startActivity(launchIntent)
                android.util.Log.d("AlarmReceiver", "startActivity succeeded (foreground)")
                return
            } catch (e: Exception) {
                android.util.Log.e("AlarmReceiver", "startActivity failed: ${e.message}")
            }
        }

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

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
        }
        context.startForegroundService(serviceIntent)
        android.util.Log.d("AlarmReceiver", "FGS started for background activity launch")
    }

    /**
     * 一回限り・特定日付のアラームが祝日でスキップされた場合、
     * 翌平日（祝日でない日）に再スケジュールする
     */
    private fun rescheduleToNextWeekday(
        context: Context,
        storage: AlarmStorage,
        alarms: MutableList<Alarm>,
        alarm: Alarm
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1) // 翌日から探す
        }

        // 祝日・土日でない日まで進める
        var maxDays = 14
        while (maxDays-- > 0) {
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
            val isHoliday = JapaneseHolidayChecker.isHoliday(cal)
            if (!isWeekend && !isHoliday) break
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        android.util.Log.d("AlarmReceiver", "Rescheduled to: ${cal.time}")

        // ストレージ更新（isEnabled=true、特定日付を新しい日付に更新）
        val newDate = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val updatedAlarm = alarm.copy(isEnabled = true, specificDate = newDate)
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
            storage.saveAlarms(alarms)
        }

        AlarmScheduler.schedule(context, updatedAlarm)
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
