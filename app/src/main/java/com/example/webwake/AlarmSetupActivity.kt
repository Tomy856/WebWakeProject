package com.example.webwake

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar

class AlarmSetupActivity : AppCompatActivity() {

    private lateinit var alarmStorage: AlarmStorage
    private var existingAlarm: Alarm? = null

    // 選択中の曜日セット (0=日〜6=土)
    private val selectedDays = mutableSetOf<Int>()

    // 特定日付 "YYYY-MM-DD"、空なら曜日繰り返し
    private var specificDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_setup)

        alarmStorage = AlarmStorage(this)

        val timePicker       = findViewById<TimePicker>(R.id.timePicker)
        val selectedDateText = findViewById<TextView>(R.id.selectedDateText)
        val calendarButton   = findViewById<ImageButton>(R.id.calendarButton)
        val excludeSwitch    = findViewById<SwitchMaterial>(R.id.excludeHolidaysSwitch)
        val labelEditText    = findViewById<EditText>(R.id.labelEditText)
        val urlEditText      = findViewById<EditText>(R.id.urlEditText)
        val cancelButton     = findViewById<Button>(R.id.cancelButton)
        val saveButton       = findViewById<Button>(R.id.saveButton)

        val dayViews = listOf(
            findViewById<TextView>(R.id.daySunday),
            findViewById<TextView>(R.id.dayMonday),
            findViewById<TextView>(R.id.dayTuesday),
            findViewById<TextView>(R.id.dayWednesday),
            findViewById<TextView>(R.id.dayThursday),
            findViewById<TextView>(R.id.dayFriday),
            findViewById<TextView>(R.id.daySaturday)
        )
        val dayActiveColors = listOf(
            0xFFE05050.toInt(), // 日: 赤
            0xFFCCCCCC.toInt(), // 月
            0xFFCCCCCC.toInt(), // 火
            0xFFCCCCCC.toInt(), // 水
            0xFFCCCCCC.toInt(), // 木
            0xFFCCCCCC.toInt(), // 金
            0xFF5080E0.toInt()  // 土: 青
        )
        val dayInactiveColor = 0xFF666666.toInt()

        timePicker.setIs24HourView(false)

        // 編集モードの場合、既存データを反映
        val alarmId = intent.getLongExtra("ALARM_ID", -1)
        if (alarmId != -1L) {
            existingAlarm = alarmStorage.loadAlarms().find { it.id == alarmId }
            existingAlarm?.let { alarm ->
                timePicker.hour   = alarm.hour
                timePicker.minute = alarm.minute
                labelEditText.setText(alarm.label)
                urlEditText.setText(alarm.url)
                excludeSwitch.isChecked = alarm.excludeHolidays
                specificDate = alarm.specificDate
                selectedDays.addAll(alarm.repeatDays)

                // 日付テキスト更新
                if (specificDate.isNotEmpty()) {
                    selectedDateText.text = formatSpecificDate(specificDate)
                }
            }
        }

        // 曜日ボタンの初期色を反映
        dayViews.forEachIndexed { index, tv ->
            updateDayView(tv, index, selectedDays.contains(index), dayActiveColors, dayInactiveColor)
        }

        // 曜日タップ
        dayViews.forEachIndexed { index, tv ->
            tv.setOnClickListener {
                if (selectedDays.contains(index)) selectedDays.remove(index)
                else selectedDays.add(index)
                updateDayView(tv, index, selectedDays.contains(index), dayActiveColors, dayInactiveColor)
                // 曜日を選んだら特定日付をクリア
                if (selectedDays.isNotEmpty()) {
                    specificDate = ""
                    selectedDateText.text = "繰り返し"
                }
            }
        }

        // カレンダーボタン
        calendarButton.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    specificDate = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                    selectedDateText.text = formatSpecificDate(specificDate)
                    // 特定日付を選んだら曜日繰り返しをクリア
                    selectedDays.clear()
                    dayViews.forEachIndexed { index, tv ->
                        updateDayView(tv, index, false, dayActiveColors, dayInactiveColor)
                    }
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // キャンセル
        cancelButton.setOnClickListener { finish() }

        // 保存
        saveButton.setOnClickListener {
            saveAlarm(timePicker, labelEditText, urlEditText, excludeSwitch)
        }
    }

    private fun updateDayView(
        tv: TextView,
        index: Int,
        isSelected: Boolean,
        activeColors: List<Int>,
        inactiveColor: Int
    ) {
        if (isSelected) {
            tv.setTextColor(activeColors[index])
            tv.setBackgroundResource(R.drawable.day_selected_bg)
        } else {
            tv.setTextColor(inactiveColor)
            tv.background = null
        }
    }

    // "YYYY-MM-DD" → 「明日-2月14日(土)」などに変換
    private fun formatSpecificDate(dateStr: String): String {
        if (dateStr.isEmpty()) return "今日"
        return try {
            val parts = dateStr.split("-")
            val year  = parts[0].toInt()
            val month = parts[1].toInt()
            val day   = parts[2].toInt()
            val cal   = Calendar.getInstance().apply { set(year, month - 1, day) }
            val dayOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")[cal.get(Calendar.DAY_OF_WEEK) - 1]

            val today    = Calendar.getInstance()
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val prefix = when {
                cal.get(Calendar.YEAR)         == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR)  == today.get(Calendar.DAY_OF_YEAR)    -> "今日-"
                cal.get(Calendar.YEAR)         == tomorrow.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR)  == tomorrow.get(Calendar.DAY_OF_YEAR) -> "明日-"
                else -> ""
            }
            "${prefix}${month}月${day}日($dayOfWeek)"
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun saveAlarm(
        timePicker: TimePicker,
        labelEditText: EditText,
        urlEditText: EditText,
        excludeSwitch: SwitchMaterial
    ) {
        val hour   = timePicker.hour
        val minute = timePicker.minute
        val label  = labelEditText.text.toString().trim()
        val url    = urlEditText.text.toString().trim()
        val excludeHolidays = excludeSwitch.isChecked

        val alarms = alarmStorage.loadAlarms()

        val alarm = if (existingAlarm != null) {
            existingAlarm!!.copy(
                hour            = hour,
                minute          = minute,
                label           = label,
                url             = url,
                repeatDays      = selectedDays.toSet(),
                excludeHolidays = excludeHolidays,
                specificDate    = specificDate,
                isEnabled       = true
            )
        } else {
            Alarm(
                id              = alarmStorage.generateId(),
                hour            = hour,
                minute          = minute,
                label           = label,
                url             = url,
                repeatDays      = selectedDays.toSet(),
                excludeHolidays = excludeHolidays,
                specificDate    = specificDate,
                isEnabled       = true
            )
        }

        if (existingAlarm != null) {
            val index = alarms.indexOfFirst { it.id == alarm.id }
            if (index != -1) alarms[index] = alarm
        } else {
            alarms.add(alarm)
        }
        alarmStorage.saveAlarms(alarms)

        // 権限チェック
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "アラームの権限がありません", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }

        try {
            AlarmScheduler.schedule(this, alarm)
            Toast.makeText(this, "${hour}時${minute}分にアラームをセットしました", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "権限エラー: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
