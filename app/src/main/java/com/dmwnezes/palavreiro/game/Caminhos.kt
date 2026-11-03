package com.dmwnezes.palavreiro.game

import kotlin.math.abs
import kotlin.math.sign

data class CaminhosQ(val n: Int, val pairs: List<List<Int>>)

/** Caminhos: mesmo motor e mesmo arquivo do site (js/caminhos.js, shared/caminhos.txt). */
object CaminhosData {
    val LEVELS = listOf(Triple("facil", "Fácil", 5), Triple("medio", "Médio", 7), Triple("dificil", "Difícil", 9))

    fun parse(text: String): List<CaminhosQ> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
        val (n, p) = l.split('|')
        CaminhosQ(n.toInt(), p.split(',').map { x -> x.split(' ').map(String::toInt) })
    }

    fun ofLevel(list: List<CaminhosQ>, level: Int) = list.filter { it.n == LEVELS[level].third }

    fun dailyIndex(day: Long, n: Int): Int = Math.floorMod(day, n.toLong()).toInt()
}

class CaminhosGame(val q: CaminhosQ) {
    val paths: MutableList<List<Int>> = MutableList(q.pairs.size) { emptyList() }
    var active = -1
        private set

    private fun adjacent(a: Int, b: Int) = abs(a - b) == q.n || (abs(a - b) == 1 && a / q.n == b / q.n)
    fun endpointOf(c: Int) = q.pairs.indexOfFirst { c in it }

    fun connected(k: Int): Boolean {
        val p = paths[k]
        val e = q.pairs[k]
        return p.size > 1 && p.first() in e && p.last() in e && p.first() != p.last()
    }

    val joined: Int get() = q.pairs.indices.count { connected(it) }
    val filled: Int get() = paths.sumOf { it.size }
    val won: Boolean get() = joined == q.pairs.size && filled == q.n * q.n

    /** Começa num ponto (recomeça a cor) ou numa casa de um caminho (corta a partir dali). */
    fun start(c: Int) {
        val k = endpointOf(c)
        if (k >= 0) { paths[k] = listOf(c); active = k; return }
        val o = paths.indexOfFirst { c in it }
        if (o >= 0) { paths[o] = paths[o].take(paths[o].indexOf(c) + 1); active = o } else active = -1
    }

    /** Avança uma casa. */
    fun step(c: Int): Boolean {
        val k = active
        if (k < 0) return false
        val p = paths[k]
        val last = p.last()
        if (c == last || !adjacent(last, c)) return false
        val i = p.indexOf(c)
        if (i >= 0) { paths[k] = p.take(i + 1); return true }
        if (connected(k)) return false // ligado: só pode voltar
        val end = endpointOf(c)
        if (end >= 0 && end != k) return false
        for (x in paths.indices) if (x != k) { val at = paths[x].indexOf(c); if (at >= 0) paths[x] = paths[x].take(at) }
        paths[k] = p + c
        return true
    }

    /** Arrasto até uma casa: anda em linha reta casa por casa, mesmo se o dedo pular. */
    fun dragTo(c: Int) {
        val n = q.n
        repeat(n * 2) {
            if (active < 0) return
            val last = paths[active].last()
            if (last == c) return
            val y = last / n
            val x = last % n
            val ty = c / n
            val tx = c % n
            val next = if (abs(tx - x) >= abs(ty - y)) y * n + x + (tx - x).sign else (y + (ty - y).sign) * n + x
            if (!step(next)) return
        }
    }

    fun end() { active = -1 }
    fun clear() { paths.indices.forEach { paths[it] = emptyList() }; active = -1 }

    fun save(): String = paths.joinToString("/") { it.joinToString(",") }
    fun restore(text: String) {
        val p = text.split('/')
        if (p.size != paths.size) return
        p.forEachIndexed { i, s -> paths[i] = s.split(',').mapNotNull { it.toIntOrNull() } }
    }
}
