package com.dmwnezes.palavreiro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Empilha
import com.dmwnezes.palavreiro.game.Placas
import com.dmwnezes.palavreiro.game.Respostas
import com.dmwnezes.palavreiro.ui.EmpilhaScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.PesquisaScreen
import com.dmwnezes.palavreiro.ui.PlacasScreen
import com.dmwnezes.palavreiro.ui.Top10Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Pesquisa, Top 10, Placas e Empilha: mesmas regras do site e telas que respondem a cada jogada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class Rodada16Test {
    @get:Rule val rule = createComposeRule()
    private val pesquisa = Respostas.parsePesquisa(File("../shared/pesquisa.txt").readText())
    private val top10 = Respostas.parseTop10(File("../shared/top10.txt").readText())
    private val placas = Placas.parse(File("../shared/placas.txt").readText())
    private val date = LocalDate.of(2026, 11, 3)
    private fun store() = Store(RuntimeEnvironment.getApplication())

    @Test fun respostasIguaisAoSite() {
        assertEquals(27, top10.size); assertEquals(40, pesquisa.size)
        assertTrue(top10.all { it.items.size == 10 })
        assertTrue(pesquisa.all { q -> q.items.sumOf { it.pts } == 100 })
        val t = top10[0].items
        assertEquals(0, Respostas.match(t, "russia"))
        assertEquals(2, Respostas.match(t, "  EUA "))
        assertEquals(4, Respostas.match(t, "o brasil"))
        assertEquals(-1, Respostas.match(t, "bras"))
        assertEquals(9, Respostas.match(top10[6].items, "guiana francesa"))
    }

    @Test fun placasIguaisAoSite() {
        assertEquals(44, placas.size)
        assertEquals(listOf(37, 39, 10, 18, 12, 3, 20, 38, 40, 29), Placas.daily(placas.size, 306)) // valores do site
        assertEquals(listOf(2, 0, 1, 3), Placas.order(5))
    }

    @Test fun empilhaRegras() {
        val g = Empilha()
        g.tick(0.1); assertTrue(g.x > 0)
        g.x = 20.0; g.drop()
        assertTrue(g.perfect); assertEquals(60.0, g.w, 0.001); assertEquals(1, g.score)
        g.x = 30.0; g.drop()
        assertEquals(50.0, g.w, 0.001); assertEquals(30.0, g.tower.last().x, 0.001)
        repeat(2000) { g.tick(0.016); assertTrue(g.x >= 0 && g.x + g.w <= 100.0001) }
        g.x = if (g.tower.last().x > 50) 0.0 else 100 - g.w
        g.w = 1.0
        g.tower[g.tower.lastIndex] = Empilha.Block(90.0, 1.0)
        g.x = 0.0
        g.drop()
        assertTrue(g.over)
    }

    @Test fun telaTop10Responde() {
        val s = store()
        rule.setContent { PalavreiroTheme { Top10Screen(top10, s, null, {}, date) } }
        val q = top10[Respostas.dailyIndex(306, top10.size)]
        rule.onNode(hasSetTextAction()).performTextInput(q.items[0].names[0])
        rule.onNodeWithText("OK").performClick()
        rule.onNodeWithText("3 erros restantes · 1 de 10").assertIsDisplayed()
        for (w in listOf("xxx", "yyy", "zzz")) {
            rule.onNode(hasSetTextAction()).performTextInput(w)
            rule.onNodeWithText("OK").performClick()
        }
        rule.onNodeWithText("Fim de jogo. · 1 de 10").assertIsDisplayed()
        assertEquals(1, s.int("t10_played"))
        assertEquals("2026-11-03", s.text("t10_done"))
    }

    @Test fun telaPesquisaCompleta() {
        val s = store()
        rule.setContent { PalavreiroTheme { PesquisaScreen(pesquisa, s, null, {}, date) } }
        val q = pesquisa[Respostas.dailyIndex(306, pesquisa.size)]
        q.items.forEach {
            rule.onNode(hasSetTextAction()).performTextInput(it.names.last())
            rule.onNodeWithText("OK").performClick()
        }
        rule.onNodeWithText("Completou tudo! 🎉 · 100 pontos").assertIsDisplayed()
        assertEquals(1, s.int("pq_full"))
        assertEquals(100, s.int("pq_best"))
    }

    @Test fun telaPlacasVaiAteOFim() {
        val s = store()
        rule.setContent { PalavreiroTheme { PlacasScreen(placas, s, null, {}, date) } }
        Placas.daily(placas.size, 306).forEachIndexed { i, qi ->
            rule.onNodeWithText("Pergunta ${i + 1} de 10", substring = true).assertIsDisplayed()
            rule.onNodeWithText(placas[qi].options[0]).performClick()
            rule.onNodeWithText(if (i == 9) "Ver resultado" else "Próxima").performClick()
        }
        rule.onNodeWithText("10/10").assertIsDisplayed()
        assertEquals(10, s.int("pl_day_best"))
    }

    @Test fun telaEmpilhaSoltaEFecha() {
        val s = store()
        rule.mainClock.autoAdvance = false // a animação nunca fica ociosa
        rule.setContent { PalavreiroTheme { EmpilhaScreen(s, null, {}) } }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Altura 0").assertIsDisplayed()
        var drops = 0
        while (s.int("emp_played") == 0 && drops < 500) {
            rule.mainClock.advanceTimeBy(337L + drops % 7 * 97)
            rule.onNodeWithText("Soltar").performClick()
            rule.mainClock.advanceTimeBy(32)
            drops++
        }
        assertEquals(1, s.int("emp_played"))
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Jogar de novo").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Altura 0").assertIsDisplayed()
    }
}
