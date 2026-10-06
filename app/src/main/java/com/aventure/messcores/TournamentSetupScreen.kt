package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun TournamentSetupScreen(
    onNext: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    var playerCountText by rememberSaveable { mutableStateOf("4") }
    val playerCount = (playerCountText.toIntOrNull() ?: 4).coerceIn(2, 32)

    var names by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(List(playerCount) { "" }) }

    LaunchedEffect(playerCount) {
        names = List(playerCount) { i -> names.getOrElse(i) { "" } }
    }

    // Toute la page défile : avec le clavier ouvert, on peut faire remonter les champs
    // (et le bouton) sans avoir à fermer le clavier.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.93f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Nouveau championnat", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Élimination directe : demi-finales, finale et petite finale sont " +
                        "générées automatiquement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = playerCountText,
                    onValueChange = { input -> playerCountText = input.filter { it.isDigit() } },
                    label = { Text("Nombre de participants") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    names.forEachIndexed { index, name ->
                        OutlinedTextField(
                            value = name,
                            onValueChange = { newName ->
                                names = names.toMutableList().also { it[index] = newName }
                            },
                            label = { Text("Participant ${index + 1}") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Button(
                    onClick = {
                        val finalNames = names.mapIndexed { i, n -> n.ifBlank { "Participant ${i + 1}" } }
                        onNext(finalNames)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tirer au sort et commencer")
                }

                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("← Retour")
                }
            }
        }
        }
    }
}
