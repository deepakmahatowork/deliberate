package com.example

import android.content.Context
import android.content.SharedPreferences

data class OverlayGateSettings(
  val isGateEnabled: Boolean = false,
  val cooldownMinutes: Int = 15,
  val lastInterventionTime: Long = 0L
)

object OverlayPreferences {
  private const val PREFS_NAME = "deliberate_overlay_settings"
  private const val KEY_GATE_ENABLED = "gate_enabled"
  private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
  private const val KEY_LAST_INTERVENTION_TIME = "last_intervention_time"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun load(context: Context): OverlayGateSettings {
    val prefs = getPrefs(context)
    return OverlayGateSettings(
      isGateEnabled = prefs.getBoolean(KEY_GATE_ENABLED, false),
      cooldownMinutes = prefs.getInt(KEY_COOLDOWN_MINUTES, 15),
      lastInterventionTime = prefs.getLong(KEY_LAST_INTERVENTION_TIME, 0L)
    )
  }

  fun saveGateEnabled(context: Context, enabled: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_GATE_ENABLED, enabled).apply()
  }

  fun saveCooldownMinutes(context: Context, minutes: Int) {
    getPrefs(context).edit().putInt(KEY_COOLDOWN_MINUTES, minutes).apply()
  }

  fun markInterventionCompleted(context: Context, timestamp: Long = System.currentTimeMillis()) {
    getPrefs(context).edit().putLong(KEY_LAST_INTERVENTION_TIME, timestamp).apply()
  }

  fun resetCooldown(context: Context) {
    getPrefs(context).edit().putLong(KEY_LAST_INTERVENTION_TIME, 0L).apply()
  }

  fun isCooldownActive(context: Context, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
    val settings = load(context)
    if (!settings.isGateEnabled) return false
    if (settings.lastInterventionTime == 0L) return false

    val elapsed = currentTimeMillis - settings.lastInterventionTime
    val cooldownMillis = settings.cooldownMinutes * 60 * 1000L
    return elapsed < cooldownMillis
  }

  fun getRemainingCooldownSeconds(context: Context, currentTimeMillis: Long = System.currentTimeMillis()): Long {
    val settings = load(context)
    if (settings.lastInterventionTime == 0L) return 0L
    val elapsed = currentTimeMillis - settings.lastInterventionTime
    val cooldownMillis = settings.cooldownMinutes * 60 * 1000L
    val remaining = cooldownMillis - elapsed
    return if (remaining > 0) remaining / 1000 else 0L
  }
}
