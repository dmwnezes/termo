package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg
import kotlin.random.Random

/**
 * Batalha Naval: mesmo motor do site (js/naval.js).
 * Casas 0..99 = linha*10 + coluna, linha 0 em cima ("A1" = 0; letra = coluna, número = linha).
 * Frota = 5 navios na ordem de [SHIPS]; cada navio = lista de casas. Navios retos e sem encostar (nem na diagonal).
 */
object Naval {
    const val N = 10
    val SHIPS = listOf("Porta-aviões" to 5, "Encouraçado" to 4, "Cruzador" to 3, "Submarino" to 3, "Destróier" to 2)
    val LENS = SHIPS.map { it.second }

    fun name(i: Int): String = "ABCDEFGHIJ"[i % N] + (i / N + 1).toString()

    /** As 8 vizinhas da casa. */
    fun around(i: Int): List<Int> {
        val r = i / N; val c = i % N
        return buildList {
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val rr = r + dr; val cc = c + dc
                if (rr in 0 until N && cc in 0 until N) add(rr * N + cc)
            }
        }
    }
    /** As 4 vizinhas (cima, baixo, esquerda, direita). */
    fun cross(i: Int): List<Int> = buildList {
        val r = i / N; val c = i % N
        if (r > 0) add(i - N); if (r < N - 1) add(i + N); if (c > 0) add(i - 1); if (c < N - 1) add(i + 1)
    }
    /** Casas do navio de tamanho [len] começando em [i], ou null se sai do mar. */
    fun span(i: Int, len: Int, vert: Boolean): List<Int>? {
        val r = i / N; val c = i % N
        if (if (vert) r + len > N else c + len > N) return null
        return List(len) { if (vert) i + it * N else i + it }
    }

    /** Frota sorteada. */
    fun place(rnd: Random = Random.Default): List<List<Int>> {
        while (true) {
            val used = HashSet<Int>()
            val fleet = mutableListOf<List<Int>>()
            for (len in LENS) {
                var put: List<Int>? = null
                var t = 0
                while (put == null && t++ < 200) {
                    val cells = span(rnd.nextInt(N * N), len, rnd.nextBoolean()) ?: continue
                    if (cells.all { x -> x !in used && around(x).all { y -> y !in used || y in cells } }) put = cells
                }
                if (put == null) break
                used += put; fleet += put
            }
            if (fleet.size == LENS.size) return fleet
        }
    }

    /** Frota válida: 5 navios dos tamanhos certos, retos, dentro do mar, sem sobrepor nem encostar. */
    fun valid(fleet: List<List<Int>>?): Boolean {
        if (fleet == null || fleet.size != LENS.size) return false
        val owner = HashMap<Int, Int>()
        fleet.forEachIndexed { k, s ->
            if (s.size != LENS[k] || s.any { it !in 0 until N * N }) return false
            val sorted = s.sorted()
            val vert = sorted.size > 1 && sorted[1] - sorted[0] == N
            if (span(sorted[0], s.size, vert) != sorted) return false
            for (x in s) { if (x in owner) return false; owner[x] = k }
        }
        for ((x, k) in owner) for (y in around(x)) if (owner[y]?.let { it != k } == true) return false
        return true
    }

    data class Fire(val hit: Boolean, val ship: Int, val sunk: Boolean, val all: Boolean)
    /** Tiro na casa [c] contra a frota, sabendo os tiros anteriores nesse mar. */
    fun fire(fleet: List<List<Int>>, shots: Collection<Int>, c: Int): Fire {
        val done = shots.toHashSet().apply { add(c) }
        val ship = fleet.indexOfFirst { c in it }
        if (ship < 0) return Fire(false, -1, false, false)
        return Fire(true, ship, fleet[ship].all { it in done }, fleet.all { s -> s.all { it in done } })
    }

    /** Níveis do computador. */
    enum class Level(val label: String, val key: String) { FACIL("Fácil", "facil"), MEDIO("Médio", "medio"), DIFICIL("Difícil", "dificil") }

    /**
     * Tiro do computador. [shots] = casa → acerto?; [sunk] = navios já afundados; [left] = tamanhos que ainda boiam.
     * Fácil: quase ao acaso. Médio: caça e mira em volta do acerto. Difícil: mapa de probabilidade.
     */
    fun aim(shots: Map<Int, Boolean>, sunk: List<List<Int>>, left: List<Int>, level: Level, rnd: Random = Random.Default): Int {
        val blocked = HashSet<Int>()
        sunk.forEach { s -> s.forEach { x -> blocked += x; if (level != Level.FACIL) blocked += around(x) } }
        val sunkCells = sunk.flatten().toHashSet()
        val open = shots.filter { it.value && it.key !in sunkCells }.keys.toList()
        fun free(i: Int) = i !in shots && i !in blocked
        val all = (0 until N * N).filter(::free)
        if (all.isEmpty()) return (0 until N * N).first { it !in shots }
        fun pick(l: List<Int>) = l[rnd.nextInt(l.size)]
        when (level) {
            Level.FACIL -> {
                val near = open.flatMap(::cross).filter(::free)
                return if (near.isNotEmpty() && rnd.nextDouble() < 0.5) pick(near) else pick(all)
            }
            Level.MEDIO -> {
                if (open.isNotEmpty()) {
                    val line = mutableListOf<Int>()
                    for (a in open) for (b in open) {
                        if (b - a == 1 && a / N == b / N) { if (a % N > 0) line += a - 1; if (b % N < N - 1) line += b + 1 }
                        if (b - a == N) { if (a >= N) line += a - N; if (b + N < N * N) line += b + N }
                    }
                    line.filter(::free).takeIf { it.isNotEmpty() }?.let { return pick(it) }
                    open.flatMap(::cross).filter(::free).takeIf { it.isNotEmpty() }?.let { return pick(it) }
                }
                val min = left.minOrNull() ?: 2
                val par = all.filter { (it / N + it % N) % min == 0 }
                return pick(par.ifEmpty { all })
            }
            Level.DIFICIL -> {
                val heat = IntArray(N * N)
                fun ok(i: Int) = i !in blocked && shots[i] != false
                for (len in left) for (i in 0 until N * N) for (vert in listOf(false, true)) {
                    val cells = span(i, len, vert) ?: continue
                    if (!cells.all(::ok)) continue
                    val hits = cells.count { shots[it] == true && it !in sunkCells }
                    if (open.isNotEmpty() && hits == 0) continue
                    val w = if (open.isNotEmpty()) Math.pow(20.0, hits.toDouble()).toInt() else 1
                    cells.forEach { if (it !in shots) heat[it] += w }
                }
                val best = all.maxOf { heat[it] }
                return pick(all.filter { heat[it] == best })
            }
        }
    }

    // ---------- partida com amigo ----------
    /** Tiro número [n] de [by] na casa [c]; [res] = resposta de quem levou (null enquanto não chegou). */
    data class Shot(val by: String, val c: Int, val res: Msg.Res? = null)

    data class Duel(
        val ready: Set<String>,
        val shots: List<Shot>,
        val oppFleet: List<List<Int>>?,
        val winner: String?,
        val resigned: String?,
    ) {
        val bothReady get() = ready.size >= 2
        val resolved get() = shots.all { it.res != null }
        val over get() = winner != null
    }

    /** Quem atira primeiro: o anfitrião nas rodadas pares (igual ao site). */
    fun firstId(n: Int, hostId: String, guestId: String) = if (n % 2 == 0) hostId else guestId

    /** Estado da rodada a partir das mensagens na ordem do tópico (mesmas regras do site). */
    fun state(msgs: List<Msg>, hostId: String, guestId: String, first: String, myId: String): Duel {
        val ready = HashSet<String>()
        val shots = mutableListOf<Shot>()
        var oppFleet: List<List<Int>>? = null
        var winner: String? = null
        var resigned: String? = null
        val players = setOf(hostId, guestId)
        fun other(id: String) = if (id == hostId) guestId else hostId
        fun shooter(k: Int) = if (k % 2 == 0) first else other(first)
        for (m in msgs) {
            when (m) {
                is Msg.Fleet -> if (m.id != myId && m.id in players && valid(m.fleet)) oppFleet = m.fleet
                is Msg.Resign -> if (winner == null && m.id in players) { resigned = m.id; winner = other(m.id) }
                is Msg.Ready -> if (winner == null && m.id in players) ready += m.id
                is Msg.Shot -> if (winner == null && ready.size >= 2 && m.n == shots.size && shots.all { it.res != null } &&
                    m.id == shooter(m.n) && m.c in 0 until N * N && shots.none { it.by == m.id && it.c == m.c }) shots += Shot(m.id, m.c)
                is Msg.Res -> {
                    val x = shots.getOrNull(m.n)
                    if (winner == null && x != null && x.res == null && m.id == other(x.by) && m.c == x.c) {
                        shots[m.n] = x.copy(res = m)
                        if (m.all) winner = x.by
                    }
                }
                else -> {}
            }
        }
        return Duel(ready, shots, oppFleet, winner, resigned)
    }

    /** Vez de quem no tiro seguinte (null enquanto a rodada não pode andar). */
    fun turn(st: Duel, first: String, hostId: String, guestId: String): String? {
        if (st.over || !st.bothReady || !st.resolved) return null
        return if (st.shots.size % 2 == 0) first else if (first == hostId) guestId else hostId
    }
}
