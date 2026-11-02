package com.dmwnezes.palavreiro.game

/** Grade 5×5: 3 palavras na horizontal (linhas 0, 2, 4) e 3 na vertical (colunas 0, 2, 4), com as 6 pistas nessa ordem. */
data class CruzadaQ(val across: List<String>, val down: List<String>, val clues: List<String>) {
    /** Letra certa da casa, ou null nas casas pretas. */
    fun letter(r: Int, c: Int): Char? = when {
        r % 2 == 0 -> across[r / 2][c]
        c % 2 == 0 -> down[c / 2][r]
        else -> null
    }
}

/** Mesmas regras do site (js/cruzada.js) e o mesmo arquivo (shared/cruzada.txt). */
object Cruzada {
    const val SIZE = 5

    fun parse(text: String): List<CruzadaQ> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
        val f = l.split('|')
        val w = f[0].split(',')
        if (f.size < 7 || w.size != 6) null else CruzadaQ(w.subList(0, 3), w.subList(3, 6), f.subList(1, 7))
    }

    fun dailyIndex(day: Long, n: Int): Int = Math.floorMod(day * 7919 + 104729, n.toLong()).toInt()
}

class CruzadaGame(val q: CruzadaQ) {
    val letters = Array(Cruzada.SIZE) { CharArray(Cruzada.SIZE) { ' ' } }
    val locked = Array(Cruzada.SIZE) { BooleanArray(Cruzada.SIZE) }
    val wrong = Array(Cruzada.SIZE) { BooleanArray(Cruzada.SIZE) }
    var r = 0; private set
    var c = 0; private set
    var across = true; private set
    var checks = 0
    var won = false

    /** Índice da pista da palavra atual (0–2 horizontais, 3–5 verticais). */
    val clue: Int get() = if (across) r / 2 else 3 + c / 2

    fun inWord(y: Int, x: Int) = if (across) y == r else x == c

    private fun cells() = (0 until Cruzada.SIZE).map { k -> if (across) r to k else k to c }

    fun select(y: Int, x: Int) {
        if (q.letter(y, x) == null) return
        if (y == r && x == c && y % 2 == 0 && x % 2 == 0) across = !across
        else {
            r = y; c = x
            across = y % 2 == 0 && (across || x % 2 != 0)
        }
    }

    /** Toca numa pista: vai para a primeira casa livre da palavra. */
    fun selectClue(i: Int) {
        across = i < 3
        if (across) { r = i * 2; c = 0 } else { c = (i - 3) * 2; r = 0 }
        firstFree()
    }

    fun firstFree() {
        cells().firstOrNull { (y, x) -> !locked[y][x] && letters[y][x] == ' ' }?.let { (y, x) -> r = y; c = x }
    }

    private fun step(d: Int): Boolean {
        val l = cells()
        var i = l.indexOf(r to c) + d
        while (i in l.indices) {
            val (y, x) = l[i]
            if (!locked[y][x]) { r = y; c = x; return true }
            i += d
        }
        return false
    }

    fun type(ch: Char) {
        if (won) return
        if (!locked[r][c]) { letters[r][c] = ch; wrong[r][c] = false; step(1) } else step(1)
    }

    fun delete() {
        if (won) return
        if (!locked[r][c] && letters[r][c] != ' ') letters[r][c] = ' '
        else if (step(-1)) letters[r][c] = ' '
    }

    private fun allOk() = (0 until Cruzada.SIZE).all { y -> (0 until Cruzada.SIZE).all { x -> q.letter(y, x) == null || locked[y][x] } }

    /** Trava as letras certas e marca as erradas. Devolve quantas estão erradas. */
    fun check(): Int {
        if (won) return 0
        checks++
        var bad = 0
        for (y in 0 until Cruzada.SIZE) for (x in 0 until Cruzada.SIZE) {
            val v = q.letter(y, x) ?: continue
            if (locked[y][x] || letters[y][x] == ' ') continue
            if (letters[y][x] == v) locked[y][x] = true else { wrong[y][x] = true; bad++ }
        }
        won = allOk()
        return bad
    }

    fun reveal() {
        if (won || locked[r][c]) return
        letters[r][c] = q.letter(r, c)!!
        locked[r][c] = true
        wrong[r][c] = false
        won = allOk()
    }

    /** Estado para guardar: linhas de letras, casas travadas e conferências. */
    fun save(): String = letters.joinToString("/") { String(it) } + "|" + locked.joinToString("/") { l -> l.joinToString("") { if (it) "1" else "0" } } + "|$checks|$won"

    fun restore(text: String) {
        val p = text.split('|')
        if (p.size != 4) return
        val g = p[0].split('/')
        val k = p[1].split('/')
        if (g.size != Cruzada.SIZE || k.size != Cruzada.SIZE || g.any { it.length != Cruzada.SIZE } || k.any { it.length != Cruzada.SIZE }) return
        for (y in 0 until Cruzada.SIZE) for (x in 0 until Cruzada.SIZE) { letters[y][x] = g[y][x]; locked[y][x] = k[y][x] == '1' }
        checks = p[2].toIntOrNull() ?: 0
        won = p[3] == "true"
    }
}
