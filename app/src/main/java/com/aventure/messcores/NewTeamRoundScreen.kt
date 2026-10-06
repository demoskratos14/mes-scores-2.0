package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTeamRoundScreen(
    viewModel: ScoreViewModel,
    onDone: () -> Unit
) {
    // Le Tarot a son propre écran, avec calcul automatique du score et des primes.
    if (viewModel.gameRules.id == "builtin_tarot") {
        NewTarotRoundScreen(viewModel = viewModel, onDone = onDone)
        return
    }

    val players = viewModel.players
    val multipliers = viewModel.gameRules.multipliers
    val allowNegative = viewModel.gameRules.allowNegativeScores

    // rememberSaveable : la saisie survit à une rotation de l'écran.
    var selectedPlayers by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(setOf<Int>()) }
    var multiplierId by rememberSaveable { mutableStateOf(GameRules.NORMAL_MULTIPLIER_ID) }
    var baseValueText by rememberSaveable { mutableStateOf("") }
    var isNegative by rememberSaveable { mutableStateOf(false) }

    val baseValue = baseValueText.toIntOrNull()
    val canValidate = selectedPlayers.isNotEmpty() && selectedPlayers.size < players.size && baseValue != null

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).imePadding(),
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
                Text("Nouvelle manche", style = MaterialTheme.typography.headlineMedium)

                Text(
                    "Qui prend, pour cette manche ? (preneur, ou preneur + appelé)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Column {
                    players.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPlayers = if (index in selectedPlayers) {
                                        selectedPlayers - index
                                    } else {
                                        selectedPlayers + index
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = index in selectedPlayers,
                                onCheckedChange = { checked ->
                                    selectedPlayers = if (checked) selectedPlayers + index else selectedPlayers - index
                                }
                            )
                            Text(name)
                        }
                    }
                }

                if (multipliers.size > 1) {
                    Text("Contrat", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        multipliers.forEach { m ->
                            FilterChip(
                                selected = multiplierId == m.id,
                                onClick = { multiplierId = m.id },
                                label = { Text("${m.label} ×${m.factor}") }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (allowNegative) {
                        Text(
                            text = if (isNegative) "−" else "+",
                            fontWeight = FontWeight.Bold,
                            color = if (isNegative) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { isNegative = !isNegative }
                                .padding(horizontal = 10.dp)
                        )
                    }
                    OutlinedTextField(
                        value = baseValueText,
                        onValueChange = { baseValueText = it.filter { c -> c.isDigit() } },
                        label = { Text("Score de l'équipe sélectionnée") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    "L'équipe sélectionnée reçoit ce score, tous les autres joueurs reçoivent l'opposé.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        val currentMultiplier = multipliers.find { it.id == multiplierId } ?: multipliers.first()
                        val label = selectedPlayers.joinToString(", ") { players[it] } +
                            if (multipliers.size > 1) " (${currentMultiplier.label})" else ""
                        viewModel.addTeamRound(
                            teamALabel = label,
                            teamAPlayers = selectedPlayers,
                            baseValue = baseValue ?: 0,
                            isNegative = isNegative,
                            multiplierId = multiplierId
                        )
                        onDone()
                    },
                    enabled = canValidate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Valider la manche")
                }
            }
        }
    }
}
