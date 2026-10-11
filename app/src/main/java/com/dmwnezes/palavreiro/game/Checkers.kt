package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg
import kotlin.math.abs

/**
 * Damas com amigo (modo "x"), regras brasileiras. Tabuleiro 8×8, casas 0..63 = linha·8 + coluna, linha 0 em cima;
 * casas escuras: (linha + coluna) ímpar. Pretas começam nas linhas 0–2, brancas nas 5–7; brancas começam e sobem.
 *
 * - Pedra anda 1 casa na diagonal para frente e captura para frente e para trás.
 * - Dama (pedra que termina a jogada na última linha) anda e captura em qualquer distância ("dama voadora").
 * - Captura obrigatória e lei da maioria: só valem as jogadas que capturam o maior número de peças.
 *   A mesma peça não é capturada duas vezes; as capturadas só saem no fim (e bloqueiam a passagem até lá).
 * - Pedra que passa pela última linha no meio de uma sequência e não termina nela não vira dama.
 *
 * Peças: 1 = pedra branca, 2 = dama branca, -1 = pedra preta, -2 = dama preta, 0 = vazia.
 * Os dois lados (app e site) usam o mesmo motor: a jogada vale se o caminho está em [legalMoves].
 */
object Checkers {
    const val WHITE = 1
    const val BLACK = -1
    /** Empate: 20 jogadas seguidas só de damas, sem captura. */
    const val DRAW_KING_MOVES = 20

    private val DIRS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    fun dark(sq: Int): Boolean = (sq / 8 + sq % 8) % 2 == 1

    fun initial(): IntArray = IntArray(64) { sq ->
        val r = sq / 8
        when { !dark(sq) -> 0; r <= 2 -> -1; r >= 5 -> 1; else -> 0 }
    }

    /** Monta um tabuleiro a partir das listas de casas (para testes e posições). */
    fun board(whiteMen: List<Int> = emptyList(), whiteKings: List<Int> = emptyList(), blackMen: List<Int> = emptyList(), blackKings: List<Int> = emptyList()): IntArray {
        val b = IntArray(64)
        whiteMen.forEach { b[it] = 1 }; whiteKings.forEach { b[it] = 2 }
        blackMen.forEach { b[it] = -1 }; blackKings.forEach { b[it] = -2 }
        return b
    }

    private fun side(p: Int): Int = Integer.signum(p)
    private fun step(sq: Int, d: Pair<Int, Int>): Int {
        val r = sq / 8 + d.first; val c = sq % 8 + d.second
        return if (r in 0..7 && c in 0..7) r * 8 + c else -1
    }

    /** Linha onde as pedras de [side] viram dama. */
    fun lastRow(side: Int): Int = if (side == WHITE) 0 else 7

    /** Jogadas legais de [side]: cada uma é o caminho [origem, parada1, parada2, …]. */
    fun legalMoves(b: IntArray, side: Int): List<List<Int>> {
        val caps = ArrayList<Pair<List<Int>, Int>>()
        for (sq in 0 until 64) {
            val p = b[sq]
            if (side(p) != side) continue
            val work = b.copyOf(); work[sq] = 0
            captureDfs(work, side, abs(p) == 2, sq, listOf(sq), emptySet(), caps)
        }
        if (caps.isNotEmpty()) {
            val best = caps.maxOf { it.second }
            return caps.filter { it.second == best }.map { it.first }.distinct()
        }
        val out = ArrayList<List<Int>>()
        for (sq in 0 until 64) {
            val p = b[sq]
            if (side(p) != side) continue
            if (abs(p) == 2) {
                for (d in DIRS) {
                    var t = step(sq, d)
                    while (t >= 0 && b[t] == 0) { out += listOf(sq, t); t = step(t, d) }
                }
            } else {
                val fwd = if (side == WHITE) -1 else 1
                for (dc in listOf(-1, 1)) {
                    val t = step(sq, fwd to dc)
                    if (t >= 0 && b[t] == 0) out += listOf(sq, t)
                }
            }
        }
        return out
    }

    private fun captureDfs(
        b: IntArray, side: Int, king: Boolean, pos: Int, path: List<Int>, taken: Set<Int>,
        out: MutableList<Pair<List<Int>, Int>>,
    ) {
        var extended = false
        for (d in DIRS) {
            if (!king) {
                val mid = step(pos, d); if (mid < 0) continue
                val land = step(mid, d); if (land < 0) continue
                if (side(b[mid]) == -side && mid !in taken && b[land] == 0) {
                    extended = true
                    captureDfs(b, side, false, land, path + land, taken + mid, out)
                }
            } else {
                var t = step(pos, d)
                while (t >= 0 && b[t] == 0) t = step(t, d)
                if (t < 0 || side(b[t]) != -side || t in taken) continue
                var land = step(t, d)
                while (land >= 0 && b[land] == 0) {
                    extended = true
                    captureDfs(b, side, true, land, path + land, taken + t, out)
                    land = step(land, d)
                }
            }
        }
        if (!extended && taken.isNotEmpty()) out += path to taken.size
    }

    /** Peças capturadas pelo caminho (a peça adversária em cada trecho). */
    fun captured(b: IntArray, path: List<Int>): List<Int> {
        val side = side(b[path[0]])
        val out = ArrayList<Int>()
        for (i in 0 until path.size - 1) {
            val a = path[i]; val z = path[i + 1]
            val dr = Integer.signum(z / 8 - a / 8); val dc = Integer.signum(z % 8 - a % 8)
            var t = step(a, dr to dc)
            while (t >= 0 && t != z) {
                if (b[t] != 0 && side(b[t]) == -side) out += t
                t = step(t, dr to dc)
            }
        }
        return out
    }

    /** Tabuleiro depois da jogada (capturas saem no fim; pedra que termina na última linha vira dama). */
    fun apply(b: IntArray, path: List<Int>): IntArray {
        val n = b.copyOf()
        val p = n[path[0]]
        captured(b, path).forEach { n[it] = 0 }
        n[path[0]] = 0
        val end = path.last()
        n[end] = if (abs(p) == 1 && end / 8 == lastRow(side(p))) 2 * side(p) else p
        return n
    }

    fun count(b: IntArray, side: Int): Int = b.count { side(it) == side }

    /**
     * Estado da rodada: [turn] = cor da vez; [moves] = jogadas válidas até agora; [last] = última jogada;
     * [winner] = cor que venceu (sem peças/jogadas do outro ou desistência); [draw] = 20 lances só de damas sem captura.
     */
    data class State(
        val board: IntArray, val turn: Int, val moves: Int, val last: List<Int>?, val quiet: Int,
        val winner: Int?, val draw: Boolean, val resigned: Int? = null,
    ) {
        val over: Boolean get() = winner != null || draw
        val legal: List<List<Int>> by lazy { if (over) emptyList() else legalMoves(board, turn) }
        /** Captura obrigatória agora? */
        val mustCapture: Boolean get() = legal.firstOrNull()?.let { captured(board, it).isNotEmpty() } == true
        override fun equals(other: Any?) = other is State && board.contentEquals(other.board) && turn == other.turn && moves == other.moves &&
            last == other.last && quiet == other.quiet && winner == other.winner && draw == other.draw && resigned == other.resigned
        override fun hashCode() = board.contentHashCode() * 31 + moves
    }

    fun start(board: IntArray = initial(), turn: Int = WHITE): State = State(board, turn, 0, null, 0, null, false)

    /** Aplica a jogada se for legal; null se não for. */
    fun play(s: State, side: Int, n: Int, path: List<Int>): State? {
        if (s.over || side != s.turn || n != s.moves || path !in s.legal) return null
        val wasKing = abs(s.board[path[0]]) == 2
        val capturedAny = captured(s.board, path).isNotEmpty()
        val nb = apply(s.board, path)
        val quiet = if (wasKing && !capturedAny) s.quiet + 1 else 0
        val other = -side
        val oppDone = count(nb, other) == 0 || legalMoves(nb, other).isEmpty()
        return State(nb, other, s.moves + 1, path, quiet, if (oppDone) side else null, !oppDone && quiet >= DRAW_KING_MOVES)
    }

    /** Estado a partir das mensagens na ordem do tópico (jogadas inválidas são ignoradas). */
    fun state(msgs: List<Msg>, whiteId: String, blackId: String, from: State = start()): State {
        var s = from
        for (m in msgs) {
            when (m) {
                is Msg.Move -> {
                    val side = when (m.id) { whiteId -> WHITE; blackId -> BLACK; else -> continue }
                    s = play(s, side, m.n, m.path) ?: s
                }
                is Msg.Resign -> if (!s.over) {
                    val side = when (m.id) { whiteId -> WHITE; blackId -> BLACK; else -> continue }
                    s = s.copy(winner = -side, resigned = side)
                }
                else -> {}
            }
        }
        return s
    }

    /** "me", "opp" ou "draw" quando acabou; null antes. */
    fun outcome(s: State, mine: Int): String? = when {
        s.winner != null -> if (s.winner == mine) "me" else "opp"
        s.draw -> "draw"
        else -> null
    }

    /** O anfitrião joga de brancas nas rodadas pares. */
    fun hostIsWhite(round: Int): Boolean = round % 2 == 0
}
