package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.Rules
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class GameLogicTest {
    private val words = Words(File("../shared/words.js").readText())

    @Test
    fun listaTemMilRespostasDeCincoLetras() {
        assertEquals(1000, words.answers.size)
        assertTrue(words.answers.all { it.length == 5 && it.all { c -> c in 'A'..'Z' } })
    }

    @Test
    fun nomesNaoSaoAceitos() {
        for (n in listOf("MARIA", "PEDRO", "PAULO", "CHICO")) assertFalse(n, words.isAccepted(n))
        for (w in listOf("CARRO", "PEDRA", "AVIAO", "FALOU")) assertTrue(w, words.isAccepted(w))
        assertEquals("AVIÃO", words.display("AVIAO"))
    }

    @Test
    fun palavraDoDiaIgualAoSite() {
        // Valores calculados pelo game.js do site com a mesma lista.
        val expected = File("src/test/resources/daily-check.txt").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: return
        for (line in expected) {
            val (date, word) = line.split(' ')
            assertEquals(date, word, words.daily(LocalDate.parse(date)))
        }
    }

    @Test
    fun letrasRepetidas() {
        assertEquals(
            listOf(Mark.PRESENT, Mark.ABSENT, Mark.CORRECT, Mark.ABSENT, Mark.ABSENT),
            Rules.evaluate("ERRAR", "CERTO"),
        )
        // A segunda letra A não fica amarela se a resposta só tem um A.
        assertEquals(listOf(Mark.CORRECT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT), Rules.evaluate("AABBB", "ACCCC"))
    }

    @Test
    fun letraEmQualquerPosicao() {
        val g = TermoGame(Mode.INFINITO, words, store = null)
        g.select(4); g.type('O')
        g.select(1); g.type('A')
        g.type('R'); g.type('R')
        assertEquals(listOf(null, 'A', 'R', 'R', 'O'), g.current.toList())
        assertEquals(0, g.cursor) // pulou para o único espaço vazio
        g.type('C')
        g.submit()
        assertEquals("CARRO", g.guesses.single().word)
        g.finishReveal()
        assertEquals(1, g.currentRow)
    }

    @Test
    fun apagarRespeitaCursor() {
        val g = TermoGame(Mode.INFINITO, words, store = null)
        "MUNDO".forEach(g::type)
        g.select(2); g.delete()
        assertEquals(listOf('M', 'U', null, 'D', 'O'), g.current.toList())
        g.delete() // quadrado já vazio: apaga o anterior
        assertEquals(listOf('M', null, null, 'D', 'O'), g.current.toList())
    }

    @Test
    fun vitoriaEDerrota() {
        val g = TermoGame(Mode.INFINITO, words, store = null)
        g.answer.forEach(g::type); g.submit(); g.finishReveal()
        assertTrue(g.over && g.won)

        val h = TermoGame(Mode.INFINITO, words, store = null)
        val wrong = words.answers.first { it != h.answer }
        repeat(6) { wrong.forEach(h::type); h.submit(); h.finishReveal() }
        assertTrue(h.over); assertFalse(h.won)
    }

    @Test
    fun palavraInvalidaNaoConta() {
        val g = TermoGame(Mode.INFINITO, words, store = null)
        "XXXXX".forEach(g::type); g.submit()
        assertEquals(0, g.guesses.size); assertEquals(1, g.shakeTick)
    }
}
