package com.example.webwake

data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(), // 0=日, 1=月, 2=火, 3=水, 4=木, 5=金, 6=土
    val vibrate: Boolean = true,
    val url: String = "",               // 起動するURL
    val excludeHolidays: Boolean = false, // 祝日を除く
    val specificDate: String = ""        // 特定日付 "YYYY-MM-DD"、空なら曜日繰り返し
)
