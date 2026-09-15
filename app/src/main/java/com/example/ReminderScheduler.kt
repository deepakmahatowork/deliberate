package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {
  const val PENDING_INTENT_REQUEST_CODE = 1001

  fun scheduleNextReminder(context: Context) {
    val settings = ReminderPreferences.load(context)
    if (!settings.isEnabled) {
      cancelReminders(context)
      return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pendingIntent = createPendingIntent(context)

    val nextTriggerMillis = calculateNextTriggerMillis(settings)
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pendingIntent)
  }

  fun cancelReminders(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pendingIntent = createPendingIntent(context)
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
  }

  fun calculateNextTriggerMillis(
    settings: ReminderSettings,
    currentTimeMillis: Long = System.currentTimeMillis()
  ): Long {
    val now = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }

    val startCal = Calendar.getInstance().apply {
      timeInMillis = currentTimeMillis
      set(Calendar.HOUR_OF_DAY, settings.startHour)
      set(Calendar.MINUTE, settings.startMinute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val endCal = Calendar.getInstance().apply {
      timeInMillis = currentTimeMillis
      set(Calendar.HOUR_OF_DAY, settings.endHour)
      set(Calendar.MINUTE, settings.endMinute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    // If end time is before or equal to start time on same day, treat end as next day
    if (endCal.before(startCal) || endCal == startCal) {
      endCal.add(Calendar.DAY_OF_YEAR, 1)
    }

    return when {
      now.before(startCal) -> {
        startCal.timeInMillis
      }
      now.after(endCal) -> {
        startCal.add(Calendar.DAY_OF_YEAR, 1)
        startCal.timeInMillis
      }
      else -> {
        val intervalMillis = settings.intervalMinutes * 60 * 1000L
        val nextTime = now.timeInMillis + intervalMillis
        if (nextTime > endCal.timeInMillis) {
          startCal.add(Calendar.DAY_OF_YEAR, 1)
          startCal.timeInMillis
        } else {
          nextTime
        }
      }
    }
  }

  private fun createPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, ReminderReceiver::class.java).apply {
      action = ReminderReceiver.ACTION_TRIGGER_REMINDER
    }
    return PendingIntent.getBroadcast(
      context,
      PENDING_INTENT_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }
}
