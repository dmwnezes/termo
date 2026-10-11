package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg
import kotlin.random.Random

/**
 * Caça-Palavras com amigo (modo "c"): o anfitrião gera a grade 10×10 e manda no "grid";
 * os dois procuram as mesmas palavras e cada uma é de quem mandar o PRIMEIRO "found" dela.
 */
object HuntDuel {
    const val SIZE = 10
    const val WORDS = 8
    /** Tempo da rodada: 5 minutos. */
    const val ROUND_MS = 300_000L
    /** O convidado manda o "timeup" se o anfitrião saiu ou depois de 5 min + 10 s. */
    const val GUEST_TIMEUP_MS = 310_000L

    /** Forma de comparar: maiúsculas, sem acento, só A–Z. */
    fun key(word: String): String = Words.normalize(word).filter { it in 'A'..'Z' }

    /**
     * Anfitrião: monta a grade da rodada [n] com um tema sorteado (sem repetir os de [used]),
     * [WORDS] palavras dele, com o gerador do Caça-Palavras solo.
     */
    fun build(pool: List<SearchTheme>, used: Set<String>, n: Int, rnd: Random = Random.Default): Msg.Grid {
        val fresh = pool.filter { it.name !in used }.ifEmpty { pool }
        var last: Exception? = null
        repeat(30) {
            val theme = fresh[rnd.nextInt(fresh.size)]
            val words = theme.words.distinctBy(::key).filter { key(it).length in 3..SIZE }.shuffled(rnd).take(WORDS)
            try {
                val ws = WordSearch(SearchTheme(theme.name, words), rnd.nextLong(), SIZE)
                return Msg.Grid(n, theme.name, ws.grid.map { String(it) }, ws.placed.map { it.display })
            } catch (e: IllegalStateException) { last = e }
        }
        throw last ?: IllegalStateException("sem grade")
    }

    /** Grade válida: [SIZE] linhas de [SIZE] letras A–Z e ao menos uma palavra. */
    fun valid(g: Msg.Grid): Boolean =
        g.rows.size == SIZE && g.rows.all { r -> r.length == SIZE && r.all { it in 'A'..'Z' } } && g.words.isNotEmpty()

    /** Letras da linha reta de (r1,c1) até (r2,c2); null se não for reta. */
    fun lineLetters(rows: List<String>, r1: Int, c1: Int, r2: Int, c2: Int): Pair<String, List<Pair<Int, Int>>>? {
        val cells = WordSearch.line(r1, c1, r2, c2) ?: return null
        if (cells.any { (r, c) -> r !in rows.indices || c !in rows[r].indices }) return null
        return cells.joinToString("") { (r, c) -> rows[r][c].toString() } to cells
    }

    /** Palavra da lista (forma sem acento) que a seleção forma, nos dois sentidos; null se nenhuma ou já pega. */
    fun match(g: Msg.Grid, r1: Int, c1: Int, r2: Int, c2: Int, taken: Set<String>): String? {
        val (letters, _) = lineLetters(g.rows, r1, c1, r2, c2) ?: return null
        if (letters.length < 2) return null
        val rev = letters.reversed()
        return g.words.map(::key).firstOrNull { k -> k !in taken && (k == letters || k == rev) }
    }

    /** Onde a palavra está na grade (primeira ocorrência, 8 direções), para pintar. */
    fun locate(rows: List<String>, word: String): List<Pair<Int, Int>>? {
        val k = key(word)
        if (k.isEmpty()) return null
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, -1 to 1, 0 to -1, -1 to 0, -1 to -1, 1 to -1)
        for (r in rows.indices) for (c in rows[r].indices) for ((dr, dc) in dirs) {
            val cells = k.indices.map { (r + dr * it) to (c + dc * it) }
            if (cells.all { (rr, cc) -> rr in rows.indices && cc in rows[rr].indices } &&
                cells.withIndex().all { (i, rc) -> rows[rc.first][rc.second] == k[i] }
            ) return cells
        }
        return null
    }

    /**
     * Estado da rodada [n]: dono de cada palavra (o primeiro "found" dela na ordem do tópico), se acabou o tempo.
     * "found" depois do "timeup" (ou de palavra fora da lista, de outra rodada ou de quem não está na sala) é ignorado.
     */
    data class State(val owners: Map<String, String>, val timeUp: Boolean, val total: Int) {
        val over: Boolean get() = timeUp || (total > 0 && owners.size >= total)
        fun count(id: String?): Int = owners.values.count { it == id }
        /** "me", "opp" ou "draw" quando acabou; null antes. */
        fun outcome(me: String, opp: String?): String? {
            if (!over) return null
            val a = count(me); val b = count(opp)
            return when { a > b -> "me"; b > a -> "opp"; else -> "draw" }
        }
    }

    fun state(grid: Msg.Grid?, msgs: List<Msg>, n: Int, players: Set<String>): State {
        if (grid == null) return State(emptyMap(), msgs.any { it is Msg.TimeUp && it.n == n }, 0)
        val keys = grid.words.map(::key).toSet()
        val owners = LinkedHashMap<String, String>()
        var timeUp = false
        for (m in msgs) {
            when (m) {
                is Msg.Found -> {
                    val k = key(m.word)
                    if (!timeUp && m.n == n && m.id in players && k in keys && k !in owners) owners[k] = m.id
                }
                is Msg.TimeUp -> if (m.n == n) timeUp = true
                else -> {}
            }
        }
        return State(owners, timeUp, keys.size)
    }
}
