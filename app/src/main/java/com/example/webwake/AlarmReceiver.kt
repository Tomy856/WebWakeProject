package com.example.webwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", intent.getLongExtra("ALARM_ID", -1))
        }
        context.startForegroundService(serviceIntent)
    }
}