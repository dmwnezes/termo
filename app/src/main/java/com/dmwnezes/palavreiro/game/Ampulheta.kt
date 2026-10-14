package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Um acontecimento: ano (negativo = a.C.) e a frase. */
data class AmpEvent(val year: Int, val text: String) {
    val yearLabel: String get() = if (year < 0) "${-year} a.C." else "$year"
}

object AmpulhetaData {
    /** Linhas ANO|acontecimento (comentários com #). */
    fun parse(text: String): List<AmpEvent> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        val p = line.split('|', limit = 2).map { it.trim() }
        val y = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        p.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { AmpEvent(y, it) }
    }

    /**
     * Os 11 índices do dia (o 1º é a carta inicial): MINSTD semeado com (dia + 1) * 7919, índice = x mod N,
     * pulando índices e anos repetidos. Com menos de 11 anos diferentes na lista, devolve os que houver.
     */
    fun dailyIndices(events: List<AmpEvent>, day: Long, count: Int = AmpulhetaGame.DAILY + 1): List<Int> {
        val want = minOf(count, events.map { it.year }.distinct().size)
        val r = Minstd.ofDay(day)
        val out = ArrayList<Int>()
        val years = HashSet<Int>()
        while (out.size < want) {
            val i = (r.next() % events.size).toInt()
            if (i in out || events[i].year in years) continue
            out += i; years += events[i].year
        }
        return out
    }
}

/**
 * Ampulheta: encaixe cada carta na linha do tempo. Do dia: 10 cartas fixas (sem limite de vidas, nota = acertos).
 * Infinito: cartas sorteadas sem repetir ano até perder as 3 vidas (ou acabarem as cartas).
 */
class AmpulhetaGame(first: AmpEvent, private val deck: MutableList<AmpEvent>, val daily: Boolean) {
    /** Cartas da partida (sem a inicial). */
    val cards = deck.size
    /** Espaços tocados, em ordem (para retomar a partida do dia). */
    val placed = mutableListOf<Int>()
    /** Linha do tempo, em ordem de ano. */
    val line = mutableStateListOf(first)
    /** Acertou (true) ou errou cada carta, em ordem. */
    val results = mutableStateListOf<Boolean>()
    var card by mutableStateOf(deck.removeFirstOrNull())
        private set
    /** Última carta posta (para destacar) e o espaço tocado nela. */
    var last by mutableStateOf<AmpEvent?>(null)
        private set
    var lives by mutableIntStateOf(LIVES)
        private set

    val right: Int get() = results.count { it }
    val over: Boolean get() = card == null || (!daily && lives <= 0)

    /** Espaços certos para [e]: antes do primeiro = 0, ..., depois do último = line.size. */
    fun slotFor(e: AmpEvent): Int = line.count { it.year < e.year }

    /** Toca no espaço [slot]; devolve se acertou. A carta entra na posição certa de qualquer jeito. */
    fun place(slot: Int): Boolean {
        val c = card ?: return false
        if (over) return false
        val at = slotFor(c)
        val ok = slot == at
        line.add(at, c)
        placed += slot
        results += ok
        if (!ok) lives--
        last = c
        card = if (!daily && lives <= 0) null else deck.removeFirstOrNull()
        return ok
    }

    fun share(): String = "Ampulheta${if (daily) "" else " Infinito"} $right/${results.size} ⏳\n" +
        results.joinToString("") { if (it) "🟩" else "🟥" }

    companion object {
        const val DAILY = 10
        const val LIVES = 3

        fun daily(events: List<AmpEvent>, day: Long): AmpulhetaGame {
            val idx = AmpulhetaData.dailyIndices(events, day)
            return AmpulhetaGame(events[idx[0]], idx.drop(1).map { events[it] }.toMutableList(), daily = true)
        }

        /** Infinito: um acontecimento por ano, em ordem aleatória. */
        fun infinite(events: List<AmpEvent>, rnd: Random): AmpulhetaGame {
            val deck = events.shuffled(rnd).distinctBy { it.year }.toMutableList()
            return AmpulhetaGame(deck.removeAt(0), deck, daily = false)
        }
    }
}
