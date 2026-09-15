package com.example

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var settings by remember { mutableStateOf(ReminderPreferences.load(context)) }
  var showPermissionRationale by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    ReminderPreferences.setAskedPermission(context)
    if (isGranted) {
      val updated = settings.copy(isEnabled = true)
      settings = updated
      ReminderPreferences.save(context, updated)
      ReminderScheduler.scheduleNextReminder(context)
    } else {
      val updated = settings.copy(isEnabled = false)
      settings = updated
      ReminderPreferences.save(context, updated)
      ReminderScheduler.cancelReminders(context)
    }
  }

  val handleToggleReminders: (Boolean) -> Unit = { shouldEnable ->
    if (shouldEnable) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
          val updated = settings.copy(isEnabled = true)
          settings = updated
          ReminderPreferences.save(context, updated)
          ReminderScheduler.scheduleNextReminder(context)
        } else {
          // Explain why notification permission is needed before asking
          showPermissionRationale = true
        }
      } else {
        val updated = settings.copy(isEnabled = true)
        settings = updated
        ReminderPreferences.save(context, updated)
        ReminderScheduler.scheduleNextReminder(context)
      }
    } else {
      val updated = settings.copy(isEnabled = false)
      settings = updated
      ReminderPreferences.save(context, updated)
      ReminderScheduler.cancelReminders(context)
    }
  }

  if (showPermissionRationale) {
    AlertDialog(
      onDismissRequest = { showPermissionRationale = false },
      title = {
        Text(
          text = "Notification Permission",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
      },
      text = {
        Text(
          text = "Deliberate uses local notifications to periodically remind you to pause and choose your next action.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showPermissionRationale = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          },
          modifier = Modifier.testTag("permission_continue_button")
        ) {
          Text("Continue", color = MaterialTheme.colorScheme.onBackground)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showPermissionRationale = false },
          modifier = Modifier.testTag("permission_dismiss_button")
        ) {
          Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(18.dp)
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top Bar: Back Action & Title
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 32.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .clickable(onClick = onBack)
          .testTag("back_button"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "←",
          fontSize = 22.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Text(
        text = "Settings",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onBackground
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 440.dp)
    ) {
      // Section Header: Reminder
      Text(
        text = "REMINDER",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.4.sp
      )

      Spacer(modifier = Modifier.height(20.dp))

      // Reminders ON / OFF Switch
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Reminders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (settings.isEnabled) "ON" else "OFF",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
          )
        }

        Switch(
          checked = settings.isEnabled,
          onCheckedChange = handleToggleReminders,
          modifier = Modifier.testTag("reminders_switch"),
          colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface
          )
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(24.dp))

      // Interval Section
      Text(
        text = "Interval",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Interval Options: 60 minutes, 90 minutes, 120 minutes
      val intervalOptions = listOf(60, 90, 120)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        intervalOptions.forEach { minutes ->
          val isSelected = settings.intervalMinutes == minutes
          Box(
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .clip(RoundedCornerShape(12.dp))
              .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
              )
              .background(
                if (isSelected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
              )
              .clickable {
                val updated = settings.copy(intervalMinutes = minutes)
                settings = updated
                ReminderPreferences.save(context, updated)
                if (updated.isEnabled) {
                  ReminderScheduler.scheduleNextReminder(context)
                }
              }
              .testTag("interval_$minutes"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "$minutes min",
              fontSize = 14.sp,
              fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
              color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(24.dp))

      // Start time
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            showTimePicker(
              context = context,
              initialHour = settings.startHour,
              initialMinute = settings.startMinute
            ) { hour, minute ->
              val updated = settings.copy(startHour = hour, startMinute = minute)
              settings = updated
              ReminderPreferences.save(context, updated)
              if (updated.isEnabled) {
                ReminderScheduler.scheduleNextReminder(context)
              }
            }
          }
          .padding(vertical = 12.dp)
          .testTag("start_time_picker"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Start time",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Normal,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onBackground
        )

        Text(
          text = settings.formatStartTime(),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(8.dp))

      // End time
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            showTimePicker(
              context = context,
              initialHour = settings.endHour,
              initialMinute = settings.endMinute
            ) { hour, minute ->
              val updated = settings.copy(endHour = hour, endMinute = minute)
              settings = updated
              ReminderPreferences.save(context, updated)
              if (updated.isEnabled) {
                ReminderScheduler.scheduleNextReminder(context)
              }
            }
          }
          .padding(vertical = 12.dp)
          .testTag("end_time_picker"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "End time",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Normal,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onBackground
        )

        Text(
          text = settings.formatEndTime(),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun showTimePicker(
  context: Context,
  initialHour: Int,
  initialMinute: Int,
  onTimeSelected: (Int, Int) -> Unit
) {
  TimePickerDialog(
    context,
    { _, hourOfDay, minute ->
      onTimeSelected(hourOfDay, minute)
    },
    initialHour,
    initialMinute,
    DateFormat.is24HourFormat(context)
  ).show()
}
