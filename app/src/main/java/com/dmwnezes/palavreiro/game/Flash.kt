package com.dmwnezes.palavreiro.game

import kotlin.random.Random

/**
 * Estado de uma rodada da "Palavra Relâmpago" (minijogo do widget): uma definição e 3 palavras.
 * [def]: índice da definição; [options]: índices das 3 definições cujas palavras viram botões
 * (uma delas é [def]); [chosen]: botão tocado (-1 = ainda não respondeu).
 */
data class FlashState(
    val def: Int,
    val options: List<Int>,
    val chosen: Int = -1,
    val streak: Int = 0,
    val best: Int = 0,
    val right: Int = 0,
    val played: Int = 0,
) {
    val answered: Boolean get() = chosen >= 0
    /** Botão da resposta certa. */
    val correctIndex: Int get() = options.indexOf(def)
    val wasRight: Boolean get() = answered && chosen == correctIndex
}

object Flash {
    const val OPTIONS = 3

    /**
     * Nova rodada: definição diferente da anterior e duas palavras erradas de tamanho parecido
     * (sem repetir palavra). Pular sem responder zera a sequência.
     */
    fun next(defs: List<Definition>, prev: FlashState?, rnd: Random = Random.Default): FlashState {
        require(defs.size >= OPTIONS) { "poucas definições" }
        var d: Int
        do { d = rnd.nextInt(defs.size) } while (defs.size > 1 && prev != null && d == prev.def)
        val answer = Words.normalize(defs[d].word)
        val others = defs.indices.filter { it != d && Words.normalize(defs[it].word) != answer }.shuffled(rnd)
        // Primeiro as de tamanho parecido (±2 letras), depois qualquer uma.
        val near = others.filter { kotlin.math.abs(defs[it].word.length - defs[d].word.length) <= 2 }
        val picked = ArrayList<Int>()
        for (i in near + others) {
            if (picked.size == OPTIONS - 1) break
            val w = Words.normalize(defs[i].word)
            if (i !in picked && picked.none { Words.normalize(defs[it].word) == w }) picked += i
        }
        val options = (picked + d).shuffled(rnd)
        val keepStreak = prev?.answered == true
        return FlashState(
            def = d, options = options,
            streak = if (keepStreak) prev!!.streak else 0,
            best = prev?.best ?: 0, right = prev?.right ?: 0, played = prev?.played ?: 0,
        )
    }

    /** Toque num botão: acerto soma na sequência, erro zera. Responder de novo não muda nada. */
    fun answer(s: FlashState, option: Int): FlashState {
        if (s.answered || option !in s.options.indices) return s
        val ok = option == s.correctIndex
        val streak = if (ok) s.streak + 1 else 0
        return s.copy(
            chosen = option, streak = streak, best = maxOf(s.best, streak),
            right = s.right + if (ok) 1 else 0, played = s.played + 1,
        )
    }

    /** Texto curto para guardar no SharedPreferences: "def;o1,o2,o3;chosen;streak;best;right;played". */
    fun encode(s: FlashState): String =
        listOf(s.def, s.options.joinToString(","), s.chosen, s.streak, s.best, s.right, s.played).joinToString(";")

    fun decode(v: String?, size: Int): FlashState? = runCatching {
        val p = v!!.split(';')
        val opts = p[1].split(',').map { it.toInt() }
        val s = FlashState(p[0].toInt(), opts, p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toInt())
        // Arquivo de definições mudou de tamanho: estado antigo não serve.
        if (opts.size != OPTIONS || (opts + s.def).any { it !in 0 until size } || s.def !in opts) null else s
    }.getOrNull()
}
