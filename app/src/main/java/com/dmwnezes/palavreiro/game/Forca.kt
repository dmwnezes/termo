package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.game.Multi.Msg

/**
 * Forca com amigo (modo "f", série melhor de N): um escolhe a palavra, o outro adivinha; os papéis trocam a cada rodada.
 * A palavra nunca trafega antes do fim: só a máscara, as posições de cada letra e o resultado dos chutes.
 */
object Forca {
    const val MAX_ERRORS = 6
    const val MIN_LETTERS = 2
    const val MAX_LETTERS = 20
    const val THEME_MAX = 24
    /** Sem "secret" depois desse tempo: quem adivinha vê "Esperando NOME escolher…" e pode sair. */
    const val WAIT_MS = 90_000L

    /** Na RODADA da sala par o anfitrião escolhe; ímpar, o convidado. */
    fun chooserIsHost(n: Int): Boolean = n % 2 == 0

    /** Palavra arrumada (NFC): sem espaços nas pontas, espaço repetido vira um, sem espaço em volta do hífen. */
    fun clean(w: String): String = java.text.Normalizer.normalize(w, java.text.Normalizer.Form.NFC)
        .trim().replace(Regex("\\s+"), " ").replace(Regex(" ?- ?"), "-")

    /** Letras (latinas, com acento valem) separadas por um espaço ou hífen; até 20 caracteres no total e pelo menos 2 letras. */
    fun validWord(w: String): Boolean {
        val c = clean(w)
        if (!c.matches(Regex("\\p{L}+([ -]\\p{L}+)*"))) return false
        val base = Words.normalize(c)
        if (base.length != c.length || base.any { it !in 'A'..'Z' && it != ' ' && it != '-' }) return false
        return c.length <= MAX_LETTERS && c.count { it.isLetter() } >= MIN_LETTERS
    }

    fun validTheme(t: String): Boolean = t.trim().length in 1..THEME_MAX

    /** Máscara: cada letra vira ".", espaço e hífen ficam. */
    fun mask(w: String): String = clean(w).map { if (it == ' ' || it == '-') it else '.' }.joinToString("")

    /** Posições da letra-base [letter] na palavra (A revela Á, Ã, Â, À; C revela Ç). */
    fun positions(w: String, letter: Char): List<Int> {
        val base = Words.normalize(clean(w))
        val l = letter.uppercaseChar()
        return base.indices.filter { base[it] == l }
    }

    /** Só as letras (sem acento, hífen nem espaço), para comparar o chute. */
    fun key(w: String): String = Words.normalize(w).filter { it in 'A'..'Z' }

    fun tryOk(guess: String, word: String): Boolean = key(word).isNotEmpty() && key(guess) == key(word)

    data class State(
        val secret: Msg.Secret?,
        /** Máscara com as letras-base já reveladas. */
        val shown: String,
        /** Letras já tocadas, na ordem. */
        val guessed: List<Char>,
        val hits: Set<Char>,
        val misses: Set<Char>,
        /** Chutes da palavra e o resultado (null = esperando). */
        val tries: List<Pair<String, Boolean?>>,
        val solved: Boolean,
        /** Palavra original (chega no fim). */
        val word: String?,
    ) {
        val errors: Int get() = misses.size + tries.count { it.second == false }
        val lost: Boolean get() = !solved && errors >= MAX_ERRORS
        val over: Boolean get() = solved || lost
        /** Letra já respondida (acerto ou erro). */
        fun answered(l: Char) = l in hits || l in misses
    }

    /** Estado da rodada [n] pelas mensagens na ordem do tópico (igual nos dois aparelhos). */
    fun state(msgs: List<Msg>, n: Int, chooser: String, guesser: String?): State {
        var secret: Msg.Secret? = null
        var shown = CharArray(0)
        val guessed = ArrayList<Char>()
        val hits = LinkedHashSet<Char>(); val misses = LinkedHashSet<Char>()
        val tries = ArrayList<Pair<String, Boolean?>>()
        var solved = false
        var word: String? = null
        fun over() = solved || misses.size + tries.count { it.second == false } >= MAX_ERRORS
        for (m in msgs) {
            when (m) {
                is Msg.Secret -> if (m.n == n && m.id == chooser && secret == null && m.mask.isNotEmpty()) { secret = m; shown = m.mask.toCharArray() }
                is Msg.Guess -> if (m.n == n && m.id == guesser && secret != null && !over() && m.letter !in guessed) guessed += m.letter
                is Msg.Reveal -> if (m.n == n && m.id == chooser && secret != null && !over() && m.letter !in hits && m.letter !in misses) {
                    val ok = m.pos.filter { it in shown.indices && secret!!.mask[it] == '.' }
                    if (ok.isEmpty()) misses += m.letter else { hits += m.letter; ok.forEach { shown[it] = m.letter } }
                    if (m.letter !in guessed) guessed += m.letter
                    if (shown.none { it == '.' }) solved = true
                }
                is Msg.Try -> if (m.n == n && m.id == guesser && secret != null && !over()) tries += m.word to null
                is Msg.TryRes -> if (m.n == n && !over()) {
                    val i = tries.indexOfFirst { it.second == null }
                    if (i >= 0) { tries[i] = tries[i].first to m.ok; if (m.ok) solved = true }
                }
                is Msg.Word -> if (m.n == n && word == null) word = m.word
                else -> {}
            }
        }
        return State(secret, String(shown), guessed, hits, misses, tries, solved, word)
    }
}
