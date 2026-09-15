package com.example

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

object UnlockLauncher {
  private const val TAG = "DeliberateUnlock"
  private const val CHANNEL_ID_UNLOCK = "deliberate_unlock_channel"
  private const val NOTIFICATION_ID_UNLOCK = 1002

  @Volatile
  private var lastLaunchTimestamp: Long = 0L

  fun launchOnUnlock(context: Context, isTest: Boolean = false): Boolean {
    val settings = UnlockPreferences.load(context)
    if (!settings.isOpenOnUnlockEnabled && !isTest) {
      Log.d(TAG, "Open on unlock is disabled in settings")
      return false
    }

    val now = System.currentTimeMillis()
    if (!isTest) {
      // 1. Debounce within 1.2 seconds so multiple receivers don't fire twice
      if (now - lastLaunchTimestamp < 1200L) {
        Log.d(TAG, "Debounced unlock trigger")
        return false
      }

      // 2. Check cooldown if configured
      if (settings.cooldownSeconds > 0) {
        val lastLaunch = UnlockPreferences.getLastUnlockLaunchTime(context)
        if (lastLaunch > 0L && (now - lastLaunch < settings.cooldownSeconds * 1000L)) {
          Log.d(TAG, "Cooldown active, skipping launch")
          return false
        }
      }
    }

    lastLaunchTimestamp = now
    UnlockPreferences.recordUnlockLaunch(context, now)

    val targetClass = when (settings.destination) {
      UnlockDestination.INTERVENTION_GATE -> InterventionActivity::class.java
      UnlockDestination.MAIN_APP -> MainActivity::class.java
    }

    val launchIntent = Intent(context, targetClass).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP or
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
        Intent.FLAG_ACTIVITY_NO_ANIMATION
      putExtra("triggered_by_unlock", true)
    }

    val animOptions = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()

    // Mechanism 1: Accessibility Service (Highest priority for instant background activity start)
    val activeService = DeliberateAccessibilityService.getInstance()
    if (activeService != null) {
      try {
        Log.d(TAG, "Instant launch via DeliberateAccessibilityService")
        activeService.startActivity(launchIntent, animOptions)
        return true
      } catch (e: Exception) {
        Log.e(TAG, "Failed launching via AccessibilityService", e)
      }
    }

    // Mechanism 2: Direct Context Launch (Works when overlay permission granted or on pre-Q)
    try {
      Log.d(TAG, "Instant launch via context.startActivity")
      context.startActivity(launchIntent, animOptions)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)) {
        return true
      }
    } catch (e: Exception) {
      Log.e(TAG, "Direct launch failed", e)
    }

    // Mechanism 3: Full-screen Intent notification for background activity launch on Android 10+
    triggerFullScreenUnlockNotification(context, launchIntent)
    return true
  }

  private fun triggerFullScreenUnlockNotification(context: Context, targetIntent: Intent) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID_UNLOCK,
        "Deliberate Unlock Notification",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Opens Deliberate immediately upon unlocking device"
        setShowBadge(true)
        enableVibration(false)
        setSound(null, null)
      }
      nm.createNotificationChannel(channel)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      NOTIFICATION_ID_UNLOCK,
      targetIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID_UNLOCK)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("Deliberate")
      .setContentText("Open Deliberate — Mindful Intent")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setFullScreenIntent(pendingIntent, true)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    try {
      nm.notify(NOTIFICATION_ID_UNLOCK, notification)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to post unlock notification", e)
    }
  }
}
