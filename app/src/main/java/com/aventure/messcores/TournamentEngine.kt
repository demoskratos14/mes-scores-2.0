package com.aventure.messcores

import kotlin.random.Random

/**
 * Un match du championnat. [playerAIndex]/[playerBIndex] référencent l'index du participant dans
 * [TournamentState.participants] ; null en cas de "bye" (qualification directe faute d'adversaire,
 * pour les effectifs qui ne sont pas une puissance de 2). [label] identifie les matchs spéciaux du
 * dernier tour ("Finale", "Petite finale") ; null pour un tour normal.
 */
data class TournamentMatch(
    val round: Int,
    val label: String? = null,
    val playerAIndex: Int? = null,
    val playerBIndex: Int? = null,
    val winnerIndex: Int? = null
) {
    val isBye: Boolean get() = playerAIndex == null || playerBIndex == null
}

/**
 * État complet d'un championnat. Immuable : chaque action de [TournamentEngine] en renvoie un nouveau,
 * ce qui le rend trivial à tester (aucun état Compose, aucune dépendance Android).
 */
data class TournamentState(
    val participants: List<String> = emptyList(),
    val rounds: List<List<TournamentMatch>> = emptyList(),
    val finished: Boolean = false
) {
    /** Vrai si un championnat (en cours ou terminé) existe. */
    val hasTournament: Boolean get() = rounds.isNotEmpty()

    /** Vrai si des matchs des tours suivants [roundIndex] ont déjà été joués (donc perdus si on corrige ce tour). */
    fun hasPlayedAfter(roundIndex: Int): Boolean =
        rounds.drop(roundIndex + 1).any { round -> round.any { !it.isBye && it.winnerIndex != null } }

    /** Vainqueur du championnat, une fois [finished] vrai. */
    fun championIndex(): Int? {
        if (!finished) return null
        val last = rounds.lastOrNull() ?: return null
        val finalMatch = last.find { it.label == "Finale" } ?: last.firstOrNull()
        return finalMatch?.winnerIndex
    }

    /** Vainqueur de la petite finale (3e place), s'il y en a une. */
    fun thirdPlaceIndex(): Int? =
        rounds.lastOrNull()?.find { it.label == "Petite finale" }?.winnerIndex

    /**
     * Classement final, de la 1re place à la 4e au maximum : vainqueur et perdant de la finale,
     * puis vainqueur et perdant de la petite finale. À 3 participants (pas de petite finale),
     * la 3e place revient au perdant du seul match réellement joué au premier tour.
     * Liste vide tant que le championnat n'est pas terminé.
     */
    fun podium(): List<Int> {
        if (!finished) return emptyList()
        val last = rounds.lastOrNull() ?: return emptyList()
        val finalMatch = last.find { it.label == "Finale" } ?: last.singleOrNull() ?: return emptyList()

        val result = mutableListOf<Int>()
        finalMatch.winnerIndex?.let { result.add(it) }
        loserOf(finalMatch)?.let { result.add(it) }

        val smallFinal = last.find { it.label == "Petite finale" }
        if (smallFinal != null) {
            smallFinal.winnerIndex?.let { result.add(it) }
            loserOf(smallFinal)?.let { result.add(it) }
        } else if (rounds.size >= 2) {
            val eliminated = rounds[rounds.size - 2].filter { !it.isBye }.mapNotNull { loserOf(it) }
            if (eliminated.size == 1) result.add(eliminated.first())
        }
        return result
    }

    private fun loserOf(match: TournamentMatch): Int? {
        val winner = match.winnerIndex ?: return null
        return if (match.playerAIndex == winner) match.playerBIndex else match.playerAIndex
    }
}

/**
 * Règles du championnat à élimination directe : le vainqueur de chaque match passe au tour suivant.
 * Dès que le tour ne compte plus que 2 matchs (les demi-finales) et que les deux sont joués sans
 * "bye", le tour suivant regroupe automatiquement la Finale (les deux vainqueurs) et la Petite
 * finale (les deux perdants). Les effectifs qui ne sont pas une puissance de 2 sont comblés par des
 * qualifications directes ("byes") uniquement au premier tour.
 */
object TournamentEngine {

    /** Démarre un championnat en tirant l'ordre du tableau au sort ([random] est injectable pour les tests). */
    fun start(names: List<String>, random: Random = Random.Default): TournamentState {
        if (names.size < 2) return TournamentState(participants = names)

        var bracketSize = 1
        while (bracketSize < names.size) bracketSize *= 2

        // Les byes sont répartis à raison d'un par match au maximum : deux places vides face à face
        // donneraient un match impossible à jouer.
        val byeCount = bracketSize - names.size
        val order = names.indices.shuffled(random)
        val pairings = mutableListOf<Pair<Int, Int?>>()
        for (k in 0 until byeCount) pairings.add(order[k] to null)
        var k = byeCount
        while (k + 1 < order.size) {
            pairings.add(order[k] to order[k + 1])
            k += 2
        }
        pairings.shuffle(random)

        val firstRound = pairings.map { (a, b) -> TournamentMatch(round = 1, playerAIndex = a, playerBIndex = b) }
        return settle(TournamentState(participants = names, rounds = listOf(firstRound)))
    }

    /**
     * Désigne [winner] (index dans les participants) comme vainqueur du match indiqué.
     * Sans effet si le match n'existe pas, si [winner] n'y joue pas, ou si le match a déjà un
     * résultat (il faut d'abord le corriger avec [resetMatch], sinon les tours suivants divergeraient).
     */
    fun setWinner(state: TournamentState, roundIndex: Int, matchIndex: Int, winner: Int): TournamentState {
        val match = state.rounds.getOrNull(roundIndex)?.getOrNull(matchIndex) ?: return state
        if (match.winnerIndex != null) return state
        if (match.playerAIndex != winner && match.playerBIndex != winner) return state
        return settle(state.replaceMatch(roundIndex, matchIndex, match.copy(winnerIndex = winner)))
    }

    /**
     * Annule le résultat d'un match (erreur de saisie) pour pouvoir désigner à nouveau le vainqueur.
     * Les tours suivants, qui découlaient de ce résultat, sont supprimés : ils sont régénérés quand
     * le tour est de nouveau complet. Sans effet sur un match sans résultat ou un "bye".
     */
    fun resetMatch(state: TournamentState, roundIndex: Int, matchIndex: Int): TournamentState {
        val match = state.rounds.getOrNull(roundIndex)?.getOrNull(matchIndex) ?: return state
        if (match.isBye || match.winnerIndex == null) return state
        val kept = state.rounds.take(roundIndex + 1)
        return state.copy(rounds = kept, finished = false)
            .replaceMatch(roundIndex, matchIndex, match.copy(winnerIndex = null))
    }

    private fun TournamentState.replaceMatch(roundIndex: Int, matchIndex: Int, match: TournamentMatch) =
        copy(
            rounds = rounds.mapIndexed { r, round ->
                if (r == roundIndex) round.mapIndexed { m, old -> if (m == matchIndex) match else old } else round
            }
        )

    /**
     * Fait avancer le championnat tant que le tour courant est complet : qualifie les byes, puis
     * génère le tour suivant (ou la Finale + Petite finale), ou termine le championnat.
     */
    private fun settle(initial: TournamentState): TournamentState {
        var rounds = initial.rounds
        var finished = initial.finished
        while (!finished) {
            val current = rounds.lastOrNull() ?: break
            // Un bye n'a pas d'adversaire : son seul joueur passe automatiquement.
            val resolved = current.map {
                if (it.winnerIndex == null && it.isBye) it.copy(winnerIndex = it.playerAIndex ?: it.playerBIndex) else it
            }
            rounds = rounds.dropLast(1) + listOf(resolved)
            if (resolved.any { it.winnerIndex == null }) break

            val next = resolved.first().round + 1
            when {
                // Le tour Finale + Petite finale vient de se terminer.
                resolved.any { it.label != null } -> finished = true
                // Championnat à 2 participants : ce match unique EST la finale.
                resolved.size == 1 -> finished = true
                // Demi-finales terminées, sans bye : Finale (vainqueurs) + Petite finale (perdants).
                resolved.size == 2 && resolved.none { it.isBye } -> {
                    val winners = resolved.map { it.winnerIndex!! }
                    val losers = resolved.map { if (it.playerAIndex == it.winnerIndex) it.playerBIndex!! else it.playerAIndex!! }
                    rounds = rounds + listOf(
                        listOf(
                            TournamentMatch(next, "Finale", winners[0], winners[1]),
                            TournamentMatch(next, "Petite finale", losers[0], losers[1])
                        )
                    )
                }
                // Tour normal : les vainqueurs sont réappariés pour le tour suivant.
                else -> {
                    val winners = resolved.map { it.winnerIndex!! }
                    rounds = rounds + listOf(
                        winners.chunked(2).map { TournamentMatch(next, null, it[0], it.getOrNull(1)) }
                    )
                }
            }
        }
        return initial.copy(rounds = rounds, finished = finished)
    }
}
