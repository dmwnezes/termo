package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.data.Stats

data class Achievement(val emoji: String, val title: String, val description: String, val unlocked: Boolean)

/** Números dos outros jogos usados nas conquistas. */
data class Records(
    val connWon: Int = 0,
    val connPerfect: Int = 0,
    val wsPlayed: Int = 0,
    val wsBest: Int = 0,
    val revApp: Int = 0,
    val revUser: Int = 0,
    val defBest: Int = 0,
    val defRight: Int = 0,
    val synBest: Int = 0,
)

/** Conquistas calculadas a partir das estatísticas e recordes. */
object Achievements {
    fun all(daily: Stats, infinite: Stats, dueto: Stats = Stats(), quarteto: Stats = Stats(), r: Records = Records()): List<Achievement> {
        val wins = daily.won + infinite.won
        val firstTry = daily.firstTry + infinite.firstTry
        val twoTries = daily.dist[1] + infinite.dist[1]
        return listOf(
            Achievement("🌱", "Primeira palavra", "Acerte sua primeira palavra", wins >= 1),
            Achievement("🎯", "De primeira", "Acerte na primeira tentativa", firstTry >= 1),
            Achievement("⚡", "Rapidinho", "Acerte em duas tentativas", twoTries + firstTry >= 1),
            Achievement("🔥", "Três dias seguidos", "Sequência de 3 no Termo", daily.maxStreak >= 3),
            Achievement("📅", "Uma semana", "Sequência de 7 no Termo", daily.maxStreak >= 7),
            Achievement("🏆", "Um mês", "Sequência de 30 no Termo", daily.maxStreak >= 30),
            Achievement("♾️", "Maratona", "Acerte 25 palavras no Infinito", infinite.won >= 25),
            Achievement("📚", "Palavreiro", "Acerte 100 palavras no total", wins >= 100),
            Achievement("👯", "Dupla certa", "Vença um Dueto", dueto.won >= 1),
            Achievement("🍀", "Quatro de uma vez", "Vença um Quarteto", quarteto.won >= 1),
            Achievement("🧩", "Conectado", "Resolva um Conexões", r.connWon >= 1),
            Achievement("💎", "Sem errar", "Resolva um Conexões sem erros", r.connPerfect >= 1),
            Achievement("🔎", "Olho de águia", "Termine um Caça-Palavras em menos de 2 minutos", r.wsBest in 1..119),
            Achievement("🤖", "Mais esperto que o app", "Vença o Reverso", r.revUser >= 1),
            Achievement("📖", "Dicionário ambulante", "Acerte 10 seguidas no Qual é a Palavra?", r.defBest >= 10),
            Achievement("⛓️", "Corrente forte", "Faça uma cadeia de 20 sinônimos", r.synBest >= 20),
        )
    }
}
