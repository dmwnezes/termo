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
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.ConnectionsScreen
import com.dmwnezes.palavreiro.ui.WordSearchScreen
import com.dmwnezes.palavreiro.ui.ReverseScreen
import com.dmwnezes.palavreiro.ui.DefineScreen
import com.dmwnezes.palavreiro.ui.SynonymScreen
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.WordSearchData
import com.dmwnezes.palavreiro.game.QuizData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

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
        save("1a-bandeira", 500)
        save("1b-tremulando", 400)
        save("1c-transformando", 600)
        save("1d-quase", 300)
        save("1-abertura", 1000)
    }

    @Test
    fun inicio() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { HomeScreen(mapOf(Dest.TERMO to "Novo", Dest.DUETO to "Feito hoje ✓", Dest.CONEXOES to "Novo"), {}, {}) } }
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
        rule.setContent { PalavreiroTheme { ProfileScreen(null, mapOf(Mode.DIARIO to d, Mode.INFINITO to i), Records(connWon = 3, wsBest = 95, synBest = 12), {}, {}, {}) } }
        save("7-perfil")
    }

    @Test
    fun ajuda() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { HomeScreen(emptyMap(), {}, {}); HelpSheet {} } }
        save("8-ajuda")
    }

    private fun asset(n: String) = File("../shared/$n").readText()
    private val day = java.time.LocalDate.of(2026, 10, 9)

    @Test
    fun dueto() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DUETO, words, store = null) { day }
        for (w in listOf("CARRO", g.answers[0], "MUNDO")) { w.forEach(g::type); g.submit(); g.finishReveal() }
        g.type('P'); g.type('E')
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        save("9-dueto")
    }

    @Test
    fun quarteto() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.QUARTETO, words, store = null) { day }
        for (w in listOf("CARRO", "MUNDO", g.answers[2], "PEDRA")) { w.forEach(g::type); g.submit(); g.finishReveal() }
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        save("10-quarteto")
    }

    @Test
    fun conexoes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { ConnectionsScreen(ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), null, null, {}, day) } }
        save("11-conexoes")
    }

    @Test
    fun conexoesInfinito() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { ConnectionsScreen(ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), null, null, {}, day, startInfinite = true) } }
        save("11b-conexoes-infinito")
    }

    @Test
    fun caca() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { WordSearchScreen(WordSearchData.parse(asset("caca.txt")), WordSearchData.parse(asset("caca-extra.txt")), null, null, {}, day) } }
        save("12-caca")
    }

    @Test
    fun cacaInfinito() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { WordSearchScreen(WordSearchData.parse(asset("caca.txt")), WordSearchData.parse(asset("caca-extra.txt")), null, null, {}, day, startInfinite = true) } }
        save("12b-caca-infinito")
    }

    @Test
    fun reverso() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { ReverseScreen(words, null, null, {}) } }
        save("13-reverso")
    }

    @Test
    fun definicao() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { DefineScreen(QuizData.definitions(asset("definicoes.txt")), null, null, {}, seed = 4) } }
        save("14-definicao")
    }

    @Test
    fun sinonimos() {
        rule.mainClock.autoAdvance = false
        val (p, f) = QuizData.synonyms(asset("sinonimos.txt"))
        rule.setContent { PalavreiroTheme { SynonymScreen(p, f, null, null, {}, seed = 4) } }
        save("15-sinonimos")
    }

    @Test
    fun calendario() {
        rule.mainClock.autoAdvance = false
        val today = java.time.LocalDate.of(2026, 10, 9)
        val act = (1..9).associate { today.withDayOfMonth(it).toString() to (it % 4 + 1) } - today.withDayOfMonth(4).toString()
        val termo = (1..9).associate { today.withDayOfMonth(it).toString() to if (it == 6) "l" else "w" } - today.withDayOfMonth(4).toString()
        rule.setContent {
            PalavreiroTheme {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.background(com.dmwnezes.palavreiro.ui.Night.surface).padding(18.dp)
                ) { com.dmwnezes.palavreiro.ui.StreakCalendar(act, termo, today) }
            }
        }
        save("16-calendario")
    }

    @Test
    fun cartaoStories() {
        val g = TermoGame(Mode.DUETO, words, store = null) { day }
        for (w in listOf("CARRO", g.answers[0], "MUNDO", g.answers[1])) { w.forEach(g::type); g.submit(); g.finishReveal() }
        val bmp = com.dmwnezes.palavreiro.ui.StoryCard.render(rule.activity, com.dmwnezes.palavreiro.ui.storyFor(g, Stats(played = 9, won = 8, streak = 5, maxStreak = 6)))
        File(out, "17-stories.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Test
    fun resultadoComSignificado() {
        rule.mainClock.autoAdvance = false
        val meanings = com.dmwnezes.palavreiro.game.QuizData.meanings(asset("significados.txt"))
        val g = TermoGame(Mode.DIARIO, words, store = null) { day }
        g.setHard(true)
        g.hint()
        for (i in 0 until 5) if (g.current[i] == null) { g.select(i); g.type(g.answer[i]) }
        g.submit(); g.finishReveal()
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}, meanings) } }
        save("18-resultado")
    }
}
