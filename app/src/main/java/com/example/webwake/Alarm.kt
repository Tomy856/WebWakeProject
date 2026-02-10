package com.example.webwake

data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(), // 0=日, 1=月, 2=火, 3=水, 4=木, 5=金, 6=土
    val vibrate: Boolean = true,
    val soundUri: String? = null
)