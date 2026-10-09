package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

data class Definition(val word: String, val text: String)

data class SynPair(val word: String, val synonym: String)

object QuizData {
    fun definitions(text: String): List<Definition> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
        .map { Definition(it.substringBefore('|').trim(), it.substringAfter('|').trim()) }

    /** Significados: PALAVRA|texto, indexado pela palavra sem acento. */
    fun meanings(text: String): Map<String, String> = text.lines()
        .filter { '|' in it }
        .associate { Words.normalize(it.substringBefore('|').trim()) to it.substringAfter('|').trim() }

    /** Pares e famílias de sentido (linhas com =). */
    fun synonyms(text: String): Pair<List<SynPair>, List<Set<String>>> {
        val pairs = ArrayList<SynPair>()
        val families = ArrayList<Set<String>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#") -> {}
                line.startsWith("=") -> families += line.drop(1).split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                '|' in line -> pairs += SynPair(line.substringBefore('|').trim(), line.substringAfter('|').trim())
            }
        }
        return pairs to families
    }
}

/**
 * Qual é a Palavra?: aparece uma definição e você adivinha a palavra.
 * 3 chances por palavra; cada erro revela uma letra. Acertos seguidos formam a sequência.
 */
class DefineGame(all: List<Definition>, seed: Long = System.nanoTime()) {
    private val rnd = Random(seed)
    private val deck = all.shuffled(rnd).toMutableList()
    private var index = 0

    var item by mutableStateOf(deck[0])
        private set
    /** Letras da resposta sem acento, com hífens preservados. */
    val target: String get() = Words.normalize(item.word)
    val slots: List<Int> get() = target.indices.filter { target[it] in 'A'..'Z' }

    val revealed = mutableStateListOf<Int>()
    val typed = mutableStateListOf<Char>()
    var misses by mutableIntStateOf(0)
        private set
    var streak by mutableIntStateOf(0)
        private set
    var points by mutableIntStateOf(0)
        private set
    /** Estado da palavra atual: jogando, acertou ou errou de vez. */
    var state by mutableStateOf(State.PLAYING)
        private set

    enum class State { PLAYING, RIGHT, WRONG }

    val maxMisses = 3
    private val open: List<Int> get() = slots.filter { it !in revealed }

    /** Letra que aparece em cada posição (revelada ou digitada). */
    fun letterAt(pos: Int): Char? {
        if (target[pos] !in 'A'..'Z') return target[pos]
        if (pos in revealed || state != State.PLAYING) return target[pos]
        val k = open.indexOf(pos)
        return typed.getOrNull(k)
    }

    fun type(c: Char) {
        if (state != State.PLAYING || typed.size >= open.size) return
        typed += c.uppercaseChar()
    }

    fun delete() {
        if (state == State.PLAYING && typed.isNotEmpty()) typed.removeAt(typed.lastIndex)
    }

    /** Envia; devolve true se acertou. */
    fun submit(): Boolean? {
        if (state != State.PLAYING || typed.size < open.size) return null
        val guess = CharArray(target.length) { target[it] }
        open.forEachIndexed { k, pos -> guess[pos] = typed[k] }
        if (String(guess) == target) {
            state = State.RIGHT
            streak++
            points += maxMisses - misses
            return true
        }
        misses++
        typed.clear()
        if (misses >= maxMisses || open.size <= 1) {
            state = State.WRONG
            streak = 0
        } else {
            revealed += open[rnd.nextInt(open.size)]
        }
        return false
    }

    /** Desiste da palavra atual (conta como erro). */
    fun giveUp() {
        if (state != State.PLAYING) return
        state = State.WRONG
        streak = 0
    }

    fun next() {
        index++
        if (index >= deck.size) { deck.shuffle(rnd); index = 0 }
        item = deck[index]
        revealed.clear(); typed.clear(); misses = 0
        state = State.PLAYING
        // Palavras longas já começam com uma letra de dica.
        if (slots.size >= 9) revealed += slots[rnd.nextInt(slots.size)]
    }

    init {
        if (slots.size >= 9) revealed += slots[rnd.nextInt(slots.size)]
    }
}

/**
 * Sinônimos em Cadeia: escolha o sinônimo certo entre 4 opções, com tempo.
 * A cadeia continua até errar ou o tempo acabar.
 */
class SynonymGame(
    private val pairs: List<SynPair>,
    private val families: List<Set<String>>,
    seed: Long = System.nanoTime(),
    val secondsPerQuestion: Int = 10,
) {
    private val rnd = Random(seed)
    private val deck = pairs.shuffled(rnd).toMutableList()
    private var index = 0

    var question by mutableStateOf(deck[0])
        private set
    val options = mutableStateListOf<String>()
    var chain by mutableIntStateOf(0)
        private set
    var over by mutableStateOf(false)
        private set
    /** Opção escolhida na pergunta atual (para pintar certo/errado). */
    var picked by mutableStateOf<String?>(null)
        private set

    init {
        buildOptions()
    }

    private fun familyOf(w: String): Set<String> = families.filter { w in it }.flatten().toSet() + w

    private fun buildOptions() {
        val q = question
        val blocked = familyOf(q.word) + familyOf(q.synonym)
        val pool = pairs.map { it.synonym }.distinct().filter { it !in blocked && familyOf(it).none { f -> f in blocked } }
        val wrong = pool.shuffled(rnd).take(3)
        options.clear()
        options.addAll((wrong + q.synonym).shuffled(rnd))
        picked = null
    }

    /** Responde; devolve true se acertou. */
    fun answer(option: String): Boolean {
        if (over || picked != null) return false
        picked = option
        return if (option == question.synonym) { chain++; true } else { over = true; false }
    }

    fun timeUp() {
        if (over || picked != null) return
        over = true
    }

    fun next() {
        if (over) return
        index++
        if (index >= deck.size) { deck.shuffle(rnd); index = 0 }
        question = deck[index]
        buildOptions()
    }
}
