package com.dmwnezes.palavreiro.game

/** Campo Minado: mesmo motor do site (js/minado.js). A partida começa com uma área aberta. */
class Minado(val level: Int, val seed: Long) {
    data class Level(val key: String, val label: String, val w: Int, val h: Int, val mines: Int)

    val w = LEVELS[level].w
    val h = LEVELS[level].h
    val mines = LEVELS[level].mines
    val mine = BooleanArray(w * h)
    val count = IntArray(w * h)
    val open = BooleanArray(w * h)
    val flag = BooleanArray(w * h)
    var lost = false
    var won = false
    var boom = -1
    val start: Int

    init {
        val total = w * h
        val r = Minstd.ofDay(seed)
        val cells = IntArray(total) { it }
        for (i in total - 1 downTo 1) { val j = (r.next() % (i + 1)).toInt(); val t = cells[i]; cells[i] = cells[j]; cells[j] = t }
        for (k in 0 until mines) mine[cells[k]] = true
        for (c in 0 until total) count[c] = near(c).count { mine[it] }
        fun dist(c: Int): Int { val a = 2 * (c / w) - (h - 1); val b = 2 * (c % w) - (w - 1); return a * a + b * b }
        start = (0 until total).filter { !mine[it] }.sortedWith(compareBy({ count[it] }, { dist(it) }, { it })).first()
        reveal(start)
    }

    fun near(c: Int): List<Int> {
        val y = c / w
        val x = c % w
        val out = ArrayList<Int>(8)
        for (a in -1..1) for (b in -1..1) {
            if (a == 0 && b == 0) continue
            val yy = y + a
            val xx = x + b
            if (yy in 0 until h && xx in 0 until w) out += yy * w + xx
        }
        return out
    }

    fun reveal(c: Int) {
        if (won || lost || flag[c] || open[c]) return
        if (mine[c]) { lost = true; boom = c; return }
        val stack = ArrayDeque(listOf(c))
        while (stack.isNotEmpty()) {
            val k = stack.removeLast()
            if (open[k] || flag[k]) continue
            open[k] = true
            if (count[k] == 0) near(k).forEach { if (!open[it]) stack.addLast(it) }
        }
        won = open.count { it } == w * h - mines
    }

    /** Toque: abre (ou abre as vizinhas de um número com as bandeiras certas na conta). */
    fun tap(c: Int) {
        if (won || lost) return
        if (!open[c]) { reveal(c); return }
        val around = near(c)
        if (count[c] > 0 && around.count { flag[it] } == count[c]) around.forEach { reveal(it) }
    }

    fun toggleFlag(c: Int) { if (!won && !lost && !open[c]) flag[c] = !flag[c] }

    val flags: Int get() = flag.count { it }

    fun save(): String = "$seed;${open.joinToString("") { if (it) "1" else "0" }};${flag.joinToString("") { if (it) "1" else "0" }};$lost;$won;$boom"

    fun restore(p: List<String>) {
        if (p.size < 6 || p[1].length != open.size || p[2].length != flag.size) return
        for (i in open.indices) { open[i] = p[1][i] == '1'; flag[i] = p[2][i] == '1' }
        lost = p[3] == "true"; won = p[4] == "true"; boom = p[5].toIntOrNull() ?: -1
    }

    companion object {
        val LEVELS = listOf(Level("facil", "Fácil", 8, 8, 10), Level("medio", "Médio", 9, 12, 18), Level("dificil", "Difícil", 10, 15, 30))
    }
}
