package com.example.webwake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("ALARM_ID", -1)

        val serviceIntent = Intent(context, AlarmService::class.java)
        context.stopService(serviceIntent)

        when (intent.action) {
            "SNOOZE_ALARM" -> snoozeAlarm(context, alarmId)
            "STOP_ALARM" -> rescheduleIfRepeat(context, alarmId)
        }
    }

    private fun snoozeAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }

        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                snoozeTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun rescheduleIfRepeat(context: Context, alarmId: Long) {
        val storage = AlarmStorage(context)
        val alarms  = storage.loadAlarms()
        val alarm   = alarms.find { it.id == alarmId } ?: return
        val index   = alarms.indexOfFirst { it.id == alarmId }

        if (alarm.repeatDays.isNotEmpty()) {
            // 曜日繰り返し: 次回を再スケジュール
            AlarmScheduler.schedule(context, alarm)
        } else {
            // 日付指定 or 何も指定なし: OFF扱いにする
            if (index != -1) {
                alarms[index] = alarm.copy(isEnabled = false)
                storage.saveAlarms(alarms)
            }
        }
    }
}
