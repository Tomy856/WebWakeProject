package com.example.webwake

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var alarmRecyclerView: RecyclerView
    private lateinit var addAlarmFab: FloatingActionButton
    private lateinit var menuFab: FloatingActionButton
    private lateinit var alarmAdapter: AlarmAdapter
    private val alarmList = mutableListOf<Alarm>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UIパーツの紐付け
        alarmRecyclerView = findViewById(R.id.alarmRecyclerView)
        addAlarmFab = findViewById(R.id.addAlarmFab)
        menuFab = findViewById(R.id.menuFab)

        // RecyclerViewの設定
        alarmRecyclerView.layoutManager = LinearLayoutManager(this)
        alarmAdapter = AlarmAdapter(alarmList) { alarm ->
            // アラームがタップされた時の処理
            editAlarm(alarm)
        }
        alarmRecyclerView.adapter = alarmAdapter

        // サンプルデータを追加（後でデータベースから読み込む）
        loadSampleAlarms()

        // アラーム追加ボタン
        addAlarmFab.setOnClickListener {
            val intent = Intent(this, AlarmSetupActivity::class.java)
            startActivity(intent)
        }

        // メニューボタン
        menuFab.setOnClickListener {
            // メニュー処理（後で実装）
        }
    }

    override fun onResume() {
        super.onResume()
        // アラーム一覧を再読み込み
        loadAlarms()
    }

    private fun loadAlarms() {
        // TODO: データベースからアラームを読み込む
        // 現在はサンプルデータのまま
        alarmAdapter.notifyDataSetChanged()
    }

    private fun loadSampleAlarms() {
        alarmList.clear()

        // サンプルアラーム1
        alarmList.add(
            Alarm(
                id = 1,
                hour = 1,
                minute = 0,
                label = "祝日を除く",
                isEnabled = true,
                repeatDays = setOf(1, 2, 3, 4, 5) // 月〜金
            )
        )

        // サンプルアラーム2
        alarmList.add(
            Alarm(
                id = 2,
                hour = 8,
                minute = 30,
                label = "",
                isEnabled = true,
                repeatDays = setOf(1, 2, 3, 4, 5) // 月〜金
            )
        )

        // サンプルアラーム3
        alarmList.add(
            Alarm(
                id = 3,
                hour = 9,
                minute = 10,
                label = "祝日を除く",
                isEnabled = true,
                repeatDays = setOf(1, 2, 3, 4, 5) // 月〜金
            )
        )

        alarmAdapter.notifyDataSetChanged()
    }

    private fun editAlarm(alarm: Alarm) {
        val intent = Intent(this, AlarmSetupActivity::class.java)
        intent.putExtra("ALARM_ID", alarm.id)
        startActivity(intent)
    }
}