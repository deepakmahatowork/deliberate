package com.example

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.KeyguardManager
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
  private var wasKeyguardLocked = true

  private val unlockReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        Intent.ACTION_SCREEN_OFF -> {
          wasKeyguardLocked = true
        }
        Intent.ACTION_USER_PRESENT -> {
          wasKeyguardLocked = false
          android.util.Log.d("DeliberateAccessibility", "ACTION_USER_PRESENT received in AccessibilityService")
          UnlockLauncher.launchOnUnlock(this@DeliberateAccessibilityService)
        }
        Intent.ACTION_SCREEN_ON -> {
          val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
          if (km?.isKeyguardLocked == false) {
            wasKeyguardLocked = false
            UnlockLauncher.launchOnUnlock(this@DeliberateAccessibilityService)
          }
        }
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
        val filter = IntentFilter().apply {
          addAction(Intent.ACTION_USER_PRESENT)
          addAction(Intent.ACTION_SCREEN_OFF)
          addAction(Intent.ACTION_SCREEN_ON)
          priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
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

    // 0. Ultra-fast unlock detection on any lockscreen window change
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
      event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    ) {
      val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      val isKeyguardCurrentlyLocked = km?.isKeyguardLocked == true
      if (wasKeyguardLocked && !isKeyguardCurrentlyLocked) {
        wasKeyguardLocked = false
        android.util.Log.d("DeliberateAccessibility", "Instant keyguard unlock detected via window state change!")
        UnlockLauncher.launchOnUnlock(this)
      } else if (isKeyguardCurrentlyLocked) {
        wasKeyguardLocked = true
      }
    }

    // 0.1 Hard Sticky Overlay Enforcement:
    // If gate is active and decision buttons have NOT been clicked yet,
    // do not allow switching to any other app, home launcher, or recents!
    if (GateLockState.shouldEnforceSticky()) {
      val eventPackage = event.packageName?.toString() ?: ""
      if (eventPackage.isNotEmpty() && eventPackage != packageName) {
        val lowerPkg = eventPackage.lowercase()
        // Allow critical emergency & phone dialer calls
        if (!lowerPkg.contains("emergency") &&
          !lowerPkg.contains("telecom") &&
          !lowerPkg.contains("incallui") &&
          !lowerPkg.contains("dialer")
        ) {
          android.util.Log.d("DeliberateAccessibility", "Sticky gate active: intercepting attempt to switch to $eventPackage")
          GateLockState.relaunchStickyGate(this)
          return
        }
      }
    }

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
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
        Intent.FLAG_ACTIVITY_SINGLE_TOP or
        Intent.FLAG_ACTIVITY_NO_ANIMATION
    }
    val options = ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
    startActivity(intent, options)
  }

  override fun onInterrupt() {
    // Required override for AccessibilityService
  }
}
