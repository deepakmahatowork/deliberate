package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class UnlockReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) return

    val action = intent.action
    Log.d("UnlockReceiver", "Received broadcast action: $action")

    when (action) {
      Intent.ACTION_USER_PRESENT -> {
        // The user unlocked the device! Open Deliberate immediately.
        UnlockLauncher.launchOnUnlock(context)
      }
      Intent.ACTION_BOOT_COMPLETED -> {
        // Ensure reminder schedule and any startup preferences are refreshed
        ReminderScheduler.scheduleNextReminder(context)
      }
    }
  }
}
