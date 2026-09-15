package com.example

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class DeliberateAccessibilityService : AccessibilityService() {

  companion object {
    @Volatile
    private var instance: DeliberateAccessibilityService? = null

    @Volatile
    private var lastTriggerTime: Long = 0L

    fun isServiceActive(): Boolean = instance != null

    fun getInstance(): DeliberateAccessibilityService? = instance

    fun lockDevice(context: Context): Boolean {
      // 1. Direct DevicePolicyManager lockNow() if admin is active
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
      val adminComponent = ComponentName(context, DeliberateDeviceAdminReceiver::class.java)
      if (dpm != null && dpm.isAdminActive(adminComponent)) {
        try {
          android.util.Log.d("Deliberate", "Attempting lockNow via DevicePolicyManager")
          dpm.lockNow()
          return true
        } catch (e: Exception) {
          android.util.Log.e("Deliberate", "DevicePolicyManager.lockNow() failed", e)
        }
      }

      // 2. AccessibilityService GLOBAL_ACTION_LOCK_SCREEN (API 28+)
      val service = instance
      if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
          android.util.Log.d("Deliberate", "Attempting GLOBAL_ACTION_LOCK_SCREEN via AccessibilityService")
          val locked = service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
          if (locked) return true
        } catch (e: Exception) {
          android.util.Log.e("Deliberate", "AccessibilityService lock screen failed", e)
        }
      }

      // 3. Fallback: navigate directly to home screen to immediately exit
      try {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
          addCategory(Intent.CATEGORY_HOME)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
      } catch (_: Exception) {
      }

      return false
    }
  }

  private var isReceiverRegistered = false

  private val unlockReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == Intent.ACTION_USER_PRESENT) {
        android.util.Log.d("DeliberateAccessibility", "ACTION_USER_PRESENT received in AccessibilityService")
        UnlockLauncher.launchOnUnlock(this@DeliberateAccessibilityService)
      }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    registerUnlockReceiver()
  }

  private fun registerUnlockReceiver() {
    if (!isReceiverRegistered) {
      try {
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(unlockReceiver, filter)
        isReceiverRegistered = true
      } catch (e: Exception) {
        android.util.Log.e("DeliberateAccessibility", "Failed to register unlock receiver", e)
      }
    }
  }

  private fun unregisterUnlockReceiver() {
    if (isReceiverRegistered) {
      try {
        unregisterReceiver(unlockReceiver)
        isReceiverRegistered = false
      } catch (e: Exception) {
        android.util.Log.e("DeliberateAccessibility", "Failed to unregister unlock receiver", e)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    unregisterUnlockReceiver()
    if (instance === this) {
      instance = null
    }
  }

  override fun onUnbind(intent: Intent?): Boolean {
    unregisterUnlockReceiver()
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
