package com.example

import android.content.Context
import android.content.SharedPreferences

enum class UnlockDestination {
  INTERVENTION_GATE,
  MAIN_APP
}

data class UnlockSettings(
  val isOpenOnUnlockEnabled: Boolean = true,
  val destination: UnlockDestination = UnlockDestination.INTERVENTION_GATE,
  val cooldownSeconds: Int = 0 // 0 means every unlock
)

object UnlockPreferences {
  private const val PREFS_NAME = "deliberate_unlock_settings"
  private const val KEY_OPEN_ON_UNLOCK = "open_on_unlock_enabled"
  private const val KEY_DESTINATION = "unlock_destination"
  private const val KEY_COOLDOWN_SECONDS = "unlock_cooldown_seconds"
  private const val KEY_LAST_UNLOCK_LAUNCH_TIME = "last_unlock_launch_time"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun load(context: Context): UnlockSettings {
    val prefs = getPrefs(context)
    val destString = prefs.getString(KEY_DESTINATION, UnlockDestination.INTERVENTION_GATE.name)
    val dest = try {
      UnlockDestination.valueOf(destString ?: UnlockDestination.INTERVENTION_GATE.name)
    } catch (_: Exception) {
      UnlockDestination.INTERVENTION_GATE
    }

    return UnlockSettings(
      isOpenOnUnlockEnabled = prefs.getBoolean(KEY_OPEN_ON_UNLOCK, true),
      destination = dest,
      cooldownSeconds = prefs.getInt(KEY_COOLDOWN_SECONDS, 0)
    )
  }

  fun save(context: Context, settings: UnlockSettings) {
    getPrefs(context).edit()
      .putBoolean(KEY_OPEN_ON_UNLOCK, settings.isOpenOnUnlockEnabled)
      .putString(KEY_DESTINATION, settings.destination.name)
      .putInt(KEY_COOLDOWN_SECONDS, settings.cooldownSeconds)
      .apply()
  }

  fun recordUnlockLaunch(context: Context, timestamp: Long = System.currentTimeMillis()) {
    getPrefs(context).edit().putLong(KEY_LAST_UNLOCK_LAUNCH_TIME, timestamp).apply()
  }

  fun getLastUnlockLaunchTime(context: Context): Long {
    return getPrefs(context).getLong(KEY_LAST_UNLOCK_LAUNCH_TIME, 0L)
  }
}
