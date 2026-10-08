package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.SavedGame
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.data.Store
import java.time.LocalDate

/** Avisos do jogo para a tela tocar som, vibrar e mostrar mensagens. */
interface GameEvents {
    fun onType() {}
    fun onInvalid(message: String) {}
    fun onRevealTile(index: Int, mark: Mark) {}
    fun onWin(tries: Int) {}
    fun onLose(answer: String) {}
}

/**
 * Estado de uma partida de Termo (uma palavra, 6 tentativas).
 * Cada letra pode ser colocada em qualquer posição da linha atual.
 */
class TermoGame(
    val mode: Mode,
    private val words: Words,
    private val store: Store?,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    var answer by mutableStateOf("")
        private set
    val guesses = mutableStateListOf<Guess>()
    val current = mutableStateListOf<Char?>().apply { repeat(Words.WORD_LENGTH) { add(null) } }
    var cursor by mutableIntStateOf(0)
        private set
    var over by mutableStateOf(false)
        private set
    var won by mutableStateOf(false)
        private set

    /** Linha que está sendo revelada (animação), ou -1. */
    var revealingRow by mutableIntStateOf(-1)
        private set

    /** Muda a cada tentativa inválida, para a linha tremer. */
    var shakeTick by mutableIntStateOf(0)
        private set

    private var key = ""
    var events: GameEvents = object : GameEvents {}

    val busy: Boolean get() = revealingRow >= 0
    val currentRow: Int get() = guesses.size

    init {
        load()
    }

    private fun todayKey() = today().toString()

    private fun load() {
        val saved = store?.game(mode)
        when (mode) {
            Mode.DIARIO -> {
                key = todayKey()
                answer = words.daily(today())
                restore(saved?.takeIf { it.key == key && it.answer == answer })
            }
            Mode.INFINITO -> {
                key = "infinito"
                val ok = saved?.takeIf { it.answer in words.answers }
                answer = ok?.answer ?: words.random()
                restore(ok)
            }
        }
    }

    private fun restore(saved: SavedGame?) {
        guesses.clear()
        saved?.words?.forEach { w -> guesses += Guess(w, words.display(w), Rules.evaluate(w, answer)) }
        over = saved?.over ?: false
        won = saved?.won ?: false
        clearRow()
    }

    private fun clearRow() {
        for (i in current.indices) current[i] = null
        cursor = 0
    }

    private fun save() {
        store?.saveGame(mode, SavedGame(key, answer, guesses.map { it.word }, over, won))
    }

    /** O Diário virou o dia enquanto o app estava aberto? */
    fun refreshIfNewDay() {
        if (mode == Mode.DIARIO && key != todayKey()) load()
    }

    fun type(letter: Char) {
        if (over || busy) return
        current[cursor] = letter.uppercaseChar()
        val next = nextEmpty(cursor + 1)
        cursor = if (next == -1) minOf(cursor + 1, Words.WORD_LENGTH - 1) else next
        events.onType()
    }

    private fun nextEmpty(from: Int): Int {
        for (i in from until Words.WORD_LENGTH) if (current[i] == null) return i
        for (i in 0 until minOf(from, Words.WORD_LENGTH)) if (current[i] == null) return i
        return -1
    }

    /** Apaga a letra do quadrado selecionado; se ele estiver vazio, apaga a anterior. */
    fun delete() {
        if (over || busy) return
        if (current[cursor] != null) {
            current[cursor] = null
        } else if (cursor > 0) {
            cursor--
            current[cursor] = null
        }
        events.onType()
    }

    fun select(index: Int) {
        if (over || busy) return
        cursor = index.coerceIn(0, Words.WORD_LENGTH - 1)
    }

    /** Envia a tentativa. A revelação termina em [finishReveal], chamada pela tela após a animação. */
    fun submit() {
        if (over || busy) return
        if (current.any { it == null }) return invalid("Palavra incompleta")
        val word = current.joinToString("") { it.toString() }
        if (!words.isAccepted(word)) return invalid("Palavra não aceita")

        val marks = Rules.evaluate(word, answer)
        guesses += Guess(word, words.display(word), marks)
        revealingRow = guesses.size - 1
        clearRow()
    }

    private fun invalid(msg: String) {
        shakeTick++
        events.onInvalid(msg)
    }

    fun finishReveal() {
        if (revealingRow < 0) return
        revealingRow = -1
        val last = guesses.last()
        when {
            last.solved -> end(true)
            guesses.size >= Rules.MAX_TRIES -> end(false)
        }
        save()
    }

    private fun end(win: Boolean) {
        over = true
        won = win
        record(win, guesses.size)
        if (win) events.onWin(guesses.size) else events.onLose(words.display(answer))
    }

    private fun record(win: Boolean, tries: Int) {
        val st = store ?: return
        val s = st.stats(mode)
        val today = Words.dayIndex(today())
        val updated = if (win) {
            val streak = when (mode) {
                Mode.DIARIO -> if (s.lastWinDay == today - 1) s.streak + 1 else 1
                Mode.INFINITO -> s.streak + 1
            }
            s.copy(
                played = s.played + 1,
                won = s.won + 1,
                streak = streak,
                maxStreak = maxOf(s.maxStreak, streak),
                lastWinDay = today,
                firstTry = s.firstTry + if (tries == 1) 1 else 0,
                dist = s.dist.mapIndexed { i, v -> if (i == tries - 1) v + 1 else v },
            )
        } else {
            s.copy(played = s.played + 1, streak = 0)
        }
        st.saveStats(mode, updated)
    }

    fun stats(): Stats = store?.stats(mode) ?: Stats()

    /** Só no Infinito: começa uma palavra nova. */
    fun newWord() {
        if (mode != Mode.INFINITO || busy) return
        answer = words.random(avoid = answer)
        restore(null)
        save()
    }

    fun answerDisplay(): String = words.display(answer)
}
