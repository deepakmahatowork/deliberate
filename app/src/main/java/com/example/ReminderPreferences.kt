package com.example

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

data class ReminderSettings(
  val isEnabled: Boolean = false,
  val intervalMinutes: Int = 60,
  val startHour: Int = 9,
  val startMinute: Int = 0,
  val endHour: Int = 21,
  val endMinute: Int = 0
) {
  fun formatStartTime(): String = formatTime(startHour, startMinute)
  fun formatEndTime(): String = formatTime(endHour, endMinute)

  companion object {
    fun formatTime(hour: Int, minute: Int): String {
      val period = if (hour < 12) "AM" else "PM"
      val displayHour = when (hour) {
        0 -> 12
        in 1..12 -> hour
        else -> hour - 12
      }
      return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, period)
    }
  }
}

object ReminderPreferences {
  private const val PREFS_NAME = "deliberate_settings"
  private const val KEY_ENABLED = "reminders_enabled"
  private const val KEY_INTERVAL = "interval_minutes"
  private const val KEY_START_HOUR = "start_hour"
  private const val KEY_START_MINUTE = "start_minute"
  private const val KEY_END_HOUR = "end_hour"
  private const val KEY_END_MINUTE = "end_minute"
  private const val KEY_PERMISSION_ASKED = "notification_permission_asked"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun load(context: Context): ReminderSettings {
    val prefs = getPrefs(context)
    return ReminderSettings(
      isEnabled = prefs.getBoolean(KEY_ENABLED, false),
      intervalMinutes = prefs.getInt(KEY_INTERVAL, 60),
      startHour = prefs.getInt(KEY_START_HOUR, 9),
      startMinute = prefs.getInt(KEY_START_MINUTE, 0),
      endHour = prefs.getInt(KEY_END_HOUR, 21),
      endMinute = prefs.getInt(KEY_END_MINUTE, 0)
    )
  }

  fun save(context: Context, settings: ReminderSettings) {
    getPrefs(context).edit()
      .putBoolean(KEY_ENABLED, settings.isEnabled)
      .putInt(KEY_INTERVAL, settings.intervalMinutes)
      .putInt(KEY_START_HOUR, settings.startHour)
      .putInt(KEY_START_MINUTE, settings.startMinute)
      .putInt(KEY_END_HOUR, settings.endHour)
      .putInt(KEY_END_MINUTE, settings.endMinute)
      .apply()
  }

  fun hasAskedPermission(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_PERMISSION_ASKED, false)
  }

  fun setAskedPermission(context: Context) {
    getPrefs(context).edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
  }
}
