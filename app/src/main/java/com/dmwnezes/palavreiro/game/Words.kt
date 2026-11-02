package com.dmwnezes.palavreiro.game

import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Lista de palavras, lida do mesmo arquivo da versão web (shared/words.js),
 * para o app e o site terem sempre a mesma palavra do dia.
 */
class Words(source: String) {

    /** Respostas possíveis, sem acento, na mesma ordem da versão web. */
    val answers: List<String>

    /** Forma com acento de cada resposta, para exibir. */
    private val accented: Map<String, String>

    /** Quantas respostas entram no sorteio do dia antes de [newFrom] (as novas só depois). */
    private val baseCount: Int
    private val newFrom: Long

    /** Tudo o que é aceito como tentativa. */
    private val accepted: Set<String>

    init {
        val answerRaw = readList(source, "ANSWERS")
        val validRaw = readList(source, "VALID")
        val newRaw = readList(source, "ANSWERS2")
        newFrom = Regex("""const\s+ANSWERS2_FROM\s*=\s*(\d+)""").find(source)?.groupValues?.get(1)?.toLong() ?: 0L

        val order = ArrayList<String>()
        val acc = HashMap<String, String>()
        fun addAnswer(w: String) {
            val n = normalize(w)
            if (n.length == WORD_LENGTH && n.all { it in 'A'..'Z' } && n !in acc) {
                acc[n] = w.uppercase()
                order += n
            }
        }
        answerRaw.forEach(::addAnswer)
        baseCount = order.size
        newRaw.forEach(::addAnswer)
        answers = order
        accented = acc
        accepted = HashSet<String>(order).apply { validRaw.forEach { add(normalize(it)) } }
    }

    fun isAccepted(word: String): Boolean = word in accepted

    /** Todas as palavras aceitas (respostas + tentativas extras). */
    val all: Set<String> get() = accepted

    fun display(word: String): String = accented[word] ?: word

    /** Mesmo cálculo da versão web: dias desde 01/01/2026, com embaralhamento fixo. */
    fun daily(date: LocalDate = LocalDate.now()): String = dailyAt(dayIndex(date))

    private fun oldDaily(i: Long): String {
        val n = poolSize(i)
        return answers[(((i * 7919L + 104729L) % n + n) % n).toInt()]
    }

    private val newDaily = ArrayList<String>()

    /** De 03/11/2026 em diante a palavra não repete nenhuma dos últimos 1000 dias. Dias anteriores não mudam, por causa do Arquivo. */
    @Synchronized
    private fun dailyAt(i: Long): String {
        if (i < NO_REPEAT_FROM) return oldDaily(i)
        var j = NO_REPEAT_FROM + newDaily.size
        while (j <= i) {
            val recent = HashSet<String>()
            for (k in maxOf(0L, j - NO_REPEAT_DAYS) until j) recent += if (k < NO_REPEAT_FROM) oldDaily(k) else newDaily[(k - NO_REPEAT_FROM).toInt()]
            val n = poolSize(j)
            var at = (((j * 7919L + 104729L) % n + n) % n).toInt()
            while (answers[at] in recent) at = (at + 1) % n
            newDaily += answers[at]
            j++
        }
        return newDaily[(i - NO_REPEAT_FROM).toInt()]
    }

    /**
     * Palavras do dia para modos com vários tabuleiros (Dueto, Quarteto).
     * [salt] separa os modos; as palavras nunca se repetem entre si.
     */
    fun dailySet(date: LocalDate, count: Int, salt: Int): List<String> {
        if (count == 1 && salt == 0) return listOf(daily(date))
        val i = dayIndex(date)
        val n = poolSize(i)
        val out = LinkedHashSet<String>()
        var k = 0
        while (out.size < count) {
            val idx = ((i * 7919L + 104729L + salt * 3331L + k * 577L) % n + n) % n
            out += answers[idx.toInt()]
            k++
        }
        return out.toList()
    }

    /** Mesmo cálculo da versão web: as palavras novas só entram no sorteio a partir de [newFrom]. */
    private fun poolSize(dayIndex: Long): Int = if (dayIndex >= newFrom) answers.size else baseCount

    fun randomSet(count: Int, rnd: Random = Random.Default): List<String> {
        val out = LinkedHashSet<String>()
        while (out.size < count) out += answers[rnd.nextInt(answers.size)]
        return out.toList()
    }

    fun random(avoid: String? = null, rnd: Random = Random.Default): String {
        var w: String
        do {
            w = answers[rnd.nextInt(answers.size)]
        } while (w == avoid && answers.size > 1)
        return w
    }

    companion object {
        const val WORD_LENGTH = 5
        private const val NO_REPEAT_FROM = 306L
        private const val NO_REPEAT_DAYS = 1000L
        private val START: LocalDate = LocalDate.of(2026, 1, 1)

        fun dayIndex(date: LocalDate): Long = ChronoUnit.DAYS.between(START, date)

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .uppercase()

        private fun readList(source: String, name: String): List<String> {
            val m = Regex("""const\s+$name\s*=\s*"([^"]*)"""").find(source) ?: return emptyList()
            return m.groupValues[1].split(' ').filter { it.isNotBlank() }
        }
    }
}
