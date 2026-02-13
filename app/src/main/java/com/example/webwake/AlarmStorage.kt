package com.example.webwake

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AlarmStorage(context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveAlarms(alarms: List<Alarm>) {
        val json = gson.toJson(alarms)
        prefs.edit().putString("alarm_list", json).apply()
    }

    fun loadAlarms(): MutableList<Alarm> {
        val json = prefs.getString("alarm_list", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Alarm>>() {}.type
        return gson.fromJson(json, type)
    }

    fun generateId(): Long {
        return System.currentTimeMillis()
    }
}