package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg

/** Mensagem da rodada com a hora (no relógio deste aparelho) em que ela chegou. */
data class Timed(val at: Long, val msg: Msg)

/**
 * Rodadas de pontos (Anagrama e Qual é a Palavra?): cada rodada termina no PRIMEIRO "solve" ou "skip"
 * dela na ordem do tópico; as rodadas contam em sequência a partir da 0.
 */
object PointRounds {
    /** Rodada decidida: [winner] = id de quem acertou (null = ninguém); [at] = quando a decisão chegou aqui. */
    data class Decision(val winner: String?, val at: Long)

    data class State(val decisions: List<Decision>, val rounds: Int, val gapMs: Long) {
        /** Rodada em jogo (0..N-1) ou N quando acabou. */
        val current: Int get() = decisions.size
        val over: Boolean get() = decisions.size >= rounds
        fun points(id: String?): Int = decisions.count { it.winner != null && it.winner == id }
        /** Início da rodada [r] no relógio deste aparelho. */
        fun startOf(r: Int, localStart: Long): Long = if (r == 0) localStart else decisions[r - 1].at + gapMs
        /** "me", "opp" ou "draw" quando acabou; null antes. */
        fun outcome(me: String, opp: String?): String? {
            if (!over) return null
            val a = points(me); val b = points(opp)
            return when { a > b -> "me"; b > a -> "opp"; else -> "draw" }
        }
    }

    fun state(msgs: List<Timed>, players: Set<String>, rounds: Int, gapMs: Long): State {
        val first = HashMap<Int, Decision>()
        for (t in msgs) {
            when (val m = t.msg) {
                is Msg.Solve -> if (m.round in 0 until rounds && m.id in players && m.round !in first) first[m.round] = Decision(m.id, t.at)
                is Msg.Skip -> if (m.round in 0 until rounds && m.round !in first) first[m.round] = Decision(null, t.at)
                else -> {}
            }
        }
        val out = ArrayList<Decision>()
        while (out.size < rounds) out += first[out.size] ?: break
        return State(out, rounds, gapMs)
    }
}

/**
 * Anagrama (modo "a"): N rodadas (5/10/15) com as mesmas 5 letras embaralhadas para os dois;
 * quem achar a palavra primeiro leva o ponto.
 */
object Anagram {
    /** Rodadas padrão (sem "r" no hello). */
    const val ROUNDS = 10
    const val ROUND_MS = 45_000L
    /** O convidado só manda o "skip" se o anfitrião saiu ou depois de 50 s. */
    const val GUEST_SKIP_MS = 50_000L
    /** Tempo mostrando a resposta antes da próxima rodada. */
    const val GAP_MS = 2_500L

    /** As N palavras da partida (mesma fórmula das palavras do Termo com amigo). */
    fun words(answers: List<String>, seed: Long, rounds: Int = ROUNDS): List<String> = Multi.words(answers, seed, rounds)

    /** Embaralhamento da palavra [k], igual nos dois aparelhos (e no site). */
    fun shuffle(word: String, seed: Long, k: Int): String {
        val a = word.toCharArray()
        var x = ((seed + k * 7919L) % 2147483646L) + 1
        for (i in a.size - 1 downTo 1) {
            x = (x * 48271L) % 2147483647L
            val j = (x % (i + 1)).toInt()
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        var s = String(a)
        var turns = 0
        while (s == word && turns < 4) { s = s.drop(1) + s.first(); turns++ }
        return s
    }

    /** Mesmas letras, contando repetições. */
    fun sameLetters(a: String, b: String): Boolean = a.length == b.length && a.toList().sorted() == b.toList().sorted()

    /** Vale qualquer palavra aceita com exatamente as letras da resposta (PORTA, PRATO, TROPA…). */
    fun solves(guess: String, answer: String, accepted: (String) -> Boolean): Boolean =
        guess.length == answer.length && sameLetters(guess, answer) && (guess == answer || accepted(guess))

    fun state(msgs: List<Timed>, players: Set<String>, rounds: Int = ROUNDS): PointRounds.State =
        PointRounds.state(msgs, players, rounds, GAP_MS)
}

/**
 * Qual é a Palavra? com amigo (modo "p"): N rodadas com a mesma definição para os dois;
 * quem acertar a palavra primeiro leva o ponto. Mensagens e decisão iguais às do Anagrama.
 */
object DefineDuel {
    const val ROUND_MS = 60_000L
    const val GUEST_SKIP_MS = 65_000L
    const val GAP_MS = 2_500L
    /** Dicas automáticas: 1ª letra aos 20 s, também a última aos 40 s. */
    const val HINT_FIRST_MS = 20_000L
    const val HINT_LAST_MS = 40_000L

    /** Linhas válidas de definicoes.txt (PALAVRA|definição), na ordem do arquivo. */
    fun parse(text: String): List<Definition> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
        .map { Definition(it.substringBefore('|').trim(), it.substringAfter('|').trim()) }
        .filter { it.word.isNotEmpty() && it.text.isNotEmpty() }

    /**
     * As [count] definições da partida: idx = ((SEED mod M)·7919 mod M + 104729 + k·577) mod M, pulando repetidas.
     */
    fun pick(defs: List<Definition>, seed: Long, count: Int): List<Definition> {
        val m = defs.size.toLong()
        if (m == 0L) return emptyList()
        val seen = LinkedHashSet<Int>()
        val words = HashSet<String>()
        val out = ArrayList<Definition>()
        var k = 0L
        while (out.size < count && k < m * 4 + 2000) {
            val idx = (((seed % m) * 7919 % m + 104729 + k * 577) % m).toInt()
            k++
            if (!seen.add(idx)) continue
            val d = defs[idx]
            if (!words.add(Words.normalize(d.word))) continue
            out += d
        }
        return out
    }

    /** Letras da resposta para comparar (maiúsculas, sem acento, só A–Z). */
    fun key(word: String): String = Words.normalize(word).filter { it in 'A'..'Z' }

    fun solves(guess: String, word: String): Boolean = key(guess) == key(word)

    /** Posições (no texto sem acento) das letras reveladas pela dica após [elapsed] ms. */
    fun hints(word: String, elapsed: Long): Set<Int> {
        val t = Words.normalize(word)
        val letters = t.indices.filter { t[it] in 'A'..'Z' }
        if (letters.isEmpty()) return emptySet()
        val out = HashSet<Int>()
        if (elapsed >= HINT_FIRST_MS) out += letters.first()
        if (elapsed >= HINT_LAST_MS) out += letters.last()
        return out
    }

    fun state(msgs: List<Timed>, players: Set<String>, rounds: Int): PointRounds.State =
        PointRounds.state(msgs, players, rounds, GAP_MS)
}
