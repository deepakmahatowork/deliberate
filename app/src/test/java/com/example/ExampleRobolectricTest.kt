package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Deliberate", appName)
  }

  @Test
  fun `verify home screen hierarchy and pause button`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DeliberateApp()
      }
    }

    composeTestRule.onNodeWithText("BEFORE YOU USE").assertIsDisplayed()
    composeTestRule.onNodeWithText("Do I really need my phone?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Breathe.\nNotice.\nChoose.").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pause_button").assertIsDisplayed()

    // Tapping pause transitions to breathing screen
    composeTestRule.onNodeWithTag("pause_button").performClick()
    composeTestRule.onNodeWithTag("breathing_circle").assertIsDisplayed()
  }

  @Test
  fun `verify notification text and channel specifications`() {
    val expectedText = "Doing? Stop.\nFeeling? Notice.\nFocus where? Here.\nNext? One deliberate action."
    assertEquals(expectedText, ReminderReceiver.NOTIFICATION_TEXT)
    assertEquals("Deliberate Reminders", ReminderReceiver.CHANNEL_NAME)
    assertEquals("deliberate_reminders", ReminderReceiver.CHANNEL_ID)
  }

  @Test
  fun `verify reminder settings defaults`() {
    val defaultSettings = ReminderSettings()
    assertEquals(false, defaultSettings.isEnabled)
    assertEquals(60, defaultSettings.intervalMinutes)
    assertEquals("9:00 AM", defaultSettings.formatStartTime())
    assertEquals("9:00 PM", defaultSettings.formatEndTime())
  }

  @Test
  fun `verify navigation to settings and back to home`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DeliberateApp()
      }
    }

    // On Home screen
    composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_button").performClick()

    // On Settings screen
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    composeTestRule.onNodeWithText("REMINDER").assertIsDisplayed()
    composeTestRule.onNodeWithTag("reminders_switch").assertIsDisplayed()
    composeTestRule.onNodeWithTag("interval_60").assertExists()
    composeTestRule.onNodeWithTag("interval_90").assertExists()
    composeTestRule.onNodeWithTag("interval_120").assertExists()
    composeTestRule.onNodeWithTag("start_time_picker").assertExists()
    composeTestRule.onNodeWithTag("end_time_picker").assertExists()

    // Tapping back returns to Home screen
    composeTestRule.onNodeWithTag("back_button").performClick()
    composeTestRule.onNodeWithText("BEFORE YOU USE").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pause_button").assertIsDisplayed()
  }

  @Test
  fun `verify reminder scheduler interval calculation`() {
    val settings = ReminderSettings(
      isEnabled = true,
      intervalMinutes = 60,
      startHour = 9,
      startMinute = 0,
      endHour = 21,
      endMinute = 0
    )

    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 10)
    calendar.set(java.util.Calendar.MINUTE, 0)
    val testNow = calendar.timeInMillis

    val nextTrigger = ReminderScheduler.calculateNextTriggerMillis(settings, testNow)
    // 60 minutes after 10:00 is 11:00
    val expectedNext = testNow + 60 * 60 * 1000L
    assertEquals(expectedNext, nextTrigger)
  }

  @Test
  fun `verify overlay gate settings defaults and cooldown logic`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    OverlayPreferences.resetCooldown(context)
    OverlayPreferences.saveGateEnabled(context, false)
    OverlayPreferences.saveCooldownMinutes(context, 15)

    val defaultSettings = OverlayPreferences.load(context)
    assertEquals(false, defaultSettings.isGateEnabled)
    assertEquals(15, defaultSettings.cooldownMinutes)

    // Cooldown is not active when gate is disabled
    val now = 1000000L
    assertEquals(false, OverlayPreferences.isCooldownActive(context, now))

    // Enable gate and complete intervention
    OverlayPreferences.saveGateEnabled(context, true)
    OverlayPreferences.markInterventionCompleted(context, now)

    // Cooldown active after 5 minutes (300,000 ms)
    assertEquals(true, OverlayPreferences.isCooldownActive(context, now + 5 * 60 * 1000L))
    // Cooldown expired after 16 minutes (960,000 ms)
    assertEquals(false, OverlayPreferences.isCooldownActive(context, now + 16 * 60 * 1000L))

    // Remaining seconds calculation
    val remaining = OverlayPreferences.getRemainingCooldownSeconds(context, now + 5 * 60 * 1000L)
    assertEquals(10 * 60L, remaining)
  }

  @Test
  fun `verify intervention gate flow step 1 question and nothing button`() {
    var dismissed = false
    var locked = false

    composeTestRule.setContent {
      MyApplicationTheme {
        InterventionGateFlow(
          onDismissAndContinue = { dismissed = true },
          onLockDevice = { locked = true }
        )
      }
    }

    composeTestRule.onNodeWithText("BEFORE YOU USE").assertIsDisplayed()
    composeTestRule.onNodeWithText("Do I really need my phone?").assertIsDisplayed()
    composeTestRule.onNodeWithTag("intervention_pause_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nothing_button_step1").assertIsDisplayed()

    // Tapping Nothing triggers lock
    composeTestRule.onNodeWithTag("nothing_button_step1").performClick()
    assertEquals(true, locked)
    assertEquals(false, dismissed)
  }

  @Test
  fun `verify intervention gate pause moves to breathing with nothing button`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        InterventionGateFlow(
          onDismissAndContinue = {},
          onLockDevice = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("intervention_pause_button").performClick()
    composeTestRule.onNodeWithText("Take one breath.").assertIsDisplayed()
    composeTestRule.onNodeWithTag("intervention_breathing_circle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nothing_button_step2").assertIsDisplayed()
  }

  @Test
  fun `verify settings screen shows deliberate gate section and options`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DeliberateApp()
      }
    }

    // Go to Settings
    composeTestRule.onNodeWithTag("settings_button").performClick()

    // Verify Deliberate Gate section
    composeTestRule.onNodeWithText("DELIBERATE GATE").assertExists()
    composeTestRule.onNodeWithText("Intervention Gate").assertExists()
    composeTestRule.onNodeWithTag("gate_switch").assertExists()
    composeTestRule.onNodeWithText("Intervention Cooldown").assertExists()
    composeTestRule.onNodeWithTag("cooldown_15").assertExists()
    composeTestRule.onNodeWithTag("accessibility_service_status").assertExists()
    composeTestRule.onNodeWithTag("overlay_permission_status").assertExists()
    composeTestRule.onNodeWithTag("device_admin_status").assertExists()
    composeTestRule.onNodeWithTag("test_intervention_button").assertExists()
  }

  @Test
  fun `verify repeated 20 intervention transitions execute smoothly`() {
    var lockedCount = 0
    val iteration = androidx.compose.runtime.mutableIntStateOf(0)

    composeTestRule.setContent {
      MyApplicationTheme {
        androidx.compose.runtime.key(iteration.intValue) {
          InterventionGateFlow(
            onDismissAndContinue = {},
            onLockDevice = {
              lockedCount++
              iteration.intValue++
            }
          )
        }
      }
    }

    for (i in 1..20) {
      composeTestRule.onNodeWithTag("nothing_button_step1").performClick()
      assertEquals(i, lockedCount)
    }
  }
}

