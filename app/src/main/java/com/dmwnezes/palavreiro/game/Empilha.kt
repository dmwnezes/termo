package com.dmwnezes.palavreiro.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Empilha: mesmo motor do site (js/empilha.js). Largura do tabuleiro: 100. */
class Empilha {
    data class Block(val x: Double, val w: Double)

    val tower = mutableListOf(Block(20.0, 60.0))
    var x = 0.0
    var w = 60.0
    private var dir = 1
    var over = false
        private set
    var perfect = false
        private set

    val score: Int get() = tower.size - 1
    private val speed: Double get() = min(120.0, 45.0 + score * 2)

    fun tick(dt: Double) {
        if (over) return
        x += dir * speed * dt
        val limit = WIDTH - w
        if (x <= 0) { x = -x; dir = 1 }
        if (x >= limit) { x = 2 * limit - x; dir = -1 }
        if (x < 0) x = 0.0
    }

    fun drop() {
        if (over) return
        val top = tower.last()
        perfect = abs(x - top.x) <= SNAP
        if (perfect) x = top.x
        val left = max(x, top.x)
        val right = min(x + w, top.x + top.w)
        val nw = right - left
        if (nw < 1) { over = true; return } // sobra invisível também acaba a partida
        tower += Block(left, nw)
        w = nw
        // O próximo sai do lado oposto ao último.
        dir = -dir
        x = if (dir > 0) 0.0 else WIDTH - nw
    }

    companion object {
        const val WIDTH = 100.0
        const val SNAP = 1.5
    }
}
