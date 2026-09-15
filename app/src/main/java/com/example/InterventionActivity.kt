package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class InterventionStep {
  QUESTION,
  BREATHING,
  INTENTION,
  CONFIRMATION
}

class InterventionActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(0, 0)
    }
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      km?.requestDismissKeyguard(this, null)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    }

    val settings = OverlayPreferences.load(this)
    GateLockState.activateGate(sticky = settings.isStickyGateEnabled)

    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
          InterventionGateFlow(
            onDismissAndContinue = {
              GateLockState.unlockAndDismiss()
              // 1. Mark intervention completed to start the 15-minute cooldown
              OverlayPreferences.markInterventionCompleted(this)
              // 2. Destroy overlay activity completely to free memory and return to normal phone use
              finish()
            },
            onLockDevice = {
              GateLockState.unlockAndDismiss()
              // Immediately lock the phone using the safest legitimate Android mechanism
              val locked = DeliberateAccessibilityService.lockDevice(this)
              if (!locked) {
                Toast.makeText(
                  this,
                  "Enable Accessibility or Device Admin in Deliberate Settings to lock device.",
                  Toast.LENGTH_SHORT
                ).show()
              }
              finish()
            },
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          )
        }
      }
    }
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (GateLockState.shouldEnforceSticky()) {
      GateLockState.relaunchStickyGate(this)
    }
  }

  override fun onPause() {
    super.onPause()
    if (GateLockState.shouldEnforceSticky() && !isFinishing) {
      GateLockState.relaunchStickyGate(this)
    }
  }

  override fun onStop() {
    super.onStop()
    if (GateLockState.shouldEnforceSticky() && !isFinishing) {
      GateLockState.relaunchStickyGate(this)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (!GateLockState.isDismissAllowed && GateLockState.shouldEnforceSticky()) {
      GateLockState.relaunchStickyGate(this)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    if (GateLockState.isDismissAllowed) {
      @Suppress("DEPRECATION")
      super.onBackPressed()
    }
    // Hard sticky gate: ignore back press until user clicks a button
  }
}

@Composable
fun InterventionGateFlow(
  onDismissAndContinue: () -> Unit,
  onLockDevice: () -> Unit,
  modifier: Modifier = Modifier
) {
  var currentStep by remember { mutableStateOf(InterventionStep.QUESTION) }
  var userAction by remember { mutableStateOf("") }
  var inactivityKey by remember { mutableIntStateOf(0) }

  // Auto-close / lock device after 20 seconds if no action is taken
  LaunchedEffect(currentStep, userAction, inactivityKey) {
    delay(20000L)
    onLockDevice()
  }

  // Prevent back gesture from escaping to home or other apps without clicking buttons
  BackHandler(enabled = true) {
    // Hard overlay: stays sticky until buttons are clicked
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.navigationBars),
    contentAlignment = Alignment.Center
  ) {
    AnimatedContent(
      targetState = currentStep,
      transitionSpec = {
        fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
      },
      label = "intervention_gate_step"
    ) { step ->
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 440.dp)
          .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        when (step) {
          InterventionStep.QUESTION -> {
            InterventionQuestionStep(
              onPause = {
                currentStep = InterventionStep.BREATHING
                inactivityKey++
              },
              onNothing = onLockDevice,
              onDismissAndContinue = onDismissAndContinue
            )
          }

          InterventionStep.BREATHING -> {
            InterventionBreathingStep(
              onFinished = {
                currentStep = InterventionStep.INTENTION
                inactivityKey++
              },
              onNothing = onLockDevice
            )
          }

          InterventionStep.INTENTION -> {
            InterventionIntentionStep(
              currentAction = userAction,
              onActionChange = {
                userAction = it
                inactivityKey++
              },
              onContinue = {
                // Clicking GO answers why and exits/closes the app immediately
                onDismissAndContinue()
              },
              onNothing = onLockDevice,
              onInteract = { inactivityKey++ }
            )
          }

          InterventionStep.CONFIRMATION -> {
            InterventionConfirmationStep(
              userAction = userAction,
              onContinue = onDismissAndContinue,
              onNothing = onLockDevice
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InterventionQuestionStep(
  onPause: () -> Unit,
  onNothing: () -> Unit,
  onDismissAndContinue: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = androidx.compose.ui.platform.LocalContext.current

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "BEFORE YOU USE",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 2.8.sp,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Do I really need my phone?",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Normal,
      fontSize = 28.sp,
      lineHeight = 36.sp
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Fast-access WhatsApp & Phone shortcut buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // WhatsApp shortcut
      OutlinedButton(
        onClick = {
          try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
              ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com"))
            context.startActivity(intent)
            onDismissAndContinue()
          } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not found", Toast.LENGTH_SHORT).show()
          }
        },
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onBackground
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
          .height(48.dp)
          .testTag("whatsapp_fast_button")
      ) {
        Text("💬 WhatsApp", fontSize = 15.sp, fontWeight = FontWeight.Medium)
      }

      // Phone dialer shortcut
      OutlinedButton(
        onClick = {
          try {
            val intent = Intent(Intent.ACTION_DIAL)
            context.startActivity(intent)
            onDismissAndContinue()
          } catch (e: Exception) {
            Toast.makeText(context, "Phone dialer not available", Toast.LENGTH_SHORT).show()
          }
        },
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onBackground
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
          .height(48.dp)
          .testTag("phone_fast_button")
      ) {
        Text("📞 Phone Call", fontSize = 15.sp, fontWeight = FontWeight.Medium)
      }
    }

    Spacer(modifier = Modifier.height(40.dp))

    Button(
      onClick = onPause,
      modifier = Modifier
        .width(200.dp)
        .height(50.dp)
        .testTag("intervention_pause_button"),
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ),
      elevation = null
    ) {
      Text(
        text = "Pause / Reflect",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.6.sp
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    NothingButton(onClick = onNothing, testTag = "nothing_button_step1")
  }
}

@Composable
private fun InterventionBreathingStep(
  onFinished: () -> Unit,
  onNothing: () -> Unit,
  modifier: Modifier = Modifier
) {
  var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
  var secondsLeftInPhase by remember { mutableIntStateOf(BreathingPhase.INHALE.totalSeconds) }

  val orbScale = remember { Animatable(0.44f) }
  val progressAnim = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
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
    Text(
      text = "Take one breath.",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
      fontSize = 17.sp,
      letterSpacing = 0.5.sp
    )

    Spacer(modifier = Modifier.height(32.dp))

    Box(
      modifier = Modifier
        .size(240.dp)
        .testTag("intervention_breathing_circle"),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.size(240.dp)) {
        drawCircle(
          color = trackColor,
          style = Stroke(width = 1.5.dp.toPx())
        )
        drawArc(
          color = indicatorColor,
          startAngle = -90f,
          sweepAngle = progressAnim.value * 360f,
          useCenter = false,
          style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
      }

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

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

    Spacer(modifier = Modifier.height(48.dp))

    NothingButton(onClick = onNothing, testTag = "nothing_button_step2")
  }
}

@Composable
private fun InterventionIntentionStep(
  currentAction: String,
  onActionChange: (String) -> Unit,
  onContinue: () -> Unit,
  onNothing: () -> Unit,
  onInteract: () -> Unit,
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

    Spacer(modifier = Modifier.height(28.dp))

    // Quick selection buttons: News : Whatsapp : GPT
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf("News", "WhatsApp", "GPT").forEach { option ->
        val isSelected = currentAction.equals(option, ignoreCase = true)
        OutlinedButton(
          onClick = {
            onActionChange(option)
            onInteract()
          },
          shape = RoundedCornerShape(999.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
          ),
          border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          ),
          modifier = Modifier
            .height(42.dp)
            .testTag("intention_chip_$option")
        ) {
          Text(
            text = option,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedTextField(
      value = currentAction,
      onValueChange = {
        onActionChange(it)
        onInteract()
      },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("intervention_action_text_field"),
      placeholder = {
        Text(
          text = "Type intention (or select above)...",
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
          fontSize = 15.sp
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

    Spacer(modifier = Modifier.height(32.dp))

    Button(
      onClick = {
        focusManager.clearFocus()
        onContinue()
      },
      modifier = Modifier
        .width(200.dp)
        .height(52.dp)
        .testTag("intervention_go_button"),
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ),
      elevation = null
    ) {
      Text(
        text = "GO",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 1.2.sp
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    NothingButton(onClick = onNothing, testTag = "nothing_button_step3")
  }
}

@Composable
private fun InterventionConfirmationStep(
  userAction: String,
  onContinue: () -> Unit,
  onNothing: () -> Unit,
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

    Spacer(modifier = Modifier.height(56.dp))

    Button(
      onClick = onContinue,
      modifier = Modifier
        .width(200.dp)
        .height(52.dp)
        .testTag("intervention_final_continue_button"),
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

    Spacer(modifier = Modifier.height(16.dp))

    NothingButton(onClick = onNothing, testTag = "nothing_button_step4")
  }
}

@Composable
private fun NothingButton(
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier
      .width(200.dp)
      .height(48.dp)
      .testTag(testTag),
    shape = RoundedCornerShape(999.dp),
    colors = ButtonDefaults.outlinedButtonColors(
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
  ) {
    Text(
      text = "Nothing",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Normal,
      fontSize = 15.sp,
      letterSpacing = 0.5.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
