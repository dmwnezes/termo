package com.dmwnezes.palavreiro.game

data class FatoQ(val fact: Boolean, val text: String, val why: String)

/** Fato ou Mito: mesmo sorteio do site (js/fatomito.js) e o mesmo arquivo (shared/fatomito.txt). */
object FatoMito {
    const val ROUNDS = 10
    const val LIVES = 3

    fun parse(text: String): List<FatoQ> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
        val f = l.split('|')
        if (f.size < 3) null else FatoQ(f[0] == "F", f[1], f[2])
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
}
