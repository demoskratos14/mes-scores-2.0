package com.aventure.messcores

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private fun formatMillis(ms: Long, roundUp: Boolean = false): String {
    // Un minuteur affiche la seconde entamée (00:01 jusqu'à zéro), un chronomètre les secondes écoulées.
    val totalSeconds = (if (roundUp) (ms + 999) / 1000 else ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * Badge affiché en superposition sur tous les écrans de l'app (voir MainActivity.kt),
 * affichant en permanence le temps du chronomètre/minuteur. Cliquer dessus ouvre la
 * fenêtre de réglage (chronomètre ou minuteur).
 */
@Composable
fun TimerOverlay(viewModel: TimerViewModel) {
    // rememberSaveable : la fenêtre du minuteur reste ouverte après une rotation de l'écran.
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val displayMillis = if (viewModel.mode == TimerMode.STOPWATCH) {
        viewModel.elapsedMillis
    } else {
        viewModel.countdownRemainingMillis
    }

    // Quand le minuteur est terminé, le badge clignote en rouge jusqu'à la réinitialisation.
    val blink = if (viewModel.justFinished) {
        val transition = rememberInfiniteTransition(label = "timerBlink")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label = "timerBlinkAlpha"
        ).value
    } else {
        1f
    }
    val badgeColor = when {
        viewModel.justFinished -> Color(0xFFD32F2F).copy(alpha = blink)
        viewModel.isRunning -> Color.Black.copy(alpha = 0.8f)
        else -> Color.Black.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = badgeColor),
            modifier = Modifier.clickable { showDialog = true }
        ) {
            Text(
                text = formatMillis(displayMillis, roundUp = viewModel.mode == TimerMode.COUNTDOWN),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }

    if (showDialog) {
        TimerDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }
}

@Composable
private fun TimerDialog(viewModel: TimerViewModel, onDismiss: () -> Unit) {
    var minutesText by rememberSaveable { mutableStateOf((viewModel.countdownDurationMillis / 60000).toString()) }
    val context = LocalContext.current
    // Android 13+ : l'alerte de fin en arrière-plan est une notification, qui demande une autorisation.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text("Fermer") }
        },
        title = { Text("Chronomètre") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewModel.mode == TimerMode.STOPWATCH,
                        onClick = { viewModel.selectMode(TimerMode.STOPWATCH) },
                        label = { Text("Chronomètre") }
                    )
                    FilterChip(
                        selected = viewModel.mode == TimerMode.COUNTDOWN,
                        onClick = { viewModel.selectMode(TimerMode.COUNTDOWN) },
                        label = { Text("Minuteur") }
                    )
                }

                if (viewModel.mode == TimerMode.COUNTDOWN && !viewModel.isRunning) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { input ->
                            minutesText = input.filter { it.isDigit() }
                            minutesText.toIntOrNull()?.let { viewModel.setCountdownMinutes(it) }
                        },
                        label = { Text("Durée (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val displayMillis = if (viewModel.mode == TimerMode.STOPWATCH) {
                    viewModel.elapsedMillis
                } else {
                    viewModel.countdownRemainingMillis
                }

                Text(
                    text = formatMillis(displayMillis, roundUp = viewModel.mode == TimerMode.COUNTDOWN),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (viewModel.justFinished) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                if (viewModel.justFinished) {
                    Text(
                        text = "Temps écoulé !",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (viewModel.isRunning) {
                            viewModel.pause()
                        } else {
                            if (viewModel.mode == TimerMode.COUNTDOWN &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.start()
                        }
                    }) {
                        Icon(
                            imageVector = if (viewModel.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (viewModel.isRunning) "Pause" else "Démarrer")
                    }
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Réinitialiser")
                    }
                }
            }
        }
    )
}
