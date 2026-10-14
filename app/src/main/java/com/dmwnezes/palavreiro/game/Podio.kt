package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Um ranking: título, top 5 em ordem e 5 opções erradas. */
data class PodioQ(val title: String, val top: List<String>, val wrong: List<String>) {
    /** As 10 opções na ordem do arquivo (top 5 e depois as erradas). */
    val all: List<String> get() = top + wrong
}

object PodioData {
    /** Linhas TÍTULO|1º|2º|3º|4º|5º|errada1..errada5 (comentários com #). Linhas inválidas são ignoradas. */
    fun parse(text: String): List<PodioQ> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        val p = line.split('|').map { it.trim() }
        if (p.size != 11 || p.any { it.isEmpty() } || p.drop(1).toSet().size != 10) null else PodioQ(p[0], p.subList(1, 6), p.subList(6, 11))
    }

    /** Índice do dia: (dia * 7919 + 104729) mod N. */
    fun dailyIndex(day: Long, n: Int): Int = Math.floorMod(day * 7919 + 104729, n.toLong()).toInt()

    /** Ordem das 10 opções no dia (MINSTD semeado com (dia + 1) * 7919). */
    fun dailyOptions(q: PodioQ, day: Long): List<String> = q.all.shuffledBy(Minstd.ofDay(day))
}

enum class PodioMark { GREEN, YELLOW, GRAY }

/**
 * Pódio: monte o top 5 com as 10 opções em até 5 tentativas.
 * Cada opção conta uma vez (verde primeiro, depois amarelo), como no Termo.
 */
class PodioGame(val q: PodioQ, val options: List<String>) {
    constructor(q: PodioQ, rnd: Random) : this(q, q.all.shuffled(rnd))

    /** Vagas 1º–5º (null = livre). */
    val slots = mutableStateListOf<String?>(null, null, null, null, null)
    val tries = mutableStateListOf<List<String>>()
    var tick by mutableIntStateOf(0)
        private set

    val won: Boolean get() = tries.lastOrNull() == q.top
    val over: Boolean get() = won || tries.size >= MAX
    val full: Boolean get() = slots.all { it != null }
    /** Opções que já vieram cinza (fora do top 5). */
    val gray: Set<String> get() = tries.flatten().filter { it !in q.top }.toSet()

    fun marks(t: List<String>): List<PodioMark> = feedback(q.top, t)

    /** Toque numa opção: vai para a próxima vaga livre. */
    fun put(o: String): Boolean {
        if (over || o in slots || o !in options) return false
        val i = slots.indexOf(null).takeIf { it >= 0 } ?: return false
        slots[i] = o; tick++
        return true
    }

    /** Toque numa vaga: devolve a opção. */
    fun take(i: Int) { if (!over && slots.getOrNull(i) != null) { slots[i] = null; tick++ } }

    fun submit(): Boolean {
        if (over || !full) return false
        tries += slots.map { it!! }
        // As verdes ficam nas vagas; o resto volta para as opções.
        val m = marks(tries.last())
        for (i in 0 until 5) if (m[i] != PodioMark.GREEN && !won) slots[i] = null
        tick++
        return true
    }

    fun share(daily: Boolean): String {
        val head = "Pódio${if (daily) "" else " Infinito"} ${if (won) tries.size else "X"}/$MAX 🏆"
        return head + "\n" + tries.joinToString("\n") { t -> marks(t).joinToString("") { when (it) { PodioMark.GREEN -> "🟩"; PodioMark.YELLOW -> "🟨"; PodioMark.GRAY -> "⬛" } } }
    }

    companion object {
        const val MAX = 5

        fun feedback(top: List<String>, t: List<String>): List<PodioMark> {
            val out = MutableList(t.size) { PodioMark.GRAY }
            val left = top.toMutableList()
            t.forEachIndexed { i, o -> if (top.getOrNull(i) == o) { out[i] = PodioMark.GREEN; left.remove(o) } }
            t.forEachIndexed { i, o -> if (out[i] != PodioMark.GREEN && left.remove(o)) out[i] = PodioMark.YELLOW }
            return out
        }
    }
}
