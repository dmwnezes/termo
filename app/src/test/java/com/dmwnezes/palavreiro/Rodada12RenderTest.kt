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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Chess
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.SudokuData
import com.dmwnezes.palavreiro.game.SudokuGame
import com.dmwnezes.palavreiro.game.SudokuLevel
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
import com.dmwnezes.palavreiro.ui.ChessPlay
import com.dmwnezes.palavreiro.ui.ChessScreen
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.Duel
import com.dmwnezes.palavreiro.ui.HomeScreen
import com.dmwnezes.palavreiro.ui.MultiScreen
import com.dmwnezes.palavreiro.ui.Night
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.RecordsSection
import com.dmwnezes.palavreiro.ui.SudokuScreen
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rodada 12: Quadrante (Sudoku) e Xeque (Xadrez) desenhados em app/build/frames para conferência. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class Rodada12RenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val T = 1_800_000_000_000L
    private val sudoku = SudokuData.parse(File("../shared/sudoku.txt").readText())

    private fun save(name: String, advance: Long = 1500) {
        rule.mainClock.advanceTimeBy(advance)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun shown(t: String, sub: Boolean = false) = rule.onAllNodes(hasText(t, substring = sub)).fetchSemanticsNodes().isNotEmpty()

    private fun freshStore(): Store {
        val ctx: android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("palavreiro", 0).edit().clear().commit()
        return Store(ctx)
    }

    // ---------- Quadrante ----------
    /** Sudoku do dia 0 (Fácil) com [fill] casas vazias já certas, anotações e um erro. */
    private fun midGame(store: Store, fill: Int): SudokuGame {
        val p = SudokuData.daily(sudoku[SudokuLevel.FACIL]!!, 0)
        val g = SudokuGame(p)
        val empty = (0 until 81).filter { !g.given(it) }
        empty.take(fill).forEach { g.selected = it; g.input(p.solution[it] - '0') }
        val rest = empty.drop(fill)
        if (rest.size > 4) {
            g.notesMode = true
            rest.take(3).forEach { i -> g.selected = i; listOf(p.solution[i] - '0', 1 + (p.solution[i] - '0') % 9, 1 + (p.solution[i] - '0' + 3) % 9).forEach(g::input) }
            g.notesMode = false
            val w = rest[3]; g.selected = w; g.input(1 + (p.solution[w] - '0') % 9)
        }
        g.seconds = 263
        g.selected = empty[fill / 2]
        store.setText("sudoku_save_facil_dia", g.toJson())
        return g
    }


    /** Rola com o relógio livre só durante a rolagem: com ele parado, performScrollTo espera um quadro que nunca vem e trava. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollFree() = apply {
        val auto = rule.mainClock.autoAdvance
        rule.mainClock.autoAdvance = true
        performScrollTo()
        rule.mainClock.autoAdvance = auto
    }

    @Test
    fun quadranteMeioComAnotacoes() {
        rule.mainClock.autoAdvance = false
        val store = freshStore()
        store.setInt("sudoku_best_facil", 341)
        midGame(store, 20)
        rule.setContent { PalavreiroTheme { SudokuScreen(sudoku, store, null, {}, day = 0, initialLevel = SudokuLevel.FACIL) } }
        save("70a-quadrante-meio")
        assertTrue(shown("Quadrante")); assertTrue(shown("Erros 1/3")); assertTrue(shown("4:2", true))
    }

    @Test
    fun quadranteVitoria() {
        rule.mainClock.autoAdvance = false
        val store = freshStore()
        val p = SudokuData.daily(sudoku[SudokuLevel.FACIL]!!, 0)
        val g = SudokuGame(p)
        val empty = (0 until 81).filter { !g.given(it) }
        empty.dropLast(1).forEach { g.selected = it; g.input(p.solution[it] - '0') }
        g.seconds = 318
        store.setText("sudoku_save_facil_dia", g.toJson())
        rule.setContent { PalavreiroTheme { SudokuScreen(sudoku, store, null, {}, day = 0, initialLevel = SudokuLevel.FACIL) } }
        rule.mainClock.advanceTimeBy(500)
        val last = empty.last()
        rule.onNode(hasContentDescription("Casa ${last / 9 + 1}-${last % 9 + 1}")).performClick()
        rule.onNode(hasContentDescription("Número ${p.solution[last]}")).performClick()
        save("70b-quadrante-vitoria", 2500)
        assertTrue(shown("Quadrante resolvido! 🎉"))
        assertEquals(1, store.int("sudoku_won")); assertTrue(store.int("sudoku_best_facil") in 318..320)
    }

    // ---------- Xeque solo ----------
    @Test
    fun xequeSoloDestinos() {
        rule.mainClock.autoAdvance = false
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        rule.setContent { PalavreiroTheme { ChessScreen(null, null, {}, initial = Triple(Chess.Level.MEDIO, Chess.WHITE, moves), initialSel = Chess.square("f3")) } }
        save("71a-xeque-destinos")
        assertTrue(shown("Sua vez")); assertTrue(shown("Xeque")); assertTrue(shown("Nível Médio", true))
    }

    @Test
    fun xequeSoloPromocao() {
        rule.mainClock.autoAdvance = false
        val moves = listOf("e2e4", "d7d5", "e4d5", "c7c6", "d5c6", "g8f6", "c6b7", "e7e6")
        rule.setContent { PalavreiroTheme { ChessScreen(null, null, {}, initial = Triple(Chess.Level.FACIL, Chess.WHITE, moves), initialSel = Chess.square("b7"), initialPromo = "b7a8") } }
        save("71b-xeque-promocao")
        assertTrue(shown("Promover a"))
        assertTrue(shown(" +3")) // três peões pretos capturados
    }

    @Test
    fun xequeSoloXequeEPretas() {
        rule.mainClock.autoAdvance = false
        val moves = listOf("e2e4", "e7e5", "d2d3", "f8b4")
        rule.setContent { PalavreiroTheme { ChessScreen(null, null, {}, initial = Triple(Chess.Level.DIFICIL, Chess.WHITE, moves), initialSel = Chess.square("c2")) } }
        save("71c-xeque-em-xeque")
        assertTrue(shown("Xeque! Sua vez"))
    }

    @Test
    fun xequeSoloNovaPartida() {
        rule.mainClock.autoAdvance = false
        val store = freshStore()
        rule.setContent { PalavreiroTheme { ChessScreen(store, null, {}) } }
        save("71d-xeque-nova")
        rule.onNode(hasText("Pretas")).performClick()
        rule.onNode(hasText("Difícil")).performClick()
        rule.onNode(hasText("Começar")).performClick()
        // De pretas: o computador abre a partida sozinho.
        rule.mainClock.advanceTimeBy(1000)
        rule.waitUntil(10_000) { rule.mainClock.advanceTimeBy(100); shown("Sua vez") }
        save("71e-xeque-pretas")
        assertTrue(shown("você joga de pretas", true))
        assertTrue(store.text("xadrez_save")!!.contains("\"m\":\""))
    }

    // ---------- Xeque com amigo ----------
    private fun duel(n: Int, host: Boolean, sent: MutableList<Msg>? = null) = Duel(
        myId = "me", oppId = "op", oppName = "Ana", isHost = host, round = n + 1, n = n, localStart = T - 60_000, series = Multi.Series(1, 0, best = 3),
        online = true, oppLeft = false, send = { sent?.add(it) }, onDecided = {}, onAgain = {}, onLeave = {}, myName = "Daniel", next = null, clock = { T },
    )

    private fun umoves(white: String, black: String, vararg u: String) = u.mapIndexed { i, m -> Msg.UMove(if (i % 2 == 0) white else black, i, m) }

    @Test
    fun xequeComAmigoDePretas() {
        rule.mainClock.autoAdvance = false
        // Rodada 1 (ímpar): o anfitrião joga de pretas.
        val msgs = umoves("op", "me", "d2d4", "g8f6", "c2c4", "e7e6", "b1c3")
        val sent = mutableListOf<Msg>()
        rule.setContent { PalavreiroTheme { ChessPlay(msgs, duel(1, host = true, sent), initialSel = Chess.square("f8")) } }
        save("72a-xeque-amigo-pretas")
        assertTrue(shown("Sua vez (pretas)"))
        rule.onNode(hasContentDescription("Casa b4")).performClick()
        assertEquals(listOf<Msg>(Msg.UMove("me", 5, "f8b4")), sent)
    }

    @Test
    fun xequeComAmigoMate() {
        rule.mainClock.autoAdvance = false
        val msgs = umoves("me", "op", "e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")
        rule.setContent { PalavreiroTheme { ChessPlay(msgs, duel(0, host = true)) } }
        save("72b-xeque-amigo-mate", 2500)
        assertTrue(shown("Xeque-mate! Você venceu!"))
    }

    // ---------- tela inicial, recordes e tela de criar ----------
    @Test
    fun inicioERecordes() {
        rule.mainClock.autoAdvance = false
        val opened = mutableListOf<Dest>()
        rule.setContent { PalavreiroTheme { HomeScreen(emptyMap(), { opened += it }, {}, scroll = androidx.compose.foundation.ScrollState(1400)) } }
        rule.onNode(hasText("Lógica")).scrollFree().performClick() // Quadrante e Xeque ficam na aba Lógica
        rule.onNode(hasText("Quadrante")).scrollFree()
        save("73a-inicio-quadrante-xeque")
        rule.onNode(hasText("Quadrante")).performClick()
        rule.onNode(hasText("Xeque")).scrollFree().performClick()
        assertEquals(listOf(Dest.SUDOKU, Dest.XADREZ), opened)
    }

    @Test
    fun recordes() {
        rule.mainClock.autoAdvance = false
        val rec = Records(mapOf("sudoku_best_medio" to 512, "sudoku_won" to 4, "xadrez_won_facil" to 3, "xadrez_won_dificil" to 1))
        rule.setContent {
            PalavreiroTheme {
                Box(Modifier.fillMaxSize().background(Night.background)) {
                    Column(Modifier.verticalScroll(androidx.compose.foundation.ScrollState(1250)).padding(20.dp)) {
                        Column(Modifier.clip(com.dmwnezes.palavreiro.ui.Shapes.card).background(Night.surface).padding(18.dp)) { RecordsSection(Stats(), Stats(), rec) }
                    }
                }
            }
        }
        save("73b-recordes-quadrante-xeque")
        assertTrue(shown("8:32")); assertTrue(shown("1 no Difícil"))
    }

    @Test
    fun criarComXeque() {
        val words = Words(File("../shared/words.js").readText())
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, Ntfy(OkHttpClient(), "http://127.0.0.1:9"), null, {}) } }
        rule.onNode(hasText("Xeque")).scrollFree().performClick()
        assertTrue(shown("1 partida"))
        save("73c-criar-xeque")
    }
}
