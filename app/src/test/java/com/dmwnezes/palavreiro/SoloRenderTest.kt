package com.dmwnezes.palavreiro

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.MemLevel
import com.dmwnezes.palavreiro.game.MemoryGame
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.TriviaCat
import com.dmwnezes.palavreiro.game.TriviaData
import com.dmwnezes.palavreiro.game.TriviaGame
import com.dmwnezes.palavreiro.game.TriviaGame.Phase
import com.dmwnezes.palavreiro.system.DailyStatus
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.HomeScreen
import com.dmwnezes.palavreiro.ui.MemoryScreen
import com.dmwnezes.palavreiro.ui.Night
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.RecordsSection
import com.dmwnezes.palavreiro.ui.TriviaScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Quadros da rodada 11 (jogos solo): painel "Hoje" compacto, Trivia, Jogo da Memória e recordes novos. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class SoloRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private fun asset(n: String) = File("../shared/$n").readText()

    private fun save(name: String, advance: Long = 1500) {
        rule.mainClock.advanceTimeBy(advance)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val badges = mapOf(Dest.TERMO to "Novo", Dest.DUETO to "Feito hoje ✓", Dest.CONEXOES to "Novo", Dest.CACA to "Novo")

    // ---------- Painel "Hoje" ----------

    private fun home(name: String, st: DailyStatus) {
        rule.mainClock.autoAdvance = false
        val opened = mutableListOf<Dest>()
        rule.setContent { PalavreiroTheme { HomeScreen(badges, { opened += it }, {}, today = st) } }
        save(name)
        // Cada bolinha abre o seu jogo; o ícone abre o Arquivo.
        rule.onNodeWithContentDescription("Quarteto", substring = true).performClick()
        rule.onNodeWithContentDescription("Arquivo de desafios").performClick()
        assertEquals(listOf(Dest.QUARTETO, Dest.ARQUIVO), opened)
    }

    @Test fun hojeNenhum() = home("60a-hoje-0de5", DailyStatus(false, false, false, false, false, 0))

    @Test fun hojeTres() {
        home("60b-hoje-3de5", DailyStatus(termo = true, dueto = true, quarteto = false, conexoes = true, caca = false, streak = 3))
        assertTrue(rule.onAllNodes(hasContentDescription("3 de 5 desafios feitos hoje")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(hasContentDescription("Termo: feito")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(hasContentDescription("Caça-Palavras: falta fazer")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun hojeTudo() {
        home("60c-hoje-5de5", DailyStatus(true, true, true, true, true, 6))
        assertTrue(rule.onAllNodes(hasContentDescription("Tudo feito hoje")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun inicioJogosNovos() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { HomeScreen(badges, {}, {}, today = DailyStatus(true, false, false, true, false, 1), scroll = androidx.compose.foundation.ScrollState(1150)) } }
        save("60d-inicio-trivia-memoria")
    }

    // ---------- Trivia ----------

    private val seed get() = TriviaData.parse(asset("trivia.txt"))
    private fun generated() = TriviaData.parse(buildString {
        TriviaCat.entries.forEach { c -> repeat(30) { i -> appendLine("${c.label}|Pergunta extra $i de ${c.label}?|Certa $i|Errada A$i|Errada B$i|Errada C$i") } }
    })

    /** Joga certo até [stop]; na coroa escolhe a primeira categoria disponível. */
    private fun drive(g: TriviaGame, stop: (TriviaGame) -> Boolean) {
        var n = 0
        while (!stop(g) && !g.over && n++ < 500) {
            when (g.phase) {
                Phase.SPIN -> g.ask()
                Phase.QUESTION -> g.answer(g.question!!.right)
                Phase.ANSWERED -> g.next()
                Phase.CROWN -> g.chooseCrown(g.crownChoices.first())
                else -> {}
            }
        }
    }

    @Test
    fun triviaRoleta() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { TriviaScreen(seed, null, null, {}, seed = 3) } }
        save("61a-trivia-roleta", 700)
        save("61b-trivia-roleta-parou", 1000)
    }

    @Test
    fun triviaPergunta() {
        rule.mainClock.autoAdvance = false
        val g = TriviaGame(seed, Random(8))
        drive(g) { it.phase == Phase.QUESTION && it.right == 1 }
        rule.setContent { PalavreiroTheme { TriviaScreen(seed, null, null, {}, initial = g) } }
        save("61c-trivia-pergunta", 3000)
        // Resposta errada: a certa fica verde e a escolhida vermelha.
        rule.runOnUiThread { g.answer(g.question!!.wrong[0]) }
        save("61d-trivia-errou", 300)
    }

    @Test
    fun triviaCoroa() {
        rule.mainClock.autoAdvance = false
        val g = TriviaGame(generated(), Random(2))
        drive(g) { it.medals.size == 2 && it.phase == Phase.CROWN }
        assertEquals(Phase.CROWN, g.phase)
        rule.setContent { PalavreiroTheme { TriviaScreen(seed, null, null, {}, initial = g) } }
        save("61e-trivia-coroa")
    }

    @Test
    fun triviaMedalhas() {
        rule.mainClock.autoAdvance = false
        val real = seed
        val g = TriviaGame(real + generated(), Random(4))
        drive(g) { it.medals.size >= 4 && it.phase == Phase.QUESTION && it.crownRound }
        g.answer(g.question!!.right)
        rule.setContent { PalavreiroTheme { TriviaScreen(seed, null, null, {}, initial = g) } }
        save("61f-trivia-medalhas", 600)
        // Vitória: as 6 medalhas.
        val w = TriviaGame(generated(), Random(5))
        drive(w) { false }
        assertTrue(w.won)
    }

    @Test
    fun triviaVitoria() {
        rule.mainClock.autoAdvance = false
        val w = TriviaGame(generated(), Random(5))
        drive(w) { it.medals.size == 6 && it.phase == Phase.ANSWERED }
        rule.setContent { PalavreiroTheme { TriviaScreen(seed, null, null, {}, initial = w) } }
        save("61g-trivia-vitoria", 2600)
    }

    // ---------- Jogo da Memória ----------

    private val syn by lazy { QuizData.synonyms(asset("sinonimos.txt")) }

    private fun pairsOf(g: MemoryGame) = g.cards.indices.groupBy { g.cards[it].pair }.values.toList()

    @Test
    fun memoriaFacil() {
        rule.mainClock.autoAdvance = false
        val g = MemoryGame(MemLevel.FACIL, syn.first, syn.second, Random(6))
        val p = pairsOf(g)
        p.take(2).forEach { (a, b) -> g.flip(a); g.flip(b) }
        g.flip(p[2][0])
        rule.setContent { PalavreiroTheme { MemoryScreen(syn.first, syn.second, null, null, {}, initial = g, initialSeconds = 37) } }
        save("62a-memoria-facil")
    }

    @Test
    fun memoriaDificil() {
        rule.mainClock.autoAdvance = false
        val g = MemoryGame(MemLevel.DIFICIL, syn.first, syn.second, Random(12))
        val p = pairsOf(g)
        p.take(5).forEach { (a, b) -> g.flip(a); g.flip(b) }
        g.flip(p[6][0]); g.flip(p[7][0]) // par errado à mostra (vermelho)
        rule.setContent { PalavreiroTheme { MemoryScreen(syn.first, syn.second, null, null, {}, initial = g, initialSeconds = 84) } }
        save("62b-memoria-dificil", 500)
        // ~0,9 s depois as erradas viram de volta.
        save("62c-memoria-dificil-desvirou", 900)
        assertTrue(g.faceUp.isEmpty())
    }

    @Test
    fun memoriaVitoria() {
        rule.mainClock.autoAdvance = false
        val g = MemoryGame(MemLevel.MEDIO, syn.first, syn.second, Random(3))
        pairsOf(g).forEach { (a, b) -> g.flip(a); g.flip(b) }
        assertTrue(g.won)
        rule.setContent { PalavreiroTheme { MemoryScreen(syn.first, syn.second, null, null, {}, initial = g, initialSeconds = 71) } }
        save("62d-memoria-vitoria", 2000)
    }

    // ---------- Recordes ----------

    @Test
    @Config(qualifiers = "w360dp-h1500dp-xhdpi")
    fun recordesNovos() {
        rule.mainClock.autoAdvance = false
        val rec = Records(
            mapOf(
                "conn_won" to 14, "conn_perfect" to 6, "ws_best" to 95, "ws_played" to 9, "def_best" to 12, "def_right" to 57,
                "sa_best" to 23, "sa_tricks" to 8, "intr_best" to 9, "intr_right" to 40, "ort_best" to 18, "ort_right" to 66,
                "mestre_best" to 7, "mestre_right" to 30, "arch_played" to 5, "arch_won" to 4, "mp_won" to 3, "mp_played" to 7,
                "trivia_best" to 11, "trivia_won" to 2, "trivia_right" to 143, "mem_best_medio" to 71, "mem_best_medio_moves" to 14,
            )
        )
        val daily = Stats(played = 31, won = 27, streak = 4, maxStreak = 12)
        val inf = Stats(played = 60, won = 48, streak = 3, maxStreak = 9)
        rule.setContent {
            PalavreiroTheme {
                Box(Modifier.fillMaxSize().background(Night.background)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                        Column(Modifier.clip(com.dmwnezes.palavreiro.ui.Shapes.card).background(Night.surface).padding(18.dp)) { RecordsSection(daily, inf, rec) }
                    }
                }
            }
        }
        save("63-recordes-trivia-memoria")
    }
}
