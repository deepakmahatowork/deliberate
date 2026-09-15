package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

  companion object {
    const val ACTION_TRIGGER_REMINDER = "com.example.ACTION_TRIGGER_REMINDER"
    const val CHANNEL_ID = "deliberate_reminders"
    const val CHANNEL_NAME = "Deliberate Reminders"
    const val NOTIFICATION_ID = 2001

    const val NOTIFICATION_TEXT = "Doing? Stop.\nFeeling? Notice.\nFocus where? Here.\nNext? One deliberate action."

    fun createNotificationChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
          description = "Mindful reminders to pause and notice throughout your day."
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return

    when (action) {
      Intent.ACTION_BOOT_COMPLETED -> {
        ReminderScheduler.scheduleNextReminder(context)
      }

      ACTION_TRIGGER_REMINDER -> {
        val settings = ReminderPreferences.load(context)
        if (!settings.isEnabled) {
          ReminderScheduler.cancelReminders(context)
          return
        }

        // Check if current time falls within user's allowed hours
        if (isWithinActiveHours(settings)) {
          showNotification(context)
        }

        // Schedule next reminder
        ReminderScheduler.scheduleNextReminder(context)
      }
    }
  }

  private fun isWithinActiveHours(settings: ReminderSettings): Boolean {
    val now = Calendar.getInstance()
    val currentMinutesOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val startMinutesOfDay = settings.startHour * 60 + settings.startMinute
    val endMinutesOfDay = settings.endHour * 60 + settings.endMinute

    return if (startMinutesOfDay <= endMinutesOfDay) {
      currentMinutesOfDay in startMinutesOfDay..endMinutesOfDay
    } else {
      // Overnight range
      currentMinutesOfDay >= startMinutesOfDay || currentMinutesOfDay <= endMinutesOfDay
    }
  }

  private fun showNotification(context: Context) {
    createNotificationChannel(context)

    // Tapping the notification opens Deliberate app's Home screen
    val tapIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      tapIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("Deliberate")
      .setContentText("Doing? Stop.")
      .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIFICATION_TEXT))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()

    val notificationManager = ContextCompat.getSystemService(
      context,
      NotificationManager::class.java
    )
    notificationManager?.notify(NOTIFICATION_ID, notification)
  }
}
