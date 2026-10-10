package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg

/** Mensagem da rodada com a hora (no relógio deste aparelho) em que ela chegou. */
data class Timed(val at: Long, val msg: Msg)

/**
 * Bomba-Relógio (modo "b" da partida com amigo): os dois se revezam mandando palavras de 5 letras,
 * sem repetir; a bomba explode num momento secreto e perde quem estiver com a vez.
 * Tudo aqui é igual ao site (js/multi.js), para app e site jogarem entre si.
 */
object Bomb {
    /** Pavio secreto da rodada, em ms desde o fim da contagem: entre 25 s e 60 s. */
    fun fuse(seed: Long): Long = 25000L + ((seed % 35001L) * 7919L % 35001L)

    /** SEED par → o anfitrião começa; ímpar → o convidado. */
    fun hostStarts(seed: Long): Boolean = seed % 2L == 0L

    /** Quanto tempo esperar o "boom" do outro depois que o seu relógio chegou ao pavio. */
    const val BOOM_WAIT_MS = 4000L

    data class Play(val id: String, val word: String, val ms: Long)

    /**
     * Estado da rodada a partir das mensagens na ordem do tópico.
     * [turn] = quem está com a bomba; [loser] = com quem ela explodiu (null enquanto não explodiu).
     */
    data class State(val plays: List<Play>, val turn: String, val loser: String?) {
        val used: Set<String> get() = plays.mapTo(HashSet()) { it.word }
        val count: Int get() = plays.size
    }

    /** [hostId]/[guestId] = ids dos dois jogadores; mensagens de outros ids são ignoradas. */
    fun state(msgs: List<Msg>, seed: Long, hostId: String, guestId: String): State {
        val f = fuse(seed)
        var turn = if (hostStarts(seed)) hostId else guestId
        val plays = ArrayList<Play>()
        val used = HashSet<String>()
        for (m in msgs) {
            when (m) {
                is Msg.Word -> {
                    val w = Words.normalize(m.word)
                    if (m.id == turn && m.n == plays.size && w !in used && m.ms < f) {
                        plays += Play(m.id, w, m.ms); used += w
                        turn = if (turn == hostId) guestId else hostId
                    }
                }
                is Msg.Boom -> if (m.id == hostId || m.id == guestId) return State(plays, turn, m.id)
                else -> {}
            }
        }
        return State(plays, turn, null)
    }

    /** Aviso ao tentar mandar [word] (já normalizada); null = pode mandar. */
    fun check(word: String, state: State, accepted: (String) -> Boolean): String? = when {
        word.length != Words.WORD_LENGTH -> "Só palavras de 5 letras"
        !accepted(word) -> "Palavra não aceita"
        word in state.used -> "Essa palavra já foi"
        else -> null
    }

    /** Período do pulso da bomba: ~1,2 s no começo até ~0,25 s perto do pavio. */
    fun pulseMs(elapsed: Long, fuse: Long): Int {
        val p = (elapsed.toDouble() / fuse).coerceIn(0.0, 1.0)
        return (1200 - 950 * p).toInt()
    }
}

/**
 * Anagrama (modo "a"): 10 rodadas com as mesmas 5 letras embaralhadas para os dois;
 * quem achar a palavra primeiro leva o ponto.
 */
object Anagram {
    const val ROUNDS = 10
    const val ROUND_MS = 45_000L
    /** O convidado só manda o "skip" se o anfitrião saiu ou depois de 50 s. */
    const val GUEST_SKIP_MS = 50_000L
    /** Tempo mostrando a resposta antes da próxima rodada. */
    const val GAP_MS = 2_500L

    /** As 10 palavras da partida (mesma fórmula das palavras do Termo com amigo). */
    fun words(answers: List<String>, seed: Long): List<String> = Multi.words(answers, seed, ROUNDS)

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

    /** Rodada decidida: [winner] = id de quem acertou (null = ninguém); [at] = quando a decisão chegou aqui. */
    data class Decision(val winner: String?, val at: Long)

    data class State(val decisions: List<Decision>) {
        /** Rodada em jogo (0..9) ou 10 quando acabou. */
        val current: Int get() = decisions.size
        val over: Boolean get() = decisions.size >= ROUNDS
        fun points(id: String?): Int = decisions.count { it.winner != null && it.winner == id }
        /** Início da rodada [r] no relógio deste aparelho. */
        fun startOf(r: Int, localStart: Long): Long = if (r == 0) localStart else decisions[r - 1].at + GAP_MS
        /** "me", "opp" ou "draw" quando acabou; null antes. */
        fun outcome(me: String, opp: String?): String? {
            if (!over) return null
            val a = points(me); val b = points(opp)
            return when { a > b -> "me"; b > a -> "opp"; else -> "draw" }
        }
    }

    /**
     * Rodada R termina no PRIMEIRO "solve" ou "skip" dela na ordem do tópico.
     * As rodadas contam em sequência a partir da 0 (uma rodada só vale depois da anterior decidida).
     */
    fun state(msgs: List<Timed>, players: Set<String>): State {
        val first = HashMap<Int, Decision>()
        for (t in msgs) {
            when (val m = t.msg) {
                is Msg.Solve -> if (m.round in 0 until ROUNDS && m.id in players && m.round !in first) first[m.round] = Decision(m.id, t.at)
                is Msg.Skip -> if (m.round in 0 until ROUNDS && m.round !in first) first[m.round] = Decision(null, t.at)
                else -> {}
            }
        }
        val out = ArrayList<Decision>()
        while (out.size < ROUNDS) out += first[out.size] ?: break
        return State(out)
    }
}
