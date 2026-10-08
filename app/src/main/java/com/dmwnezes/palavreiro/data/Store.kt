package com.dmwnezes.palavreiro.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Modos de jogo. Dueto e Quarteto entram aqui quando ficarem prontos. */
enum class Mode(val key: String, val title: String) {
    DIARIO("diario", "Diário"),
    INFINITO("infinito", "Infinito"),
}

data class Stats(
    val played: Int = 0,
    val won: Int = 0,
    val streak: Int = 0,
    val maxStreak: Int = 0,
    val lastWinDay: Long = Long.MIN_VALUE,
    val firstTry: Int = 0,
    val dist: List<Int> = List(6) { 0 },
) {
    val winPct: Int get() = if (played == 0) 0 else Math.round(won * 100f / played)

    fun toJson(): String = JSONObject()
        .put("played", played).put("won", won).put("streak", streak).put("maxStreak", maxStreak)
        .put("lastWinDay", lastWinDay).put("firstTry", firstTry).put("dist", JSONArray(dist))
        .toString()

    companion object {
        fun fromJson(s: String?): Stats {
            if (s.isNullOrBlank()) return Stats()
            return runCatching {
                val o = JSONObject(s)
                val d = o.optJSONArray("dist")
                Stats(
                    played = o.optInt("played"),
                    won = o.optInt("won"),
                    streak = o.optInt("streak"),
                    maxStreak = o.optInt("maxStreak"),
                    lastWinDay = o.optLong("lastWinDay", Long.MIN_VALUE),
                    firstTry = o.optInt("firstTry"),
                    dist = List(6) { i -> d?.optInt(i) ?: 0 },
                )
            }.getOrDefault(Stats())
        }
    }
}

/** Partida salva: as tentativas são reavaliadas ao carregar. */
data class SavedGame(val key: String, val answer: String, val words: List<String>, val over: Boolean, val won: Boolean) {
    fun toJson(): String = JSONObject()
        .put("key", key).put("answer", answer).put("words", JSONArray(words)).put("over", over).put("won", won)
        .toString()

    companion object {
        fun fromJson(s: String?): SavedGame? {
            if (s.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(s)
                val arr = o.getJSONArray("words")
                SavedGame(
                    o.getString("key"), o.getString("answer"),
                    List(arr.length()) { arr.getString(it) }, o.optBoolean("over"), o.optBoolean("won"),
                )
            }.getOrNull()
        }
    }
}

class Store(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("palavreiro", Context.MODE_PRIVATE)

    fun stats(mode: Mode): Stats = Stats.fromJson(prefs.getString("stats_${mode.key}", null))
    fun saveStats(mode: Mode, s: Stats) = prefs.edit().putString("stats_${mode.key}", s.toJson()).apply()

    fun game(mode: Mode): SavedGame? = SavedGame.fromJson(prefs.getString("game_${mode.key}", null))
    fun saveGame(mode: Mode, g: SavedGame) = prefs.edit().putString("game_${mode.key}", g.toJson()).apply()

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()

    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(v) = prefs.edit().putBoolean("vibration", v).apply()

    var seenHelp: Boolean
        get() = prefs.getBoolean("seen_help", false)
        set(v) = prefs.edit().putBoolean("seen_help", v).apply()

    var skippedUpdate: String?
        get() = prefs.getString("skipped_update", null)
        set(v) = prefs.edit().putString("skipped_update", v).apply()
}
