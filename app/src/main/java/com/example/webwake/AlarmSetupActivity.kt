package com.example.webwake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.util.Calendar

class AlarmSetupActivity : AppCompatActivity() {

    private var alarmId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_setup)

        // 編集モードかどうかをチェック
        alarmId = intent.getLongExtra("ALARM_ID", -1)

        // UIパーツの紐付け
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val setAlarmButton = findViewById<Button>(R.id.setAlarmButton)

        // 24時間表示にする
        timePicker.setIs24HourView(true)

        // 編集モードの場合、既存の時刻を設定
        if (alarmId != -1L) {
            // TODO: データベースから既存のアラーム情報を読み込む
        }

        setAlarmButton.setOnClickListener {
            saveAlarm(timePicker)
        }
    }

    private fun saveAlarm(timePicker: TimePicker) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        // Android 12(S)以降で、正確なアラームの権限があるかチェック
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // 権限がない場合、設定画面へ飛ばす
                val intent = Intent().apply {
                    action = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
                Toast.makeText(this, "アラームの権限をONにしてください", Toast.LENGTH_LONG).show()
                return
            }
        }

        // TimePickerから時間と分を取得
        val hour = timePicker.hour
        val minute = timePicker.minute

        // カレンダーを設定
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // もし設定した時間が「今」より過去なら、明日の同じ時間にセットする
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // AlarmReceiverを呼ぶ
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // アラームをセット
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            Toast.makeText(this, "${hour}時${minute}分にアラームをセットしました", Toast.LENGTH_SHORT).show()

            // TODO: データベースにアラーム情報を保存

            // メイン画面に戻る
            finish()
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "権限エラー: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}