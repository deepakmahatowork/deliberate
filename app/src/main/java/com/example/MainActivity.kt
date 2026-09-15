package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DeliberateState {
  HOME,
  BREATHING,
  INTENTION,
  CONFIRMATION
}

enum class BreathingPhase(val label: String, val totalSeconds: Int) {
  INHALE("Inhale", 4),
  HOLD("Hold", 2),
  EXHALE("Exhale", 6)
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
          DeliberateApp(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          )
        }
      }
    }
  }
}

@Composable
fun DeliberateApp(modifier: Modifier = Modifier) {
  var currentState by remember { mutableStateOf(DeliberateState.HOME) }
  var userAction by remember { mutableStateOf("") }

  Box(
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.navigationBars),
    contentAlignment = Alignment.Center
  ) {
    AnimatedContent(
      targetState = currentState,
      transitionSpec = {
        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
      },
      label = "deliberate_state_transition"
    ) { state ->
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 440.dp)
          .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        when (state) {
          DeliberateState.HOME -> {
            HomeScreen(
              onPause = { currentState = DeliberateState.BREATHING }
            )
          }

          DeliberateState.BREATHING -> {
            BreathingScreen(
              onFinished = { currentState = DeliberateState.INTENTION }
            )
          }

          DeliberateState.INTENTION -> {
            IntentionScreen(
              currentAction = userAction,
              onActionChange = { userAction = it },
              onContinue = { currentState = DeliberateState.CONFIRMATION }
            )
          }

          DeliberateState.CONFIRMATION -> {
            ConfirmationScreen(
              userAction = userAction,
              onGo = {
                userAction = ""
                currentState = DeliberateState.HOME
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeScreen(
  onPause: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Subtle Eyebrow Label
    Text(
      text = "BEFORE YOU USE",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 2.8.sp,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(28.dp))

    // Main Question
    Text(
      text = "Do I really need my phone?",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Normal,
      fontSize = 32.sp,
      lineHeight = 42.sp
    )

    Spacer(modifier = Modifier.height(36.dp))

    // Mindfulness Stanza
    Text(
      text = "Breathe.\nNotice.\nChoose.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      lineHeight = 36.sp,
      fontSize = 19.sp,
      letterSpacing = 0.5.sp
    )

    Spacer(modifier = Modifier.height(64.dp))

    // Obvious Primary Action Button
    Button(
      onClick = onPause,
      modifier = Modifier
        .width(180.dp)
        .height(52.dp)
        .testTag("pause_button"),
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ),
      elevation = null
    ) {
      Text(
        text = "Pause",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.6.sp
      )
    }
  }
}

@Composable
private fun BreathingScreen(
  onFinished: () -> Unit,
  modifier: Modifier = Modifier
) {
  var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
  var secondsLeftInPhase by remember { mutableIntStateOf(BreathingPhase.INHALE.totalSeconds) }

  val orbScale = remember { Animatable(0.44f) }
  val progressAnim = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    // 12-second total cycle subtle progress indicator
    launch {
      progressAnim.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 12000, easing = LinearEasing)
      )
    }

    // Phase 1: Inhale (4 seconds)
    currentPhase = BreathingPhase.INHALE
    secondsLeftInPhase = 4
    val inhaleCountdown = launch {
      for (s in 4 downTo 1) {
        secondsLeftInPhase = s
        delay(1000L)
      }
    }
    launch {
      orbScale.animateTo(
        targetValue = 1.0f,
        animationSpec = tween(durationMillis = 4000, easing = EaseInOutCubic)
      )
    }
    inhaleCountdown.join()

    // Phase 2: Hold (2 seconds)
    currentPhase = BreathingPhase.HOLD
    secondsLeftInPhase = 2
    val holdCountdown = launch {
      for (s in 2 downTo 1) {
        secondsLeftInPhase = s
        delay(1000L)
      }
    }
    holdCountdown.join()

    // Phase 3: Exhale (6 seconds)
    currentPhase = BreathingPhase.EXHALE
    secondsLeftInPhase = 6
    val exhaleCountdown = launch {
      for (s in 6 downTo 1) {
        secondsLeftInPhase = s
        delay(1000L)
      }
    }
    launch {
      orbScale.animateTo(
        targetValue = 0.44f,
        animationSpec = tween(durationMillis = 6000, easing = EaseInOutCubic)
      )
    }
    exhaleCountdown.join()

    delay(300L)
    onFinished()
  }

  val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
  val indicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Breathing Visual with Subtle Progress Indicator
    Box(
      modifier = Modifier
        .size(240.dp)
        .testTag("breathing_circle"),
      contentAlignment = Alignment.Center
    ) {
      // Subtle Circular Progress Indicator
      Canvas(modifier = Modifier.size(240.dp)) {
        // Track
        drawCircle(
          color = trackColor,
          style = Stroke(width = 1.5.dp.toPx())
        )
        // Active Progress Arc
        drawArc(
          color = indicatorColor,
          startAngle = -90f,
          sweepAngle = progressAnim.value * 360f,
          useCenter = false,
          style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
      }

      // Minimal flat breathing sphere (no gradients, lightweight)
      Box(
        modifier = Modifier
          .size(240.dp)
          .scale(orbScale.value)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
          .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
            shape = CircleShape
          )
      )

      // Current Phase Display
      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = currentPhase.label,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
          fontWeight = FontWeight.Normal,
          fontSize = 28.sp,
          letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "${secondsLeftInPhase}s",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 15.sp
        )
      }
    }
  }
}

@Composable
private fun IntentionScreen(
  currentAction: String,
  onActionChange: (String) -> Unit,
  onContinue: () -> Unit,
  modifier: Modifier = Modifier
) {
  val focusManager = LocalFocusManager.current

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "What am I here to do?",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Normal,
      fontSize = 30.sp,
      lineHeight = 40.sp
    )

    Spacer(modifier = Modifier.height(44.dp))

    // Minimal single text field
    OutlinedTextField(
      value = currentAction,
      onValueChange = onActionChange,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("action_text_field"),
      placeholder = {
        Text(
          text = "Type your intention...",
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
          fontSize = 16.sp
        )
      },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        cursorColor = MaterialTheme.colorScheme.onBackground
      ),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(
        onDone = {
          focusManager.clearFocus()
          onContinue()
        }
      )
    )

    Spacer(modifier = Modifier.height(36.dp))

    Button(
      onClick = {
        focusManager.clearFocus()
        onContinue()
      },
      modifier = Modifier
        .width(180.dp)
        .height(52.dp)
        .testTag("continue_button"),
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ),
      elevation = null
    ) {
      Text(
        text = "Continue",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.6.sp
      )
    }
  }
}

@Composable
private fun ConfirmationScreen(
  userAction: String,
  onGo: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "One deliberate action.",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Normal,
      fontSize = 32.sp,
      lineHeight = 42.sp
    )

    if (userAction.isNotBlank()) {
      Spacer(modifier = Modifier.height(28.dp))
      Text(
        text = userAction,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        fontSize = 18.sp,
        lineHeight = 26.sp
      )
    }

    Spacer(modifier = Modifier.height(64.dp))

    Button(
      onClick = onGo,
      modifier = Modifier
        .width(180.dp)
        .height(52.dp)
        .testTag("go_button")
        .testTag("reset_button"),
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ),
      elevation = null
    ) {
      Text(
        text = "Go",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.6.sp
      )
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun DeliberateAppPreview() {
  MyApplicationTheme {
    DeliberateApp()
  }
}


