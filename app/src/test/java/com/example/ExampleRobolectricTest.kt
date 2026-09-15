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
}

