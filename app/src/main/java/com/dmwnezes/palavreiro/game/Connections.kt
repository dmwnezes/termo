package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlin.random.Random

data class ConnGroup(val name: String, val words: List<String>, val level: Int)
data class ConnPuzzle(val groups: List<ConnGroup>) {
    val allWords: List<String> get() = groups.flatMap { it.words }
}

/** Lê o arquivo conexoes.txt. */
object ConnectionsData {
    fun parse(text: String): List<ConnPuzzle> {
        val puzzles = ArrayList<ConnPuzzle>()
        var current = ArrayList<ConnGroup>()
        fun flush() {
            if (current.isNotEmpty()) puzzles += ConnPuzzle(current)
            current = ArrayList()
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("#")) continue
            if (line.isEmpty()) { flush(); continue }
            val i = line.lastIndexOf(':')
            val name = line.substring(0, i).trim()
            val words = line.substring(i + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            current += ConnGroup(name, words, current.size)
        }
        flush()
        return puzzles
    }

    /** Lê conexoes-familias.txt: nome do grupo -> famílias de assunto. */
    fun families(text: String): Map<String, Set<String>> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && ':' in it }
        .associate { l -> l.substringBefore(':').trim() to l.substringAfter(':').split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }

    /**
     * Conexões Infinito: junta 4 grupos de desafios diferentes, um de cada cor,
     * sem assunto em comum e sem palavra repetida. Mesma semente = mesmo desafio.
     */
    fun remix(puzzles: List<ConnPuzzle>, families: Map<String, Set<String>>, seed: Long): ConnPuzzle {
        val rnd = Random(seed)
        val byLevel = (0..3).map { lv ->
            puzzles.mapNotNull { p -> p.groups.getOrNull(lv) }
                .filter { g -> val f = families[g.name]; f != null && "fora" !in f && g.words.size == 4 }
        }
        repeat(5000) {
            val pick = ArrayList<ConnGroup>()
            val usedFam = HashSet<String>()
            val usedWords = HashSet<String>()
            for (lv in 0..3) {
                val g = byLevel[lv][rnd.nextInt(byLevel[lv].size)]
                val fam = families.getValue(g.name)
                val norm = g.words.map { Words.normalize(it) }
                if (fam.any { it in usedFam } || norm.any { it in usedWords }) break
                usedFam += fam; usedWords += norm; pick += g
            }
            if (pick.size == 4) return ConnPuzzle(pick)
        }
        return puzzles[rnd.nextInt(puzzles.size)]
    }

    /** Desafio do dia, sempre o mesmo para todos na mesma data. */
    fun dailyIndex(date: LocalDate, count: Int): Int {
        val i = Words.dayIndex(date)
        return (((i * 13L + 7L) % count + count) % count).toInt()
    }
}

/** Estado de uma partida de Conexões: 16 palavras, 4 grupos, até 4 erros. */
class ConnectionsGame(val puzzle: ConnPuzzle, seed: Long) {
    val tiles = mutableStateListOf<String>().apply { addAll(puzzle.allWords.shuffled(Random(seed))) }
    val selected = mutableStateListOf<String>()
    val solved = mutableStateListOf<ConnGroup>()
    val tries = mutableStateListOf<List<String>>()
    var mistakes by mutableIntStateOf(0)
        private set
    var over by mutableStateOf(false)
        private set
    var won by mutableStateOf(false)
        private set
    var shakeTick by mutableIntStateOf(0)
        private set

    val maxMistakes = 4

    fun toggle(word: String) {
        if (over || word !in tiles) return
        if (word in selected) selected.remove(word)
        else if (selected.size < 4) selected.add(word)
    }

    fun deselectAll() = selected.clear()

    fun shuffle(rnd: Random = Random.Default) {
        val copy = tiles.shuffled(rnd)
        tiles.clear(); tiles.addAll(copy)
    }

    sealed interface Result {
        data class Correct(val group: ConnGroup) : Result
        data object OneAway : Result
        data object Wrong : Result
        data object Repeated : Result
        data object Incomplete : Result
    }

    fun submit(): Result {
        if (over) return Result.Incomplete
        if (selected.size != 4) return Result.Incomplete
        val set = selected.toSet()
        if (tries.any { it.toSet() == set }) return Result.Repeated
        tries += selected.toList()
        val group = puzzle.groups.firstOrNull { it.words.toSet() == set }
        if (group != null) {
            solved += group
            tiles.removeAll(group.words)
            selected.clear()
            if (solved.size == puzzle.groups.size) { over = true; won = true }
            return Result.Correct(group)
        }
        mistakes++
        shakeTick++
        val best = puzzle.groups.maxOf { g -> g.words.count { it in set } }
        if (mistakes >= maxMistakes) revealAll()
        return if (best == 3) Result.OneAway else Result.Wrong
    }

    private fun revealAll() {
        over = true
        won = false
        selected.clear()
        for (g in puzzle.groups.sortedBy { it.level }) if (g !in solved) {
            solved += g
            tiles.removeAll(g.words)
        }
    }

    /** Linha de quadradinhos coloridos por tentativa, para compartilhar. */
    fun shareGrid(): String = tries.joinToString("\n") { t ->
        t.joinToString("") { w -> listOf("🟨", "🟩", "🟦", "🟪")[puzzle.groups.first { w in it.words }.level] }
    }
}
