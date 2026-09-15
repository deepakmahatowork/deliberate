package com.example

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manages the hard sticky overlay state for Deliberate.
 * Ensures the intervention screen cannot be bypassed by pressing Home,
 * opening Recents, or switching to other apps until a deliberate choice button is clicked.
 */
object GateLockState {
  private const val TAG = "GateLockState"

  @Volatile
  var isGateActive: Boolean = false
    private set

  @Volatile
  var isDismissAllowed: Boolean = false
    private set

  @Volatile
  var isStickyMode: Boolean = true
    private set

  fun activateGate(sticky: Boolean = true) {
    Log.d(TAG, "Intervention gate activated (sticky=$sticky)")
    isGateActive = true
    isDismissAllowed = false
    isStickyMode = sticky
  }

  fun unlockAndDismiss() {
    Log.d(TAG, "Intervention gate decision made, unlocking and dismissing")
    isDismissAllowed = true
    isGateActive = false
  }

  fun shouldEnforceSticky(): Boolean {
    return isGateActive && !isDismissAllowed && isStickyMode
  }

  /**
   * Immediately brings the InterventionActivity back to the front without animations.
   */
  fun relaunchStickyGate(context: Context) {
    if (!shouldEnforceSticky()) return

    try {
      Log.d(TAG, "Re-launching sticky gate to front")
      val intent = Intent(context, InterventionActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_NO_ANIMATION
      }
      val options = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
      context.startActivity(intent, options)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to relaunch sticky gate", e)
    }
  }
}
