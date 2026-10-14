package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
import com.dmwnezes.palavreiro.ui.MultiScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.Collections

/** Xeque com amigo pela rede: rode com PV_LIVE=1 e PV_NTFY apontando para o ntfy de teste. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
@LooperMode(LooperMode.Mode.PAUSED)
class Rodada12FlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val words = Words(File("../shared/words.js").readText())

    private fun ntfy(): Ntfy {
        assumeTrue(System.getenv("PV_LIVE") == "1" && System.getenv("PV_NTFY") != null)
        return Ntfy(OkHttpClient(), System.getenv("PV_NTFY")!!)
    }
    private fun shown(text: String, sub: Boolean = false) = rule.onAllNodes(hasText(text, substring = sub)).fetchSemanticsNodes().isNotEmpty()
    private fun waitShown(text: String, ms: Long = 20_000, got: List<Msg>? = null) = try {
        rule.waitUntil(ms) { shown(text) }
    } catch (e: Throwable) {
        println("DEBUG esperando \"$text\" got=$got"); println(rule.onRoot().printToString()); throw e
    }

    /**
     * O app cria a sala de Xeque (1 partida) e joga de brancas (rodada 0, anfitrião); o convidado de mentira responde
     * pelo tópico com {"t":"move","u":…} e o app dá o mate do pastor.
     */

    /** Rola com o relógio livre só durante a rolagem: com ele parado, performScrollTo espera um quadro que nunca vem e trava. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollFree() = apply {
        val auto = rule.mainClock.autoAdvance
        rule.mainClock.autoAdvance = true
        performScrollTo()
        rule.mainClock.autoAdvance = auto
    }

    @Test
    fun xequeMateDoPastor() {
        val ntfy = ntfy()
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        rule.onNodeWithText("Xeque").scrollFree().performClick()
        rule.onNodeWithText("1 partida").scrollFree().performClick()
        rule.onNodeWithText("Seu nome").performTextInput("Teste")
        rule.onNodeWithText("Criar partida").scrollFree().performClick()
        rule.waitUntil(15_000) { shown("https://dmwnezes.com", true) }
        val link = rule.onAllNodes(hasText("https://dmwnezes.com", substring = true)).fetchSemanticsNodes().first()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        val room = Multi.fromPasted(link)!!
        assertEquals('k', room.mode)

        val guest = "guest001"
        val replies = listOf("e7e5", "b8c6", "g8f6")
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val bot = Thread {
            runCatching {
                runBlocking {
                    withTimeout(120_000) {
                        ntfy.events(room.topic).collect { e ->
                            if (e !is Ntfy.Event.Message) return@collect
                            val m = Multi.decode(e.text) ?: return@collect
                            got += m
                            if (m is Msg.UMove && m.id != guest) {
                                val k = m.n / 2
                                if (k >= replies.size) throw CancellationException("fim")
                                delay(200)
                                ntfy.publish(room.topic, Multi.encode(Msg.UMove(guest, m.n + 1, replies[k])))
                            }
                        }
                    }
                }
            }
        }.also { it.start() }
        rule.waitUntil(5_000) { got.any { it is Msg.Hello } }
        runBlocking { ntfy.publish(room.topic, Multi.encode(Msg.Hello(guest, "Ana", false))) }
        waitShown("Sua vez (brancas)", 30_000, got)

        val mine = listOf("e2e4", "d1h5", "f1c4", "h5f7")
        mine.forEachIndexed { i, u ->
            // Repete o toque até o lance sair (a contagem inicial segura o tabuleiro).
            rule.waitUntil(20_000) {
                if (got.none { it is Msg.UMove && it.id != guest && it.n == i * 2 }) {
                    rule.onNode(hasContentDescription("Casa ${u.substring(0, 2)}", substring = true)).performClick()
                    rule.onNode(hasContentDescription("Casa ${u.substring(2, 4)}", substring = true)).performClick()
                }
                got.any { it is Msg.UMove && it.id != guest && it.n == i * 2 }
            }
            if (i < replies.size) waitShown("Sua vez (brancas)", 20_000, got)
        }
        waitShown("Xeque-mate! Você venceu!", 20_000, got)
        bot.join(10_000)
        assertEquals(listOf("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7"), got.filterIsInstance<Msg.UMove>().map { it.uci })
        assertEquals(listOf(0, 2, 4, 6), got.filterIsInstance<Msg.UMove>().filter { it.id != guest }.map { it.n })
    }
}
