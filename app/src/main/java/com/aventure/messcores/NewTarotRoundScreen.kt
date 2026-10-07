package com.aventure.messcores

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}

/** Nombre d'atouts à montrer pour une poignée simple / double / triple, selon le nombre de joueurs. */
private fun handfulThresholds(playerCount: Int): String? = when (playerCount) {
    3 -> "13 / 15 / 18"
    4 -> "10 / 13 / 15"
    5 -> "8 / 10 / 13"
    else -> null
}

/** Rangée de choix exclusifs, défilante horizontalement si elle ne tient pas à l'écran. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(label) }
            )
        }
    }
}

/**
 * Saisie d'une manche de Tarot avec calcul automatique du score selon les règles officielles :
 * contrat, bouts, points réalisés, petit au bout, poignée et chelem. Le résultat est réparti
 * entre le preneur, son éventuel partenaire (à 5) et les défenseurs, de façon à ce que la somme
 * des scores d'une manche soit toujours nulle.
 */
@Composable
fun NewTarotRoundScreen(
    viewModel: ScoreViewModel,
    onDone: () -> Unit
) {
    val players = viewModel.players
    val multipliers = viewModel.gameRules.multipliers

    // Si on corrige une manche existante, les champs démarrent avec sa saisie d'origine.
    val editIndex = viewModel.editingTeamRoundIndex
    val editing: TarotRoundInput? = editIndex?.let { viewModel.teamRounds.getOrNull(it)?.tarotInput }

    // rememberSaveable : la saisie survit à une rotation de l'écran.
    var takerIndex by rememberSaveable { mutableStateOf<Int?>(editing?.taker) }
    // Index d'un joueur ; null = pas de partenaire (preneur seul).
    var partnerIndex by rememberSaveable { mutableStateOf<Int?>(editing?.partner) }
    var multiplierIndex by rememberSaveable { mutableStateOf(editing?.multiplierIndex ?: 0) }
    var bouts by rememberSaveable { mutableStateOf(editing?.bouts ?: 0) }
    var pointsText by rememberSaveable { mutableStateOf(editing?.points?.toString() ?: "") }
    var petitAuBout by rememberSaveable { mutableStateOf(editing?.petitAuBout ?: 0) } // 0 aucun, 1 preneur, 2 défense
    var handful by rememberSaveable { mutableStateOf(editing?.handful ?: 0) }         // 0 aucune, 1 simple, 2 double, 3 triple
    var slamAnnounced by rememberSaveable { mutableStateOf(editing?.slamAnnounced ?: false) } // chelem annoncé par le preneur
    var defenseSlam by rememberSaveable { mutableStateOf(editing?.defenseSlam ?: false) }     // la défense a fait tous les plis

    val taker = takerIndex
    val points = pointsText.toIntOrNull()?.takeIf { it in 0..TAROT_TOTAL_POINTS }
    val hasPartnerChoice = players.size >= 5
    val partner = if (hasPartnerChoice && partnerIndex != taker) partnerIndex else null
    val multiplier = multipliers.getOrElse(multiplierIndex) { multipliers.first() }

    // ----- Calcul (voir TarotScoring.kt) -----
    val required = tarotRequiredPoints(bouts)
    val result = points?.let {
        computeTarotRound(
            playerCount = players.size,
            taker = taker,
            partner = partner,
            factor = multiplier.factor,
            bouts = bouts,
            points = it,
            petitAuBout = petitAuBout,
            handful = handful,
            slamAnnounced = slamAnnounced,
            defenseSlam = defenseSlam
        )
    }
    val success = result?.success ?: true
    val contractFailed = result?.success == false
    val slamSucceeded = result?.slamSucceeded ?: false
    val defenseSlamActive = result?.defenseSlamActive ?: false
    val deltas = result?.deltas
    val canValidate = taker != null && result != null && deltas != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 24.dp)
            .imePadding()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.93f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (editing != null) "Modifier la manche" else "Nouvelle manche",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text("Preneur", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(players, takerIndex) { takerIndex = it }

                if (hasPartnerChoice) {
                    Text("Joueur appelé (partenaire)", style = MaterialTheme.typography.titleSmall)
                    val others = players.indices.filter { it != taker }
                    ChoiceRow(
                        options = listOf("Aucun (seul)") + others.map { players[it] },
                        selected = partner?.let { others.indexOf(it) + 1 } ?: 0
                    ) { choice -> partnerIndex = if (choice == 0) null else others[choice - 1] }
                }

                Text("Contrat", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    options = multipliers.map { "${it.label} ×${it.factor}" },
                    selected = multiplierIndex
                ) { multiplierIndex = it }

                Text("Bouts du preneur", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(listOf("0 bout", "1 bout", "2 bouts", "3 bouts"), bouts) { bouts = it }
                Text(
                    "Contrat à réaliser : $required points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Points réalisés par le preneur (0 à 91)") },
                    isError = pointsText.isNotEmpty() && points == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Petit au bout", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(listOf("Aucun", "Preneur", "Défense"), petitAuBout) { petitAuBout = it }

                Text("Poignée", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    listOf("Aucune", "Simple 20", "Double 30", "Triple 40"),
                    handful
                ) { handful = it }
                handfulThresholds(players.size)?.let {
                    Text(
                        "Atouts à montrer (simple / double / triple) : $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text("Chelem", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    options = listOf("Non annoncé", "Annoncé par le preneur"),
                    selected = if (slamAnnounced) 1 else 0
                ) { slamAnnounced = it == 1 }
                if (contractFailed) {
                    ChoiceRow(
                        options = listOf("La défense n'a pas fait tous les plis", "Chelem de la défense"),
                        selected = if (defenseSlam) 1 else 0
                    ) { defenseSlam = it == 1 }
                }
                val slamHint = when {
                    slamSucceeded && slamAnnounced -> "91 points : chelem annoncé réussi (+400)"
                    slamSucceeded -> "91 points : chelem réussi non annoncé (+200)"
                    defenseSlamActive -> "Chelem de la défense : +$DEFENSE_SLAM_PRIZE pour les défenseurs (règle maison)"
                    slamAnnounced && points != null -> "Chelem annoncé mais raté (−200)"
                    else -> "Le chelem réussi est ajouté automatiquement à 91 points."
                }
                Text(
                    slamHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ----- Aperçu du résultat -----
                val preview = deltas
                if (taker != null && result != null && preview != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (success) {
                                    "Contrat réussi de ${result.difference} point(s)"
                                } else {
                                    "Contrat chuté de ${-result.difference} point(s)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Score par défenseur : ${signed(-result.unit)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            players.forEachIndexed { index, name ->
                                Text("$name : ${signed(preview[index])}")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val roundDeltas = deltas ?: return@Button
                        val takerId = taker ?: return@Button
                        val pointsMade = points ?: return@Button
                        val unit = result?.unit ?: return@Button
                        val attackers = setOfNotNull(takerId, partner)
                        val team = attackers.joinToString(" + ") { players[it] }
                        val label = "$team · ${multiplier.label} · " +
                            "$bouts bout${if (bouts > 1) "s" else ""} · $points pts " +
                            (if (success) "(réussi)" else "(chuté)") +
                            when {
                                slamSucceeded -> " · chelem"
                                defenseSlamActive -> " · chelem de la défense"
                                else -> ""
                            }
                        val round = TeamRound(
                            teamALabel = label,
                            teamAPlayers = attackers,
                            value = unit,
                            tarotInput = TarotRoundInput(
                                taker = takerId,
                                partner = partner,
                                multiplierIndex = multiplierIndex,
                                bouts = bouts,
                                points = pointsMade,
                                petitAuBout = petitAuBout,
                                handful = handful,
                                slamAnnounced = slamAnnounced,
                                defenseSlam = defenseSlam
                            ),
                            deltas = roundDeltas
                        )
                        if (editIndex != null && editing != null) {
                            viewModel.replaceTeamRound(editIndex, round)
                        } else {
                            viewModel.appendTeamRound(round)
                        }
                        viewModel.editingTeamRoundIndex = null
                        onDone()
                    },
                    enabled = canValidate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (editing != null) "Enregistrer la modification" else "Valider la manche")
                }
            }
        }
    }
}
