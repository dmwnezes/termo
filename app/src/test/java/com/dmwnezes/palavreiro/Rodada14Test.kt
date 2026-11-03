package com.dmwnezes.palavreiro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.MaisMenos
import com.dmwnezes.palavreiro.game.Silhueta
import com.dmwnezes.palavreiro.ui.MaisMenosScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.SilhuetaScreen
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

/** Silhueta e Mais ou Menos: mesmos desafios do site e telas que respondem a cada jogada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class Rodada14Test {
    @get:Rule val rule = createComposeRule()
    private val items = Silhueta.parse(File("../shared/silhueta.txt").readText())
    private val cats = MaisMenos.parse(File("../shared/maismenos.txt").readText())
    private val date = LocalDate.of(2026, 11, 3)
    private fun store() = Store(RuntimeEnvironment.getApplication())
    private fun item(n: String) = items.first { it.name == n }

    @Test fun silhuetaIgualAoSite() {
        assertEquals(191, items.size)
        assertEquals(27, items.count { it.kind == "e" })
        assertEquals("Austrália", items[Silhueta.dailyIndex(306, items.size)].name) // valor do site
        items.forEach { assertTrue(it.name, it.rings.isNotEmpty() && it.rings.all { r -> r.size >= 3 }) }
        val h = Silhueta.hint(item("Ceará"), item("São Paulo"))
        assertTrue(h.km in 1800..2600)
        assertEquals(1, Silhueta.hint(item("Paraná"), item("Ceará")).dir)
        assertEquals(listOf("Ceará"), Silhueta.matches(items.filter { it.kind == "e" }, emptyList(), "cear").map { it.name })
    }

    @Test fun maisMenosIgualAoSite() {
        // Valores calculados por P.mmDaily no site.
        val expected = listOf("Arroz cozido/Alface", "Os Vingadores/De Volta para o Futuro", "Estanho/Tungstênio", "Voo do 14-Bis/Wikipédia", "Argentina/Portugal", "Gato/Coelho", "Bahia/Paraná", "Espanha/Brasil", "Yangtzé/Reno", "Elefante-africano/Avestruz")
        assertEquals(expected, MaisMenos.daily(cats, 306).map { cats[it.cat].items[it.a].name + "/" + cats[it.cat].items[it.b].name })
        assertEquals("130 kcal", cats.first { it.title.startsWith("Calorias") }.label(130))
        assertEquals("1997", cats.first { it.title.contains("filmes") }.label(1997))
    }

    @Test fun telaDaSilhuetaResponde() {
        val s = store()
        rule.setContent { PalavreiroTheme { SilhuetaScreen(items, s, null, {}, date) } }
        rule.onNode(hasSetTextAction()).performTextInput("Turq")
        rule.onNodeWithText("Turquia").performClick()
        rule.onNodeWithText("País · tentativa 2 de 6").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("Austr")
        rule.onNodeWithText("Austrália").performClick()
        rule.onNodeWithText("Acertou em 2 tentativas!").assertIsDisplayed()
        assertEquals(1, s.int("sil_won"))
        assertEquals("2026-11-03", s.text("sil_done"))
    }

    @Test fun telaDoMaisMenosVaiAteOFim() {
        val s = store()
        rule.setContent { PalavreiroTheme { MaisMenosScreen(cats, s, null, {}, date) } }
        for (i in 1..10) {
            rule.onNodeWithText("Rodada $i de 10", substring = true).assertIsDisplayed()
            val yearRound = i == 2 || i == 4
            rule.onNodeWithText(if (yearRound) "Mais recente" else "Mais").performClick()
            rule.onNodeWithText(if (i == 10) "Ver resultado" else "Próxima").performClick()
        }
        rule.onNodeWithText("acertos no desafio de hoje").assertIsDisplayed()
        assertEquals(1, s.int("mm_played"))
        assertEquals("2026-11-03", s.text("mm_done"))
    }
}
