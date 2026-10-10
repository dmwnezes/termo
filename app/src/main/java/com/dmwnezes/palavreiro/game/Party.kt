package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/** Rodada do Intruso: 4 palavras de um grupo + 1 intrusa, embaralhadas. */
data class IntruderRound(val group: ConnGroup, val intruder: String, val options: List<String>)

/**
 * Intruso: ache a palavra que não pertence ao grupo. 3 vidas; cada acerto vale 1 ponto.
 * Usa os grupos do Conexões; a intrusa vem de um grupo sem nenhum assunto em comum.
 */
class IntruderGame(puzzles: List<ConnPuzzle>, private val families: Map<String, Set<String>>, private val rnd: Random = Random.Default) {
    private val banned = setOf("lacuna", "palavra", "fora")
    private val groups = puzzles.flatMap { it.groups }
        .filter { g -> g.words.size == 4 && families[g.name]?.let { f -> f.isNotEmpty() && f.none { it in banned } } == true }
    private val used = HashSet<String>()

    var round by mutableStateOf(nextRound())
        private set
    var score by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(3)
        private set
    /** Palavra tocada nesta rodada (null = ainda não respondeu). */
    var picked by mutableStateOf<String?>(null)
        private set
    val over: Boolean get() = lives <= 0

    private fun nextRound(): IntruderRound {
        if (used.size >= groups.size) used.clear()
        val base = groups.filter { it.name !in used }.random(rnd)
        used += base.name
        val fam = families.getValue(base.name)
        val inBase = base.words.map { Words.normalize(it) }.toSet()
        val others = groups.filter { o -> o.name != base.name && families.getValue(o.name).none { it in fam } }
        val intruder = others.flatMap { it.words }.filter { Words.normalize(it) !in inBase }.random(rnd)
        return IntruderRound(base, intruder, (base.words + intruder).shuffled(rnd))
    }

    /** Responde a rodada. Devolve true se acertou a intrusa. */
    fun pick(word: String): Boolean {
        if (picked != null || over) return false
        picked = word
        val right = word == round.intruder
        if (right) score++ else lives--
        return right
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

/** Gráfico de evolução: média semanal de um campo do histórico ("termo" ou "conn"). */
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
