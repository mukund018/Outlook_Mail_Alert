package com.example.outlookringalert

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class TimePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("time_prefs", Context.MODE_PRIVATE)

    var startHour: Int
        get() = prefs.getInt("start_hour", 0) // Default 12 AM
        set(value) = prefs.edit().putInt("start_hour", value).apply()

    var startMinute: Int
        get() = prefs.getInt("start_minute", 0)
        set(value) = prefs.edit().putInt("start_minute", value).apply()

    var endHour: Int
        get() = prefs.getInt("end_hour", 6) // Default 6 AM
        set(value) = prefs.edit().putInt("end_hour", value).apply()

    var endMinute: Int
        get() = prefs.getInt("end_minute", 0)
        set(value) = prefs.edit().putInt("end_minute", value).apply()

    var isAppEnabled: Boolean
        get() = prefs.getBoolean("is_app_enabled", true)
        set(value) = prefs.edit().putBoolean("is_app_enabled", value).apply()

    var isAlwaysRingModeEnabled: Boolean
        get() = prefs.getBoolean("is_always_ring_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("is_always_ring_mode_enabled", value).apply()

    var eventLogs: String
        get() = prefs.getString("event_logs", "") ?: ""
        set(value) = prefs.edit().putString("event_logs", value).apply()

    var ringtoneUri: String?
        get() = prefs.getString("ringtone_uri", null)
        set(value) = prefs.edit().putString("ringtone_uri", value).apply()

    fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val newEntry = "$timestamp - $message"
        val currentLogs = eventLogs.split("\n").filter { it.isNotBlank() }
        val updatedLogs = (listOf(newEntry) + currentLogs).take(5) // Keep last 5
        eventLogs = updatedLogs.joinToString("\n")
    }

    fun isCurrentlyInWindow(): Boolean {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute

        return if (startTimeInMinutes < endTimeInMinutes) {
            // Normal range: e.g., 9 AM to 5 PM
            currentTimeInMinutes in startTimeInMinutes until endTimeInMinutes
        } else {
            // Crossover midnight: e.g., 10 PM to 6 AM
            currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes
        }
    }
}
