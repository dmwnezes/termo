package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.data.Stats

/** Conquista com progresso (ex.: 3 de 10). */
data class Achievement(
    val emoji: String,
    val title: String,
    val description: String,
    val progress: Int,
    val goal: Int,
) {
    val unlocked: Boolean get() = progress >= goal
}

/** Números dos outros jogos (contadores do Store, pelo nome: "conn_won", "ws_best"...). */
data class Records(val values: Map<String, Int> = emptyMap()) {
    operator fun get(key: String): Int = values[key] ?: 0
    val connWon get() = this["conn_won"]
    val connPerfect get() = this["conn_perfect"]
    val wsPlayed get() = this["ws_played"]
    val wsBest get() = this["ws_best"]
    val defBest get() = this["def_best"]
    val defRight get() = this["def_right"]
    val synBest get() = this["syn_best"]

    companion object {
        /** Todos os contadores que entram nas conquistas, nos recordes e no código de sincronização. */
        val KEYS = listOf(
            "conn_won", "conn_perfect", "conn_played", "conn_inf_played", "conn_inf_won",
            "ws_played", "ws_best", "ws_inf_played", "ws_inf_best",
            "rev_appwins", "rev_userwins", "rev_played", "def_best", "def_right", "syn_best",
            "intr_played", "intr_best", "intr_right", "ort_played", "ort_best", "ort_right", "ant_best", "mestre_played", "mestre_best", "mestre_right", "mp_played", "mp_won", "sa_best", "sa_tricks",
            "arch_played", "arch_won", "alldone_days",
            "trivia_won", "trivia_best", "trivia_right",
            "mem_best_facil", "mem_best_facil_moves", "mem_best_medio", "mem_best_medio_moves", "mem_best_dificil", "mem_best_dificil_moves",
        )
        /** Contadores em que o menor valor positivo é o melhor (tempos). */
        val LOWER_IS_BETTER = setOf(
            "ws_best", "ws_inf_best",
            "mem_best_facil", "mem_best_facil_moves", "mem_best_medio", "mem_best_medio_moves", "mem_best_dificil", "mem_best_dificil_moves",
        )
    }
}

/** Conquistas calculadas a partir das estatísticas e recordes (mesma lista do site). */
object Achievements {
    fun all(daily: Stats, infinite: Stats, dueto: Stats = Stats(), quarteto: Stats = Stats(), r: Records = Records()): List<Achievement> {
        val wins = daily.won + infinite.won
        val firstTry = daily.firstTry + infinite.firstTry
        val twoTries = daily.dist[1] + infinite.dist[1]
        val fastWs = listOf(r["ws_best"], r["ws_inf_best"]).any { it in 1..119 }
        fun a(e: String, t: String, d: String, v: Int, goal: Int) = Achievement(e, t, d, minOf(v, goal), goal)
        return listOf(
            a("🌱", "Primeira palavra", "Acerte sua primeira palavra", wins, 1),
            a("🎯", "De primeira", "Acerte na primeira tentativa", firstTry, 1),
            a("⚡", "Rapidinho", "Acerte em duas tentativas", twoTries + firstTry, 1),
            a("🔥", "Três dias seguidos", "Sequência de 3 no Termo", daily.maxStreak, 3),
            a("📅", "Uma semana", "Sequência de 7 no Termo", daily.maxStreak, 7),
            a("🏆", "Um mês", "Sequência de 30 no Termo", daily.maxStreak, 30),
            a("♾️", "Maratona", "Acerte 25 palavras no Infinito", infinite.won, 25),
            a("📚", "Palavreiro", "Acerte 100 palavras no total", wins, 100),
            a("👯", "Dupla certa", "Vença um Dueto", dueto.won, 1),
            a("🍀", "Quatro de uma vez", "Vença um Quarteto", quarteto.won, 1),
            a("🧩", "Conectado", "Resolva um Conexões", r["conn_won"], 1),
            a("💎", "Sem errar", "Resolva um Conexões sem erros", r["conn_perfect"], 1),
            a("♻️", "Conexão sem fim", "Resolva 10 Conexões no Infinito", r["conn_inf_won"], 10),
            a("🔎", "Olho de águia", "Termine um Caça-Palavras em menos de 2 minutos", if (fastWs) 1 else 0, 1),
            a("🗺️", "Caçador incansável", "Termine 10 grades no Caça-Palavras Infinito", r["ws_inf_played"], 10),
            a("📖", "Dicionário ambulante", "Acerte 10 seguidas no Qual é a Palavra?", r["def_best"], 10),
            a("⛓️", "Corrente forte", "Faça uma cadeia de 20 no Sinônimo ou Antônimo", maxOf(r["syn_best"], r["sa_best"]), 20),
            a("🔄", "Do avesso", "Acerte 20 pegadinhas no Sinônimo ou Antônimo", maxOf(r["ant_best"], r["sa_tricks"]), 20),
            a("🕵️", "Detetive", "Faça 10 pontos no Intruso", r["intr_best"], 10),
            a("✍️", "Escrita impecável", "Faça 20 pontos no Certo ou Errado", r["ort_best"], 20),
            a("👑", "Obediente", "Faça 15 pontos no Mestre Mandou", r["mestre_best"], 15),
            a("🧠", "Sabichão", "Ganhe 1 partida de Trivia (as 6 medalhas)", r["trivia_won"], 1),
            a("📚", "Enciclopédia", "Acerte 100 perguntas na Trivia", r["trivia_right"], 100),
            a("🃏", "Memória de elefante", "Termine a Memória no Difícil", if (r["mem_best_dificil"] > 0) 1 else 0, 1),
            a("⚔️", "Duelista", "Vença 5 partidas com amigos", r["mp_won"], 5),
            a("🗂️", "Viajante do tempo", "Termine 5 desafios do Arquivo", r["arch_played"], 5),
            a("🌟", "Dia completo", "Faça os 5 desafios do dia", r["alldone_days"], 1),
        )
    }
}

/** Aviso de conquista nova: compara as desbloqueadas com as já vistas (salvas em "ach_seen"). */
object AchievementWatch {
    /**
     * Devolve as conquistas recém-desbloqueadas e o novo conjunto de vistas.
     * [seen] null = primeira vez neste aparelho (ou logo após atualizar): nada é avisado e
     * todas as já desbloqueadas passam a contar como vistas.
     */
    fun newlyUnlocked(all: List<Achievement>, seen: Set<String>?): Pair<List<Achievement>, Set<String>> {
        val unlocked = all.filter { it.unlocked }
        if (seen == null) return emptyList<Achievement>() to unlocked.map { it.title }.toSet()
        val fresh = unlocked.filter { it.title !in seen }
        return fresh to (seen + fresh.map { it.title })
    }
}
