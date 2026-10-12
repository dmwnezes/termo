package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Níveis do Jogo da Memória: pares e grade (colunas × linhas). */
enum class MemLevel(val key: String, val label: String, val pairs: Int, val cols: Int, val rows: Int) {
    FACIL("facil", "Fácil", 6, 3, 4),
    MEDIO("medio", "Médio", 8, 4, 4),
    DIFICIL("dificil", "Difícil", 12, 4, 6);

    /** Chaves dos recordes: melhor tempo (s) e menos jogadas. */
    val timeKey: String get() = "mem_best_$key"
    val movesKey: String get() = "mem_best_${key}_moves"
}

/** Uma carta: a palavra e o número do par (as duas cartas do par têm o mesmo [pair]). */
data class MemCard(val word: String, val pair: Int)

object MemoryDeck {
    /** Palavras longas demais não cabem na carta. */
    private const val MAX_LEN = 11

    /**
     * Sorteia [n] pares de sinônimos sem sobreposição de família: nenhuma palavra de um par é
     * da mesma família (ou igual) a uma palavra de outro par, então cada carta tem um único par.
     */
    fun pick(pairs: List<SynPair>, families: List<Set<String>>, n: Int, rnd: Random): List<SynPair> {
        fun key(w: String) = Words.normalize(w)
        val famOf = HashMap<String, MutableSet<Int>>()
        families.forEachIndexed { i, f -> f.forEach { famOf.getOrPut(key(it)) { HashSet() } += i } }
        fun group(p: SynPair): Set<String> {
            val words = setOf(key(p.word), key(p.synonym))
            val fams = words.flatMap { famOf[it].orEmpty() }.toSet()
            return words + fams.map { "#$it" }
        }
        val taken = HashSet<String>()
        val out = ArrayList<SynPair>()
        for (p in pairs.shuffled(rnd)) {
            if (out.size == n) break
            if (p.word.length > MAX_LEN || p.synonym.length > MAX_LEN || key(p.word) == key(p.synonym)) continue
            val g = group(p)
            if (g.any { it in taken }) continue
            taken += g
            out += p
        }
        return out
    }

    /** Embaralha as cartas (2 por par). */
    fun deal(pairs: List<SynPair>, rnd: Random): List<MemCard> =
        pairs.flatMapIndexed { i, p -> listOf(MemCard(p.word, i), MemCard(p.synonym, i)) }.shuffled(rnd)
}

/**
 * Jogo da Memória: cartas viradas para baixo; ache cada palavra e o seu sinônimo.
 * Duas cartas viradas contam uma jogada; se não forem par, ficam à mostra até [hideMismatch].
 */
class MemoryGame(val level: MemLevel, val cards: List<MemCard>) {
    constructor(level: MemLevel, pairs: List<SynPair>, families: List<Set<String>>, rnd: Random = Random.Default) :
        this(level, MemoryDeck.deal(MemoryDeck.pick(pairs, families, level.pairs, rnd), rnd))

    enum class Flip { IGNORED, FIRST, MATCH, MISS }

    /** Cartas viradas para cima que ainda não formaram par (no máximo 2). */
    val faceUp = mutableStateListOf<Int>()
    val matched = mutableStateListOf<Int>()
    var moves by mutableIntStateOf(0)
        private set
    /** Duas cartas erradas à mostra: espera [hideMismatch]. */
    var locked by mutableStateOf(false)
        private set

    val won: Boolean get() = cards.isNotEmpty() && matched.size == cards.size
    val started: Boolean get() = moves > 0 || faceUp.isNotEmpty()

    fun isUp(i: Int) = i in faceUp || i in matched

    fun flip(i: Int): Flip {
        if (locked || i !in cards.indices || isUp(i)) return Flip.IGNORED
        faceUp += i
        if (faceUp.size == 1) return Flip.FIRST
        moves++
        val (a, b) = faceUp[0] to faceUp[1]
        return if (cards[a].pair == cards[b].pair) {
            matched += a; matched += b; faceUp.clear(); Flip.MATCH
        } else {
            locked = true; Flip.MISS
        }
    }

    /** Vira de volta as duas cartas erradas. */
    fun hideMismatch() {
        if (!locked) return
        faceUp.clear()
        locked = false
    }
}
