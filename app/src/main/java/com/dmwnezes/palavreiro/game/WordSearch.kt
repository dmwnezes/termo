package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.mutableStateListOf
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

data class SearchTheme(val name: String, val words: List<String>)

/** Palavra colocada na grade: início, direção e tamanho. */
data class Placed(val word: String, val display: String, val row: Int, val col: Int, val dr: Int, val dc: Int) {
    val cells: List<Pair<Int, Int>> get() = word.indices.map { (row + dr * it) to (col + dc * it) }
}

object WordSearchData {
    fun parse(text: String): List<SearchTheme> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val i = line.indexOf(':')
            SearchTheme(line.substring(0, i).trim(), line.substring(i + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() })
        }

    fun dailyIndex(date: LocalDate, count: Int): Int {
        val i = Words.dayIndex(date)
        return (((i * 11L + 3L) % count + count) % count).toInt()
    }
}

/**
 * Grade do caça-palavras gerada a partir de um tema e de uma semente (a data),
 * então todos recebem a mesma grade no mesmo dia.
 */
class WordSearch(val theme: SearchTheme, seed: Long, val size: Int = 10) {
    val grid: Array<CharArray>
    val placed: List<Placed>
    val found = mutableStateListOf<String>()

    init {
        val rnd = Random(seed)
        var result: Pair<Array<CharArray>, List<Placed>>? = null
        var attempt = 0
        while (result == null) {
            result = tryBuild(Random(rnd.nextLong()), allowBackwards = attempt < 40)
            attempt++
        }
        grid = result.first
        placed = result.second
    }

    private fun tryBuild(rnd: Random, allowBackwards: Boolean): Pair<Array<CharArray>, List<Placed>>? {
        val g = Array(size) { CharArray(size) { ' ' } }
        val out = ArrayList<Placed>()
        // Direções: horizontal, vertical, diagonais; de trás para frente só às vezes.
        val forward = listOf(0 to 1, 1 to 0, 1 to 1, -1 to 1)
        val backward = listOf(0 to -1, -1 to 0, -1 to -1, 1 to -1)
        val words = theme.words.map { it to Words.normalize(it).filter { c -> c in 'A'..'Z' } }
            .sortedByDescending { it.second.length }
        for ((display, w) in words) {
            if (w.length > size) return null
            var ok = false
            repeat(300) {
                if (ok) return@repeat
                val dirs = if (allowBackwards && rnd.nextInt(4) == 0) backward else forward
                val (dr, dc) = dirs[rnd.nextInt(dirs.size)]
                val r = rnd.nextInt(size)
                val c = rnd.nextInt(size)
                val endR = r + dr * (w.length - 1)
                val endC = c + dc * (w.length - 1)
                if (endR !in 0 until size || endC !in 0 until size) return@repeat
                val fits = w.indices.all { i -> val ch = g[r + dr * i][c + dc * i]; ch == ' ' || ch == w[i] }
                if (!fits) return@repeat
                w.indices.forEach { i -> g[r + dr * i][c + dc * i] = w[i] }
                out += Placed(w, display, r, c, dr, dc)
                ok = true
            }
            if (!ok) return null
        }
        // Preenche o resto com letras comuns do português.
        val fill = "AAAAEEEEIIOOOUURRSSTTNNMMLLCCDDPPBGVFHQJZX"
        for (r in 0 until size) for (c in 0 until size) if (g[r][c] == ' ') g[r][c] = fill[rnd.nextInt(fill.length)]
        return g to out
    }

    /** Confere uma seleção em linha reta de (r1,c1) até (r2,c2). Devolve a palavra achada ou null. */
    fun check(r1: Int, c1: Int, r2: Int, c2: Int): Placed? {
        val cells = line(r1, c1, r2, c2) ?: return null
        return placed.firstOrNull { p ->
            p.word !in found && (p.cells == cells || p.cells == cells.reversed())
        }?.also { found += it.word }
    }

    val done: Boolean get() = found.size == placed.size

    companion object {
        /** Células de uma linha reta (horizontal, vertical ou diagonal); null se não for reta. */
        fun line(r1: Int, c1: Int, r2: Int, c2: Int): List<Pair<Int, Int>>? {
            val dr = r2 - r1
            val dc = c2 - c1
            if (dr != 0 && dc != 0 && abs(dr) != abs(dc)) return null
            val n = max(abs(dr), abs(dc))
            val sr = Integer.signum(dr)
            val sc = Integer.signum(dc)
            return (0..n).map { (r1 + sr * it) to (c1 + sc * it) }
        }

        /** Ajusta o fim do arrasto para a linha reta mais próxima (8 direções). */
        fun snap(r1: Int, c1: Int, r2: Int, c2: Int): Pair<Int, Int> {
            val dr = r2 - r1
            val dc = c2 - c1
            if (dr == 0 || dc == 0) return r2 to c2
            val adr = abs(dr)
            val adc = abs(dc)
            return when {
                adr > 2 * adc -> r2 to c1
                adc > 2 * adr -> r1 to c2
                else -> { val n = minOf(adr, adc); (r1 + Integer.signum(dr) * n) to (c1 + Integer.signum(dc) * n) }
            }
        }
    }
}
