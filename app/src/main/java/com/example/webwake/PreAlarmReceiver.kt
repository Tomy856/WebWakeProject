package com.example.webwake

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class PreAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PRE_ALARM_OFF       = "com.example.webwake.ACTION_PRE_ALARM_OFF"
        const val ACTION_PRE_ALARM_OFF_TODAY = "com.example.webwake.ACTION_PRE_ALARM_OFF_TODAY"
        const val CHANNEL_ID = "pre_alarm_v1"

        fun notificationId(alarmId: Long) = (alarmId + 5000).toInt()
        fun requestCode(alarmId: Long)    = (alarmId + 5000).toInt()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId   = intent.getLongExtra("ALARM_ID", -1)
        val isRepeat  = intent.getBooleanExtra("IS_REPEAT", false)
        val alarmHour = intent.getIntExtra("ALARM_HOUR", -1)
        val alarmMin  = intent.getIntExtra("ALARM_MINUTE", -1)

        when (intent.action) {
            ACTION_PRE_ALARM_OFF       -> turnOffAlarm(context, alarmId, todayOnly = false)
            ACTION_PRE_ALARM_OFF_TODAY -> turnOffAlarm(context, alarmId, todayOnly = true)
            else -> showPreAlarmNotification(context, alarmId, isRepeat, alarmHour, alarmMin)
        }
    }

    private fun showPreAlarmNotification(
        context: Context, alarmId: Long, isRepeat: Boolean, alarmHour: Int, alarmMin: Int
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(nm)

        val period = if (alarmHour < 12) "午前" else "午後"
        val displayHour = when {
            alarmHour == 0 || alarmHour == 12 -> 12
            alarmHour > 12 -> alarmHour - 12
            else -> alarmHour
        }
        val timeText = "${period}${displayHour}:${String.format("%02d", alarmMin)}"

        val offAction = if (isRepeat) ACTION_PRE_ALARM_OFF_TODAY else ACTION_PRE_ALARM_OFF
        val offLabel  = if (isRepeat) "本日のみOFF" else "OFF"

        val offPi = PendingIntent.getBroadcast(
            context, (alarmId + 6000).toInt(),
            Intent(context, PreAlarmReceiver::class.java).apply {
                action = offAction
                putExtra("ALARM_ID", alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val tapPi = PendingIntent.getActivity(
            context, (alarmId + 7000).toInt(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("次のアラームをOFFにしますか？")
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapPi)
            .setAutoCancel(false)
            .addAction(0, offLabel, offPi)
            .build()

        nm.notify(notificationId(alarmId), notification)
    }

    private fun turnOffAlarm(context: Context, alarmId: Long, todayOnly: Boolean) {
        val storage = AlarmStorage(context)
        val alarms  = storage.loadAlarms().toMutableList()
        val index   = alarms.indexOfFirst { it.id == alarmId }
        if (index == -1) return
        val alarm = alarms[index]

        AlarmScheduler.cancel(context, alarm)

        if (todayOnly && alarm.repeatDays.isNotEmpty()) {
            // 本日のみOFF: 次の発火時刻を計算して即スケジュール（reactivateAlarmと同じ処理）
            val todayDow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
            val nextFire = alarm.repeatDays.mapNotNull { dow ->
                java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
                    set(java.util.Calendar.MINUTE, alarm.minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    set(java.util.Calendar.DAY_OF_WEEK, dow + 1)
                    // 今日は必ず除外、それ以外も過去なら次週へ
                    if (dow == todayDow || timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.WEEK_OF_YEAR, 1)
                }.timeInMillis
            }.minOrNull()

            if (nextFire != null) {
                // reactivateAlarmと同じ: isEnabled=true、isReactivated=true、lastScheduledMillisをセット
                val updated = alarm.copy(
                    isEnabled = true,
                    lastScheduledMillis = nextFire,
                    isReactivated = true,
                    showReactivateButton = false
                )
                alarms[index] = updated
                storage.saveAlarms(alarms)

                // scheduleExactOnceと同じ: nextFireの曜日でsetAlarmClock
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val targetDow = java.util.Calendar.getInstance().apply { timeInMillis = nextFire }.get(java.util.Calendar.DAY_OF_WEEK) - 1
                val reqCode = "${alarm.id}_${targetDow}".hashCode()
                val alarmIntent = Intent(context, AlarmReceiver::class.java).apply { putExtra("ALARM_ID", alarm.id) }
                val alarmPi = android.app.PendingIntent.getBroadcast(
                    context, reqCode, alarmIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val showPi = android.app.PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.setAlarmClock(
                        android.app.AlarmManager.AlarmClockInfo(nextFire, showPi), alarmPi
                    )
                } catch (e: SecurityException) { e.printStackTrace() }

                // 15分前通知もセット
                AlarmScheduler.cancelPreAlarmNotification(context, alarm)
                schedulePreAlarmNotificationForNext(context, alarm, nextFire, targetDow)
            }
        } else {
            // 完全OFF
            alarms[index] = alarm.copy(isEnabled = false)
            storage.saveAlarms(alarms)
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(notificationId(alarmId))
    }

    private fun schedulePreAlarmNotificationForNext(context: Context, alarm: Alarm, triggerTime: Long, dayOfWeek: Int) {
        val preTime = triggerTime - 15 * 60 * 1000L
        val now = System.currentTimeMillis()
        val actualPreTime = if (preTime <= now) now + 1000L else preTime
        val reqCode = "${alarm.id}_pre_$dayOfWeek".hashCode()
        val pi = android.app.PendingIntent.getBroadcast(
            context, reqCode,
            Intent(context, PreAlarmReceiver::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                putExtra("IS_REPEAT", true)
                putExtra("ALARM_HOUR", alarm.hour)
                putExtra("ALARM_MINUTE", alarm.minute)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, actualPreTime, pi)
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel(CHANNEL_ID, "アラーム前通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            }.also { nm.createNotificationChannel(it) }
        }
    }
}
