package com.example.webwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val storage = AlarmStorage(context)
            val alarms = storage.loadAlarms()
            alarms.filter { it.isEnabled }.forEach { alarm ->
                AlarmScheduler.schedule(context, alarm)
            }
        }
    }
}
