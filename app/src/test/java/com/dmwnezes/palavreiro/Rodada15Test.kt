package com.dmwnezes.palavreiro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.CaminhosData
import com.dmwnezes.palavreiro.game.CaminhosGame
import com.dmwnezes.palavreiro.game.CaminhosQ
import com.dmwnezes.palavreiro.game.FatoMito
import com.dmwnezes.palavreiro.game.Minado
import com.dmwnezes.palavreiro.ui.CaminhosScreen
import com.dmwnezes.palavreiro.ui.FatoMitoScreen
import com.dmwnezes.palavreiro.ui.MinadoScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Campo Minado, Caminhos e Fato ou Mito: mesmos desafios do site e telas que respondem a cada jogada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class Rodada15Test {
    @get:Rule val rule = createComposeRule()
    private val caminhos = CaminhosData.parse(File("../shared/caminhos.txt").readText())
    private val fatos = FatoMito.parse(File("../shared/fatomito.txt").readText())
    private val date = LocalDate.of(2026, 11, 3)
    private fun store() = Store(RuntimeEnvironment.getApplication())

    @Test fun minadoIgualAoSite() {
        val a = Minado(0, 921)
        assertEquals(listOf(1, 4, 7, 11, 23, 27, 28, 35, 41, 50), a.mine.indices.filter { a.mine[it] }) // valores do site
        assertEquals(45, a.start)
        for (lv in 0..2) for (s in 0L until 300L) {
            val g = Minado(lv, s)
            assertEquals(Minado.LEVELS[lv].mines, g.mine.count { it })
            assertTrue(g.open[g.start]); assertFalse(g.lost)
        }
    }

    @Test fun minadoRegras() {
        val w = Minado(0, 7)
        w.mine.indices.filter { !w.mine[it] }.forEach(w::reveal)
        assertTrue(w.won)
        val l = Minado(0, 7)
        l.reveal(l.mine.indexOf(true))
        assertTrue(l.lost)
        val f = Minado(0, 7)
        val m = f.mine.indexOf(true)
        f.toggleFlag(m); f.reveal(m)
        assertFalse(f.lost)
        val back = Minado(0, 7).apply { toggleFlag(m) }
        val copy = Minado(0, 7).apply { restore(back.save().split(';')) }
        assertTrue(copy.flag[m])
    }

    @Test fun caminhosIgualAoSite() {
        assertEquals(450, caminhos.size)
        val pool = CaminhosData.ofLevel(caminhos, 1)
        assertEquals(CaminhosQ(7, listOf(listOf(16, 12), listOf(11, 27), listOf(20, 28), listOf(29, 37), listOf(30, 39), listOf(32, 34))), pool[CaminhosData.dailyIndex(306, pool.size)])
        val g = CaminhosGame(CaminhosQ(3, listOf(listOf(0, 8), listOf(1, 5))))
        g.start(0); g.dragTo(3); g.dragTo(6); g.dragTo(8)
        assertEquals(listOf(0, 3, 6, 7, 8), g.paths[0])
        assertTrue(g.connected(0))
        g.start(1); g.dragTo(2); g.dragTo(5)
        assertTrue(g.connected(1))
        assertFalse("falta uma casa", g.won)
        val cut = CaminhosGame(CaminhosQ(3, listOf(listOf(0, 2), listOf(6, 8))))
        cut.start(0); cut.dragTo(3); cut.dragTo(4)
        cut.start(6); cut.dragTo(7); cut.dragTo(4)
        assertEquals(listOf(0, 3), cut.paths[0])
        assertEquals(listOf(6, 7, 4), cut.paths[1])
        val blocked = CaminhosGame(CaminhosQ(3, listOf(listOf(0, 8), listOf(1, 5))))
        blocked.start(0)
        assertFalse(blocked.step(1))
        val full = CaminhosGame(CaminhosQ(2, listOf(listOf(0, 1))))
        full.start(0); full.dragTo(2); full.dragTo(3); full.dragTo(1)
        assertTrue(full.won)
        val saved = CaminhosGame(g.q).apply { restore(g.save()) }
        assertEquals(g.paths, saved.paths)
    }

    @Test fun fatoIgualAoSite() {
        assertEquals(100, fatos.size)
        assertEquals(50, fatos.count { it.fact })
        assertEquals(listOf(5, 27, 42, 46, 90, 72, 35, 48, 30, 4), FatoMito.daily(fatos.size, 306)) // valores do site
    }

    @Test fun telaDoMinadoRespondeEMarca() {
        val s = store()
        rule.setContent { PalavreiroTheme { MinadoScreen(s, null, {}, date) } }
        val g = Minado(0, 306L * 3)
        val closed = g.mine.indices.first { !g.open[it] && !g.mine[it] }
        rule.onNodeWithText("🚩 Bandeira").performClick()
        rule.onNodeWithContentDescription("Linha ${closed / g.w + 1}, coluna ${closed % g.w + 1}, fechada").performClick()
        rule.onNodeWithContentDescription("Linha ${closed / g.w + 1}, coluna ${closed % g.w + 1}, com bandeira").assertIsDisplayed()
        rule.onNodeWithText("9 minas restantes").assertIsDisplayed()
        rule.onNodeWithText("🚩 Bandeira ligada").performClick()
        val m = g.mine.indexOf(true)
        rule.onNodeWithContentDescription("Linha ${m / g.w + 1}, coluna ${m % g.w + 1}, fechada").performClick()
        rule.onNodeWithText("Boom! Pisou numa mina.").assertIsDisplayed()
        assertEquals(1, s.int("mina_played"))
        assertEquals("2026-11-03", s.text("mina_done"))
    }

    @Test fun telaDoCaminhosRespondeAoArrasto() {
        val s = store()
        val q = CaminhosQ(5, listOf(listOf(0, 4), listOf(5, 9), listOf(10, 14), listOf(15, 19), listOf(20, 24)))
        rule.setContent { PalavreiroTheme { CaminhosScreen(List(1) { q }, s, null, {}, date) } }
        for (y in 0 until 5) {
            rule.onNodeWithTag("caminhos").performTouchInput {
                val c = width / 5f
                down(androidx.compose.ui.geometry.Offset(c / 2, y * c + c / 2))
                moveTo(androidx.compose.ui.geometry.Offset(c * 1.5f, y * c + c / 2))
                moveTo(androidx.compose.ui.geometry.Offset(c * 4.5f, y * c + c / 2))
                up()
            }
            rule.waitForIdle()
        }
        rule.onNodeWithText("Grade completa", substring = true).assertIsDisplayed()
        assertEquals(1, s.int("cam_won"))
        assertEquals("2026-11-03", s.text("cam_done"))
    }

    @Test fun telaDoFatoVaiAteOFim() {
        val s = store()
        rule.setContent { PalavreiroTheme { FatoMitoScreen(fatos, s, null, {}, date) } }
        for (i in 1..10) {
            rule.onNodeWithText("Afirmação $i de 10", substring = true).assertIsDisplayed()
            rule.onNodeWithText("Fato").performClick()
            rule.onNodeWithText(if (i == 10) "Ver resultado" else "Próxima").performClick()
        }
        rule.onNodeWithText("acertos no desafio de hoje").assertIsDisplayed()
        assertEquals(1, s.int("fm_played"))
    }
}
