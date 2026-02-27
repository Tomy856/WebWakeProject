package com.example.webwake

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

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
                if (alarm.repeatDays.isEmpty()) {
                    rescheduleToNextWeekday(context, storage, alarms, alarm)
                }
                return
            }
        }

        // ---- 一回限り → 発火後にOFF ----
        if (alarm.repeatDays.isEmpty()) {
            val index = alarms.indexOfFirst { it.id == alarmId }
            if (index != -1) {
                alarms[index] = alarm.copy(isEnabled = false)
                storage.saveAlarms(alarms)
            }
        } else {
            // ---- 毎週繰り返し → 次回の同じ曜日を再スケジュール（15分前通知も含む） ----
            AlarmScheduler.schedule(context, alarm)
        }

        val alarmUrl = alarm.url

        // 15分前予告通知をキャンセル（アラームが鳴ったので不要）
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(PreAlarmReceiver.notificationId(alarmId))

        // 古い通知チャンネルを削除（サウンドが焼き付いている可能性）
        nm.deleteNotificationChannel("alarm_channel2")
        nm.deleteNotificationChannel("alarm_channel")

        // RingerService を起動（サウンド・バイブ・画面起動はすべてRingerService内で実施）
        val ringerIntent = Intent(context, AlarmRingerService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarmUrl)
        }
        context.startForegroundService(ringerIntent)
        android.util.Log.d("AlarmReceiver", "AlarmRingerService started")
    }

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
            add(Calendar.DAY_OF_YEAR, 1)
        }

        var maxDays = 14
        while (maxDays-- > 0) {
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
            val isHoliday = JapaneseHolidayChecker.isHoliday(cal)
            if (!isWeekend && !isHoliday) break
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

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
}
