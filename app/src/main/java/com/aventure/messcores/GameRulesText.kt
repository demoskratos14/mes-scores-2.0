package com.aventure.messcores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Une section d'une fiche de règles (ex : "But du jeu"). */
private data class RulesSection(val title: String, val body: String)

/** Règles rédigées pour les jeux prédéfinis, indexées par l'id du jeu (voir GameRepository). */
private val BUILT_IN_RULES: Map<String, List<RulesSection>> = mapOf(
    "builtin_generic" to listOf(
        RulesSection(
            "Principe",
            "Jeu libre : l'appli sert simplement de feuille de scores pour n'importe quel jeu."
        ),
        RulesSection(
            "Comptage des points",
            "Chaque joueur a sa colonne. À chaque manche, on note les points marqués. " +
                "Le total le plus haut est en tête du classement."
        ),
        RulesSection(
            "Dans l'appli",
            "Touchez une case pour saisir un score. Une nouvelle manche s'ajoute automatiquement. " +
                "La partie ne se termine jamais toute seule."
        )
    ),
    "builtin_skyjo" to listOf(
        RulesSection(
            "But du jeu",
            "Avoir le moins de points possible quand un joueur atteint ou dépasse 100 points."
        ),
        RulesSection(
            "Mise en place",
            "• 150 cartes valant de −2 à 12.\n" +
                "• Chaque joueur reçoit 12 cartes face cachée, en grille de 3 lignes × 4 colonnes.\n" +
                "• Chacun retourne 2 cartes de sa grille. Celui qui a le total le plus élevé commence.\n" +
                "• On retourne une carte pour démarrer la défausse."
        ),
        RulesSection(
            "Déroulement d'un tour",
            "• Piocher une carte, ou prendre la carte visible de la défausse.\n" +
                "• Carte de la pioche : l'échanger contre une carte de sa grille (visible ou cachée), " +
                "ou la défausser et retourner une carte cachée.\n" +
                "• Carte de la défausse : on doit l'échanger contre une carte de sa grille.\n" +
                "• Si une colonne affiche 3 cartes identiques, elle est défaussée."
        ),
        RulesSection(
            "Fin d'une manche",
            "Dès qu'un joueur a toutes ses cartes visibles, les autres jouent un dernier tour. " +
                "Chacun additionne ensuite ses cartes. Si celui qui a terminé n'a pas strictement " +
                "le plus petit score de la manche, son score est doublé (s'il est positif)."
        ),
        RulesSection(
            "Dans l'appli",
            "Saisissez le score de chaque joueur à chaque manche (bouton +/− pour les négatifs). " +
                "Le classement favorise le total le plus bas. La partie s'arrête à la fin de la manche " +
                "où un joueur atteint 100 points."
        )
    ),
    "builtin_tarot" to listOf(
        RulesSection(
            "But du jeu",
            "Le preneur (seul, ou avec un partenaire à 5 joueurs) doit réaliser le contrat annoncé " +
                "face aux défenseurs. Se joue de 3 à 5 joueurs, le plus souvent à 4."
        ),
        RulesSection(
            "Les cartes",
            "78 cartes : 56 cartes de couleur, 21 atouts et l'Excuse. " +
                "Les 3 bouts sont le 1 d'atout (le petit), le 21 et l'Excuse. " +
                "Valeurs : Roi 4,5 · Dame 3,5 · Cavalier 2,5 · Valet 1,5 · bout 4,5 · autres cartes 0,5 " +
                "(91 points en tout)."
        ),
        RulesSection(
            "Enchères",
            "• Petite (×1) : le preneur prend le chien, puis écarte le même nombre de cartes.\n" +
                "• Garde (×2) : comme la petite, avec une mise plus élevée.\n" +
                "• Garde sans le chien (×4) : le chien reste au preneur sans être utilisé.\n" +
                "• Garde contre le chien (×6) : le chien va aux défenseurs."
        ),
        RulesSection(
            "Contrat à réaliser",
            "Selon le nombre de bouts du preneur : 0 bout = 56 points · 1 bout = 51 · " +
                "2 bouts = 41 · 3 bouts = 36."
        ),
        RulesSection(
            "Comptage des points",
            "Score de base = 25 + l'écart entre les points faits et le contrat. Il est positif si " +
                "le contrat est réussi, négatif s'il est chuté. Il est multiplié par le coefficient " +
                "de l'enchère.\n" +
                "• Petit au bout : ±10, multiplié par le coefficient (+ pour le preneur, − pour la défense).\n" +
                "• Poignée : simple 20 · double 30 · triple 40, acquise par le camp qui gagne la manche " +
                "(non multipliée).\n" +
                "• Chelem (tous les plis) : +200 non annoncé · +400 annoncé réussi · −200 annoncé raté " +
                "(non multiplié).\n" +
                "• Chelem de la défense : +200 pour les défenseurs (règle maison, absente du règlement officiel).\n" +
                "Le score obtenu est payé par chaque défenseur au preneur (et à son partenaire à 5 joueurs)."
        ),
        RulesSection(
            "Dans l'appli",
            "Pour chaque manche, choisissez le preneur (et l'appelé à 5 joueurs), le contrat, " +
                "le nombre de bouts et les points réalisés, puis cochez les primes éventuelles. " +
                "À 91 points, le chelem réussi est ajouté automatiquement. " +
                "L'appli calcule le score de chaque joueur (la somme de la manche est toujours nulle) " +
                "et l'affiche avant validation. Le crayon de l'historique permet de corriger une manche."
        )
    ),
    "builtin_belote" to listOf(
        RulesSection(
            "But du jeu",
            "Se joue à 4 en 2 équipes avec 32 cartes. Une équipe prend un atout et doit marquer " +
                "plus de points que l'adversaire. La partie se joue en plusieurs manches jusqu'au score cible."
        ),
        RulesSection(
            "Valeur des cartes",
            "• À l'atout : Valet 20 · 9 14 · As 11 · 10 10 · Roi 4 · Dame 3 · 8 et 7 : 0.\n" +
                "• Hors atout : As 11 · 10 10 · Roi 4 · Dame 3 · Valet 2 · 9, 8, 7 : 0.\n" +
                "• Dix de der (dernier pli) : +10. Total d'une manche : 162 points."
        ),
        RulesSection(
            "Déroulement",
            "Chaque joueur reçoit 8 cartes. Le preneur choisit l'atout. On joue 8 plis, " +
                "il faut fournir la couleur demandée, sinon couper ou surcouper à l'atout."
        ),
        RulesSection(
            "Comptage des points",
            "• Belote-rebelote (Roi + Dame d'atout) : +20.\n" +
                "• Le preneur doit faire au moins 82 points. Sinon c'est « dedans » : " +
                "l'adversaire marque 162 points.\n" +
                "• Capot (tous les plis) : 252 points.\n" +
                "• Des annonces (tierce, cinquante, cent, carré) existent selon les variantes."
        ),
        RulesSection(
            "Dans l'appli",
            "Saisissez les points de chaque manche. La partie s'arrête à la fin de la manche où " +
                "un joueur ou une équipe atteint 501 points."
        )
    ),
    "builtin_rami" to listOf(
        RulesSection(
            "But du jeu",
            "Être le premier à se débarrasser de toutes ses cartes, et finir la partie " +
                "avec le moins de points possible."
        ),
        RulesSection(
            "Déroulement",
            "• À son tour, on pioche (pioche ou défausse), on peut poser des combinaisons, " +
                "puis on défausse une carte.\n" +
                "• Combinaisons : suites d'au moins 3 cartes de la même couleur, " +
                "ou brelans / carrés de même valeur dans des couleurs différentes.\n" +
                "• Le joker remplace n'importe quelle carte.\n" +
                "• La première pose doit atteindre un minimum de points, à convenir avant de jouer " +
                "(les variantes diffèrent)."
        ),
        RulesSection(
            "Comptage des points",
            "Le joueur qui « ferme » (plus aucune carte en main) marque 0. Les autres comptent " +
                "les cartes qui leur restent : cartes numérotées = leur valeur, figures = 10, " +
                "As et joker selon la variante choisie."
        ),
        RulesSection(
            "Dans l'appli",
            "Saisissez les points de chaque joueur à chaque manche. Le total le plus bas gagne " +
                "et la partie s'arrête à la fin de la manche où un joueur atteint 500 points."
        )
    ),
    "builtin_uno" to listOf(
        RulesSection(
            "But du jeu",
            "Être le premier à poser toutes ses cartes."
        ),
        RulesSection(
            "Déroulement",
            "• Chaque joueur reçoit 7 cartes. On retourne une carte pour démarrer la défausse.\n" +
                "• On pose une carte de la même couleur, du même chiffre ou du même symbole.\n" +
                "• Sans carte jouable, on pioche une carte.\n" +
                "• Cartes spéciales : +2, inversion de sens, passe ton tour, joker, joker +4.\n" +
                "• Il faut dire « UNO » avant de poser son avant-dernière carte, sinon on pioche 2 cartes de pénalité."
        ),
        RulesSection(
            "Comptage des points",
            "Le gagnant de la manche marque les points des cartes restant dans les mains des autres : " +
                "cartes numérotées = leur valeur · +2, inversion, passe ton tour = 20 · " +
                "joker et joker +4 = 50."
        ),
        RulesSection(
            "Dans l'appli",
            "Saisissez les points gagnés par joueur à chaque manche. Le score le plus haut est en tête. " +
                "La partie ne se termine pas automatiquement (la règle officielle fixe l'objectif à 500 points)."
        )
    )
)

/**
 * Bouton « Règles » à afficher à la suite du nom du jeu. N'affiche rien pour les jeux
 * personnalisés, qui n'ont pas de règles rédigées.
 */
@Composable
fun RulesButton(rules: GameRules, modifier: Modifier = Modifier) {
    val sections = BUILT_IN_RULES[rules.id] ?: return
    var showDialog by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text("Règles", style = MaterialTheme.typography.labelLarge)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Règles · ${rules.name}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    sections.forEachIndexed { index, section ->
                        if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = section.body,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Fermer") }
            }
        )
    }
}
