package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/** Rodada do Intruso: 3 ou 4 palavras de um grupo + 1 intrusa, embaralhadas. */
data class IntruderRound(val group: ConnGroup, val intruder: String, val options: List<String>)

/**
 * Intruso: ache a palavra que não pertence ao grupo, antes do tempo acabar. 3 vidas; cada acerto vale 1 ponto.
 * Usa os grupos do Conexões. A intrusa nunca vem de um grupo com assunto (família) em comum com o grupo
 * da rodada — assim ela é sempre inequívoca. A dificuldade sobe com os pontos (mesmas regras do site):
 *  - até 4 pontos: 3 palavras do grupo + a intrusa (4 opções); a partir de 5 pontos: 4 + a intrusa (5 opções);
 *  - a partir de 3 pontos a intrusa vem de um assunto "vizinho" (a 1ª família de cada grupo forma um par de
 *    [NEAR], como animal e natureza), quando houver; antes disso, de qualquer assunto diferente;
 *  - o tempo da rodada é max(4, 12 − 0,3 × pontos) segundos; deixar acabar custa uma vida.
 */
class IntruderGame(puzzles: List<ConnPuzzle>, private val families: Map<String, Set<String>>, private val rnd: Random = Random.Default) {
    private val banned = setOf("lacuna", "palavra", "fora")
    private val groups = puzzles.flatMap { it.groups }
        .filter { g -> g.words.size == 4 && families[g.name]?.let { f -> f.isNotEmpty() && f.none { it in banned } } == true }
        .distinctBy { it.name }
    private val used = HashSet<String>()

    var score by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(3)
        private set
    /** Palavra tocada nesta rodada (null = ainda não respondeu; [TIME_UP] = o tempo acabou). */
    var picked by mutableStateOf<String?>(null)
        private set
    var round by mutableStateOf(nextRound())
        private set
    val over: Boolean get() = lives <= 0
    /** A rodada acabou porque o tempo esgotou. */
    val timedOut: Boolean get() = picked == TIME_UP
    /** Segundos da rodada atual. */
    val seconds: Double get() = secondsFor(score)

    /** O grupo [other] é de um assunto vizinho ao de [base]: sem família em comum, e as famílias principais (1ª da lista) formam um par de [NEAR]. */
    fun isNear(base: String, other: String): Boolean {
        val fb = families.getValue(base); val fo = families.getValue(other)
        return fb.none { it in fo } && "${fb.first()}-${fo.first()}" in NEAR
    }

    private fun nextRound(): IntruderRound {
        if (used.size >= groups.size) used.clear()
        val fresh = groups.filter { it.name !in used }
        repeat(100) {
            val base = fresh.random(rnd)
            val fam = families.getValue(base.name)
            val far = groups.filter { o -> o.name != base.name && families.getValue(o.name).none { it in fam } }
            // Palavras que também aparecem num grupo de assunto em comum (inclusive o próprio) nunca são intrusas.
            val inBase = (groups - far.toSet()).flatMap { it.words }.map { Words.normalize(it) }.toSet()
            fun words(gs: List<ConnGroup>) = gs.flatMap { it.words }.filter { Words.normalize(it) !in inBase }
            val close = if (score >= NEAR_FROM) words(far.filter { isNear(base.name, it.name) }) else emptyList()
            val cands = close.ifEmpty { words(far) }
            if (cands.isEmpty()) return@repeat
            used += base.name
            val intruder = cands.random(rnd)
            val shown = base.words.shuffled(rnd).take(optionsFor(score) - 1)
            return IntruderRound(base, intruder, (shown + intruder).shuffled(rnd))
        }
        error("Intruso sem grupos válidos")
    }

    /** Responde a rodada. Devolve true se acertou a intrusa. */
    fun pick(word: String): Boolean {
        if (picked != null || over) return false
        picked = word
        val right = word == round.intruder
        if (right) score++ else lives--
        return right
    }

    /** O tempo acabou sem resposta: perde uma vida. */
    fun timeUp() {
        if (picked != null || over) return
        picked = TIME_UP
        lives--
    }

    companion object {
        const val TIME_UP = ""
        /** Assuntos vizinhos (vale nos dois sentidos); a mesma lista está no js/intruso.js. */
        val NEAR: Set<String> = """animal-natureza comida-natureza comida-casa comida-festa casa-escola casa-tecnologia roupa-corpo roupa-esporte roupa-artesanato corpo-saude esporte-jogo esporte-praia jogo-festa musica-festa musica-arte arte-artesanato ceu-natureza ceu-tempo ceu-mito tempo-festa tempo-medida lugar-transporte lugar-praia transporte-tecnologia escola-trabalho escola-jogo trabalho-saude trabalho-dinheiro mineral-natureza mineral-cor cor-arte forma-escola forma-medida sentimento-familia mito-signo""".split(' ').flatMap { p -> listOf(p, p.split('-').reversed().joinToString("-")) }.toSet()
        /** A partir destes pontos a intrusa vem de um assunto vizinho. */
        const val NEAR_FROM = 3
        /** A partir destes pontos a rodada tem 5 opções (4 do grupo + a intrusa). */
        const val FIVE_FROM = 5
        fun optionsFor(score: Int): Int = if (score >= FIVE_FROM) 5 else 4
        fun secondsFor(score: Int): Double = maxOf(4.0, 12.0 - 0.3 * score)
    }

    fun next() {
        if (over) return
        picked = null
        round = nextRound()
    }
}

/** Par de grafias: a certa e a errada. */
data class SpellPair(val right: String, val wrong: String)

object SpellingData {
    fun parse(text: String): List<SpellPair> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
        .map { SpellPair(it.substringBefore('|').trim(), it.substringAfter('|').trim()) }
}

/** Certo ou Errado: mostra uma palavra (certa ou errada) e você diz se está escrita certo. 3 vidas. */
class SpellingGame(private val pairs: List<SpellPair>, private val rnd: Random = Random.Default) {
    private var queue = pairs.shuffled(rnd).toMutableList()

    var pair by mutableStateOf(take())
        private set
    /** A palavra mostrada é a forma certa? */
    var showsRight by mutableStateOf(rnd.nextBoolean())
        private set
    var score by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(3)
        private set
    /** null = ainda não respondeu; true/false = acertou ou errou a rodada. */
    var answered by mutableStateOf<Boolean?>(null)
        private set
    val over: Boolean get() = lives <= 0
    val shown: String get() = if (showsRight) pair.right else pair.wrong

    private fun take(): SpellPair {
        if (queue.isEmpty()) queue = pairs.shuffled(rnd).toMutableList()
        return queue.removeAt(0)
    }

    /** O jogador diz se a palavra está certa ([saysRight]). Devolve true se acertou. */
    fun answer(saysRight: Boolean): Boolean {
        if (answered != null || over) return false
        val ok = saysRight == showsRight
        answered = ok
        if (ok) score++ else lives--
        return ok
    }

    fun next() {
        if (over) return
        answered = null
        pair = take()
        showsRight = rnd.nextBoolean()
    }
}

/** Gráfico de evolução: média semanal de um campo do histórico ("termo", "conn" ou "caca"). */
object Evolution {
    /** Últimas [count] semanas (segunda a domingo), da mais antiga para a atual. Valor null = sem dados. */
    fun weeks(history: JSONObject, field: String, today: LocalDate = LocalDate.now(), count: Int = 8): List<Pair<LocalDate, Double?>> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (count - 1 downTo 0).map { back ->
            val start = monday.minusWeeks(back.toLong())
            val values = (0 until 7).mapNotNull { d ->
                history.optJSONObject(start.plusDays(d.toLong()).toString())?.takeIf { it.has(field) }?.optInt(field)
            }
            start to values.takeIf { it.isNotEmpty() }?.average()
        }
    }
}

/** "Seus chutes": letras mais usadas no 1º chute, chute favorito e melhor chute inicial. */
data class FirstGuessStats(
    val letters: List<Pair<Char, Int>>,
    val favorite: Pair<String, Int>?,
    /** Melhor chute inicial (usado ≥ 3 vezes): palavra e média de tentativas. */
    val best: Pair<String, Double>?,
    val total: Int,
) {
    companion object {
        fun from(list: List<Pair<String, Int>>): FirstGuessStats {
            val words = list.map { Words.normalize(it.first) to it.second }
            val letters = words.flatMap { it.first.toList() }.filter { it in 'A'..'Z' }
                .groupingBy { it }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).take(8).map { it.key to it.value }
            val byWord = words.groupBy { it.first }
            val favorite = byWord.entries.sortedWith(compareBy({ -it.value.size }, { it.key })).firstOrNull()?.let { it.key to it.value.size }
            val best = byWord.filter { it.value.size >= 3 }.map { (w, l) -> w to l.map { it.second }.average() }
                .sortedWith(compareBy({ it.second }, { it.first })).firstOrNull()
            return FirstGuessStats(letters, favorite, best, words.size)
        }
    }
}
