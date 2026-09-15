package com.example

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class DeliberateAccessibilityService : AccessibilityService() {

  companion object {
    @Volatile
    private var instance: DeliberateAccessibilityService? = null

    @Volatile
    private var lastTriggerTime: Long = 0L

    fun isServiceActive(): Boolean = instance != null

    fun lockDevice(context: Context): Boolean {
      // Safest official Android mechanism 1: AccessibilityService GLOBAL_ACTION_LOCK_SCREEN (API 28+)
      val service = instance
      if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val locked = service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (locked) return true
      }

      // Safest official Android mechanism 2: DevicePolicyManager lockNow() if admin granted
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
      val adminComponent = ComponentName(context, DeliberateDeviceAdminReceiver::class.java)
      if (dpm != null && dpm.isAdminActive(adminComponent)) {
        try {
          dpm.lockNow()
          return true
        } catch (_: SecurityException) {
        }
      }

      return false
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
  }

  override fun onDestroy() {
    super.onDestroy()
    if (instance === this) {
      instance = null
    }
  }

  override fun onUnbind(intent: Intent?): Boolean {
    if (instance === this) {
      instance = null
    }
    return super.onUnbind(intent)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

    // 1. Check if gate is enabled
    val settings = OverlayPreferences.load(this)
    if (!settings.isGateEnabled) return

    // 2. Check if cooldown is active
    val now = System.currentTimeMillis()
    if (OverlayPreferences.isCooldownActive(this, now)) return

    // 3. Debounce: Do not trigger repeatedly within 3 seconds
    if (now - lastTriggerTime < 3000L) return

    // 4. Exclude Deliberate itself and critical system interfaces (emergency, dialer, keyguard)
    val eventPackage = event.packageName?.toString() ?: ""
    if (eventPackage.isEmpty() || eventPackage == packageName) return

    val lowerPkg = eventPackage.lowercase()
    if (lowerPkg.contains("emergency") ||
      lowerPkg.contains("telecom") ||
      lowerPkg.contains("incallui") ||
      lowerPkg.contains("dialer") ||
      lowerPkg.contains("keyguard")
    ) {
      return
    }

    lastTriggerTime = now

    // 5. Trigger the Intervention Activity full-screen gate
    val intent = Intent(this, InterventionActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_SINGLE_TOP or
        Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    startActivity(intent)
  }

  override fun onInterrupt() {
    // Required override for AccessibilityService
  }
}
