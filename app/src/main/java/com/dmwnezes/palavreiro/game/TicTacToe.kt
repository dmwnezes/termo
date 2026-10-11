package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg

/**
 * Jogo da Velha com amigo (modo "v"): 3×3, X sempre começa.
 * Na rodada 0 (start) o anfitrião é X; a cada "again" os símbolos trocam.
 */
object TicTacToe {
    val LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6),
    )

    /** O anfitrião é X nas rodadas pares (0, 2, 4…). */
    fun hostIsX(round: Int): Boolean = round % 2 == 0

    /**
     * [board] = 'X', 'O' ou null em cada casa; [turn] = símbolo da vez; [winner]/[line] = quem fez a linha;
     * [draw] = velha (tabuleiro cheio sem linha).
     */
    data class State(val board: List<Char?>, val moves: Int, val winner: Char?, val line: List<Int>?) {
        val turn: Char get() = if (moves % 2 == 0) 'X' else 'O'
        val draw: Boolean get() = winner == null && moves >= 9
        val over: Boolean get() = winner != null || draw
    }

    fun empty() = State(List(9) { null }, 0, null, null)

    /** Aplica uma jogada se for válida (vez certa, n certo, casa vazia, jogo em andamento); senão devolve null. */
    fun play(s: State, symbol: Char, n: Int, cell: Int): State? {
        if (s.over || symbol != s.turn || n != s.moves || cell !in 0..8 || s.board[cell] != null) return null
        val b = s.board.toMutableList().also { it[cell] = symbol }
        val line = LINES.firstOrNull { l -> l.all { b[it] == symbol } }
        return State(b, s.moves + 1, if (line != null) symbol else null, line)
    }

    /** Estado a partir das jogadas na ordem do tópico; inválidas são ignoradas. */
    fun state(msgs: List<Msg>, xId: String, oId: String): State {
        var s = empty()
        for (m in msgs) {
            if (m !is Msg.Cell) continue
            val sym = when (m.id) { xId -> 'X'; oId -> 'O'; else -> continue }
            s = play(s, sym, m.n, m.cell) ?: s
        }
        return s
    }

    /** "me", "opp" ou "draw" quando acabou; null antes. */
    fun outcome(s: State, mine: Char): String? = when {
        s.winner != null -> if (s.winner == mine) "me" else "opp"
        s.draw -> "draw"
        else -> null
    }
}
