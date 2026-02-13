package com.example.webwake

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class AlarmAdapter(
    private val alarms: MutableList<Alarm>,
    private val onAlarmClick: (Alarm) -> Unit,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onLongClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    // 選択モードかどうか
    var isSelectionMode = false
        private set

    // 選択中のアラームIDセット
    private val selectedIds = mutableSetOf<Long>()

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)
        val alarmLabel: TextView = itemView.findViewById(R.id.alarmLabel)
        val alarmPeriod: TextView = itemView.findViewById(R.id.alarmPeriod)
        val alarmTime: TextView = itemView.findViewById(R.id.alarmTime)
        val alarmSwitch: SwitchMaterial = itemView.findViewById(R.id.alarmSwitch)
        val daySunday: TextView = itemView.findViewById(R.id.daySunday)
        val dayMonday: TextView = itemView.findViewById(R.id.dayMonday)
        val dayTuesday: TextView = itemView.findViewById(R.id.dayTuesday)
        val dayWednesday: TextView = itemView.findViewById(R.id.dayWednesday)
        val dayThursday: TextView = itemView.findViewById(R.id.dayThursday)
        val dayFriday: TextView = itemView.findViewById(R.id.dayFriday)
        val daySaturday: TextView = itemView.findViewById(R.id.daySaturday)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        val isSelected = selectedIds.contains(alarm.id)

        // ラベル
        if (alarm.label.isNotEmpty()) {
            holder.alarmLabel.visibility = View.VISIBLE
            holder.alarmLabel.text = alarm.label
        } else {
            holder.alarmLabel.visibility = View.GONE
        }

        // 午前/午後
        holder.alarmPeriod.text = if (alarm.hour < 12) "午前" else "午後"

        // 時刻
        val displayHour = when {
            alarm.hour == 0 -> 12
            alarm.hour > 12 -> alarm.hour - 12
            else -> alarm.hour
        }
        holder.alarmTime.text = String.format("%d:%02d", displayHour, alarm.minute)

        // 曜日の色設定
        val dayViews = listOf(
            holder.daySunday, holder.dayMonday, holder.dayTuesday,
            holder.dayWednesday, holder.dayThursday, holder.dayFriday, holder.daySaturday
        )
        val activeColor = 0xFF7B68EE.toInt()
        val inactiveColor = 0xFF666666.toInt()
        dayViews.forEachIndexed { index, textView ->
            textView.setTextColor(if (alarm.repeatDays.contains(index)) activeColor else inactiveColor)
        }

        // 選択モードの表示切り替え
        if (isSelectionMode) {
            holder.checkIcon.visibility = View.VISIBLE
            holder.alarmSwitch.visibility = View.GONE
            holder.checkIcon.setImageResource(
                if (isSelected) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            )
            holder.cardView.setCardBackgroundColor(
                if (isSelected) 0xFF2E2B4A.toInt() else 0xFF1E1E1E.toInt()
            )
        } else {
            holder.checkIcon.visibility = View.GONE
            holder.alarmSwitch.visibility = View.VISIBLE
            holder.cardView.setCardBackgroundColor(0xFF1E1E1E.toInt())

            // スイッチ
            holder.alarmSwitch.setOnCheckedChangeListener(null)
            holder.alarmSwitch.isChecked = alarm.isEnabled
            holder.alarmSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
            }
        }

        // タップ
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(alarm.id)
                notifyItemChanged(position)
            } else {
                onAlarmClick(alarm)
            }
        }

        // 長押し
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

    // 選択モード開始
    fun enterSelectionMode() {
        isSelectionMode = true
        selectedIds.clear()
    }

    // 選択モード終了
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    // チェック切り替え
    private fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id)
        else selectedIds.add(id)
    }

    // 選択中のID一覧を返す
    fun getSelectedIds(): Set<Long> = selectedIds.toSet()

    // 選択件数
    fun getSelectedCount(): Int = selectedIds.size

    // 全選択
    fun selectAll() {
        selectedIds.clear()
        alarms.forEach { selectedIds.add(it.id) }
        notifyDataSetChanged()
    }
}
