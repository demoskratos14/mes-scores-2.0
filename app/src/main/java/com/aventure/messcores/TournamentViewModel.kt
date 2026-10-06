package com.aventure.messcores

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un match du championnat. [playerAIndex]/[playerBIndex] référencent l'index du participant
 * dans [TournamentViewModel.participants] ; null tant que non déterminé, ou en cas de "bye"
 * (qualification directe faute d'adversaire, pour les effectifs qui ne sont pas une
 * puissance de 2). [label] identifie les matchs spéciaux du dernier tour ("Finale",
 * "Petite finale") ; null pour un tour normal.
 */
class TournamentMatch(
    val round: Int,
    val label: String? = null
) {
    var playerAIndex by mutableStateOf<Int?>(null)
    var playerBIndex by mutableStateOf<Int?>(null)
    var winnerIndex by mutableStateOf<Int?>(null)

    val isBye: Boolean get() = playerAIndex == null || playerBIndex == null
}

/**
 * Championnat à élimination directe : le vainqueur de chaque match passe au tour suivant.
 * Dès que le tour ne compte plus que 2 matchs (les demi-finales) et que les deux sont joués
 * sans "bye", le tour suivant regroupe automatiquement la Finale (les deux vainqueurs) et la
 * Petite finale (les deux perdants). Les effectifs qui ne sont pas une puissance de 2 sont
 * comblés par des qualifications directes ("byes") uniquement au premier tour.
 */
class TournamentViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("mes_scores_tournament", Context.MODE_PRIVATE)

    var participants by mutableStateOf<List<String>>(emptyList())
        private set

    var finished by mutableStateOf(false)
        private set

    private val _rounds = mutableStateListOf<SnapshotStateList<TournamentMatch>>()
    val rounds: List<SnapshotStateList<TournamentMatch>> get() = _rounds

    // Après les propriétés ci-dessus : restore() les remplit, leurs initialiseurs ne doivent pas passer après.
    init {
        restore()
    }

    /** Vrai si un championnat (en cours ou terminé) est disponible, y compris restauré après un redémarrage. */
    val hasTournament: Boolean get() = _rounds.isNotEmpty()

    /** Démarre un nouveau championnat, en tirant l'ordre du tableau au sort. */
    fun startTournament(names: List<String>) {
        participants = names
        finished = false
        _rounds.clear()
        if (names.size < 2) {
            save()
            return
        }

        var bracketSize = 1
        while (bracketSize < names.size) bracketSize *= 2

        // Les qualifications directes (byes) sont réparties à raison d'une par match au maximum :
        // deux places vides face à face donneraient un match impossible à jouer.
        val byeCount = bracketSize - names.size
        val order = names.indices.shuffled()
        val pairings = mutableListOf<Pair<Int, Int?>>()
        for (k in 0 until byeCount) pairings.add(order[k] to null)
        var k = byeCount
        while (k + 1 < order.size) {
            pairings.add(order[k] to order[k + 1])
            k += 2
        }
        pairings.shuffle()

        val firstRound = mutableStateListOf<TournamentMatch>()
        pairings.forEach { (playerA, playerB) ->
            firstRound.add(
                TournamentMatch(round = 1).apply {
                    playerAIndex = playerA
                    playerBIndex = playerB
                }
            )
        }
        _rounds.add(firstRound)
        resolveByes()
        save()
    }

    /** Désigne [winner] (index dans [participants]) comme vainqueur du match indiqué. */
    fun setWinner(roundIndex: Int, matchIndex: Int, winner: Int) {
        val match = _rounds.getOrNull(roundIndex)?.getOrNull(matchIndex) ?: return
        if (match.playerAIndex != winner && match.playerBIndex != winner) return
        match.winnerIndex = winner
        advanceIfRoundComplete()
        save()
    }

    /**
     * Annule le résultat d'un match (erreur de saisie) pour pouvoir désigner à nouveau le vainqueur.
     * Les tours suivants, qui découlaient de ce résultat, sont supprimés : ils seront régénérés
     * quand le tour sera de nouveau complet. Sans effet sur un match sans résultat ou un "bye".
     */
    fun resetMatch(roundIndex: Int, matchIndex: Int) {
        val match = _rounds.getOrNull(roundIndex)?.getOrNull(matchIndex) ?: return
        if (match.isBye || match.winnerIndex == null) return
        while (_rounds.size > roundIndex + 1) _rounds.removeAt(_rounds.lastIndex)
        match.winnerIndex = null
        finished = false
        save()
    }

    /** Vrai si des matchs des tours suivants ont déjà été joués (donc perdus si on corrige ce tour). */
    fun hasPlayedAfter(roundIndex: Int): Boolean =
        _rounds.drop(roundIndex + 1).any { round -> round.any { !it.isBye && it.winnerIndex != null } }

    // --- Sauvegarde : le championnat survit à la fermeture de l'appli. ---

    private fun save() {
        if (_rounds.isEmpty()) {
            prefs.edit { remove(KEY_TOURNAMENT) }
            return
        }
        val obj = JSONObject()
        obj.put("version", FORMAT_VERSION)
        obj.put("participants", JSONArray(participants))
        obj.put("finished", finished)
        val roundsArray = JSONArray()
        _rounds.forEach { round ->
            val roundArray = JSONArray()
            round.forEach { match ->
                val m = JSONObject()
                m.put("round", match.round)
                match.label?.let { m.put("label", it) }
                match.playerAIndex?.let { m.put("a", it) }
                match.playerBIndex?.let { m.put("b", it) }
                match.winnerIndex?.let { m.put("winner", it) }
                roundArray.put(m)
            }
            roundsArray.put(roundArray)
        }
        obj.put("rounds", roundsArray)
        prefs.edit { putString(KEY_TOURNAMENT, obj.toString()) }
    }

    private fun restore() {
        val json = prefs.getString(KEY_TOURNAMENT, null) ?: return
        try {
            val obj = JSONObject(json)
            val names = obj.getJSONArray("participants").let { a -> (0 until a.length()).map { a.getString(it) } }
            val roundsArray = obj.getJSONArray("rounds")
            val restored = (0 until roundsArray.length()).map { r ->
                val roundArray = roundsArray.getJSONArray(r)
                val list = mutableStateListOf<TournamentMatch>()
                for (i in 0 until roundArray.length()) {
                    val m = roundArray.getJSONObject(i)
                    list.add(
                        TournamentMatch(
                            round = m.getInt("round"),
                            label = if (m.has("label")) m.getString("label") else null
                        ).apply {
                            playerAIndex = if (m.has("a")) m.getInt("a") else null
                            playerBIndex = if (m.has("b")) m.getInt("b") else null
                            winnerIndex = if (m.has("winner")) m.getInt("winner") else null
                        }
                    )
                }
                list
            }
            participants = names
            finished = obj.optBoolean("finished", false)
            _rounds.clear()
            _rounds.addAll(restored)
        } catch (e: Exception) {
            // Sauvegarde illisible : on repart sans championnat plutôt que de planter au démarrage.
            Log.w(TAG, "Championnat sauvegardé illisible, ignoré", e)
            participants = emptyList()
            finished = false
            _rounds.clear()
        }
    }

    private companion object {
        const val KEY_TOURNAMENT = "tournament"
        const val FORMAT_VERSION = 1
        const val TAG = "TournamentViewModel"
    }

    /** Vainqueur du championnat, une fois [finished] vrai. */
    fun championIndex(): Int? {
        if (!finished) return null
        val last = _rounds.lastOrNull() ?: return null
        val finalMatch = last.find { it.label == "Finale" } ?: last.firstOrNull()
        return finalMatch?.winnerIndex
    }

    /** Vainqueur de la petite finale (3e place), s'il y en a une. */
    fun thirdPlaceIndex(): Int? {
        val last = _rounds.lastOrNull() ?: return null
        return last.find { it.label == "Petite finale" }?.winnerIndex
    }

    /**
     * Classement final, de la 1re place à la 4e au maximum : vainqueur et perdant de la finale,
     * puis vainqueur et perdant de la petite finale. À 3 participants (pas de petite finale),
     * la 3e place revient au perdant du seul match réellement joué au premier tour.
     * Liste vide tant que le championnat n'est pas terminé.
     */
    fun podium(): List<Int> {
        if (!finished) return emptyList()
        val last = _rounds.lastOrNull() ?: return emptyList()
        val finalMatch = last.find { it.label == "Finale" } ?: last.singleOrNull() ?: return emptyList()

        val result = mutableListOf<Int>()
        finalMatch.winnerIndex?.let { result.add(it) }
        loserOf(finalMatch)?.let { result.add(it) }

        val smallFinal = last.find { it.label == "Petite finale" }
        if (smallFinal != null) {
            smallFinal.winnerIndex?.let { result.add(it) }
            loserOf(smallFinal)?.let { result.add(it) }
        } else if (_rounds.size >= 2) {
            val eliminated = _rounds[_rounds.size - 2].filter { !it.isBye }.mapNotNull { loserOf(it) }
            if (eliminated.size == 1) result.add(eliminated.first())
        }
        return result
    }

    private fun loserOf(match: TournamentMatch): Int? {
        val winner = match.winnerIndex ?: return null
        return if (match.playerAIndex == winner) match.playerBIndex else match.playerAIndex
    }

    // Auto-qualifie les matchs sans adversaire (byes), en cascade si besoin.
    private fun resolveByes() {
        val current = _rounds.lastOrNull() ?: return
        var changed = false
        current.forEach { match ->
            if (match.winnerIndex == null && match.isBye) {
                match.winnerIndex = match.playerAIndex ?: match.playerBIndex
                changed = true
            }
        }
        if (changed) advanceIfRoundComplete()
    }

    private fun advanceIfRoundComplete() {
        val currentRound = _rounds.lastOrNull() ?: return
        if (currentRound.any { it.winnerIndex == null }) return

        when {
            currentRound.any { it.label != null } -> {
                // Le tour Finale + Petite finale vient de se terminer.
                finished = true
            }
            currentRound.size == 1 -> {
                // Championnat à 2 participants : ce match unique EST la finale.
                finished = true
            }
            currentRound.size == 2 && currentRound.none { it.isBye } -> {
                // Demi-finales terminées : on génère Finale + Petite finale.
                val winners = currentRound.map { it.winnerIndex!! }
                val losers = currentRound.map { match ->
                    if (match.playerAIndex == match.winnerIndex) match.playerBIndex!! else match.playerAIndex!!
                }
                val nextRound = mutableStateListOf<TournamentMatch>()
                nextRound.add(
                    TournamentMatch(round = currentRound.first().round + 1, label = "Finale").apply {
                        playerAIndex = winners[0]
                        playerBIndex = winners[1]
                    }
                )
                nextRound.add(
                    TournamentMatch(round = currentRound.first().round + 1, label = "Petite finale").apply {
                        playerAIndex = losers[0]
                        playerBIndex = losers[1]
                    }
                )
                _rounds.add(nextRound)
            }
            else -> {
                // Tour normal : les vainqueurs sont réappariés pour le tour suivant.
                val winners = currentRound.map { it.winnerIndex!! }
                val nextRound = mutableStateListOf<TournamentMatch>()
                var i = 0
                while (i < winners.size) {
                    nextRound.add(
                        TournamentMatch(round = currentRound.first().round + 1).apply {
                            playerAIndex = winners[i]
                            playerBIndex = winners.getOrNull(i + 1)
                        }
                    )
                    i += 2
                }
                _rounds.add(nextRound)
                resolveByes()
            }
        }
    }
}
