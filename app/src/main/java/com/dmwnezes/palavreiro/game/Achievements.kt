package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.data.Stats

data class Achievement(val emoji: String, val title: String, val description: String, val unlocked: Boolean)

/** Conquistas calculadas a partir das estatísticas dos modos Diário e Infinito. */
object Achievements {
    fun all(daily: Stats, infinite: Stats): List<Achievement> {
        val wins = daily.won + infinite.won
        val firstTry = daily.firstTry + infinite.firstTry
        val twoTries = daily.dist[1] + infinite.dist[1]
        return listOf(
            Achievement("🌱", "Primeira palavra", "Acerte sua primeira palavra", wins >= 1),
            Achievement("🎯", "De primeira", "Acerte na primeira tentativa", firstTry >= 1),
            Achievement("⚡", "Rapidinho", "Acerte em duas tentativas", twoTries + firstTry >= 1),
            Achievement("🔥", "Três dias seguidos", "Sequência de 3 no Diário", daily.maxStreak >= 3),
            Achievement("📅", "Uma semana", "Sequência de 7 no Diário", daily.maxStreak >= 7),
            Achievement("🏆", "Um mês", "Sequência de 30 no Diário", daily.maxStreak >= 30),
            Achievement("♾️", "Maratona", "Acerte 25 palavras no Infinito", infinite.won >= 25),
            Achievement("📚", "Palavreiro", "Acerte 100 palavras no total", wins >= 100),
        )
    }
}
