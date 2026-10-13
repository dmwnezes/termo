package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg
import java.text.Normalizer
import kotlin.random.Random

/**
 * Adedonha (modo "s", inspirado no StopotS): N rodadas (3/5/7). O anfitrião sorteia 6 categorias por partida
 * e uma letra por rodada; cada um preenche, alguém dá STOP, os dois validam as respostas do amigo e somam pontos.
 */
object Adedonha {
    const val CATEGORIES = 6
    /** Letras que podem sair (sem K, Q, W, X, Y, Z). */
    const val LETTERS = "ABCDEFGHIJLMNOPRSTUV"
    /** Letra girando antes de liberar os campos. */
    const val SPIN_MS = 1_500L
    /** Tempo máximo para preencher. */
    const val FILL_MS = 90_000L
    /** O convidado só manda o "stop" do tempo se o anfitrião saiu ou depois de 90 s + 10 s. */
    const val GUEST_STOP_MS = 100_000L
    /** Depois do STOP, espera as respostas do amigo por até 10 s (depois disso valem como vazias). */
    const val ANS_WAIT_MS = 10_000L
    /** Tempo para validar as respostas do amigo. */
    const val VOTE_MS = 40_000L
    /** Folga para o voto do amigo chegar depois do limite. */
    const val VOTE_GRACE_MS = 5_000L
    /** Resultado da rodada na tela antes da próxima letra. */
    const val NEXT_MS = 6_000L
    /** "id" do stop mandado quando o tempo acaba. */
    const val TEMPO = "tempo"
    /** Tamanho máximo de cada resposta. */
    const val MAX_ANSWER = 40

    /** Categorias do arquivo (uma por linha, sem vazias e sem "#"). */
    fun parse(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.distinct()

    /** Anfitrião: as 6 categorias da partida. */
    fun pickCategories(all: List<String>, rnd: Random = Random.Default): List<String> = all.shuffled(rnd).take(CATEGORIES)

    /** Anfitrião: letra da rodada, sem repetir as já usadas na partida. */
    fun pickLetter(used: Collection<Char>, rnd: Random = Random.Default): Char {
        val pool = LETTERS.filter { it !in used }.ifEmpty { LETTERS }
        return pool[rnd.nextInt(pool.length)]
    }

    /** Para comparar: sem acento, minúsculas, sem espaços nas pontas e espaços repetidos viram um. */
    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .lowercase().trim().replace(Regex("\\s+"), " ")

    /** Resposta como vai no "ans": sem espaços nas pontas, espaço repetido vira um, até 40 caracteres. */
    fun cleanAnswer(s: String): String = s.trim().replace(Regex("\\s+"), " ").take(MAX_ANSWER).trim()

    /** "a" do "ans": completa (ou corta) até o número de categorias, cada resposta arrumada. */
    fun answers(a: List<String>, count: Int): List<String> = List(count) { cleanAnswer(a.getOrElse(it) { "" }) }

    /** Validação automática: não vazia e começando com a letra. */
    fun autoValid(answer: String, letter: Char): Boolean {
        val t = norm(answer)
        return t.isNotEmpty() && t[0] == norm(letter.toString()).firstOrNull()
    }

    /** O STOP só libera com todos os campos preenchidos e começando com a letra. */
    fun canStop(answers: List<String>, letter: Char): Boolean = answers.isNotEmpty() && answers.all { autoValid(it, letter) }

    /** Votos que eu mando sobre as respostas do amigo (sempre um por categoria): as automáticas ✗ sempre 0. */
    fun votesFor(theirs: List<String>, letter: Char, marks: List<Boolean>, count: Int = theirs.size): List<Int> =
        (0 until count).map { if (autoValid(theirs.getOrElse(it) { "" }, letter) && marks.getOrElse(it) { true }) 1 else 0 }

    /**
     * Pontos por categoria dos dois: [votesOnMine] = votos do amigo sobre as minhas respostas (null = não votou: tudo ✓);
     * [votesOnTheirs] = os meus votos sobre as dele. Válida = passa no automático e o voto é 1.
     * Qualquer voto diferente de 0 vale como 1. Válida e diferente da resposta válida do outro: 10; igual: 5; inválida: 0.
     */
    fun score(
        mine: List<String>, theirs: List<String>, letter: Char, votesOnMine: List<Int>?, votesOnTheirs: List<Int>?, count: Int,
    ): Pair<List<Int>, List<Int>> {
        fun valid(a: List<String>, v: List<Int>?, i: Int) = autoValid(a.getOrElse(i) { "" }, letter) && (v?.getOrNull(i) ?: 1) != 0
        val a = ArrayList<Int>(); val b = ArrayList<Int>()
        for (i in 0 until count) {
            val va = valid(mine, votesOnMine, i); val vb = valid(theirs, votesOnTheirs, i)
            val same = va && vb && norm(mine.getOrElse(i) { "" }) == norm(theirs.getOrElse(i) { "" })
            a += if (!va) 0 else if (same) 5 else 10
            b += if (!vb) 0 else if (same) 5 else 10
        }
        return a to b
    }

    enum class Stage { SPIN, FILL, LOCKED, VOTE, RESULT }

    /** Uma rodada (letra) vista deste aparelho. Horas no relógio local (chegada das mensagens). */
    data class Round(
        val k: Int,
        val letter: Char,
        val cats: List<String>,
        /** Chegada do "sround". */
        val at: Long,
        /** Primeiro "stop" da rodada (ordem do tópico) e quando chegou. */
        val stop: Msg.Stop?,
        val stopAt: Long,
        val mine: List<String>?,
        val theirs: List<String>?,
        /** O amigo não mandou as respostas (saiu ou passou do tempo): valem vazias e ele não vota. */
        val oppGone: Boolean,
        /** Início da validação (as duas respostas chegaram). */
        val voteStart: Long,
        val myVote: List<Int>?,
        val theirVote: List<Int>?,
        /** Quando a rodada fechou (null = ainda não). */
        val resolvedAt: Long?,
        val myPoints: List<Int>,
        val oppPoints: List<Int>,
    ) {
        val fillStart: Long get() = at + SPIN_MS
        val fillEnd: Long get() = fillStart + FILL_MS
        val myTotal: Int get() = myPoints.sum()
        val oppTotal: Int get() = oppPoints.sum()
        fun stage(now: Long): Stage = when {
            resolvedAt != null -> Stage.RESULT
            stop == null -> if (now < fillStart) Stage.SPIN else Stage.FILL
            mine == null || theirs == null -> Stage.LOCKED
            else -> Stage.VOTE
        }
    }

    /**
     * [oppLeft]: o amigo saiu — entre rodadas a partida acaba com o placar de agora; no meio de uma rodada,
     * ela continua até o STOP/tempo, fecha e aí a partida acaba.
     */
    data class State(val rounds: List<Round>, val total: Int, val cats: List<String>, val oppLeft: Boolean = false) {
        val current: Round? get() = rounds.lastOrNull()
        val over: Boolean get() = (rounds.size >= total || oppLeft) && rounds.lastOrNull().let { it == null || it.resolvedAt != null }
        val me: Int get() = rounds.sumOf { if (it.resolvedAt != null) it.myTotal else 0 }
        val opp: Int get() = rounds.sumOf { if (it.resolvedAt != null) it.oppTotal else 0 }
        val usedLetters: List<Char> get() = rounds.map { it.letter }
        /** "me", "opp" ou "draw" no fim; null antes. */
        fun outcome(): String? = if (!over) null else when { me > opp -> "me"; opp > me -> "opp"; else -> "draw" }
    }

    /** Estado da partida [n] a partir das mensagens (ordem do tópico, com a hora de chegada). */
    fun state(msgs: List<Timed>, n: Int, me: String, opp: String?, total: Int, now: Long, oppLeft: Boolean): State {
        val rounds = HashMap<Int, Timed>()
        val stops = HashMap<Int, Timed>()
        val ans = HashMap<Pair<Int, String>, Timed>()
        val votes = HashMap<Pair<Int, String>, Timed>()
        var lastK = -1
        for (t in msgs) when (val m = t.msg) {
            // Só aceita a letra seguinte (k = anterior + 1, k < N) da partida de agora — igual ao site.
            is Msg.SRound -> if (m.n == n && m.k == lastK + 1 && m.k < total) { rounds[m.k] = t; lastK = m.k }
            is Msg.Stop -> if (m.n == n && m.k !in stops) stops[m.k] = t
            is Msg.Ans -> if (m.n == n && (m.k to m.id) !in ans) ans[m.k to m.id] = t
            is Msg.Vote -> if (m.n == n && (m.k to m.id) !in votes) votes[m.k to m.id] = t
            else -> {}
        }
        val first = rounds[0]?.msg as Msg.SRound?
        val cats = first?.cats.orEmpty()
        val out = ArrayList<Round>()
        for (k in 0 until total) {
            val sr = rounds[k] ?: break
            val m = sr.msg as Msg.SRound
            // Só começa a rodada seguinte depois de a anterior fechar (letra adiantada fica guardada).
            if (k > 0 && out[k - 1].resolvedAt == null) break
            val c = m.cats.ifEmpty { cats }
            val stop = stops[k]
            val myAns = ans[k to me]
            val theirAns = opp?.let { ans[k to it] }
            val stopAt = stop?.at ?: Long.MAX_VALUE
            val gone = theirAns == null && stop != null && (oppLeft || opp == null || now >= stopAt + ANS_WAIT_MS)
            val mine = (myAns?.msg as Msg.Ans?)?.answers?.let { answers(it, c.size) }
            val theirs = (theirAns?.msg as Msg.Ans?)?.answers?.let { answers(it, c.size) } ?: if (gone) List(c.size) { "" } else null
            val voteStart = when {
                mine == null || theirs == null -> Long.MAX_VALUE
                gone -> maxOf(myAns!!.at, if (oppLeft) stopAt else stopAt + ANS_WAIT_MS)
                else -> maxOf(myAns!!.at, theirAns!!.at)
            }
            val mv = votes[k to me]
            val tv = opp?.let { votes[k to it] }
            val deadline = if (voteStart == Long.MAX_VALUE) Long.MAX_VALUE else voteStart + VOTE_MS + VOTE_GRACE_MS
            val resolvedAt = when {
                voteStart == Long.MAX_VALUE -> null
                mv != null && tv != null -> maxOf(mv.at, tv.at)
                mv != null && (gone || oppLeft) -> mv.at
                now >= deadline -> deadline
                else -> null
            }
            val myVote = (mv?.msg as Msg.Vote?)?.votes
            val theirVote = (tv?.msg as Msg.Vote?)?.votes
            val (a, b) = if (resolvedAt != null && mine != null && theirs != null) score(mine, theirs, m.letter, theirVote, myVote, c.size)
            else emptyList<Int>() to emptyList()
            out += Round(k, m.letter, c, sr.at, stop?.msg as Msg.Stop?, stopAt, mine, theirs, gone, voteStart, myVote, theirVote, resolvedAt, a, b)
        }
        return State(out, total, cats, oppLeft)
    }
}
