package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg
import kotlin.math.abs
import kotlin.random.Random

/**
 * Xadrez (solo contra o computador e com amigo, modo "k"), regras completas: roque, en passant, promoção,
 * xeque-mate, afogamento, material insuficiente (R×R, R+B×R, R+C×R), 50 lances e tripla repetição.
 *
 * Casas 0..63 = fileira·8 + coluna, com a1 = 0, h1 = 7, a8 = 56, h8 = 63. Lances em UCI ("e2e4", promoção "e7e8q").
 * Peças: 1 peão, 2 cavalo, 3 bispo, 4 torre, 5 dama, 6 rei; positivas = brancas, negativas = pretas.
 * App e site usam as mesmas regras: um lance vale se está em [legalUci] da posição.
 */
object Chess {
    const val WHITE = 1
    const val BLACK = -1
    const val PAWN = 1; const val KNIGHT = 2; const val BISHOP = 3; const val ROOK = 4; const val QUEEN = 5; const val KING = 6

    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    /** Posição: tabuleiro, vez, direitos de roque (1 = K, 2 = Q, 4 = k, 8 = q), casa de en passant (-1), meio-lances sem captura/peão. */
    class Pos(val b: IntArray, val turn: Int, val castle: Int, val ep: Int, val half: Int, val full: Int)

    fun name(sq: Int): String = "${'a' + sq % 8}${sq / 8 + 1}"
    fun square(s: String): Int {
        val f = s[0] - 'a'; val r = s[1] - '1'
        return if (s.length == 2 && f in 0..7 && r in 0..7) r * 8 + f else -1
    }

    // ---------- lances (Int: origem | destino << 6 | promoção << 12) ----------
    fun move(from: Int, to: Int, promo: Int = 0) = from or (to shl 6) or (promo shl 12)
    fun from(m: Int) = m and 63
    fun to(m: Int) = (m shr 6) and 63
    fun promo(m: Int) = m shr 12
    fun uci(m: Int): String = name(from(m)) + name(to(m)) + when (promo(m)) { QUEEN -> "q"; ROOK -> "r"; BISHOP -> "b"; KNIGHT -> "n"; else -> "" }

    // ---------- FEN ----------
    fun fen(text: String): Pos {
        val p = text.trim().split(Regex("\\s+"))
        val b = IntArray(64)
        p[0].split('/').forEachIndexed { i, row ->
            var f = 0
            for (ch in row) {
                if (ch.isDigit()) { f += ch - '0'; continue }
                val t = "pnbrqk".indexOf(ch.lowercaseChar()) + 1
                b[(7 - i) * 8 + f] = if (ch.isUpperCase()) t else -t
                f++
            }
        }
        val castle = p.getOrElse(2) { "-" }.sumOf { when (it) { 'K' -> 1; 'Q' -> 2; 'k' -> 4; 'q' -> 8; else -> 0 }.toInt() }
        return Pos(
            b, if (p.getOrElse(1) { "w" } == "b") BLACK else WHITE, castle,
            p.getOrElse(3) { "-" }.let { if (it == "-") -1 else square(it) },
            p.getOrNull(4)?.toIntOrNull() ?: 0, p.getOrNull(5)?.toIntOrNull() ?: 1,
        )
    }

    fun start(): Pos = fen(START_FEN)

    // ---------- ataques ----------
    private val KN = intArrayOf(-2, -1, -2, 1, -1, -2, -1, 2, 1, -2, 1, 2, 2, -1, 2, 1)
    private val KG = intArrayOf(-1, -1, -1, 0, -1, 1, 0, -1, 0, 1, 1, -1, 1, 0, 1, 1)
    private val DIAG = intArrayOf(-1, -1, -1, 1, 1, -1, 1, 1)
    private val ORTH = intArrayOf(-1, 0, 1, 0, 0, -1, 0, 1)

    private fun at(r: Int, f: Int) = if (r in 0..7 && f in 0..7) r * 8 + f else -1

    /** A casa [sq] é atacada por alguma peça de [by]? */
    fun attacked(b: IntArray, sq: Int, by: Int): Boolean {
        val r = sq / 8; val f = sq % 8
        // Peão de [by] ataca para a frente dele: está uma fileira atrás.
        for (df in intArrayOf(-1, 1)) { val s = at(r - by, f + df); if (s >= 0 && b[s] == PAWN * by) return true }
        for (i in 0 until 16 step 2) { val s = at(r + KN[i], f + KN[i + 1]); if (s >= 0 && b[s] == KNIGHT * by) return true }
        for (i in 0 until 16 step 2) { val s = at(r + KG[i], f + KG[i + 1]); if (s >= 0 && b[s] == KING * by) return true }
        if (ray(b, r, f, DIAG, by, BISHOP)) return true
        return ray(b, r, f, ORTH, by, ROOK)
    }

    private fun ray(b: IntArray, r: Int, f: Int, dirs: IntArray, by: Int, slider: Int): Boolean {
        for (i in 0 until 8 step 2) {
            var rr = r + dirs[i]; var ff = f + dirs[i + 1]
            while (rr in 0..7 && ff in 0..7) {
                val p = b[rr * 8 + ff]
                if (p != 0) { if (p == slider * by || p == QUEEN * by) return true; break }
                rr += dirs[i]; ff += dirs[i + 1]
            }
        }
        return false
    }

    fun kingSq(b: IntArray, side: Int): Int = b.indexOf(KING * side)
    fun inCheck(p: Pos): Boolean = kingSq(p.b, p.turn).let { it >= 0 && attacked(p.b, it, -p.turn) }

    // ---------- geração ----------
    private fun pseudo(p: Pos, out: MutableList<Int>) {
        val b = p.b; val me = p.turn
        for (sq in 0 until 64) {
            val pc = b[sq]
            if (pc == 0 || Integer.signum(pc) != me) continue
            val r = sq / 8; val f = sq % 8
            when (abs(pc)) {
                PAWN -> {
                    val last = if (me == WHITE) 7 else 0
                    fun add(to: Int) {
                        if (to / 8 == last) for (pr in intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)) out += move(sq, to, pr) else out += move(sq, to)
                    }
                    val one = at(r + me, f)
                    if (one >= 0 && b[one] == 0) {
                        add(one)
                        val home = if (me == WHITE) 1 else 6
                        val two = at(r + 2 * me, f)
                        if (r == home && b[two] == 0) out += move(sq, two)
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val t = at(r + me, f + df); if (t < 0) continue
                        if (b[t] != 0 && Integer.signum(b[t]) == -me) add(t)
                        else if (t == p.ep && b[t] == 0) out += move(sq, t)
                    }
                }
                KNIGHT -> jumps(b, sq, r, f, KN, me, out)
                KING -> {
                    jumps(b, sq, r, f, KG, me, out)
                    castles(p, sq, out)
                }
                BISHOP -> slides(b, sq, r, f, DIAG, me, out)
                ROOK -> slides(b, sq, r, f, ORTH, me, out)
                QUEEN -> { slides(b, sq, r, f, DIAG, me, out); slides(b, sq, r, f, ORTH, me, out) }
            }
        }
    }

    private fun jumps(b: IntArray, sq: Int, r: Int, f: Int, d: IntArray, me: Int, out: MutableList<Int>) {
        for (i in d.indices step 2) {
            val t = at(r + d[i], f + d[i + 1])
            if (t >= 0 && (b[t] == 0 || Integer.signum(b[t]) == -me)) out += move(sq, t)
        }
    }

    private fun slides(b: IntArray, sq: Int, r: Int, f: Int, d: IntArray, me: Int, out: MutableList<Int>) {
        for (i in 0 until 8 step 2) {
            var rr = r + d[i]; var ff = f + d[i + 1]
            while (rr in 0..7 && ff in 0..7) {
                val t = rr * 8 + ff
                if (b[t] != 0) { if (Integer.signum(b[t]) == -me) out += move(sq, t); break }
                out += move(sq, t)
                rr += d[i]; ff += d[i + 1]
            }
        }
    }

    /** Roque: direitos, casas livres, rei fora de xeque e sem passar por casa atacada (o destino é conferido no filtro legal). */
    private fun castles(p: Pos, sq: Int, out: MutableList<Int>) {
        val me = p.turn; val b = p.b
        val home = if (me == WHITE) 4 else 60
        if (sq != home) return
        val (kBit, qBit) = if (me == WHITE) 1 to 2 else 4 to 8
        if (p.castle and (kBit or qBit) == 0 || attacked(b, sq, -me)) return
        if (p.castle and kBit != 0 && b[sq + 3] == ROOK * me && b[sq + 1] == 0 && b[sq + 2] == 0 && !attacked(b, sq + 1, -me)) out += move(sq, sq + 2)
        if (p.castle and qBit != 0 && b[sq - 4] == ROOK * me && b[sq - 1] == 0 && b[sq - 2] == 0 && b[sq - 3] == 0 && !attacked(b, sq - 1, -me)) out += move(sq, sq - 2)
    }

    /** Posição depois do lance (sem conferir se é legal). */
    fun apply(p: Pos, m: Int): Pos {
        val b = p.b.copyOf()
        val fr = from(m); val to = to(m); val pc = b[fr]; val me = Integer.signum(pc)
        val captured = b[to]
        var ep = -1
        var castle = p.castle
        b[fr] = 0
        when (abs(pc)) {
            PAWN -> {
                if (to == p.ep && captured == 0 && fr % 8 != to % 8) b[to - 8 * me] = 0
                if (abs(to - fr) == 16) ep = (fr + to) / 2
            }
            KING -> {
                if (to - fr == 2) { b[fr + 1] = b[fr + 3]; b[fr + 3] = 0 }
                if (fr - to == 2) { b[fr - 1] = b[fr - 4]; b[fr - 4] = 0 }
                castle = castle and (if (me == WHITE) 3.inv() else 12.inv())
            }
        }
        b[to] = if (promo(m) != 0) promo(m) * me else pc
        for (s in intArrayOf(fr, to)) castle = castle and when (s) { 7 -> 1.inv(); 0 -> 2.inv(); 63 -> 4.inv(); 56 -> 8.inv(); else -> -1 }
        val half = if (abs(pc) == PAWN || captured != 0) 0 else p.half + 1
        return Pos(b, -me, castle, ep, half, p.full + if (me == BLACK) 1 else 0)
    }

    /** Lances legais da vez. */
    fun legal(p: Pos): List<Int> {
        val all = ArrayList<Int>(48)
        pseudo(p, all)
        return all.filter { m -> val n = apply(p, m); val k = kingSq(n.b, p.turn); k >= 0 && !attacked(n.b, k, -p.turn) }
    }

    fun legalUci(p: Pos): List<String> = legal(p).map(::uci)

    fun perft(p: Pos, depth: Int): Long {
        if (depth == 0) return 1
        val ms = legal(p)
        if (depth == 1) return ms.size.toLong()
        return ms.sumOf { perft(apply(p, it), depth - 1) }
    }

    // ---------- empates ----------
    /** Só R×R, R+B×R ou R+C×R. */
    fun insufficient(b: IntArray): Boolean {
        val rest = b.filter { it != 0 && abs(it) != KING }
        return rest.isEmpty() || (rest.size == 1 && abs(rest[0]) in setOf(KNIGHT, BISHOP))
    }

    /** Chave da posição para a repetição: peças, vez, roque e en passant só se a captura en passant for possível. */
    fun key(p: Pos): String {
        val epOk = p.ep >= 0 && legal(p).any { to(it) == p.ep && abs(p.b[from(it)]) == PAWN }
        return p.b.joinToString(",") + "|" + p.turn + "|" + p.castle + "|" + (if (epOk) p.ep else -1)
    }

    enum class End { MATE, STALEMATE, MATERIAL, FIFTY, REPETITION, RESIGN }

    /**
     * Estado da partida: [moves] = lances feitos; [last] = último lance (UCI); [keys] = posições já vistas (repetição);
     * [winner] = cor vencedora (mate ou desistência); [end] = como acabou.
     */
    data class State(
        val pos: Pos, val moves: Int, val history: List<String>, val keys: List<String>,
        val end: End? = null, val winner: Int? = null, val resigned: Int? = null,
    ) {
        val turn: Int get() = pos.turn
        val over: Boolean get() = end != null
        val draw: Boolean get() = end != null && winner == null
        val last: String? get() = history.lastOrNull()
        val legal: List<Int> by lazy { if (over) emptyList() else legal(pos) }
        val check: Boolean get() = inCheck(pos)
    }

    fun newGame(p: Pos = start()): State = State(p, 0, emptyList(), listOf(key(p)))

    /** Aplica o lance (UCI) se for legal; null se não for. */
    fun play(s: State, uci: String): State? {
        if (s.over) return null
        val m = s.legal.firstOrNull { uci(it) == uci.lowercase() } ?: return null
        val np = apply(s.pos, m)
        val k = key(np)
        val keys = s.keys + k
        val replies = legal(np)
        val end = when {
            replies.isEmpty() -> if (inCheck(np)) End.MATE else End.STALEMATE
            insufficient(np.b) -> End.MATERIAL
            np.half >= 100 -> End.FIFTY
            keys.count { it == k } >= 3 -> End.REPETITION
            else -> null
        }
        return State(np, s.moves + 1, s.history + uci(m), keys, end, if (end == End.MATE) s.pos.turn else null)
    }

    /** Partida a partir da lista de lances (para retomar); para no primeiro inválido. */
    fun replay(moves: List<String>): State {
        var s = newGame()
        for (u in moves) s = play(s, u) ?: break
        return s
    }

    /** Com amigo: lance [n] de [side] vale se é a vez dele e é legal. */
    fun play(s: State, side: Int, n: Int, uci: String): State? = if (side != s.turn || n != s.moves) null else play(s, uci)

    /** Estado a partir das mensagens na ordem do tópico (lances inválidos são ignorados). */
    fun state(msgs: List<Msg>, whiteId: String, blackId: String): State {
        var s = newGame()
        for (m in msgs) {
            when (m) {
                is Msg.UMove -> {
                    val side = when (m.id) { whiteId -> WHITE; blackId -> BLACK; else -> continue }
                    s = play(s, side, m.n, m.uci) ?: s
                }
                is Msg.Resign -> if (!s.over) {
                    val side = when (m.id) { whiteId -> WHITE; blackId -> BLACK; else -> continue }
                    s = s.copy(end = End.RESIGN, winner = -side, resigned = side)
                }
                else -> {}
            }
        }
        return s
    }

    /** "me", "opp" ou "draw" quando acabou; null antes. */
    fun outcome(s: State, mine: Int): String? = when {
        !s.over -> null
        s.winner == null -> "draw"
        s.winner == mine -> "me"
        else -> "opp"
    }

    /** O anfitrião joga de brancas nas rodadas pares. */
    fun hostIsWhite(round: Int): Boolean = round % 2 == 0

    /** Texto de como acabou ("Xeque-mate", "Afogamento"…). */
    fun endText(e: End?): String = when (e) {
        End.MATE -> "Xeque-mate"; End.STALEMATE -> "Empate por afogamento"; End.MATERIAL -> "Empate por material insuficiente"
        End.FIFTY -> "Empate pela regra dos 50 lances"; End.REPETITION -> "Empate por tripla repetição"; End.RESIGN -> "Desistência"; null -> ""
    }

    // ---------- material ----------
    val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)
    private val POINTS = intArrayOf(0, 1, 3, 3, 5, 9, 0)
    private val FULL = intArrayOf(0, 8, 2, 2, 2, 1, 1)

    /** Peças de [side] que já foram capturadas (tipos, do maior para o menor). */
    fun captured(b: IntArray, side: Int): List<Int> = (QUEEN downTo PAWN).flatMap { t ->
        List(maxOf(0, FULL[t] - b.count { it == t * side })) { t }
    }

    /** Vantagem de material de [side] em pontos (peão 1, cavalo/bispo 3, torre 5, dama 9). */
    fun advantage(b: IntArray, side: Int): Int = b.sumOf { if (it == 0) 0 else POINTS[abs(it)] * Integer.signum(it) * side }

    // ---------- computador ----------
    enum class Level(val key: String, val label: String, val depth: Int) { FACIL("facil", "Fácil", 1), MEDIO("medio", "Médio", 2), DIFICIL("dificil", "Difícil", 3) }

    // Tabelas simples de posição (vista das brancas, a1 = índice 0).
    private val PST_PAWN = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, -20, -20, 10, 10, 5, 5, -5, -10, 0, 0, -10, -5, 5, 0, 0, 0, 20, 20, 0, 0, 0,
        5, 5, 10, 25, 25, 10, 5, 5, 10, 10, 20, 30, 30, 20, 10, 10, 50, 50, 50, 50, 50, 50, 50, 50, 0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val PST_KNIGHT = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 5, 5, 0, -20, -40, -30, 5, 10, 15, 15, 10, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30, -30, 0, 10, 15, 15, 10, 0, -30, -40, -20, 0, 0, 0, 0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50,
    )
    private val PST_BISHOP = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20, -10, 5, 0, 0, 0, 0, 5, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 0, 0, 0, 0, 0, 0, -10, -20, -10, -10, -10, -10, -10, -10, -20,
    )
    private val PST_ROOK = intArrayOf(
        0, 0, 0, 5, 5, 0, 0, 0, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 5, 10, 10, 10, 10, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val PST_KING = intArrayOf(
        20, 30, 10, 0, 0, 10, 30, 20, 20, 20, 0, 0, 0, 0, 20, 20, -10, -20, -20, -20, -20, -20, -20, -10, -20, -30, -30, -40, -40, -30, -30, -20,
        -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30,
    )
    private val PST = arrayOf(IntArray(64), PST_PAWN, PST_KNIGHT, PST_BISHOP, PST_ROOK, IntArray(64), PST_KING)

    /** Avaliação do ponto de vista de quem joga (centipeões). */
    fun evaluate(p: Pos): Int {
        var s = 0
        for (sq in 0 until 64) {
            val pc = p.b[sq]; if (pc == 0) continue
            val t = abs(pc)
            val idx = if (pc > 0) sq else (7 - sq / 8) * 8 + sq % 8
            s += (VALUE[t] + PST[t][idx]) * Integer.signum(pc)
        }
        return s * p.turn
    }

    private const val MATE = 100_000

    private fun ordered(p: Pos, ms: List<Int>): List<Int> =
        ms.sortedByDescending { m -> val v = p.b[to(m)]; (if (v != 0) VALUE[abs(v)] * 10 - VALUE[abs(p.b[from(m)])] else 0) + promo(m) * 100 }

    private fun quiesce(p: Pos, alpha0: Int, beta: Int, qd: Int): Int {
        var alpha = alpha0
        val stand = evaluate(p)
        if (stand >= beta) return stand
        if (stand > alpha) alpha = stand
        if (qd == 0) return stand
        val caps = legal(p).filter { p.b[to(it)] != 0 || promo(it) == QUEEN }
        for (m in ordered(p, caps)) {
            val v = -quiesce(apply(p, m), -beta, -alpha, qd - 1)
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    private fun search(p: Pos, depth: Int, alpha0: Int, beta: Int, ply: Int, quiet: Boolean): Int {
        val ms = legal(p)
        if (ms.isEmpty()) return if (inCheck(p)) -MATE + ply else 0
        if (insufficient(p.b) || p.half >= 100) return 0
        if (depth == 0) return if (quiet) quiesce(p, alpha0, beta, 4) else evaluate(p)
        var alpha = alpha0
        var best = -MATE * 2
        for (m in ordered(p, ms)) {
            val v = -search(apply(p, m), depth - 1, -beta, -alpha, ply + 1, quiet)
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    /**
     * Lance do computador: minimax com poda alfa-beta na profundidade do nível (1/2/3) + capturas no fim (Médio e Difícil).
     * No Fácil, um pouco de aleatoriedade na nota de cada lance.
     */
    fun bestMove(p: Pos, level: Level, rnd: Random = Random.Default): Int? {
        val ms = legal(p)
        if (ms.isEmpty()) return null
        val quiet = level != Level.FACIL
        var best = ms[0]; var bestV = Int.MIN_VALUE
        var alpha = -MATE * 2
        for (m in ordered(p, ms)) {
            var v = -search(apply(p, m), level.depth - 1, -MATE * 2, -alpha, 1, quiet)
            if (level == Level.FACIL) v += rnd.nextInt(-90, 91)
            if (v > bestV) { bestV = v; best = m }
            if (level != Level.FACIL && v > alpha) alpha = v
        }
        return best
    }
}
