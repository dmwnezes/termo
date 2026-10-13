package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg

/**
 * Trivia (modo "g", inspirado no Perguntados): N perguntas (5/10/15) iguais para os dois, sorteadas pela SEED.
 * Cada um responde a sua; acerto = 1 ponto; desempate pela soma dos tempos dos acertos (menor vence).
 */
object TriviaDuel {
    val CATEGORIES = listOf("Ciência", "História", "Geografia", "Esportes", "Arte", "Entretenimento")
    /** Roleta antes da pergunta. */
    const val WHEEL_MS = 1_500L
    /** Tempo para responder. */
    const val ANSWER_MS = 15_000L
    /** Resposta certa na tela antes da próxima pergunta. */
    const val REVEAL_MS = 3_000L
    /** Folga para o "pick" do amigo chegar (depois disso conta como não respondeu). */
    const val GRACE_MS = 10_000L

    data class Question(val category: String, val text: String, val right: String, val wrong: List<String>)

    /**
     * Linhas válidas: CATEGORIA|pergunta|certa|e1|e2|e3 (exatamente 6 campos, todos não vazios depois do trim,
     * categoria uma das 6), sem "#", na ordem do arquivo — igual ao site.
     */
    fun parse(text: String): List<Question> = text.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { l -> l.split('|').map { it.trim() } }
        .filter { p -> p.size == 6 && p.all { it.isNotEmpty() } && p[0] in CATEGORIES }
        .map { p -> Question(p[0], p[1], p[2], p.subList(3, 6)) }

    /**
     * idx = ((SEED mod Q)·7919 mod Q + 104729 + k·577) mod Q, para k = 0, 1, 2…, pulando repetidos,
     * até min([count], Q) perguntas; desiste depois de Q·4 tentativas (igual ao site).
     */
    fun indices(q: Int, seed: Long, count: Int): List<Int> {
        if (q <= 0) return emptyList()
        val n = q.toLong()
        val out = LinkedHashSet<Int>()
        var k = 0L
        while (out.size < minOf(count, q) && k < n * 4) {
            out += (((seed % n) * 7919 % n + 104729 + k * 577) % n).toInt()
            k++
        }
        return out.toList()
    }

    fun pick(all: List<Question>, seed: Long, count: Int): List<Question> = indices(all.size, seed, count).map { all[it] }

    /** Quantas vezes as opções da pergunta [k] giram para a esquerda. */
    fun rotation(seed: Long, k: Int): Int = ((seed + k * 7L) % 4).toInt()

    /** Opções da pergunta [k]: [certa, e1, e2, e3] girada para a esquerda. */
    fun options(q: Question, seed: Long, k: Int): List<String> {
        val base = listOf(q.right) + q.wrong
        val r = rotation(seed, k)
        return base.drop(r) + base.take(r)
    }

    /** Posição (0..3) da certa depois do giro. */
    fun correctIndex(seed: Long, k: Int): Int = (4 - rotation(seed, k)) % 4

    data class Answer(val option: Int, val ms: Long)

    /** Pergunta [k] fechada: o que cada um escolheu (null = não chegou / saiu) e quando fechou aqui. */
    data class Decision(val k: Int, val mine: Answer?, val theirs: Answer?, val at: Long)

    data class State(val decisions: List<Decision>, val total: Int, val seed: Long) {
        val current: Int get() = decisions.size
        val over: Boolean get() = decisions.size >= total
        fun startOf(k: Int, localStart: Long): Long = if (k == 0) localStart else decisions[k - 1].at + REVEAL_MS
        private fun hit(a: Answer?, k: Int) = a != null && a.option == correctIndex(seed, k)
        val myHits: Int get() = decisions.count { hit(it.mine, it.k) }
        val oppHits: Int get() = decisions.count { hit(it.theirs, it.k) }
        val myMs: Long get() = decisions.filter { hit(it.mine, it.k) }.sumOf { it.mine!!.ms }
        val oppMs: Long get() = decisions.filter { hit(it.theirs, it.k) }.sumOf { it.theirs!!.ms }
        fun iHit(k: Int) = hit(decisions.getOrNull(k)?.mine, k)
        fun oppHit(k: Int) = hit(decisions.getOrNull(k)?.theirs, k)
        /** "me", "opp" ou "draw" no fim (empate nos acertos → menor tempo total). */
        fun outcome(): String? = if (!over) null else when {
            myHits > oppHits -> "me"
            oppHits > myHits -> "opp"
            myMs < oppMs -> "me"
            oppMs < myMs -> "opp"
            else -> "draw"
        }
    }

    /** Fecha cada pergunta quando os dois "pick" chegaram (o amigo que saiu conta como -1). */
    fun state(msgs: List<Timed>, me: String, opp: String?, total: Int, seed: Long, localStart: Long, now: Long, oppLeft: Boolean): State {
        val picks = HashMap<Pair<Int, String>, Timed>()
        for (t in msgs) {
            val m = t.msg
            if (m is Msg.Pick && m.round in 0 until total && (m.id == me || m.id == opp) && (m.round to m.id) !in picks) picks[m.round to m.id] = t
        }
        val out = ArrayList<Decision>()
        while (out.size < total) {
            val k = out.size
            val start = if (k == 0) localStart else out[k - 1].at + REVEAL_MS
            val mine = picks[k to me]
            val theirs = opp?.let { picks[k to it] }
            val deadline = start + WHEEL_MS + ANSWER_MS + GRACE_MS
            val at = when {
                mine != null && theirs != null -> maxOf(mine.at, theirs.at)
                mine != null && (oppLeft || opp == null) -> mine.at
                now >= deadline -> deadline
                else -> break
            }
            fun ans(t: Timed?) = (t?.msg as Msg.Pick?)?.let { Answer(it.option, it.ms) }
            out += Decision(k, ans(mine), ans(theirs), maxOf(at, start))
        }
        return State(out, total, seed)
    }
}
