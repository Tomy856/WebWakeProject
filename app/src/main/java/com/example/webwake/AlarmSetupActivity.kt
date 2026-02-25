package com.example.webwake

import android.app.ActivityManager
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
        
        // Edge-to-Edgeを有効化
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
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
            0xFFFF5555.toInt(), // 日: 赤
            0xFFCCCCCC.toInt(), // 月
            0xFFCCCCCC.toInt(), // 火
            0xFFCCCCCC.toInt(), // 水
            0xFFCCCCCC.toInt(), // 木
            0xFFCCCCCC.toInt(), // 金
            0xFF55BBFF.toInt()  // 土: 水色
        )
        val dayInactiveColor = 0xFF666666.toInt()

        timePicker.setIs24HourView(false)

        // TimePickerのスピナーにコロンを追加
        customizeTimePicker(timePicker)

        // 共有Intent（YouTubeなど）からURLを受け取る
        val sharedUrl: String? = if (intent?.action == Intent.ACTION_SEND &&
            intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { text ->
                    // YouTubeの共有テキストは「タイトル https://youtu.be/xxx」の形式なので
                    // URLだけを抽出する
                    Regex("https?://\\S+").find(text)?.value
                }
        } else null

        // 新規作成時のデフォルト値
        timePicker.hour   = 6
        timePicker.minute = 0
        urlEditText.setText(sharedUrl ?: "https://www.youtube.com")

        // 初期表示を更新
        updateDateText(timePicker, selectedDateText)

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

                // 日付テキスト更新（曜日・特定日付を反映）
                updateDateText(timePicker, selectedDateText)
            }
        }

        // 曜日ボタンの初期色を反映
        dayViews.forEachIndexed { index, tv ->
            updateDayView(tv, index, selectedDays.contains(index), dayActiveColors, dayInactiveColor)
        }

        // TimePickerの値が変わったときに日付テキストを更新
        timePicker.setOnTimeChangedListener { _, _, _ ->
            updateDateText(timePicker, selectedDateText)
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
                    updateDateText(timePicker, selectedDateText)
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
                    // 特定日付を選んだら曜日繰り返しをクリア
                    selectedDays.clear()
                    dayViews.forEachIndexed { index, tv ->
                        updateDayView(tv, index, false, dayActiveColors, dayInactiveColor)
                    }
                    updateDateText(timePicker, selectedDateText)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // YouTubeボタン: ブラウザでYouTubeトップを開くだけ（URL欄は変更しない）
        val youtubeButton = findViewById<LinearLayout>(R.id.youtubeButton)
        youtubeButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            startActivity(intent)
        }

        // キャンセル
        cancelButton.setOnClickListener { finish() }

        // 保存
        saveButton.setOnClickListener {
            saveAlarm(timePicker, labelEditText, urlEditText, excludeSwitch)
        }
    }

    // 日付テキストを更新
    private fun updateDateText(timePicker: TimePicker, textView: TextView) {
        val now = Calendar.getInstance()
        val selectedTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timePicker.hour)
            set(Calendar.MINUTE, timePicker.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayNames = listOf("日", "月", "火", "水", "木", "金", "土")

        when {
            // 特定日付が設定されている場合
            specificDate.isNotEmpty() -> {
                textView.text = formatSpecificDate(specificDate)
            }
            // 曜日が選択されている場合
            selectedDays.isNotEmpty() -> {
                if (selectedDays.size == 7) {
                    textView.text = "毎日"
                } else {
                    val dayText = selectedDays.sorted().joinToString(" ") { dayNames[it] }
                    textView.text = "毎週 $dayText"
                }
            }
            // 曜日も特定日付も未選択（一回限り）
            else -> {
                // 現在時刻より過去なら明日、そうでなければ今日
                val isToday = selectedTime.timeInMillis > now.timeInMillis
                
                val targetCal = if (isToday) now else now.apply { add(Calendar.DAY_OF_YEAR, 1) }
                val month = targetCal.get(Calendar.MONTH) + 1
                val day = targetCal.get(Calendar.DAY_OF_MONTH)
                val dayOfWeek = dayNames[targetCal.get(Calendar.DAY_OF_WEEK) - 1]
                
                val prefix = if (isToday) "今日" else "明日"
                textView.text = "${prefix}-${month}月${day}日($dayOfWeek)"
            }
        }
    }

    private fun updateDayView(
        tv: TextView,
        index: Int,
        isSelected: Boolean,
        activeColors: List<Int>,
        inactiveColor: Int
    ) {
        // 色は常に日曜日＝赤、土曜日＝青、その他＝白
        tv.setTextColor(activeColors[index])
        
        if (isSelected) {
            tv.alpha = 1.0f  // 選択時は不透明
            tv.setBackgroundResource(R.drawable.day_selected_bg)
        } else {
            tv.alpha = 0.4f  // 非選択時は半透明
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
            // 現在時刻からアラームまでの残り時間を計算
            val now = System.currentTimeMillis()
            val nextTrigger = if (selectedDays.isEmpty() && specificDate.isEmpty()) {
                // 一回限り: 今日か明日
                java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now) add(java.util.Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
            } else if (specificDate.isNotEmpty()) {
                // 特定日付
                val parts = specificDate.split("-")
                java.util.Calendar.getInstance().apply {
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(),
                        hour, minute, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else {
                // 曜日繰り返し: 一番近い発火時刻
                selectedDays.mapNotNull { dow ->
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, hour)
                        set(java.util.Calendar.MINUTE, minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                        set(java.util.Calendar.DAY_OF_WEEK, dow + 1)
                        if (timeInMillis <= now) add(java.util.Calendar.WEEK_OF_YEAR, 1)
                    }.timeInMillis
                }.minOrNull() ?: now
            }
            val diffMin = ((nextTrigger - now) / 1000 / 60).toInt()
            val days    = diffMin / (60 * 24)
            val hours   = (diffMin % (60 * 24)) / 60
            val mins    = diffMin % 60
            val message = when {
                diffMin < 1       -> "まもなくアラームが鳴ります"
                days > 0 && hours == 0 && mins == 0 -> "${days}日後にアラームが鳴ります"
                days > 0 && hours == 0 -> "${days}日${mins}分後にアラームが鳴ります"
                days > 0 && mins == 0  -> "${days}日${hours}時間後にアラームが鳴ります"
                days > 0               -> "${days}日${hours}時間${mins}分後にアラームが鳴ります"
                hours > 0 && mins == 0 -> "${hours}時間後にアラームが鳴ります"
                hours > 0              -> "${hours}時間${mins}分後にアラームが鳴ります"
                else                   -> "${mins}分後にアラームが鳴ります"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // YouTubeの共有から起動した場合は、YouTubeも閉じてMainActivityに戻る
            if (intent?.action == Intent.ACTION_SEND) {
                // YouTubeのタスクをOverviewから除去する
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                am.appTasks.forEach { task ->
                    val info = task.taskInfo
                    val pkg = info.baseActivity?.packageName ?: ""
                    // 自分以外のタスク（YouTube等）をOverviewから除去
                    if (pkg != packageName) {
                        task.finishAndRemoveTask()
                    }
                }
                // 自分のタスクをOverviewから消してからMainActivityへ
                finishAndRemoveTask()
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(mainIntent)
            } else {
                finish()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "権限エラー: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // TimePickerのスピナー間のコロンを整理（既存コロンを使い回す）
    private fun customizeTimePicker(timePicker: TimePicker) {
        try {
            val timePickerLayout = timePicker.getChildAt(0) as? android.view.ViewGroup ?: return
            val spinnersLayout = timePickerLayout.getChildAt(1) as? android.view.ViewGroup ?: return

            // 既存の子ビューをすべて調べて TextView（コロン候補）を探す
            // 実機では既にコロンのTextViewが含まれている場合がある
            var existingColon: TextView? = null
            for (i in 0 until spinnersLayout.childCount) {
                val child = spinnersLayout.getChildAt(i)
                if (child is TextView) {
                    // 既存コロンを見つけたらスタイルだけ上書きして使い回す
                    child.text = ":"
                    child.textSize = 32f
                    child.setTextColor(0xFFFFFFFF.toInt())
                    child.gravity = android.view.Gravity.CENTER
                    existingColon = child
                    break
                }
            }

            // 既存コロンがなかった場合のみ新規追加
            if (existingColon == null && spinnersLayout.childCount >= 2) {
                val colonView = TextView(this).apply {
                    text = ":"
                    textSize = 32f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = android.view.Gravity.CENTER
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply { gravity = android.view.Gravity.CENTER }
                }
                spinnersLayout.addView(colonView, 1)
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmSetup", "Failed to customize TimePicker: ${e.message}", e)
        }
    }
}
