package com.aventure.messcores

import android.content.Context
import androidx.core.content.edit
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import java.util.UUID

/**
 * Gère les jeux disponibles : quelques jeux prédéfinis, plus les jeux
 * personnalisés créés par l'utilisateur, sauvegardés dans les SharedPreferences
 * (donc conservés d'une ouverture de l'app à l'autre, sans dépendance externe).
 */
class GameRepository(context: Context) {

    private val prefs = context.getSharedPreferences("mes_scores_games", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_CUSTOM_GAMES = "custom_games"
        /** Copie de sécurité du texte brut quand au moins un jeu n'a pas pu être relu. */
        const val KEY_BACKUP = "custom_games_unreadable_backup"
        const val TAG = "GameRepository"
    }

    fun builtInGames(): List<GameRules> = listOf(
        GameRules(
            id = "builtin_generic",
            name = "Jeu classique",
            lowestWins = false,
            allowNegativeScores = false,
            scoreMode = ScoreMode.TABLE
        ),
        GameRules(
            id = "builtin_skyjo",
            name = "Skyjo",
            minPlayers = 2,
            maxPlayers = 8,
            lowestWins = true,
            allowNegativeScores = true,
            scoreMode = ScoreMode.TABLE,
            endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 100, stopImmediately = false)
        ),
        GameRules(
            id = "builtin_tarot",
            name = "Tarot",
            minPlayers = 3,
            maxPlayers = 5,
            lowestWins = false,
            allowNegativeScores = true,
            scoreMode = ScoreMode.VARIABLE_TEAMS,
            multipliers = listOf(
                GameRules.NORMAL_MULTIPLIER.copy(label = "Petite"),
                ScoreMultiplier(id = "tarot_garde", label = "Garde", factor = 2),
                ScoreMultiplier(id = "tarot_garde_sans", label = "Garde sans", factor = 4),
                ScoreMultiplier(id = "tarot_garde_contre", label = "Garde contre", factor = 6)
            )
        ),
        GameRules(
            id = "builtin_belote",
            name = "Belote (à 501 points)",
            minPlayers = 2,
            maxPlayers = 4,
            lowestWins = false,
            allowNegativeScores = false,
            scoreMode = ScoreMode.TABLE,
            endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 501, stopImmediately = false)
        ),
        GameRules(
            id = "builtin_rami",
            name = "Rami",
            minPlayers = 2,
            maxPlayers = 6,
            lowestWins = true,
            allowNegativeScores = false,
            scoreMode = ScoreMode.TABLE,
            endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 500, stopImmediately = false)
        ),
        GameRules(
            id = "builtin_uno",
            name = "Uno",
            minPlayers = 2,
            maxPlayers = 10,
            lowestWins = false,
            allowNegativeScores = false,
            scoreMode = ScoreMode.TABLE
        )
    )

    fun customGames(): List<GameRules> {
        val json = prefs.getString(KEY_CUSTOM_GAMES, null) ?: return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            Log.w(TAG, "Liste des jeux illisible, copie de sécurité conservée", e)
            backUp(json)
            return emptyList()
        }
        var skipped = 0
        // Un jeu abîmé est ignoré seul : les autres jeux personnalisés restent disponibles.
        val games = (0 until array.length()).mapNotNull { i ->
            try {
                GameRules.fromJson(array.getJSONObject(i))
            } catch (e: Exception) {
                skipped++
                Log.w(TAG, "Jeu personnalisé $i illisible, ignoré", e)
                null
            }
        }
        if (skipped > 0) backUp(json)
        return games
    }

    /** Conserve le texte brut (une seule copie, la plus ancienne) pour une récupération manuelle. */
    private fun backUp(rawJson: String) {
        if (!prefs.contains(KEY_BACKUP)) prefs.edit { putString(KEY_BACKUP, rawJson) }
    }

    fun allGames(): List<GameRules> = builtInGames() + customGames()

    fun saveCustomGame(game: GameRules) {
        val current = customGames().toMutableList()
        current.add(game)
        persist(current)
    }

    /** Remplace le jeu personnalisé de même id par [game] (modification). Sans effet si l'id est inconnu. */
    fun updateCustomGame(game: GameRules) {
        val current = customGames()
        if (current.none { it.id == game.id }) return
        persist(current.map { if (it.id == game.id) game else it })
    }

    /**
     * Supprime un jeu personnalisé. Les parties déjà enregistrées au journal ne sont pas touchées :
     * chacune embarque sa propre copie des règles.
     */
    fun deleteCustomGame(id: String) {
        persist(customGames().filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun persist(games: List<GameRules>) {
        val array = JSONArray()
        games.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_CUSTOM_GAMES, array.toString()) }
    }
}
