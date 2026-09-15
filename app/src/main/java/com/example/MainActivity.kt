package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
  INITIAL,
  BREATHING,
  INTENTION,
  RESOLVE
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
  var currentState by remember { mutableStateOf(DeliberateState.INITIAL) }
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
          .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        when (state) {
          DeliberateState.INITIAL -> {
            InitialStep(
              onPause = { currentState = DeliberateState.BREATHING }
            )
          }

          DeliberateState.BREATHING -> {
            BreathingStep(
              onFinished = { currentState = DeliberateState.INTENTION }
            )
          }

          DeliberateState.INTENTION -> {
            IntentionStep(
              currentAction = userAction,
              onActionChange = { userAction = it },
              onContinue = { currentState = DeliberateState.RESOLVE }
            )
          }

          DeliberateState.RESOLVE -> {
            ResolveStep(
              userAction = userAction,
              onReset = {
                userAction = ""
                currentState = DeliberateState.INITIAL
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InitialStep(
  onPause: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Do I really need my phone?",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Light,
      lineHeight = 42.sp,
      fontSize = 32.sp
    )

    Spacer(modifier = Modifier.height(36.dp))

    Text(
      text = "Breathe.\nNotice.\nChoose.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      lineHeight = 34.sp,
      fontSize = 20.sp,
      letterSpacing = 1.sp
    )

    Spacer(modifier = Modifier.height(56.dp))

    Button(
      onClick = onPause,
      modifier = Modifier
        .fillMaxWidth(0.6f)
        .height(52.dp)
        .testTag("pause_button"),
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      )
    ) {
      Text(
        text = "Pause",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        letterSpacing = 0.5.sp
      )
    }
  }
}

@Composable
private fun BreathingStep(
  onFinished: () -> Unit,
  modifier: Modifier = Modifier
) {
  var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
  var secondsLeftInPhase by remember { mutableIntStateOf(BreathingPhase.INHALE.totalSeconds) }

  val orbScale = remember { Animatable(0.48f) }
  val orbAlpha = remember { Animatable(0.4f) }

  LaunchedEffect(Unit) {
    // 1. Inhale: 4 seconds
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
    launch {
      orbAlpha.animateTo(
        targetValue = 0.95f,
        animationSpec = tween(durationMillis = 4000, easing = EaseInOutCubic)
      )
    }
    inhaleCountdown.join()

    // 2. Hold: 2 seconds
    currentPhase = BreathingPhase.HOLD
    secondsLeftInPhase = 2
    val holdCountdown = launch {
      for (s in 2 downTo 1) {
        secondsLeftInPhase = s
        delay(1000L)
      }
    }
    holdCountdown.join()

    // 3. Exhale: 6 seconds
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
        targetValue = 0.48f,
        animationSpec = tween(durationMillis = 6000, easing = EaseInOutCubic)
      )
    }
    launch {
      orbAlpha.animateTo(
        targetValue = 0.4f,
        animationSpec = tween(durationMillis = 6000, easing = EaseInOutCubic)
      )
    }
    exhaleCountdown.join()

    delay(400L)
    onFinished()
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Take one breath.",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Light,
      fontSize = 28.sp,
      lineHeight = 36.sp
    )

    Spacer(modifier = Modifier.height(48.dp))

    // Animated Breathing Orb Canvas / Shape
    Box(
      modifier = Modifier
        .size(230.dp)
        .testTag("breathing_circle"),
      contentAlignment = Alignment.Center
    ) {
      // Outer subtle perimeter ring
      Box(
        modifier = Modifier
          .size(230.dp)
          .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            shape = CircleShape
          )
      )

      // Dynamic breathing sphere
      Box(
        modifier = Modifier
          .size(230.dp)
          .scale(orbScale.value)
          .clip(CircleShape)
          .background(
            MaterialTheme.colorScheme.secondary.copy(alpha = orbAlpha.value * 0.22f)
          )
          .border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = orbAlpha.value),
            shape = CircleShape
          )
      )

      // Inner text indicators
      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = currentPhase.label.uppercase(),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 2.5.sp,
          fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "${secondsLeftInPhase}s",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Light,
          fontSize = 18.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = when (currentPhase) {
        BreathingPhase.INHALE -> "Deep inhale through your nose"
        BreathingPhase.HOLD -> "Gentle still pause"
        BreathingPhase.EXHALE -> "Slow, complete release"
      },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
      fontSize = 15.sp,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun IntentionStep(
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
      fontWeight = FontWeight.Light,
      fontSize = 30.sp,
      lineHeight = 38.sp
    )

    Spacer(modifier = Modifier.height(40.dp))

    OutlinedTextField(
      value = currentAction,
      onValueChange = onActionChange,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("action_text_field"),
      placeholder = {
        Text(
          text = "Enter your intention...",
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
      },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        cursorColor = MaterialTheme.colorScheme.primary
      ),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(
        onDone = {
          focusManager.clearFocus()
          onContinue()
        }
      )
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
      onClick = {
        focusManager.clearFocus()
        onContinue()
      },
      modifier = Modifier
        .fillMaxWidth(0.6f)
        .height(52.dp)
        .testTag("continue_button"),
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      )
    ) {
      Text(
        text = "Continue",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        letterSpacing = 0.5.sp
      )
    }
  }
}

@Composable
private fun ResolveStep(
  userAction: String,
  onReset: () -> Unit,
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
      fontWeight = FontWeight.Light,
      fontSize = 32.sp,
      lineHeight = 42.sp
    )

    if (userAction.isNotBlank()) {
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "“$userAction”",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        fontSize = 18.sp,
        lineHeight = 26.sp
      )
    }

    Spacer(modifier = Modifier.height(64.dp))

    OutlinedButton(
      onClick = onReset,
      modifier = Modifier
        .height(44.dp)
        .testTag("reset_button"),
      shape = RoundedCornerShape(999.dp),
      border = ButtonDefaults.outlinedButtonBorder.copy(
        brush = androidx.compose.ui.graphics.SolidColor(
          MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
      )
    ) {
      Text(
        text = "Start again",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
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

