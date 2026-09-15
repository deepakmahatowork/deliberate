package com.example

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var settings by remember { mutableStateOf(ReminderPreferences.load(context)) }
  var customMessageInput by remember(settings.customMessage) { mutableStateOf(settings.customMessage) }
  var showPermissionRationale by remember { mutableStateOf(false) }

  var gateSettings by remember { mutableStateOf(OverlayPreferences.load(context)) }
  var unlockSettings by remember { mutableStateOf(UnlockPreferences.load(context)) }
  var showGateExplanationDialog by remember { mutableStateOf(false) }
  var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
  var hasOverlayPermission by remember { mutableStateOf(canDrawOverlays(context)) }
  var isDeviceAdminOn by remember { mutableStateOf(isDeviceAdminActive(context)) }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        hasOverlayPermission = canDrawOverlays(context)
        isDeviceAdminOn = isDeviceAdminActive(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val handleToggleGate: (Boolean) -> Unit = { shouldEnable ->
    if (shouldEnable) {
      if (!isAccessibilityEnabled) {
        showGateExplanationDialog = true
      } else {
        val updated = gateSettings.copy(isGateEnabled = true)
        gateSettings = updated
        OverlayPreferences.saveGateEnabled(context, true)
      }
    } else {
      val updated = gateSettings.copy(isGateEnabled = false)
      gateSettings = updated
      OverlayPreferences.saveGateEnabled(context, false)
    }
  }

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

  if (showGateExplanationDialog) {
    AlertDialog(
      onDismissRequest = { showGateExplanationDialog = false },
      title = {
        Text(
          text = "Deliberate Gate Setup",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
      },
      text = {
        Text(
          text = "To pause you when unlocking your phone and allow the 'Nothing' button to lock the device safely without draining battery, Deliberate needs Accessibility Service access.\n\nDeliberate runs completely dormant, never tracks typing or observes personal screen content, and only activates after you unlock.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 22.sp
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showGateExplanationDialog = false
            val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
          },
          modifier = Modifier.testTag("gate_dialog_open_settings_button")
        ) {
          Text("Open Settings", color = MaterialTheme.colorScheme.onBackground)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showGateExplanationDialog = false },
          modifier = Modifier.testTag("gate_dialog_cancel_button")
        ) {
          Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(16.dp))

      // Custom Message Section
      Text(
        text = "Reminder message",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onBackground
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Customize the mindful text sent to your notification",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
      )
      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = customMessageInput,
        onValueChange = { newValue ->
          customMessageInput = newValue
          val updated = settings.copy(customMessage = newValue)
          settings = updated
          ReminderPreferences.save(context, updated)
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("custom_reminder_message_input"),
        minLines = 3,
        maxLines = 6,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.onBackground,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
          focusedTextColor = MaterialTheme.colorScheme.onBackground,
          unfocusedTextColor = MaterialTheme.colorScheme.onBackground
        )
      )

      if (customMessageInput != ReminderSettings.DEFAULT_REMINDER_MESSAGE) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = {
            customMessageInput = ReminderSettings.DEFAULT_REMINDER_MESSAGE
            val updated = settings.copy(customMessage = ReminderSettings.DEFAULT_REMINDER_MESSAGE)
            settings = updated
            ReminderPreferences.save(context, updated)
          },
          modifier = Modifier.testTag("reset_reminder_message_button")
        ) {
          Text(
            text = "Reset to default text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(32.dp))

      // Section Header: OPEN ON PHONE UNLOCK
      Text(
        text = "OPEN ON PHONE UNLOCK",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.4.sp
      )

      Spacer(modifier = Modifier.height(20.dp))

      // Open on Unlock Switch
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Open on Unlock",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (unlockSettings.isOpenOnUnlockEnabled) "Active: opens first when phone is unlocked" else "OFF",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
          )
        }

        Switch(
          checked = unlockSettings.isOpenOnUnlockEnabled,
          onCheckedChange = { isChecked ->
            val updated = unlockSettings.copy(isOpenOnUnlockEnabled = isChecked)
            unlockSettings = updated
            UnlockPreferences.save(context, updated)
          },
          modifier = Modifier.testTag("open_on_unlock_switch"),
          colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface
          )
        )
      }

      // Background launch capability indicator
      val hasBackgroundCapability = isAccessibilityEnabled || hasOverlayPermission
      Spacer(modifier = Modifier.height(8.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(
            if (hasBackgroundCapability) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
          )
          .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = if (hasBackgroundCapability) "●" else "⚠",
            color = if (hasBackgroundCapability) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontSize = 12.sp
          )
          Text(
            text = if (hasBackgroundCapability) {
              "Background launch ready (via ${if (isAccessibilityEnabled) "Accessibility" else "Overlay"})"
            } else {
              "Enable Accessibility Service below for instant background launch"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (hasBackgroundCapability) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
            fontSize = 12.sp
          )
        }
      }

      if (unlockSettings.isOpenOnUnlockEnabled) {
        Spacer(modifier = Modifier.height(18.dp))

        // Destination Screen Selector
        Text(
          text = "Screen to open on unlock",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Normal,
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Choose which screen appears first when you unlock",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Mindful Gate Option
          val isGate = unlockSettings.destination == UnlockDestination.INTERVENTION_GATE
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .border(
                width = 1.dp,
                color = if (isGate) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
              )
              .background(
                if (isGate) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
              )
              .clickable {
                val updated = unlockSettings.copy(destination = UnlockDestination.INTERVENTION_GATE)
                unlockSettings = updated
                UnlockPreferences.save(context, updated)
              }
              .padding(vertical = 12.dp, horizontal = 12.dp)
              .testTag("destination_gate"),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "Mindful Gate",
                fontSize = 14.sp,
                fontWeight = if (isGate) FontWeight.Medium else FontWeight.Normal,
                color = if (isGate) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Why pick up? + lock",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
              )
            }
          }

          // Deliberate Home Option
          val isHome = unlockSettings.destination == UnlockDestination.MAIN_APP
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .border(
                width = 1.dp,
                color = if (isHome) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
              )
              .background(
                if (isHome) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
              )
              .clickable {
                val updated = unlockSettings.copy(destination = UnlockDestination.MAIN_APP)
                unlockSettings = updated
                UnlockPreferences.save(context, updated)
              }
              .padding(vertical = 12.dp, horizontal = 12.dp)
              .testTag("destination_home"),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "Deliberate Home",
                fontSize = 14.sp,
                fontWeight = if (isHome) FontWeight.Medium else FontWeight.Normal,
                color = if (isHome) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "App dashboard",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Cooldown Options
        Text(
          text = "Unlock Frequency",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Normal,
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "How often Deliberate should open on unlock",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        val cooldownOptions = listOf(
          0 to "Always",
          30 to "30s",
          60 to "1 min",
          300 to "5 min"
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          cooldownOptions.forEach { (seconds, label) ->
            val isSelected = unlockSettings.cooldownSeconds == seconds
            Box(
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
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
                  val updated = unlockSettings.copy(cooldownSeconds = seconds)
                  unlockSettings = updated
                  UnlockPreferences.save(context, updated)
                }
                .testTag("unlock_cooldown_$seconds"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test Unlock Button
        OutlinedButton(
          onClick = {
            UnlockLauncher.launchOnUnlock(context, isTest = true)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("test_unlock_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text(
            text = "Test Unlock Open Now",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 14.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(32.dp))

      // Section Header: DELIBERATE GATE
      Text(
        text = "DELIBERATE GATE",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.4.sp
      )

      Spacer(modifier = Modifier.height(20.dp))

      // Gate ON / OFF Switch
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Intervention Gate",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (gateSettings.isGateEnabled) "ON" else "OFF",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
          )
        }

        Switch(
          checked = gateSettings.isGateEnabled,
          onCheckedChange = handleToggleGate,
          modifier = Modifier.testTag("gate_switch"),
          colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface
          )
        )
      }

      // Hard Sticky Overlay Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Hard Sticky Overlay",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (gateSettings.isStickyGateEnabled)
              "Blocks Home & app switching until choice buttons are clicked"
            else "OFF",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
          )
        }

        Switch(
          checked = gateSettings.isStickyGateEnabled,
          onCheckedChange = { isChecked ->
            val updated = gateSettings.copy(isStickyGateEnabled = isChecked)
            gateSettings = updated
            OverlayPreferences.saveStickyGateEnabled(context, isChecked)
          },
          modifier = Modifier.testTag("sticky_gate_switch"),
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

      // Cooldown Section
      Text(
        text = "Intervention Cooldown",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onBackground
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Normal phone use continues during cooldown.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        fontSize = 13.sp
      )

      Spacer(modifier = Modifier.height(14.dp))

      val cooldownOptions = listOf(5, 15, 30, 60)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        cooldownOptions.forEach { minutes ->
          val isSelected = gateSettings.cooldownMinutes == minutes
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
                val updated = gateSettings.copy(cooldownMinutes = minutes)
                gateSettings = updated
                OverlayPreferences.saveCooldownMinutes(context, minutes)
              }
              .testTag("cooldown_$minutes"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "$minutes min",
              fontSize = 13.sp,
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
      Spacer(modifier = Modifier.height(20.dp))

      // Capabilities & Permissions
      // 1. Accessibility Service
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
          }
          .padding(vertical = 10.dp)
          .testTag("accessibility_service_status"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Accessibility Service",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp
          )
          Text(
            text = "Event-driven unlock detection & device lock",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
          )
        }
        Text(
          text = if (isAccessibilityEnabled) "Active" else "Setup →",
          style = MaterialTheme.typography.labelMedium,
          color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium
        )
      }

      // 2. Overlay Permission
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              val intent = Intent(
                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
              ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
              }
              context.startActivity(intent)
            }
          }
          .padding(vertical = 10.dp)
          .testTag("overlay_permission_status"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Display Over Other Apps",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp
          )
          Text(
            text = "Allows intervention screen above other apps",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
          )
        }
        Text(
          text = if (hasOverlayPermission) "Granted" else "Setup →",
          style = MaterialTheme.typography.labelMedium,
          color = if (hasOverlayPermission) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium
        )
      }

      // 3. Device Admin (Optional Fallback)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            val adminComponent = ComponentName(context, DeliberateDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
              putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
              putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allows Deliberate to lock the phone when you tap Nothing."
              )
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
          }
          .padding(vertical = 10.dp)
          .testTag("device_admin_status"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Device Admin (Optional)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp
          )
          Text(
            text = if (isDeviceAdminOn) "Active: taps 'Nothing' lock screen directly" else "Alternative lock mechanism",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
          )
        }
        Text(
          text = if (isDeviceAdminOn) "Active" else "Setup →",
          style = MaterialTheme.typography.labelMedium,
          color = if (isDeviceAdminOn) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
          fontWeight = if (isDeviceAdminOn) FontWeight.Medium else FontWeight.Normal
        )
      }

      if (isDeviceAdminOn) {
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
          onClick = {
            val locked = DeliberateAccessibilityService.lockDevice(context)
            if (!locked) {
              android.widget.Toast.makeText(context, "Lock attempt failed", android.widget.Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("test_device_admin_lock_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text(
            text = "Test Device Lock Now",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 14.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      // Test Intervention Gate Button
      OutlinedButton(
        onClick = {
          val intent = Intent(context, InterventionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(intent)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("test_intervention_button"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.onBackground
        )
      ) {
        Text(
          text = "Test Intervention Gate",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          fontSize = 15.sp
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
  if (DeliberateAccessibilityService.isServiceActive()) return true
  val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
  val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
  for (service in enabledServices) {
    if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
      return true
    }
  }
  return false
}

private fun canDrawOverlays(context: Context): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    AndroidSettings.canDrawOverlays(context)
  } else {
    true
  }
}

private fun isDeviceAdminActive(context: Context): Boolean {
  val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
  val adminComponent = ComponentName(context, DeliberateDeviceAdminReceiver::class.java)
  return dpm?.isAdminActive(adminComponent) == true
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
