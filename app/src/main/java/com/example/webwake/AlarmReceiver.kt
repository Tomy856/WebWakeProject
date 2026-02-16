package com.example.webwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("ALARM_ID", -1)

        val storage = AlarmStorage(context)
        val alarms  = storage.loadAlarms()
        val alarm   = alarms.find { it.id == alarmId }

        // 日付指定 or 何も指定なし（一回限り）→ 発火後にOFF扱いにする
        if (alarm != null && alarm.repeatDays.isEmpty()) {
            val index = alarms.indexOfFirst { it.id == alarmId }
            if (index != -1) {
                alarms[index] = alarm.copy(isEnabled = false)
                storage.saveAlarms(alarms)
            }
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_URL", alarm?.url ?: "")
        }
        context.startForegroundService(serviceIntent)
    }
}
