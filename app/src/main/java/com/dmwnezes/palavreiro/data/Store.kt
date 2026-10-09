package com.dmwnezes.palavreiro.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Modos do jogo de adivinhar palavras.
 * [boards]: quantas palavras ao mesmo tempo; tentativas = 5 + boards (6, 7 ou 9).
 * [daily]: tem desafio do dia; [free]: permite jogar partidas livres (palavras sorteadas).
 */
enum class Mode(val key: String, val title: String, val boards: Int, val daily: Boolean, val free: Boolean) {
    DIARIO("diario", "Termo", 1, daily = true, free = false),
    INFINITO("infinito", "Infinito", 1, daily = false, free = true),
    DUETO("dueto", "Dueto", 2, daily = true, free = true),
    QUARTETO("quarteto", "Quarteto", 4, daily = true, free = true),
    /** Palavra escolhida por um amigo, recebida por link. */
    DESAFIO("desafio", "Desafio", 1, daily = false, free = false);

    val maxTries: Int get() = 5 + boards
}

data class Stats(
    val played: Int = 0,
    val won: Int = 0,
    val streak: Int = 0,
    val maxStreak: Int = 0,
    val lastWinDay: Long = Long.MIN_VALUE,
    val firstTry: Int = 0,
    val dist: List<Int> = List(9) { 0 },
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
                    dist = List(9) { i -> d?.optInt(i) ?: 0 },
                )
            }.getOrDefault(Stats())
        }
    }
}

/** Partida salva: as tentativas são reavaliadas ao carregar. */
data class SavedGame(
    val key: String,
    val answers: List<String>,
    val words: List<String>,
    val over: Boolean,
    val won: Boolean,
    val hints: List<Int> = emptyList(),
    val hard: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("key", key).put("answers", JSONArray(answers)).put("words", JSONArray(words)).put("over", over).put("won", won)
        .put("hints", JSONArray(hints)).put("hard", hard)
        .toString()

    companion object {
        fun fromJson(s: String?): SavedGame? {
            if (s.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(s)
                val arr = o.getJSONArray("words")
                val ans = o.optJSONArray("answers")
                val answers = if (ans != null) List(ans.length()) { ans.getString(it) } else listOf(o.getString("answer"))
                val h = o.optJSONArray("hints")
                SavedGame(
                    o.getString("key"), answers, List(arr.length()) { arr.getString(it) }, o.optBoolean("over"), o.optBoolean("won"),
                    hints = if (h != null) List(h.length()) { h.getInt(it) } else emptyList(),
                    hard = o.optBoolean("hard"),
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

    /** Números simples dos outros jogos (recordes, contadores). */
    fun int(key: String): Int = prefs.getInt("n_$key", 0)
    fun setInt(key: String, v: Int) = prefs.edit().putInt("n_$key", v).apply()
    fun add(key: String, delta: Int = 1) = setInt(key, int(key) + delta)
    fun max(key: String, v: Int) { if (v > int(key)) setInt(key, v) }
    /** Menor valor positivo (ex.: melhor tempo). */
    fun min(key: String, v: Int) { val cur = int(key); if (cur == 0 || v < cur) setInt(key, v) }

    fun text(key: String): String? = prefs.getString("t_$key", null)
    fun setText(key: String, v: String?) = prefs.edit().putString("t_$key", v).apply()

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()

    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(v) = prefs.edit().putBoolean("vibration", v).apply()

    /** Modo difícil: verdes ficam no lugar e amarelos precisam ser usados. */
    var hard: Boolean
        get() = prefs.getBoolean("hard", false)
        set(v) = prefs.edit().putBoolean("hard", v).apply()

    /** Lembrete diário (hora do dia, 0–23). */
    var reminder: Boolean
        get() = prefs.getBoolean("reminder", false)
        set(v) = prefs.edit().putBoolean("reminder", v).apply()
    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 9)
        set(v) = prefs.edit().putInt("reminder_hour", v).apply()

    /** Quantos jogos foram concluídos em cada dia (para o calendário). */
    fun activity(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString("activity", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun logActivity(day: String = java.time.LocalDate.now().toString()) {
        val o = runCatching { JSONObject(prefs.getString("activity", "{}") ?: "{}") }.getOrDefault(JSONObject())
        o.put(day, o.optInt(day) + 1)
        prefs.edit().putString("activity", o.toString()).apply()
    }

    /** Resultado do Termo do dia em cada data: "w" (acertou) ou "l" (errou). */
    fun termoResults(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString("termo_days", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun logTermo(day: String, won: Boolean) {
        val o = runCatching { JSONObject(prefs.getString("termo_days", "{}") ?: "{}") }.getOrDefault(JSONObject())
        o.put(day, if (won) "w" else "l")
        prefs.edit().putString("termo_days", o.toString()).apply()
    }

    var seenHelp: Boolean
        get() = prefs.getBoolean("seen_help", false)
        set(v) = prefs.edit().putBoolean("seen_help", v).apply()

    var skippedUpdate: String?
        get() = prefs.getString("skipped_update", null)
        set(v) = prefs.edit().putString("skipped_update", v).apply()
}
