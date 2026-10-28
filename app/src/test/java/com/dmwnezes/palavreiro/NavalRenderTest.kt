package com.dmwnezes.palavreiro

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Naval
import com.dmwnezes.palavreiro.ui.Duel
import com.dmwnezes.palavreiro.ui.NavalPlay
import com.dmwnezes.palavreiro.ui.NavalSave
import com.dmwnezes.palavreiro.ui.NavalScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Quadros da Batalha Naval: solo no meio da partida e com amigo (resposta automática do tiro levado). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class NavalRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val t = 1_800_000_000_000L

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(1500)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun soloNoMeioDaPartida() {
        rule.mainClock.autoAdvance = false
        val rnd = Random(11)
        val me = Naval.place(rnd); val ai = Naval.place(rnd)
        val ps = ai[4] + listOf(ai[1][0], 0, 55, 99, 31).filter { c -> ai[4].none { it == c } }.distinct()
        val cs = listOf(me[0][0], me[0][1], 7, 63, 88, 21, 40).distinct().take(ps.size)
        rule.setContent { PalavreiroTheme { NavalScreen(null, null, {}, initial = NavalSave(Naval.Level.MEDIO, me, ai, ps, cs)) } }
        save("naval_solo")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun soloTelaBaixaSemRolar() {
        rule.mainClock.autoAdvance = false
        val rnd = Random(11)
        val me = Naval.place(rnd); val ai = Naval.place(rnd)
        rule.setContent { PalavreiroTheme { NavalScreen(null, null, {}, initial = NavalSave(Naval.Level.DIFICIL, me, ai, listOf(0, 55), listOf(7, 63))) } }
        save("naval_solo_640")
        // As frotas de baixo continuam na tela.
        rule.onNodeWithText("VOCÊ").assertIsDisplayed()
    }

    @Test
    fun posicionarNavio() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { NavalScreen(null, null, {}, rnd = Random(4)) } }
        rule.mainClock.advanceTimeBy(500)
        // Escolhe um navio (toque numa casa dele) e leva para outra casa livre.
        val fleet = Naval.place(Random(4))
        val ship = fleet[4]
        rule.onAllNodesWithContentDescription(Naval.name(ship[0]))[0].performClick()
        rule.mainClock.advanceTimeBy(300)
        save("naval_posicionar")
    }

    @Test
    fun comAmigoRespondeOTiro() {
        rule.mainClock.autoAdvance = false
        val fleet = Naval.place(Random(2))
        val sent = mutableListOf<Msg>()
        val d = Duel(
            myId = "me", oppId = "op", oppName = "Ana", isHost = true, round = 1, n = 0, localStart = t - 60_000, series = Multi.Series(0, 0, best = 3),
            online = true, oppLeft = false, send = { sent += it }, onDecided = {}, onAgain = {}, onLeave = {}, myName = "Daniel", next = null, clock = { t },
        )
        // Anfitrião atira primeiro (rodada par); o tiro 1 é do amigo, num navio meu.
        val msgs = listOf(Msg.Ready("me"), Msg.Ready("op"), Msg.Shot("me", 0, 44), Msg.Res("op", 0, 44, true, 1, emptyList(), false), Msg.Shot("op", 1, fleet[0][0]))
        rule.setContent { PalavreiroTheme { NavalPlay(msgs, d, initialFleet = fleet) } }
        save("naval_amigo")
        val res = sent.filterIsInstance<Msg.Res>().single()
        assertEquals(1, res.n); assertTrue(res.hit); assertEquals(0, res.ship)
    }
}
