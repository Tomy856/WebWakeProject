package com.example.webwake

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class AlarmAdapter(
    private val alarms: List<Alarm>,
    private val onAlarmClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

        // スイッチ
        holder.alarmSwitch.isChecked = alarm.isEnabled
        holder.alarmSwitch.setOnCheckedChangeListener { _, isChecked ->
            // TODO: アラームのオン/オフを切り替える
        }

        // 曜日の色設定
        val dayViews = listOf(
            holder.daySunday,
            holder.dayMonday,
            holder.dayTuesday,
            holder.dayWednesday,
            holder.dayThursday,
            holder.dayFriday,
            holder.daySaturday
        )

        dayViews.forEachIndexed { index, textView ->
            if (alarm.repeatDays.contains(index)) {
                textView.setTextColor(0xFF7B68EE.toInt()) // 紫色
            } else {
                textView.setTextColor(0xFF666666.toInt()) // グレー
            }
        }

        // アイテムクリック
        holder.itemView.setOnClickListener {
            onAlarmClick(alarm)
        }
    }

    override fun getItemCount(): Int = alarms.size
}