package com.aventure.messcores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Jeux pour lesquels le seuil de fin de partie (500/1000 points) est ajustable ici. */
private val LONG_GAME_TOGGLE_IDS = setOf("builtin_belote", "builtin_rami")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseGameScreen(
    repository: GameRepository,
    playerCount: Int,
    onGameChosen: (GameRules) -> Unit,
    onCreateNewGame: () -> Unit,
    onEditGame: (GameRules) -> Unit
) {
    var games by remember { mutableStateOf(repository.allGames()) }
    // On retient l'id (et non le jeu) : la sélection survit au passage par le formulaire de modification.
    var selectedId by rememberSaveable { mutableStateOf("builtin_generic") }
    val selected = games.firstOrNull { it.id == selectedId } ?: games.first()
    var expanded by remember { mutableStateOf(false) }
    var longGame by remember { mutableStateOf(false) }
    var genericMode by remember { mutableStateOf(ScoreMode.TABLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                Text("Quel jeu ?", style = MaterialTheme.typography.headlineMedium)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selected.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Jeu") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        games.forEach { game ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(game.name)
                                        Text(
                                            text = ruleSummary(game),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedId = game.id
                                    errorMessage = null
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = ruleSummary(selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selected.id == "builtin_generic") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Mode de score", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = genericMode == ScoreMode.TABLE,
                                onClick = { genericMode = ScoreMode.TABLE },
                                label = { Text("Tableau (manches)") }
                            )
                            FilterChip(
                                selected = genericMode == ScoreMode.COUNTER,
                                onClick = { genericMode = ScoreMode.COUNTER },
                                label = { Text("Compteur (+1/-1)") }
                            )
                        }
                    }
                }

                if (selected.id in LONG_GAME_TOGGLE_IDS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Partie longue")
                            Text(
                                text = if (longGame) "Fin à 1000 points" else "Fin à 500 points",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = longGame, onCheckedChange = { longGame = it })
                    }
                }

                // Seuls les jeux créés par l'utilisateur sont modifiables ou supprimables.
                if (!selected.id.startsWith("builtin_")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEditGame(selected) }, modifier = Modifier.weight(1f)) {
                            Text("Modifier ce jeu")
                        }
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                            Text("Supprimer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCreateNewGame,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Créer un nouveau jeu")
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        errorMessage = playerCountError(selected, playerCount)
                        if (errorMessage != null) return@Button
                        val finalRules = when {
                            selected.id == "builtin_generic" -> selected.copy(scoreMode = genericMode)
                            selected.id in LONG_GAME_TOGGLE_IDS -> selected.copy(
                                endCondition = EndCondition(
                                    type = EndConditionType.SCORE_THRESHOLD,
                                    scoreThreshold = if (longGame) 1000 else 500
                                )
                            )
                            else -> selected
                        }
                        onGameChosen(finalRules)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continuer")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${selected.name} » ?") },
            text = { Text("Le jeu disparaît de la liste. Les parties déjà enregistrées au journal sont conservées.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteCustomGame(selected.id)
                    games = repository.allGames()
                    selectedId = "builtin_generic"
                    errorMessage = null
                    confirmDelete = false
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            }
        )
    }
}

/** Message d'erreur si le nombre de joueurs ne convient pas au jeu, sinon null. */
private fun playerCountError(game: GameRules, playerCount: Int): String? = when {
    playerCount < game.minPlayers ->
        "${game.name} se joue à ${playersRange(game)}. Tu as saisi $playerCount joueur${if (playerCount > 1) "s" else ""} : " +
            "il en faut au moins ${game.minPlayers}. Reviens en arrière pour en ajouter."
    playerCount > game.maxPlayers ->
        "${game.name} se joue à ${playersRange(game)}. Tu as saisi $playerCount joueurs : " +
            "il n'en faut pas plus de ${game.maxPlayers}. Reviens en arrière pour en retirer."
    else -> null
}

private fun playersRange(game: GameRules): String =
    if (game.minPlayers == game.maxPlayers) "${game.minPlayers} joueurs"
    else "${game.minPlayers} à ${game.maxPlayers} joueurs"

private fun ruleSummary(game: GameRules): String {
    val direction = if (game.lowestWins) "le plus petit score gagne" else "le plus grand score gagne"
    val negative = if (game.allowNegativeScores) "scores négatifs autorisés" else "scores positifs uniquement"
    val players = if (game.minPlayers == GameRules.DEFAULT_MIN_PLAYERS && game.maxPlayers == GameRules.DEFAULT_MAX_PLAYERS) ""
    else " · ${playersRange(game)}"
    return "$direction · $negative$players"
}
