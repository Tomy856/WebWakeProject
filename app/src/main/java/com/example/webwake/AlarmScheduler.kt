package com.example.webwake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {

    // スケジュールして、実際に設定した最初の発火時刻(ms)を返す
    fun schedule(context: Context, alarm: Alarm): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return 0L
        }

        return if (alarm.repeatDays.isEmpty()) {
            val calendar = Calendar.getInstance().apply {
                if (alarm.specificDate.isNotEmpty()) {
                    val parts = alarm.specificDate.split("-")
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // specificDateありなしにかかわらず、設定時刻が現在以前なら習日へ
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            setAlarm(context, alarmManager, alarm, calendar.timeInMillis)
            calendar.timeInMillis
        } else {
            // 曜日繰り返し: 最初の発火時刻（最も近い曜日）を返す
            val now = System.currentTimeMillis()
            var firstTrigger = Long.MAX_VALUE
            alarm.repeatDays.forEach { dayOfWeek ->
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                    set(Calendar.MINUTE, alarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_WEEK, dayOfWeek + 1)
                    if (timeInMillis <= now) add(Calendar.WEEK_OF_YEAR, 1)
                }
                if (calendar.timeInMillis < firstTrigger) firstTrigger = calendar.timeInMillis
                setAlarm(context, alarmManager, alarm, calendar.timeInMillis, dayOfWeek)
            }
            firstTrigger
        }
    }

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (alarm.repeatDays.isEmpty()) {
            cancelAlarm(context, alarmManager, alarm.id.toInt())
        } else {
            alarm.repeatDays.forEach { dayOfWeek ->
                cancelAlarm(context, alarmManager, "${alarm.id}_$dayOfWeek".hashCode())
            }
        }

        // 15分前通知もキャンセル
        cancelPreAlarmNotification(context, alarm)
    }

    private fun schedulePreAlarmNotification(
        context: Context, alarm: Alarm, triggerTime: Long, dayOfWeek: Int? = null
    ) {
        val now = System.currentTimeMillis()
        if (triggerTime - now <= 0) return  // すでに過ぎたアラームはスキップ

        val preTime = triggerTime - 15 * 60 * 1000L
        val actualPreTime = if (preTime <= now) now + 1000L else preTime

        // 曜日ごとに別リクエストコード（上書き防止）
        val reqCode = if (dayOfWeek != null)
            "${alarm.id}_pre_$dayOfWeek".hashCode()
        else
            PreAlarmReceiver.requestCode(alarm.id)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PreAlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("IS_REPEAT", alarm.repeatDays.isNotEmpty())
            putExtra("ALARM_HOUR", alarm.hour)
            putExtra("ALARM_MINUTE", alarm.minute)
        }
        val pi = PendingIntent.getBroadcast(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, actualPreTime, pi)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelPreAlarmNotification(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarm.repeatDays.isEmpty()) {
            val pi = PendingIntent.getBroadcast(
                context, PreAlarmReceiver.requestCode(alarm.id),
                Intent(context, PreAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { alarmManager.cancel(it) }
        } else {
            // 曜日ごとにキャンセル
            alarm.repeatDays.forEach { dayOfWeek ->
                val reqCode = "${alarm.id}_pre_$dayOfWeek".hashCode()
                val pi = PendingIntent.getBroadcast(
                    context, reqCode,
                    Intent(context, PreAlarmReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                pi?.let { alarmManager.cancel(it) }
            }
        }
    }

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        alarm: Alarm,
        triggerTime: Long,
        dayOfWeek: Int? = null
    ) {
        val requestCode = if (dayOfWeek != null) "${alarm.id}_$dayOfWeek".hashCode() else alarm.id.toInt()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock を使用: Androidが"本物のアラーム"と認識し、
        // 画面OFF時でもロック画面上に直接Activityを起動できる
        // showIntent = ステータスバーの時計アイコンタップ時に開く画面
        val showIntent = Intent(context, MainActivity::class.java)
        val showPi = PendingIntent.getActivity(
            context, 0, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, showPi),
                pendingIntent
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // 15分前通知をセット（曜日ごとに別コード）
        schedulePreAlarmNotification(context, alarm, triggerTime, dayOfWeek)
    }

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}
