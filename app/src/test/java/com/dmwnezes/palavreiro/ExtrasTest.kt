package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.game.Challenge
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExtrasTest {
    private val words = Words(File("../shared/words.js").readText())

    @Test
    fun desafioIdaEVolta() {
        for (w in listOf("PEDRA", "AVIÃO", "ZEBRA", "CHAVE")) {
            val code = Challenge.encode(w)
            assertEquals(Words.normalize(w), Challenge.decode(code))
            assertTrue("código não pode mostrar a palavra", !code.equals(Words.normalize(w), ignoreCase = true))
        }
        assertNull(Challenge.decode("abc"))
        val g = TermoGame(Mode.DESAFIO, words, store = null, challenge = "PEDRA")
        assertEquals("PEDRA", g.answer)
        "PEDRA".forEach(g::type); g.submit(); g.finishReveal()
        assertTrue(g.won)
    }

    @Test
    fun modoDificil() {
        val g = TermoGame(Mode.DESAFIO, words, store = null, challenge = "CARTA")
        assertTrue(g.setHard(true))
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        // C, A e R verdes: MUNDO não pode.
        "MUNDO".forEach(g::type); g.submit()
        assertEquals(1, g.rows.size)
        for (i in 0 until 5) g.delete()
        repeat(5) { g.delete() }
        g.select(0); "CARTA".forEach(g::type); g.submit(); g.finishReveal()
        assertTrue(g.won)
    }

    @Test
    fun dicaRevelaLetraCerta() {
        val g = TermoGame(Mode.DESAFIO, words, store = null, challenge = "PEDRA")
        val pos = g.hint()
        assertNotNull(pos)
        assertEquals("PEDRA"[pos!!], g.current[pos])
        g.hint()
        assertNull(g.hint()) // só 2 por partida
    }

    @Test
    fun todaRespostaTemSignificado() {
        val m = QuizData.meanings(File("../shared/significados.txt").readText())
        val missing = words.answers.filter { it !in m }
        assertTrue("sem significado: $missing", missing.isEmpty())
    }
}
