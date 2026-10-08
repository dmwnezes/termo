package com.dmwnezes.palavreiro.game

enum class Mark { CORRECT, PRESENT, ABSENT }

data class Guess(val word: String, val display: String, val marks: List<Mark>) {
    val solved: Boolean get() = marks.all { it == Mark.CORRECT }
}

object Rules {
    const val MAX_TRIES = 6

    /** Avalia em duas passadas, tratando letras repetidas como no Termo. */
    fun evaluate(guess: String, answer: String): List<Mark> {
        val result = MutableList(guess.length) { Mark.ABSENT }
        val left = HashMap<Char, Int>()
        for (i in guess.indices) {
            if (guess[i] == answer[i]) result[i] = Mark.CORRECT
            else left[answer[i]] = (left[answer[i]] ?: 0) + 1
        }
        for (i in guess.indices) {
            if (result[i] == Mark.CORRECT) continue
            val n = left[guess[i]] ?: 0
            if (n > 0) {
                result[i] = Mark.PRESENT
                left[guess[i]] = n - 1
            }
        }
        return result
    }

    /** Melhor informação conhecida de cada letra, para pintar o teclado. */
    fun keyboardMarks(guesses: List<Guess>): Map<Char, Mark> {
        val best = HashMap<Char, Mark>()
        fun rank(m: Mark) = when (m) { Mark.ABSENT -> 1; Mark.PRESENT -> 2; Mark.CORRECT -> 3 }
        for (g in guesses) for (i in g.word.indices) {
            val c = g.word[i]
            val m = g.marks[i]
            val cur = best[c]
            if (cur == null || rank(m) > rank(cur)) best[c] = m
        }
        return best
    }

    /** Texto de compartilhamento com os quadradinhos coloridos. */
    fun shareText(title: String, guesses: List<Guess>, won: Boolean): String {
        val score = if (won) "${guesses.size}/$MAX_TRIES" else "X/$MAX_TRIES"
        val grid = guesses.joinToString("\n") { g ->
            g.marks.joinToString("") { when (it) { Mark.CORRECT -> "🟩"; Mark.PRESENT -> "🟨"; Mark.ABSENT -> "⬛" } }
        }
        return "Palavreiro · $title $score\n\n$grid"
    }
}
