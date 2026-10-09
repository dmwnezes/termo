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
    fun onBoardSolved(board: Int) {}
    fun onWin(tries: Int) {}
    fun onLose(answers: List<String>) {}
}

/**
 * Partida de Termo com 1, 2 ou 4 palavras ao mesmo tempo (Termo, Dueto, Quarteto).
 * Cada tentativa vale para todos os tabuleiros que ainda não foram resolvidos.
 * Cada letra pode ser colocada em qualquer posição da linha atual.
 */
class TermoGame(
    val mode: Mode,
    private val words: Words,
    private val store: Store?,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    val boards: Int get() = mode.boards
    val maxTries: Int get() = mode.maxTries

    val answers = mutableStateListOf<String>()
    /** Palavras enviadas, na ordem. */
    val rows = mutableStateListOf<String>()
    val current = mutableStateListOf<Char?>().apply { repeat(Words.WORD_LENGTH) { add(null) } }
    var cursor by mutableIntStateOf(0)
        private set
    var over by mutableStateOf(false)
        private set
    var won by mutableStateOf(false)
        private set
    /** Partida do dia (true) ou livre, com palavras sorteadas (false). */
    var isDaily by mutableStateOf(mode.daily)
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
    val currentRow: Int get() = rows.size

    /** Compatibilidade: a resposta do primeiro tabuleiro. */
    val answer: String get() = answers.firstOrNull().orEmpty()

    /** Tentativas do primeiro tabuleiro (Termo simples). */
    val guesses: List<Guess> get() = boardGuesses(0)

    init {
        load()
    }

    private fun todayKey() = today().toString()
    private val salt: Int get() = when (mode) { Mode.DUETO -> 1; Mode.QUARTETO -> 2; else -> 0 }

    private fun load() {
        val saved = store?.game(mode)
        if (mode.daily) {
            val dayKey = todayKey()
            val daily = words.dailySet(today(), boards, salt)
            when {
                saved != null && saved.key == dayKey && saved.answers == daily -> { isDaily = true; key = dayKey; restore(daily, saved) }
                // Partida livre em andamento, mas o desafio do dia de hoje ainda não foi jogado: volta para o do dia.
                saved != null && saved.key.startsWith("livre") && saved.answers.size == boards && store?.text("daily_done_${mode.key}") == dayKey ->
                    { isDaily = false; key = saved.key; restore(saved.answers, saved) }
                else -> { isDaily = true; key = dayKey; restore(daily, null) }
            }
        } else {
            val ok = saved?.takeIf { s -> s.answers.size == boards && s.answers.all { it in words.answers } }
            isDaily = false
            key = "livre"
            restore(ok?.answers ?: words.randomSet(boards), ok)
        }
    }

    private fun restore(list: List<String>, saved: SavedGame?) {
        answers.clear(); answers.addAll(list)
        rows.clear()
        saved?.words?.let { rows.addAll(it) }
        over = saved?.over ?: false
        won = saved?.won ?: false
        revealingRow = -1
        clearRow()
    }

    private fun clearRow() {
        for (i in current.indices) current[i] = null
        cursor = 0
    }

    private fun save() {
        store?.saveGame(mode, SavedGame(key, answers.toList(), rows.toList(), over, won))
    }

    /** O dia virou enquanto o app estava aberto? */
    fun refreshIfNewDay() {
        if (mode.daily && isDaily && key != todayKey()) load()
    }

    // ---------- leitura dos tabuleiros ----------

    /** Linha em que o tabuleiro foi resolvido, ou -1. */
    fun solvedAt(board: Int): Int = rows.indexOf(answers[board])

    fun isSolved(board: Int, upToRow: Int = rows.size): Boolean {
        val at = solvedAt(board)
        return at in 0 until upToRow
    }

    /** Tentativas que aparecem no tabuleiro (para de mostrar depois de resolvido). */
    fun boardGuesses(board: Int): List<Guess> {
        if (board >= answers.size) return emptyList()
        val ans = answers[board]
        val at = solvedAt(board)
        val last = if (at >= 0) at else rows.size - 1
        return (0..last).filter { it < rows.size }.map { r -> Guess(rows[r], words.display(rows[r]), Rules.evaluate(rows[r], ans)) }
    }

    /** Linhas já reveladas (exclui a que está virando). */
    val revealedRows: Int get() = if (revealingRow >= 0) revealingRow else rows.size

    // ---------- ações ----------

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
        rows += word
        revealingRow = rows.size - 1
        clearRow()
    }

    private fun invalid(msg: String) {
        shakeTick++
        events.onInvalid(msg)
    }

    fun finishReveal() {
        if (revealingRow < 0) return
        val row = revealingRow
        revealingRow = -1
        for (b in answers.indices) if (solvedAt(b) == row && boards > 1) events.onBoardSolved(b)
        when {
            answers.indices.all { isSolved(it) } -> end(true)
            rows.size >= maxTries -> end(false)
        }
        save()
    }

    private fun end(win: Boolean) {
        over = true
        won = win
        record(win, rows.size)
        if (isDaily && mode.daily) store?.setText("daily_done_${mode.key}", todayKey())
        if (win) events.onWin(rows.size) else events.onLose(answers.filter { !isSolved(answers.indexOf(it)) }.map(words::display))
    }

    private fun record(win: Boolean, tries: Int) {
        val st = store ?: return
        val s = st.stats(mode)
        val today = Words.dayIndex(today())
        val streak = when {
            !win -> 0
            mode == Mode.INFINITO -> s.streak + 1
            isDaily -> if (s.lastWinDay == today - 1) s.streak + 1 else 1
            else -> s.streak // partidas livres não mexem na sequência do dia
        }
        val updated = if (win) {
            s.copy(
                played = s.played + 1,
                won = s.won + 1,
                streak = streak,
                maxStreak = maxOf(s.maxStreak, streak),
                lastWinDay = if (isDaily || mode == Mode.INFINITO) today else s.lastWinDay,
                firstTry = s.firstTry + if (tries == 1) 1 else 0,
                dist = s.dist.mapIndexed { i, v -> if (i == tries - 1) v + 1 else v },
            )
        } else {
            s.copy(played = s.played + 1, streak = if (isDaily || mode == Mode.INFINITO) 0 else s.streak)
        }
        st.saveStats(mode, updated)
    }

    fun stats(): Stats = store?.stats(mode) ?: Stats()

    /** Começa uma partida livre com palavras sorteadas (Infinito, e Dueto/Quarteto depois do desafio do dia). */
    fun newWord() {
        if (!mode.free || busy) return
        isDaily = false
        key = "livre-${System.currentTimeMillis()}"
        val avoid = answers.toSet()
        var next: List<String>
        do { next = words.randomSet(boards) } while (next.any { it in avoid } && words.answers.size > boards * 2)
        restore(next, null)
        save()
    }

    fun answerDisplay(): String = answers.joinToString(", ") { words.display(it) }
}
