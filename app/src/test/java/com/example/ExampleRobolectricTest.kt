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
}

