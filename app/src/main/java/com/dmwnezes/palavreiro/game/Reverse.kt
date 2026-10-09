package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Reverso: você pensa numa palavra de 5 letras e o app tenta adivinhar.
 * A cada chute você marca as cores (verde, amarelo, cinza) e o app filtra as palavras possíveis.
 */
class ReverseGame(private val words: Words, val maxTries: Int = 6) {
    /** Chutes já respondidos, com as cores que você marcou. */
    val history = mutableStateListOf<Guess>()
    /** Chute atual e as cores que você está marcando nele. */
    var guess by mutableStateOf("")
        private set
    val marks = mutableStateListOf<Mark>().apply { repeat(Words.WORD_LENGTH) { add(Mark.ABSENT) } }
    var over by mutableStateOf(false)
        private set
    var appWon by mutableStateOf(false)
        private set
    /** As cores marcadas não batem com nenhuma palavra que o app conhece. */
    var contradiction by mutableStateOf(false)
        private set

    private var common: List<String> = words.answers
    private var rare: List<String> = words.all.filter { it !in words.answers.toSet() }

    val remaining: Int get() = common.size + rare.size

    init {
        guess = pick()
    }

    /** Toque num quadrado: cinza → amarelo → verde → cinza. */
    fun cycle(i: Int) {
        if (over) return
        marks[i] = when (marks[i]) { Mark.ABSENT -> Mark.PRESENT; Mark.PRESENT -> Mark.CORRECT; Mark.CORRECT -> Mark.ABSENT }
    }

    fun setMark(i: Int, m: Mark) { if (!over) marks[i] = m }

    /** Envia as cores do chute atual. */
    fun answer() {
        if (over || guess.isEmpty()) return
        val fb = marks.toList()
        history += Guess(guess, words.display(guess), fb)
        if (fb.all { it == Mark.CORRECT }) { over = true; appWon = true; return }
        common = common.filter { it != guess && Rules.evaluate(guess, it) == fb }
        rare = rare.filter { it != guess && Rules.evaluate(guess, it) == fb }
        if (history.size >= maxTries) { over = true; appWon = false; return }
        if (remaining == 0) { contradiction = true; over = true; appWon = false; return }
        guess = pick()
        // Mantém os verdes já conhecidos marcados para facilitar.
        for (i in 0 until Words.WORD_LENGTH) marks[i] = if (fb[i] == Mark.CORRECT && guess[i] == history.last().word[i]) Mark.CORRECT else Mark.ABSENT
    }

    /** Desfaz a última resposta (para corrigir uma cor marcada errado). */
    fun undo() {
        if (history.isEmpty()) return
        history.removeAt(history.lastIndex)
        rebuild()
    }

    private fun rebuild() {
        common = words.answers
        rare = words.all.filter { it !in words.answers.toSet() }
        for (h in history) {
            common = common.filter { it != h.word && Rules.evaluate(h.word, it) == h.marks }
            rare = rare.filter { it != h.word && Rules.evaluate(h.word, it) == h.marks }
        }
        over = false; appWon = false; contradiction = false
        guess = pick()
        for (i in 0 until Words.WORD_LENGTH) marks[i] = Mark.ABSENT
    }

    /**
     * Escolhe o próximo chute: entre as palavras possíveis (de preferência as comuns),
     * a que cobre as letras mais frequentes nas palavras que sobraram.
     */
    private fun pick(): String {
        val pool = common.ifEmpty { rare }
        if (pool.isEmpty()) return ""
        if (pool.size <= 2) return pool.first()
        val freq = IntArray(26)
        for (w in pool) for (c in w.toSet()) freq[c - 'A']++
        return pool.maxBy { w -> w.toSet().sumOf { freq[it - 'A'] } }
    }
}
