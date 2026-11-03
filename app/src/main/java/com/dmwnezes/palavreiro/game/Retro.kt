package com.dmwnezes.palavreiro.game

import java.time.LocalDate

/** Números da retrospectiva do ano. Mesmo cálculo do site (P.retro em js/extras.js). */
data class Retro(
    val year: Int,
    val days: Int,
    val games: Int,
    val bestRun: Int,
    val month: String?,
    val termoPlayed: Int,
    val termoWon: Int,
    val topGame: String?,
    val topGuess: String?,
) {
    val termoPct: Int get() = if (termoPlayed == 0) 0 else Math.round(termoWon * 100f / termoPlayed)

    companion object {
        private val NAMES = mapOf(
            "termo" to "Termo", "dueto" to "Dueto", "quarteto" to "Quarteto", "conn" to "Conexões", "conexoes" to "Conexões",
            "caca" to "Caça-Palavras", "ws" to "Caça-Palavras", "cruzada" to "Palavra Cruzada", "silhueta" to "Silhueta", "maismenos" to "Mais ou Menos", "minado" to "Campo Minado", "caminhos" to "Caminhos", "fatomito" to "Fato ou Mito", "pesquisa" to "Pesquisa", "top10" to "Top 10", "placas" to "Placas",
        )
        private val MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

        /** O mais frequente; empate vai para o primeiro em ordem alfabética. */
        private fun top(counts: Map<String, Int>) = counts.entries.sortedWith(compareBy({ -it.value }, { it.key })).firstOrNull()?.key

        /** [activity] dia → jogos; [termoDays] dia → w/l; [resultKeys] "jogo|dia"; [guesses] 1º chute → dia. */
        fun of(year: Int, activity: Map<String, Int>, termoDays: Map<String, String>, resultKeys: Collection<String>, guesses: List<Pair<String, String>>): Retro {
            val pre = "$year-"
            val act = activity.filter { it.key.startsWith(pre) && it.value > 0 }.toSortedMap()
            val months = IntArray(12)
            var run = 0
            var best = 0
            var prev: LocalDate? = null
            act.forEach { (d, v) ->
                val day = LocalDate.parse(d)
                months[day.monthValue - 1] += v
                run = if (prev != null && day == prev!!.plusDays(1)) run + 1 else 1
                best = maxOf(best, run)
                prev = day
            }
            val td = termoDays.filter { it.key.startsWith(pre) }
            val games = resultKeys.mapNotNull { k ->
                val p = k.split('|')
                if (p.size == 2 && p[1].startsWith(pre)) NAMES[p[0]] else null
            }.groupingBy { it }.eachCount()
            val g = guesses.filter { it.second.startsWith(pre) && it.first.isNotBlank() }.groupingBy { it.first }.eachCount()
            return Retro(
                year, act.size, act.values.sum(), best, if (act.isEmpty()) null else MONTHS[months.indices.maxByOrNull { months[it] }!!],
                td.size, td.values.count { it == "w" }, top(games), top(g),
            )
        }
    }
}
