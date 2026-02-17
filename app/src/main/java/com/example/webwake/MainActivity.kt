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
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
        
        // Edge-to-Edgeを有効化
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
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
            onAlarmClick  = { alarm -> editAlarm(alarm) },
            onToggle      = { alarm, isEnabled -> toggleAlarm(alarm, isEnabled) },
            onLongClick   = { enterSelectionMode() },
            onReactivate  = { alarm, baseMillis -> reactivateAlarm(alarm, baseMillis) }
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
        
        // バックボタンの処理
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (alarmAdapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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

    private fun loadAlarms() {
        alarmList.clear()
        // アプリ起動時は showReactivateButton を全件リセット
        val stored = alarmStorage.loadAlarms()
        val reset = stored.map { it.copy(showReactivateButton = false) }
        if (reset != stored) alarmStorage.saveAlarms(reset)
        alarmList.addAll(reset)
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

        if (alarmList.isEmpty()) {
            // アラームが1件もない → 「アラーム」タイトルを表示
            headerTitle.text = "アラーム"
            headerTitle.visibility = View.VISIBLE
            nextAlarmInfo.visibility = View.GONE
            return
        }

        if (enabledAlarms.isEmpty()) {
            // アラームはあるが全てOFF → 「全てのアラームがOFF」を表示
            headerTitle.text = "全てのアラームがOFF"
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
        val totalHours   = totalMinutes / 60
        val days         = totalMinutes / (60 * 24)
        val hours        = (totalMinutes % (60 * 24)) / 60
        val minutes      = totalMinutes % 60

        // カウントダウン表示
        // 1分未満         → 「まもなくアラームが鳴動」
        // 24時間未満      → 「XX時間XX分後にアラームが鳴動」
        // 24時間〜48時間  → 「明日アラームが鳴動」
        // 48時間〜        → 「X日後にアラームが鳴動」
        val countdownText = when {
            totalMinutes < 1    -> "まもなく\nアラームが鳴動"
            totalHours < 24     -> buildString {
                if (hours > 0) append("${hours}時間")
                append("${minutes}分後に\nアラームが鳴動")
            }
            days == 1           -> "明日\nアラームが鳴動"
            else                -> "${days}日後に\nアラームが鳴動"
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

        // 「再度ON」で起動した場合は lastScheduledMillis（実際にセットした水曜6時等）をそのまま使う
        if (alarm.isReactivated && alarm.lastScheduledMillis > now) {
            return alarm.lastScheduledMillis
        }

        return if (alarm.repeatDays.isEmpty()) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        } else {
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

    // 「再度ON」ボタン用
    // nextTriggerMillis = AlarmAdapterが計算済みの発火時刻をそのまま受け取りセットするだけ
    private fun reactivateAlarm(alarm: Alarm, nextTriggerMillis: Long) {
        if (alarm.repeatDays.isEmpty()) {
            toggleAlarm(alarm, true)
            return
        }

        // 受け取った時刻をそのまま使用
        val nextTrigger = nextTriggerMillis

        // isEnabled=true、isReactivated=true、showReactivateButton=falseで保存
        val updatedAlarm = alarm.copy(
            isEnabled = true,
            lastScheduledMillis = nextTrigger,
            isReactivated = true,
            showReactivateButton = false
        )
        val alarms = alarmStorage.loadAlarms()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
            alarmStorage.saveAlarms(alarms)
        }
        val listIndex = alarmList.indexOfFirst { it.id == alarm.id }
        if (listIndex != -1) {
            alarmList[listIndex] = updatedAlarm
            alarmAdapter.notifyItemChanged(listIndex)
        }

        // nextTriggerの曜日を特定してその1つだけをセット
        // （全曜日の場合も「次に鳴る曜日」つ1つだけ正確にセットする）
        scheduleExactOnce(updatedAlarm, nextTrigger)
        updateNextAlarmHeader()
    }

    // nextTriggerMillisの日時にアラームを1つだけセットする
    private fun scheduleExactOnce(alarm: Alarm, nextTriggerMillis: Long) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) return
        }
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager

        // nextTriggerMillisの曜日を特定
        val targetCal = Calendar.getInstance().apply { timeInMillis = nextTriggerMillis }
        val targetDow = targetCal.get(Calendar.DAY_OF_WEEK) - 1  // 0=日〜6=土

        // その曜日の requestCode でセット
        val requestCode = "${alarm.id}_${targetDow}".hashCode()
        val intent = android.content.Intent(this, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this, requestCode, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, nextTriggerMillis, pi
            )
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun toggleAlarm(alarm: Alarm, isEnabled: Boolean) {
        // 通常スイッチ操作の場合はisReactivatedをリセット
        val updatedAlarm = alarm.copy(isEnabled = isEnabled, isReactivated = false)

        // ストレージを更新
        val alarms = alarmStorage.loadAlarms()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
            alarmStorage.saveAlarms(alarms)
        }

        // alarmList（表示用）も即座に更新してヘッダーを再計算
        val listIndex = alarmList.indexOfFirst { it.id == alarm.id }
        if (listIndex != -1) {
            alarmList[listIndex] = updatedAlarm
            alarmAdapter.notifyItemChanged(listIndex)
        }

        if (isEnabled) {
            val triggerMillis = AlarmScheduler.schedule(this, updatedAlarm)
            // 実際にスケジュールした発火時刻を保存
            if (triggerMillis > 0L && updatedAlarm.repeatDays.isNotEmpty()) {
                val savedAlarm = updatedAlarm.copy(lastScheduledMillis = triggerMillis)
                val savedAlarms = alarmStorage.loadAlarms()
                val savedIndex = savedAlarms.indexOfFirst { it.id == alarm.id }
                if (savedIndex != -1) {
                    savedAlarms[savedIndex] = savedAlarm
                    alarmStorage.saveAlarms(savedAlarms)
                }
                val li = alarmList.indexOfFirst { it.id == alarm.id }
                if (li != -1) alarmList[li] = savedAlarm
            }
        } else {
            // OFFにする直前に「次に鳴るはずだった時刻」を lastScheduledMillis に保存
            // かつ showReactivateButton=true をセット（スイッチで手動OFFの時のみ）
            val nextFire = calcNextFireMillis(updatedAlarm)
            if (updatedAlarm.repeatDays.isNotEmpty()) {
                val withNext = updatedAlarm.copy(
                    lastScheduledMillis = nextFire ?: updatedAlarm.lastScheduledMillis,
                    showReactivateButton = true
                )
                val s2 = alarmStorage.loadAlarms()
                val i2 = s2.indexOfFirst { it.id == alarm.id }
                if (i2 != -1) { s2[i2] = withNext; alarmStorage.saveAlarms(s2) }
                val l2 = alarmList.indexOfFirst { it.id == alarm.id }
                if (l2 != -1) alarmList[l2] = withNext
                alarmAdapter.notifyItemChanged(l2.coerceAtLeast(0))
            }
            AlarmScheduler.cancel(this, updatedAlarm)
        }

        // alarmList が更新された状態でヘッダーを再計算
        updateNextAlarmHeader()
    }

    // 次に鳴るはずの時刻を現在時刻基準で計算
    private fun calcNextFireMillis(alarm: Alarm): Long? {
        val now = System.currentTimeMillis()
        if (alarm.repeatDays.isEmpty()) return null
        return alarm.repeatDays.mapNotNull { dayOfWeek ->
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
