package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Níveis do Sudoku (mesmos nomes de shared/sudoku.txt). */
enum class SudokuLevel(val key: String, val label: String) {
    FACIL("facil", "Fácil"), MEDIO("medio", "Médio"), DIFICIL("dificil", "Difícil");

    /** Recorde: melhor tempo (s), menor é melhor. */
    val bestKey: String get() = "sudoku_best_$key"
}

/** Um Sudoku: 81 dígitos (0 = vazio) e a solução. */
data class SudokuPuzzle(val level: SudokuLevel, val puzzle: String, val solution: String)

object SudokuData {
    /** Lê "NIVEL|puzzle|solução" (linhas com # são comentário). */
    fun parse(text: String): Map<SudokuLevel, List<SudokuPuzzle>> = text.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val p = line.split('|')
            val lvl = SudokuLevel.entries.firstOrNull { it.key == p.getOrNull(0) } ?: return@mapNotNull null
            if (p.size < 3 || p[1].length != 81 || p[2].length != 81) null else SudokuPuzzle(lvl, p[1], p[2])
        }
        .groupBy { it.level }

    /** Sudoku do dia do nível: índice = dia mod tamanho da lista (366). */
    fun daily(list: List<SudokuPuzzle>, day: Long): SudokuPuzzle = list[Math.floorMod(day, list.size.toLong()).toInt()]
}

/**
 * Partida de Sudoku: números do jogador, anotações (bits 1..9) e erros. Número errado fica em vermelho e conta erro;
 * 3 erros = fim de jogo.
 */
class SudokuGame(val puzzle: SudokuPuzzle) {
    val values = mutableStateListOf<Int>().apply { puzzle.puzzle.forEach { add(it - '0') } }
    val notes = mutableStateListOf<Int>().apply { repeat(81) { add(0) } }
    var errors by mutableIntStateOf(0)
    var selected by mutableIntStateOf(-1)
    var notesMode by mutableStateOf(false)
    var seconds by mutableIntStateOf(0)

    fun given(i: Int) = puzzle.puzzle[i] != '0'
    fun wrong(i: Int) = values[i] != 0 && values[i] != puzzle.solution[i] - '0'
    val won: Boolean get() = (0 until 81).all { values[it] == puzzle.solution[it] - '0' }
    val lost: Boolean get() = errors >= MAX_ERRORS
    val over: Boolean get() = won || lost
    val started: Boolean get() = (0 until 81).any { !given(it) && values[it] != 0 } || notes.any { it != 0 }

    enum class Input { IGNORED, NOTE, RIGHT, WRONG }

    /** Coloca [d] (1..9) na casa escolhida (ou anota, no modo Anotações). */
    fun input(d: Int): Input {
        val i = selected
        if (over || i !in 0 until 81 || given(i) || d !in 1..9) return Input.IGNORED
        if (notesMode) {
            if (values[i] != 0) return Input.IGNORED
            notes[i] = notes[i] xor (1 shl d)
            return Input.NOTE
        }
        if (values[i] == d) return Input.IGNORED
        values[i] = d
        notes[i] = 0
        if (d != puzzle.solution[i] - '0') { errors++; return Input.WRONG }
        // Número certo: some das anotações da linha, coluna e bloco.
        peers(i).forEach { notes[it] = notes[it] and (1 shl d).inv() }
        return Input.RIGHT
    }

    /** Apaga o número (ou as anotações) da casa escolhida; número certo não sai. */
    fun erase() {
        val i = selected
        if (over || i !in 0 until 81 || given(i)) return
        if (values[i] != 0 && !wrong(i)) return
        values[i] = 0; notes[i] = 0
    }

    /** Volta ao começo (mesmo Sudoku). */
    fun reset() {
        puzzle.puzzle.forEachIndexed { i, c -> values[i] = c - '0'; notes[i] = 0 }
        errors = 0; seconds = 0; selected = -1
    }

    fun toJson(): String = JSONObject().put("p", puzzle.puzzle).put("s", puzzle.solution).put("l", puzzle.level.key)
        .put("v", values.joinToString("")).put("n", JSONArray(notes.toList())).put("e", errors).put("t", seconds).toString()

    companion object {
        const val MAX_ERRORS = 3

        fun peers(i: Int): Set<Int> {
            val r = i / 9; val c = i % 9; val br = r / 3 * 3; val bc = c / 3 * 3
            return ((0 until 9).map { r * 9 + it } + (0 until 9).map { it * 9 + c } + (0 until 9).map { (br + it / 3) * 9 + bc + it % 3 }).toSet() - i
        }

        fun fromJson(text: String?): SudokuGame? = runCatching {
            val o = JSONObject(text ?: return null)
            val lvl = SudokuLevel.entries.first { it.key == o.getString("l") }
            val g = SudokuGame(SudokuPuzzle(lvl, o.getString("p"), o.getString("s")))
            val v = o.getString("v"); val n = o.getJSONArray("n")
            require(v.length == 81 && n.length() == 81)
            for (i in 0 until 81) { g.values[i] = v[i] - '0'; g.notes[i] = n.getInt(i) }
            g.errors = o.optInt("e"); g.seconds = o.optInt("t")
            g
        }.getOrNull()
    }
}
