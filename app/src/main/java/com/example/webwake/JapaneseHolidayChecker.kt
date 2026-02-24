package com.example.webwake

import java.util.Calendar

/**
 * 日本の祝日判定
 * 内閣府の祝日定義に基づき、振替休日・国民の休日も考慮
 */
object JapaneseHolidayChecker {

    fun isHoliday(year: Int, month: Int, day: Int): Boolean {
        if (isFixedOrCalculatedHoliday(year, month, day)) return true
        if (isTransferHoliday(year, month, day)) return true
        if (isCitizenHoliday(year, month, day)) return true
        return false
    }

    fun isHoliday(cal: Calendar): Boolean {
        return isHoliday(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // ---- 固定・計算祝日 ----
    private fun isFixedOrCalculatedHoliday(year: Int, month: Int, day: Int): Boolean {
        return when (month) {
            1 -> when {
                day == 1 -> true                              // 元日
                isNthWeekday(year, month, day, 2, Calendar.MONDAY) -> true // 成人の日（第2月曜）
                else -> false
            }
            2 -> day == 11 ||                                 // 建国記念の日
                 day == 23                                    // 天皇誕生日（2020年〜）
            3 -> day == vernalEquinox(year)                   // 春分の日
            4 -> day == 29                                    // 昭和の日
            5 -> day == 3 || day == 4 || day == 5            // 憲法記念日・みどりの日・こどもの日
            6 -> false
            7 -> isNthWeekday(year, month, day, 3, Calendar.MONDAY) // 海の日（第3月曜）
            8 -> day == 11                                    // 山の日
            9 -> isNthWeekday(year, month, day, 3, Calendar.MONDAY) || // 敬老の日（第3月曜）
                 day == autumnalEquinox(year)                 // 秋分の日
            10 -> isNthWeekday(year, month, day, 2, Calendar.MONDAY) // スポーツの日（第2月曜）
            11 -> day == 3 || day == 23                       // 文化の日・勤労感謝の日
            12 -> false
            else -> false
        }
    }

    // 振替休日: 祝日が日曜の場合、翌平日が振替休日
    // （祝日が連続する場合は最初の非祝日平日まで繰り越し）
    private fun isTransferHoliday(year: Int, month: Int, day: Int): Boolean {
        val cal = Calendar.getInstance().apply { set(year, month - 1, day) }
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) return false

        // この日の前の日々を遡って「日曜の祝日」があるか確認
        val check = cal.clone() as Calendar
        check.add(Calendar.DAY_OF_YEAR, -1)
        while (true) {
            val y = check.get(Calendar.YEAR)
            val m = check.get(Calendar.MONTH) + 1
            val d = check.get(Calendar.DAY_OF_MONTH)
            val dow = check.get(Calendar.DAY_OF_WEEK)

            if (dow == Calendar.SUNDAY) {
                // 日曜祝日があれば、そこから連続する祝日を数えた翌日が振替
                if (isFixedOrCalculatedHoliday(y, m, d)) {
                    // 日曜祝日から連続する祝日を数える
                    val transfer = check.clone() as Calendar
                    transfer.add(Calendar.DAY_OF_YEAR, 1)
                    while (isFixedOrCalculatedHoliday(
                            transfer.get(Calendar.YEAR),
                            transfer.get(Calendar.MONTH) + 1,
                            transfer.get(Calendar.DAY_OF_MONTH)
                        ) || isCitizenHoliday(
                            transfer.get(Calendar.YEAR),
                            transfer.get(Calendar.MONTH) + 1,
                            transfer.get(Calendar.DAY_OF_MONTH)
                        )
                    ) {
                        transfer.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return transfer.get(Calendar.YEAR) == year &&
                           transfer.get(Calendar.MONTH) + 1 == month &&
                           transfer.get(Calendar.DAY_OF_MONTH) == day
                }
                break
            }

            // 祝日でも国民の休日でもない平日なら遡りをやめる
            if (!isFixedOrCalculatedHoliday(y, m, d) && !isCitizenHoliday(y, m, d)) break

            check.add(Calendar.DAY_OF_YEAR, -1)
        }
        return false
    }

    // 国民の休日: 前後が祝日で挟まれた平日（日曜以外）
    private fun isCitizenHoliday(year: Int, month: Int, day: Int): Boolean {
        val cal = Calendar.getInstance().apply { set(year, month - 1, day) }
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) return false
        if (isFixedOrCalculatedHoliday(year, month, day)) return false

        val prev = cal.clone() as Calendar
        prev.add(Calendar.DAY_OF_YEAR, -1)
        val next = cal.clone() as Calendar
        next.add(Calendar.DAY_OF_YEAR, 1)

        val prevIsHoliday = isFixedOrCalculatedHoliday(
            prev.get(Calendar.YEAR), prev.get(Calendar.MONTH) + 1, prev.get(Calendar.DAY_OF_MONTH)
        )
        val nextIsHoliday = isFixedOrCalculatedHoliday(
            next.get(Calendar.YEAR), next.get(Calendar.MONTH) + 1, next.get(Calendar.DAY_OF_MONTH)
        )
        return prevIsHoliday && nextIsHoliday
    }

    // 第N週の曜日
    private fun isNthWeekday(year: Int, month: Int, day: Int, n: Int, dayOfWeek: Int): Boolean {
        val cal = Calendar.getInstance().apply { set(year, month - 1, day) }
        return cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek &&
               cal.get(Calendar.DAY_OF_WEEK_IN_MONTH) == n
    }

    // 春分の日（簡易計算）
    private fun vernalEquinox(year: Int): Int {
        return when {
            year <= 1979 -> ((year * 0.242194 + 19.8277) - (year - 1980) / 4).toInt()
            year <= 2099 -> ((year * 0.242194 + 20.8431) - (year - 1980) / 4).toInt()
            else         -> ((year * 0.242194 + 21.8510) - (year - 1980) / 4).toInt()
        }
    }

    // 秋分の日（簡易計算）
    private fun autumnalEquinox(year: Int): Int {
        return when {
            year <= 1979 -> ((year * 0.242194 + 22.2588) - (year - 1980) / 4).toInt()
            year <= 2099 -> ((year * 0.242194 + 23.2488) - (year - 1980) / 4).toInt()
            else         -> ((year * 0.242194 + 24.2488) - (year - 1980) / 4).toInt()
        }
    }
}
