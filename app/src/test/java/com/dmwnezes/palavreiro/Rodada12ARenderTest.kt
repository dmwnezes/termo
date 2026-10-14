package com.dmwnezes.palavreiro

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.dmwnezes.palavreiro.game.AmpulhetaData
import com.dmwnezes.palavreiro.game.PodioData
import com.dmwnezes.palavreiro.ui.AmpulhetaIcon
import com.dmwnezes.palavreiro.ui.AmpulhetaScreen
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.HomeScreen
import com.dmwnezes.palavreiro.ui.MatizIcon
import com.dmwnezes.palavreiro.ui.MatizScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.PodioIcon
import com.dmwnezes.palavreiro.ui.PodioScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Rodada 12 (app A): Matiz, Pódio, Ampulheta e os cartões novos, desenhados em app/build/frames. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class Rodada12ARenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val day = LocalDate.of(2026, 10, 28) // dia 300
    private val podio = PodioData.parse(File("../shared/podio.txt").readText())
    private val amp = AmpulhetaData.parse(File("../shared/ampulheta.txt").readText())

    private fun save(name: String, advance: Long = 1500) {
        rule.mainClock.advanceTimeBy(advance)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun click(t: String) = rule.onAllNodes(hasText(t)).onFirst().performScrollTo().performClick()
    private fun shown(t: String) = rule.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun inicioCartoes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme {
                HomeScreen(mapOf(Dest.MATIZ to "Novo", Dest.PODIO to "Feito hoje ✓", Dest.AMPULHETA to "Novo"), {}, {})
            }
        }
        rule.onAllNodes(hasText("Ampulheta")).onFirst().performScrollTo()
        save("r12a-inicio-cartoes")
    }

    @Test
    fun icones() {
        rule.setContent {
            PalavreiroTheme {
                Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) { MatizIcon(); PodioIcon(); AmpulhetaIcon() }
            }
        }
        save("r12a-icones")
    }

    @Test
    fun matiz() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { MatizScreen(null, null, {}, date = day) } }
        save("r12a-matiz-1-mostra", 500)
        assertTrue(shown("Memorize esta cor"))
        rule.mainClock.advanceTimeBy(3000)
        save("r12a-matiz-2-recria")
        assertTrue(shown("Pronto"))
        click("Pronto")
        save("r12a-matiz-3-nota")
        assertTrue(shown("Original") && shown("Sua cor"))
        repeat(4) {
            click("Próxima cor"); rule.mainClock.advanceTimeBy(3500); click("Pronto")
        }
        rule.mainClock.advanceTimeBy(500)
        click("Ver resultado")
        save("r12a-matiz-4-fim")
        assertTrue(shown("/500"))
    }

    @Test
    fun podio() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { PodioScreen(podio, null, null, {}, date = day) } }
        save("r12a-podio-1-inicio")
        val q = podio[PodioData.dailyIndex(300, podio.size)]
        // 1ª tentativa: 2 do top trocados, 1 certo e 2 de fora.
        listOf(q.top[1], q.top[0], q.top[2], q.wrong[0], q.wrong[1]).forEach(::click)
        save("r12a-podio-2-cheio")
        click("Enviar")
        save("r12a-podio-3-tentativa")
        assertTrue(shown("Tentativa 2 de 5"))
        q.top.filter { it != q.top[2] }.forEach(::click)
        click("Enviar")
        save("r12a-podio-4-venceu")
        assertTrue(shown("Pódio completo!"))
    }

    @Test
    fun ampulheta() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { AmpulhetaScreen(amp, null, null, {}, date = day) } }
        save("r12a-amp-1-inicio")
        click("＋ Encaixar aqui")
        save("r12a-amp-2-jogada")
        assertTrue(shown("Certo!") || shown("Errou!"))
        while (shown("Encaixar aqui")) click("＋ Encaixar aqui")
        save("r12a-amp-3-fim", 2000)
        assertTrue(shown("Ampulheta do dia"))
    }

    @Test
    fun ampulhetaInfinito() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { Column { AmpulhetaScreen(amp, null, null, {}, date = day, seed = 7, startInfinite = true) } } }
        click("＋ Encaixar aqui")
        save("r12a-amp-4-infinito")
        assertTrue(shown("❤️") || shown("🤍"))
    }
}
