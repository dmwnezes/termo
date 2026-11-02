package com.dmwnezes.palavreiro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Cruzada
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.ui.CruzadaScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.ProfileScreen
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class Rodada13RenderTest {
    @get:Rule val rule = createComposeRule()
    private val list = Cruzada.parse(File("../shared/cruzada.txt").readText())
    private fun store() = Store(RuntimeEnvironment.getApplication())

    @Test fun cruzadaDigitaConfereGuarda() {
        val s = store()
        val date = LocalDate.of(2026, 11, 3)
        rule.setContent { PalavreiroTheme { CruzadaScreen(list, s, null, {}, date) } }
        val q = list[Cruzada.dailyIndex(306, list.size)]
        rule.onNodeWithText(q.clues[1]).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Linha 1, coluna 1, vazia").performClick()
        rule.onNodeWithText("Conferir").performClick()
        rule.waitForIdle()
        assertTrue(s.text("cruz_state")!!.startsWith("2026-11-03;347;"))
    }

    @Test fun gradeMostraCadaJogada() {
        val s = store()
        rule.setContent { PalavreiroTheme { CruzadaScreen(list, s, null, {}, LocalDate.of(2026, 11, 3)) } }
        rule.onNodeWithText("Q").performClick()
        rule.onNodeWithText("Z").performClick()
        rule.onNodeWithContentDescription("Linha 1, coluna 1, Q").assertIsDisplayed()
        rule.onNodeWithContentDescription("Linha 1, coluna 2, Z").assertIsDisplayed()
        rule.onNodeWithContentDescription("Apagar").performClick()
        rule.onNodeWithContentDescription("Apagar").performClick()
        rule.onNodeWithContentDescription("Linha 1, coluna 2, vazia").assertIsDisplayed()
        // Tocar de novo na mesma casa troca para a vertical: a próxima letra desce.
        rule.onNodeWithContentDescription("Linha 3, coluna 1, vazia").performClick()
        rule.onNodeWithContentDescription("Linha 3, coluna 1, vazia").performClick()
        rule.onNodeWithText("X").performClick()
        rule.onNodeWithText("W").performClick()
        rule.onNodeWithContentDescription("Linha 3, coluna 1, X").assertIsDisplayed()
        rule.onNodeWithContentDescription("Linha 4, coluna 1, W").assertIsDisplayed()
    }

    @Test fun revelarTudoVenceEContaNoRecorde() {
        val s = store()
        rule.setContent { PalavreiroTheme { CruzadaScreen(list, s, null, {}, LocalDate.of(2026, 11, 3)) } }
        val q = list[347]
        for (y in 0 until 5) for (x in 0 until 5) if (q.letter(y, x) != null) {
            val letter = s.text("cruz_state")?.split(';')?.get(2)?.split('|')?.get(0)?.split('/')?.get(y)?.get(x)
            rule.onNodeWithContentDescription("Linha ${y + 1}, coluna ${x + 1}, ${if (letter == null || letter == ' ') "vazia" else letter}").performClick()
            rule.onNodeWithText("Revelar letra").performClick()
            rule.waitForIdle()
        }
        assertEquals(1, s.int("cruz_won"))
        assertEquals("2026-11-03", s.text("cruz_done"))
        rule.onNodeWithText("Compartilhar").assertIsDisplayed()
    }

    @Test fun perfilMostraRetrospectivaESincronizacao() {
        val s = store()
        s.setActivity(mapOf(LocalDate.now().toString() to 3))
        rule.setContent { PalavreiroTheme { ProfileScreen(s, Mode.entries.associateWith { Stats() }, Records(), {}, {}, {}) } }
        rule.onNodeWithText("Sua retrospectiva de ${LocalDate.now().year}").performClick()
        rule.onNodeWithText("Compartilhar nos Stories").assertIsDisplayed()
    }
}
