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
    val revApp get() = this["rev_appwins"]
    val revUser get() = this["rev_userwins"]
    val defBest get() = this["def_best"]
    val defRight get() = this["def_right"]
    val synBest get() = this["syn_best"]

    companion object {
        /** Todos os contadores que entram nas conquistas, nos recordes e no código de sincronização. */
        val KEYS = listOf(
            "conn_won", "conn_perfect", "conn_played", "conn_inf_played", "conn_inf_won",
            "ws_played", "ws_best", "ws_inf_played", "ws_inf_best",
            "rev_appwins", "rev_userwins", "rev_played", "def_best", "def_right", "syn_best",
            "intr_played", "intr_best", "intr_right", "ort_played", "ort_best", "ort_right", "ant_best", "mestre_played", "mestre_best", "mestre_right",
            "arch_played", "arch_won", "alldone_days",
        )
        /** Contadores em que o menor valor positivo é o melhor (tempos). */
        val LOWER_IS_BETTER = setOf("ws_best", "ws_inf_best")
    }
}

/** Conquistas calculadas a partir das estatísticas e recordes (mesma lista do site). */
object Achievements {
    fun all(daily: Stats, infinite: Stats, dueto: Stats = Stats(), quarteto: Stats = Stats(), r: Records = Records()): List<Achievement> {
        val wins = daily.won + infinite.won
        val firstTry = daily.firstTry + infinite.firstTry
        val twoTries = daily.dist[1] + infinite.dist[1]
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
            a("🤖", "Mais esperto que o app", "Vença o Reverso", r["rev_userwins"], 1),
            a("📖", "Dicionário ambulante", "Acerte 10 seguidas no Qual é a Palavra?", r["def_best"], 10),
            a("⛓️", "Corrente forte", "Faça uma cadeia de 20 sinônimos", r["syn_best"], 20),
            a("🔄", "Do avesso", "Faça uma cadeia de 20 antônimos", r["ant_best"], 20),
            a("🕵️", "Detetive", "Faça 10 pontos no Intruso", r["intr_best"], 10),
            a("✍️", "Escrita impecável", "Faça 20 pontos no Certo ou Errado", r["ort_best"], 20),
            a("👑", "Obediente", "Faça 15 pontos no Mestre Mandou", r["mestre_best"], 15),
            a("🗂️", "Viajante do tempo", "Termine 5 desafios do Arquivo", r["arch_played"], 5),
            a("🌟", "Dia completo", "Faça os 4 desafios do dia", r["alldone_days"], 1),
        )
    }
}
