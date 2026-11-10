package com.mzazitech.automatedmachine.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Time {

    /** Kenya has no daylight saving; the machine always reports in EAT. */
    val nairobi: TimeZone = TimeZone.getTimeZone("Africa/Nairobi")

    fun nowMillis(): Long = System.currentTimeMillis()

    fun startOfToday(): Long {
        val calendar = Calendar.getInstance(nairobi).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun formatDateTime(millis: Long): String =
        formatter("dd MMM yyyy, HH:mm").format(Date(millis))

    fun formatTime(millis: Long): String =
        formatter("HH:mm").format(Date(millis))

    fun formatRelative(millis: Long, reference: Long = nowMillis()): String {
        val diff = reference - millis
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            diff < 172_800_000 -> "yesterday"
            else -> formatDateTime(millis)
        }
    }

    private fun formatter(pattern: String) =
        SimpleDateFormat(pattern, Locale.UK).apply { timeZone = nairobi }
}
