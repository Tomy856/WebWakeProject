package com.example.webwake

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar

class AlarmAdapter(
    private val alarms: MutableList<Alarm>,
    private val onAlarmClick: (Alarm) -> Unit,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onLongClick: (Alarm) -> Unit,
    // 「再度ON」ボタン用: baseMillisを基準に次回発火を計算してスケジュールする
    private val onReactivate: (Alarm, Long) -> Unit = { alarm, _ -> onToggle(alarm, true) }
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    var isSelectionMode = false
        private set

    private val selectedIds = mutableSetOf<Long>()

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView          = itemView.findViewById(R.id.cardView)
        val checkIcon: ImageView        = itemView.findViewById(R.id.checkIcon)
        val excludeHolidaysText: TextView = itemView.findViewById(R.id.excludeHolidaysText)
        val alarmLabel: TextView        = itemView.findViewById(R.id.alarmLabel)
        val labelDivider: TextView      = itemView.findViewById(R.id.labelDivider)
        val nextRingText: TextView      = itemView.findViewById(R.id.nextRingText)
        val alarmPeriod: TextView       = itemView.findViewById(R.id.alarmPeriod)
        val alarmTime: TextView         = itemView.findViewById(R.id.alarmTime)
        val alarmDateText: TextView     = itemView.findViewById(R.id.alarmDateText)
        val alarmSwitch: SwitchMaterial = itemView.findViewById(R.id.alarmSwitch)
        val daysLayout: LinearLayout    = itemView.findViewById(R.id.daysLayout)
        val daySunday: TextView         = itemView.findViewById(R.id.daySunday)
        val dayMonday: TextView         = itemView.findViewById(R.id.dayMonday)
        val dayTuesday: TextView        = itemView.findViewById(R.id.dayTuesday)
        val dayWednesday: TextView      = itemView.findViewById(R.id.dayWednesday)
        val dayThursday: TextView       = itemView.findViewById(R.id.dayThursday)
        val dayFriday: TextView         = itemView.findViewById(R.id.dayFriday)
        val daySaturday: TextView       = itemView.findViewById(R.id.daySaturday)
        val dotSunday: View             = itemView.findViewById(R.id.dotSunday)
        val dotMonday: View             = itemView.findViewById(R.id.dotMonday)
        val dotTuesday: View            = itemView.findViewById(R.id.dotTuesday)
        val dotWednesday: View          = itemView.findViewById(R.id.dotWednesday)
        val dotThursday: View           = itemView.findViewById(R.id.dotThursday)
        val dotFriday: View             = itemView.findViewById(R.id.dotFriday)
        val dotSaturday: View           = itemView.findViewById(R.id.dotSaturday)
        val nextOnLayout: LinearLayout  = itemView.findViewById(R.id.nextOnLayout)
        val nextOnButton: LinearLayout  = itemView.findViewById(R.id.nextOnButton)
        val nextOnText: TextView        = itemView.findViewById(R.id.nextOnText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        val isSelected = selectedIds.contains(alarm.id)

        // ---- ラベル・祝日を除く（左詰め、ラベル優先） ----
        // 表示パターン:
        //   ラベルあり + 祝日除く →「ラベル名 | 祝日を除く」
        //   ラベルあり のみ       →「ラベル名」
        //   祝日除く のみ         → 「祝日を除く」
        //   どちらもなし          → 何も表示しない
        if (alarm.label.isNotEmpty()) {
            holder.alarmLabel.visibility = View.VISIBLE
            holder.alarmLabel.text = alarm.label
        } else {
            holder.alarmLabel.visibility = View.GONE
        }
        // ラベルと祝日除くが両方ある時だけ区切りを表示
        holder.labelDivider.visibility =
            if (alarm.label.isNotEmpty() && alarm.excludeHolidays) View.VISIBLE else View.GONE
        holder.excludeHolidaysText.visibility = if (alarm.excludeHolidays) View.VISIBLE else View.GONE

        // ---- 午前/午後・時刻 ----
        holder.alarmPeriod.text = if (alarm.hour < 12) "午前" else "午後"
        val displayHour = when {
            alarm.hour == 0  -> 0   // 午前0時 → 0時
            alarm.hour == 12 -> 0   // 午後12時 → 0時
            alarm.hour > 12  -> alarm.hour - 12
            else             -> alarm.hour
        }
        holder.alarmTime.text = String.format("%d:%02d", displayHour, alarm.minute)

        // ---- 曜日 / 日付 / 次回鳴動テキスト ----
        val dayViews = listOf(
            holder.daySunday, holder.dayMonday, holder.dayTuesday,
            holder.dayWednesday, holder.dayThursday, holder.dayFriday, holder.daySaturday
        )
        val dotViews = listOf(
            holder.dotSunday, holder.dotMonday, holder.dotTuesday,
            holder.dotWednesday, holder.dotThursday, holder.dotFriday, holder.dotSaturday
        )
        val activeColor   = 0xFF7B68EE.toInt()
        val inactiveColor = 0xFF666666.toInt()

        if (alarm.repeatDays.isNotEmpty()) {
            val isAllDays = alarm.repeatDays.size == 7

            if (isAllDays) {
                // 全曜日 → 曜日行を非表示にして「毎日」を日付テキスト欄に表示
                holder.daysLayout.visibility    = View.GONE
                holder.alarmDateText.visibility = View.VISIBLE
                holder.alarmDateText.text       = "毎日"
            } else {
                holder.daysLayout.visibility    = View.VISIBLE
                holder.alarmDateText.visibility = View.GONE
                dayViews.forEachIndexed { index, tv ->
                    val active = alarm.repeatDays.contains(index)
                    tv.setTextColor(if (active) activeColor else inactiveColor)
                    dotViews[index].visibility = if (active) View.VISIBLE else View.INVISIBLE
                }
            }

            if (alarm.isEnabled || (alarm.isReactivated && alarm.lastScheduledMillis > 0L)) {
                // ON時 or 再度ONボタン押下後(isReactivated=true): 鳴動情報を表示
                if (alarm.isReactivated && alarm.lastScheduledMillis > 0L) {
                    // lastScheduledMillis（「再度ON」でセットした実際の発火時刻）から直接ラベルを作る
                    val cal = Calendar.getInstance().apply { timeInMillis = alarm.lastScheduledMillis }
                    val dow = listOf("日","月","火","水","木","金","土")[cal.get(Calendar.DAY_OF_WEEK) - 1]
                    val now = System.currentTimeMillis()
                    val oneWeekMillis = 7L * 24 * 60 * 60 * 1000
                                val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val isTomorrow = cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                                     cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
                    val label = when {
                        isTomorrow -> "明日"
                        alarm.lastScheduledMillis - now >= oneWeekMillis ->
                            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
                        else -> "${dow}曜日"
                    }
                    val suffix = if (isTomorrow) "鳴動" else "に鳴動"
                    holder.nextRingText.visibility = View.VISIBLE
                    holder.nextRingText.text       = "アラームは${label}${suffix}"
                } else {
                    // 通常スイッチONはnextRingTextを非表示
                    holder.nextRingText.visibility = View.GONE
                }
                holder.nextOnLayout.visibility = View.GONE
            } else {
                // OFF時: スイッチで手動OFFにした時のみ「再度ON」ボタンを表示
                holder.nextRingText.visibility = View.GONE
                if (alarm.showReactivateButton) {
                    val (nextLabel, nextMillis) = getNextRingMillis(alarm)
                    if (nextLabel != null && nextMillis != null) {
                        holder.nextOnLayout.visibility = View.VISIBLE
                        holder.nextOnText.text         = "${nextLabel}再度ON"
                        holder.nextOnButton.setOnClickListener {
                            onReactivate(alarm, nextMillis)
                        }
                    } else {
                        holder.nextOnLayout.visibility = View.GONE
                    }
                } else {
                    // アプリ起動時やその他のタイミングはボタンを表示しない
                    holder.nextOnLayout.visibility = View.GONE
                }
            }
        } else if (alarm.specificDate.isNotEmpty()) {
            holder.daysLayout.visibility    = View.GONE
            holder.alarmDateText.visibility = View.VISIBLE
            holder.alarmDateText.text       = formatDate(alarm.specificDate)
            holder.nextRingText.visibility  = View.GONE
            holder.nextOnLayout.visibility  = View.GONE
        } else {
            // 何も指定なし → 設定時刻が現在以前なら習日、これからなら当日
            holder.daysLayout.visibility    = View.GONE
            holder.alarmDateText.visibility = View.VISIBLE
            holder.nextRingText.visibility  = View.GONE
            holder.nextOnLayout.visibility  = View.GONE
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }
            val month     = cal.get(Calendar.MONTH) + 1
            val day       = cal.get(Calendar.DAY_OF_MONTH)
            val dayOfWeek = listOf("日","月","火","水","木","金","土")[cal.get(Calendar.DAY_OF_WEEK) - 1]
            holder.alarmDateText.text = "${month}月${day}日($dayOfWeek)"
        }

        // ---- 選択モード ----
        if (isSelectionMode) {
            holder.checkIcon.visibility  = View.VISIBLE
            holder.alarmSwitch.visibility = View.GONE
            holder.checkIcon.setImageResource(
                if (isSelected) android.R.drawable.checkbox_on_background
                else            android.R.drawable.checkbox_off_background
            )
            holder.cardView.setCardBackgroundColor(
                if (isSelected) 0xFF2E2B4A.toInt() else 0xFF1E1E1E.toInt()
            )
        } else {
            holder.checkIcon.visibility  = View.GONE
            holder.alarmSwitch.visibility = View.VISIBLE
            holder.cardView.setCardBackgroundColor(0xFF1E1E1E.toInt())

            holder.alarmSwitch.setOnCheckedChangeListener(null)
            holder.alarmSwitch.isChecked = alarm.isEnabled
            // スイッチのトラック色: ON=紫, OFF=灰色
            holder.alarmSwitch.trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFF7B68EE.toInt(), 0xFF555555.toInt())
            )
            holder.alarmSwitch.setOnCheckedChangeListener { _, isChecked ->
                // 曜日あり・日付指定・何も指定なし、全て確認なしで即OFF/ON
                onToggle(alarm, isChecked)
            }
        }

        // ---- タップ ----
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(alarm.id)
                notifyItemChanged(position)
            } else {
                onAlarmClick(alarm)
            }
        }

        // ---- 長押し ----
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                enterSelectionMode()
                toggleSelection(alarm.id)
                notifyDataSetChanged()
                onLongClick(alarm)
            }
            true
        }
    }

    override fun getItemCount(): Int = alarms.size

    // OFF時「再度ON」ボタン用: 表示ラベルと実際の発火時刻(ms)を返す
    // lastScheduledMillis（次に鳴るはずだった時刻）を基準にその「次」を計算
    private fun getNextRingMillis(alarm: Alarm): Pair<String?, Long?> {
        if (alarm.repeatDays.isEmpty()) return Pair(null, null)
        val now = System.currentTimeMillis()
        val dayNames = listOf("日","月","火","水","木","金","土")
        val oneWeekMillis = 7L * 24 * 60 * 60 * 1000

        // base = lastScheduledMillis（必ずOFF時に「次に鳴るはずだった時刻」が入る）
        val base = if (alarm.lastScheduledMillis > 0L) alarm.lastScheduledMillis else now

        // baseを厳密に超える最初の発火時刻を各曜日から探す
        val nextEntry = alarm.repeatDays.mapNotNull { dayOfWeek ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, dayOfWeek + 1)
                // base以前(<=)なら習週に進める → baseを厳密に超える時刻のみ残る
                if (timeInMillis <= base) add(Calendar.WEEK_OF_YEAR, 1)
            }
            Pair(dayOfWeek, cal.timeInMillis)
        }.minByOrNull { it.second } ?: return Pair(null, null)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextEntry.second }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val isTomorrow = nextCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                         nextCal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
        val label = when {
            isTomorrow -> "明日"
            nextEntry.second - now >= oneWeekMillis ->
                "${nextCal.get(Calendar.MONTH) + 1}月${nextCal.get(Calendar.DAY_OF_MONTH)}日"
            else -> "${dayNames[nextEntry.first]}曜日"
        }
        return Pair(label, nextEntry.second)
    }

    // ON時ラベル用: 表示文字列のみ返す
    // useLastScheduled=false → 現在時刻から次を計算
    private fun getNextRingInfo(alarm: Alarm, useLastScheduled: Boolean = false): Pair<String?, Boolean> {
        if (alarm.repeatDays.isEmpty()) return Pair(null, false)
        val now = System.currentTimeMillis()
        val dayNames = listOf("日","月","火","水","木","金","土")
        val oneWeekMillis = 7L * 24 * 60 * 60 * 1000

        // OFF時: lastScheduledMillis = 「次に鳴るはずだった時刻」が入っている
        // その時刻を基準に「の次」を計算する
        // useLastScheduled=falseの場合はnow基準
        val baseMillis = if (useLastScheduled && alarm.lastScheduledMillis > 0L)
            alarm.lastScheduledMillis  // 必ずOFF時に未来時刻が入っている
        else
            now

        val nextEntry = alarm.repeatDays.mapNotNull { dayOfWeek ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, dayOfWeek + 1)
                // baseMillis以前(小数点以下が引次ぐのを防ぐため厳密に<でなく<=で比較)なら習週に
                if (timeInMillis <= baseMillis) add(Calendar.WEEK_OF_YEAR, 1)
            }
            Pair(dayOfWeek, cal.timeInMillis)
        }.minByOrNull { it.second } ?: return Pair(null, false)

        val diffMillis = nextEntry.second - now
        return if (diffMillis >= oneWeekMillis) {
            // 1週間以上先 → 日付表示
            val cal   = Calendar.getInstance().apply { timeInMillis = nextEntry.second }
            val month = cal.get(Calendar.MONTH) + 1
            val day   = cal.get(Calendar.DAY_OF_MONTH)
            Pair("${month}月${day}日", true)
        } else {
            // 1週間以内 → 曜日名表示
            Pair("${dayNames[nextEntry.first]}曜日", false)
        }
    }

    fun enterSelectionMode() {
        isSelectionMode = true
        selectedIds.clear()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    private fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun getSelectedIds(): Set<Long> = selectedIds.toSet()
    fun getSelectedCount(): Int = selectedIds.size

    fun selectAll() {
        selectedIds.clear()
        alarms.forEach { selectedIds.add(it.id) }
        notifyDataSetChanged()
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parts = dateStr.split("-")
            val year  = parts[0].toInt()
            val month = parts[1].toInt()
            val day   = parts[2].toInt()
            val cal   = Calendar.getInstance().apply { set(year, month - 1, day) }
            val dow   = listOf("日","月","火","水","木","金","土")[cal.get(Calendar.DAY_OF_WEEK) - 1]
            "${month}月${day}日($dow)"
        } catch (e: Exception) { dateStr }
    }
}
