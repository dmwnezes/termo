package com.dmwnezes.palavreiro

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.ui.GameScreen
import com.dmwnezes.palavreiro.ui.HelpSheet
import com.dmwnezes.palavreiro.ui.HomeScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.ProfileScreen
import com.dmwnezes.palavreiro.ui.SplashCredits
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Desenha as telas principais e salva imagens em app/build/frames para conferência. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class ScreensRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val words = Words(File("../shared/words.js").readText())

    private fun save(name: String, advance: Long = 1500) {
        rule.mainClock.advanceTimeBy(advance)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun abertura() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { SplashCredits {} } }
        save("1-abertura", 900)
    }

    @Test
    fun inicio() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { HomeScreen(dailyDone = false, dailyWon = false, onPlay = {}, onProfile = {}) } }
        save("2-inicio")
    }

    @Test
    fun jogoEmAndamento() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DIARIO, words, store = null) { java.time.LocalDate.of(2026, 10, 8) } // resposta: ÚNICO
        for (w in listOf("CARRO", "MUNDO")) { w.forEach(g::type); g.submit(); g.finishReveal() }
        g.select(1); g.type('N'); g.select(4); g.type('O')
        g.select(2)
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        save("3-jogo")
    }

    @Test
    fun vitoria() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.INFINITO, words, store = null)
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnUiThread { g.answer.forEach(g::type); g.submit() }
        save("4-revelando", 700)
        save("5a-t1500", 800)
        save("5b-t1900", 400)
        save("5c-t2400", 500)
        save("6-resultado", 2500)
    }

    @Test
    fun perfil() {
        rule.mainClock.autoAdvance = false
        val d = Stats(played = 12, won = 10, streak = 4, maxStreak = 7, firstTry = 1, dist = listOf(1, 2, 4, 2, 1, 0))
        val i = Stats(played = 30, won = 26, streak = 9, maxStreak = 11, dist = listOf(0, 3, 9, 8, 4, 2))
        rule.setContent { PalavreiroTheme { ProfileScreen(null, d, i, {}, {}, {}) } }
        save("7-perfil")
    }

    @Test
    fun ajuda() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { HomeScreen(true, true, {}, {}); HelpSheet {} } }
        save("8-ajuda")
    }
}
