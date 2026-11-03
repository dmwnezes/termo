package com.dmwnezes.palavreiro.game

/** A opção certa é a de índice 0 no arquivo (shared/placas.txt). */
data class PlacaQ(val shape: String, val symbol: String, val text: String, val options: List<String>, val why: String)

/** Placas: mesmo sorteio do site (js/placas.js). */
object Placas {
    const val ROUNDS = 10
    const val LIVES = 3

    fun parse(text: String): List<PlacaQ> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
        val f = l.split('|')
        if (f.size < 8) null else PlacaQ(f[0], f[1], f[2], f.subList(3, 7), f[7])
    }

    fun daily(size: Int, day: Long): List<Int> {
        val r = Minstd.ofDay(day)
        val out = ArrayList<Int>()
        while (out.size < minOf(ROUNDS, size)) {
            val i = (r.next() % size).toInt()
            if (i !in out) out += i
        }
        return out
    }

    /** Ordem fixa das 4 opções da pergunta [i]. */
    fun order(i: Int): List<Int> {
        val r = Minstd.ofDay(i.toLong())
        val a = mutableListOf(0, 1, 2, 3)
        for (k in 3 downTo 1) { val j = (r.next() % (k + 1)).toInt(); val t = a[k]; a[k] = a[j]; a[j] = t }
        return a
    }
}
