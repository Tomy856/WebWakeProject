package com.example.webwake

import android.app.AlarmManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var alarmRecyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var addAlarmButton: ImageButton
    private lateinit var normalHeader: LinearLayout
    private lateinit var selectionHeader: LinearLayout
    private lateinit var normalBottomBar: LinearLayout
    private lateinit var selectionBottomBar: LinearLayout
    private lateinit var selectionCount: TextView
    private lateinit var cancelSelection: TextView
    private lateinit var selectAllCheck: ImageView
    private lateinit var actionTurnOff: LinearLayout
    private lateinit var actionDelete: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var nextAlarmInfo: LinearLayout
    private lateinit var nextAlarmCountdown: TextView
    private lateinit var nextAlarmDate: TextView
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var alarmStorage: AlarmStorage
    private val alarmList = mutableListOf<Alarm>()

    // 1分ごとにカウントダウンを更新するハンドラー
    private val handler = Handler(Looper.getMainLooper())
    private val updateCountdownRunnable = object : Runnable {
        override fun run() {
            updateNextAlarmHeader()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alarmStorage = AlarmStorage(this)

        alarmRecyclerView = findViewById(R.id.alarmRecyclerView)
        emptyText = findViewById(R.id.emptyText)
        addAlarmButton = findViewById(R.id.addAlarmButton)
        normalHeader = findViewById(R.id.normalHeader)
        selectionHeader = findViewById(R.id.selectionHeader)
        normalBottomBar = findViewById(R.id.normalBottomBar)
        selectionBottomBar = findViewById(R.id.selectionBottomBar)
        selectionCount = findViewById(R.id.selectionCount)
        cancelSelection = findViewById(R.id.cancelSelection)
        selectAllCheck = findViewById(R.id.selectAllCheck)
        actionTurnOff = findViewById(R.id.actionTurnOff)
        actionDelete = findViewById(R.id.actionDelete)
        headerTitle = findViewById(R.id.headerTitle)
        nextAlarmInfo = findViewById(R.id.nextAlarmInfo)
        nextAlarmCountdown = findViewById(R.id.nextAlarmCountdown)
        nextAlarmDate = findViewById(R.id.nextAlarmDate)

        alarmAdapter = AlarmAdapter(
            alarmList,
            onAlarmClick = { alarm -> editAlarm(alarm) },
            onToggle = { alarm, isEnabled -> toggleAlarm(alarm, isEnabled) },
            onLongClick = { enterSelectionMode() }
        )

        alarmRecyclerView.layoutManager = LinearLayoutManager(this)
        alarmRecyclerView.adapter = alarmAdapter

        addAlarmButton.setOnClickListener { checkPermissionAndOpenSetup() }
        cancelSelection.setOnClickListener { exitSelectionMode() }
        selectAllCheck.setOnClickListener {
            alarmAdapter.selectAll()
            updateSelectionCount()
            selectAllCheck.setImageResource(android.R.drawable.checkbox_on_background)
        }
        actionTurnOff.setOnClickListener { turnOffSelected() }
        actionDelete.setOnClickListener { deleteSelected() }
    }

    override fun onResume() {
        super.onResume()
        loadAlarms()
        handler.post(updateCountdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateCountdownRunnable)
    }

    override fun onBackPressed() {
        if (alarmAdapter.isSelectionMode) exitSelectionMode()
        else super.onBackPressed()
    }

    private fun loadAlarms() {
        alarmList.clear()
        alarmList.addAll(alarmStorage.loadAlarms())
        alarmAdapter.notifyDataSetChanged()
        updateEmptyView()
        updateNextAlarmHeader()
    }

    private fun updateEmptyView() {
        if (alarmList.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            alarmRecyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            alarmRecyclerView.visibility = View.VISIBLE
        }
    }

    // 次のアラームを探してヘッダーを更新
    private fun updateNextAlarmHeader() {
        val enabledAlarms = alarmList.filter { it.isEnabled }

        if (enabledAlarms.isEmpty()) {
            // アラームなし → 「アラーム」タイトルを表示
            headerTitle.visibility = View.VISIBLE
            nextAlarmInfo.visibility = View.GONE
            return
        }

        // 次に鳴るアラームを計算
        val nextTrigger = enabledAlarms
            .mapNotNull { alarm -> getNextTriggerTime(alarm)?.let { time -> Pair(alarm, time) } }
            .minByOrNull { it.second }

        if (nextTrigger == null) {
            headerTitle.visibility = View.VISIBLE
            nextAlarmInfo.visibility = View.GONE
            return
        }

        val (alarm, triggerMillis) = nextTrigger
        val now = System.currentTimeMillis()
        val diffMillis = triggerMillis - now

        // 残り時間を計算
        val totalMinutes = (diffMillis / 1000 / 60).toInt()
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60

        // 「XX日XX時間XX分後にアラームが鳴動」
        val countdownText = buildString {
            if (days > 0) append("${days}日")
            if (hours > 0) append("${hours}時間")
            if (minutes > 0 || (days == 0 && hours == 0)) append("${minutes}分")
            append("後に\nアラームが鳴動")
        }

        // 「2月14日(土) 午前6:00」
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val period = if (alarm.hour < 12) "午前" else "午後"
        val displayHour = when {
            alarm.hour == 0 -> 12
            alarm.hour > 12 -> alarm.hour - 12
            else -> alarm.hour
        }
        val dateText = "${month}月${day}日(${dayOfWeek}) ${period}${displayHour}:${String.format("%02d", alarm.minute)}"

        headerTitle.visibility = View.GONE
        nextAlarmInfo.visibility = View.VISIBLE
        nextAlarmCountdown.text = countdownText
        nextAlarmDate.text = dateText
    }

    // 次のトリガー時刻をミリ秒で返す
    private fun getNextTriggerTime(alarm: Alarm): Long? {
        val now = System.currentTimeMillis()

        return if (alarm.repeatDays.isEmpty()) {
            // 繰り返しなし
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        } else {
            // 繰り返しあり: 最も近い曜日を探す
            alarm.repeatDays.mapNotNull { dayOfWeek ->
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                    set(Calendar.MINUTE, alarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_WEEK, dayOfWeek + 1)
                    if (timeInMillis <= now) add(Calendar.WEEK_OF_YEAR, 1)
                }.timeInMillis
            }.minOrNull()
        }
    }

    private fun enterSelectionMode() {
        normalHeader.visibility = View.GONE
        selectionHeader.visibility = View.VISIBLE
        normalBottomBar.visibility = View.GONE
        selectionBottomBar.visibility = View.VISIBLE
        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        alarmAdapter.exitSelectionMode()
        normalHeader.visibility = View.VISIBLE
        selectionHeader.visibility = View.GONE
        normalBottomBar.visibility = View.VISIBLE
        selectionBottomBar.visibility = View.GONE
        selectAllCheck.setImageResource(android.R.drawable.checkbox_off_background)
    }

    private fun updateSelectionCount() {
        selectionCount.text = "${alarmAdapter.getSelectedCount()}件選択"
    }

    private fun turnOffSelected() {
        val selectedIds = alarmAdapter.getSelectedIds()
        if (selectedIds.isEmpty()) return
        val alarms = alarmStorage.loadAlarms()
        alarms.forEachIndexed { index, alarm ->
            if (selectedIds.contains(alarm.id)) {
                alarms[index] = alarm.copy(isEnabled = false)
                AlarmScheduler.cancel(this, alarm)
            }
        }
        alarmStorage.saveAlarms(alarms)
        exitSelectionMode()
        loadAlarms()
    }

    private fun deleteSelected() {
        val selectedIds = alarmAdapter.getSelectedIds()
        if (selectedIds.isEmpty()) return
        val alarms = alarmStorage.loadAlarms()
        val toDelete = alarms.filter { selectedIds.contains(it.id) }
        toDelete.forEach { AlarmScheduler.cancel(this, it) }
        alarms.removeAll(toDelete)
        alarmStorage.saveAlarms(alarms)
        exitSelectionMode()
        loadAlarms()
    }

    private fun checkPermissionAndOpenSetup(alarmId: Long = -1) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent().apply {
                    action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    data = "package:$packageName".toUri()
                })
                return
            }
        }
        val intent = Intent(this, AlarmSetupActivity::class.java)
        if (alarmId != -1L) intent.putExtra("ALARM_ID", alarmId)
        startActivity(intent)
    }

    private fun editAlarm(alarm: Alarm) {
        checkPermissionAndOpenSetup(alarm.id)
    }

    private fun toggleAlarm(alarm: Alarm, isEnabled: Boolean) {
        val updatedAlarm = alarm.copy(isEnabled = isEnabled)
        val alarms = alarmStorage.loadAlarms()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
            alarmStorage.saveAlarms(alarms)
        }
        if (isEnabled) AlarmScheduler.schedule(this, updatedAlarm)
        else AlarmScheduler.cancel(this, updatedAlarm)
        updateNextAlarmHeader()
    }
}
