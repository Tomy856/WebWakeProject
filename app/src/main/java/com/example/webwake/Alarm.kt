package com.example.webwake

data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val vibrate: Boolean = true,
    val url: String = "",
    val excludeHolidays: Boolean = false,
    val specificDate: String = "",
    val lastScheduledMillis: Long = 0L,
    val isReactivated: Boolean = false,
    val showReactivateButton: Boolean = false  // スイッチOFFにした時だけtrue、アプリ起動時はfalse
)
