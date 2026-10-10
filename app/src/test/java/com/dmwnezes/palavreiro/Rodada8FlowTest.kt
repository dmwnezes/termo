package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.Collections

/** Rodada 8 pela rede: reações e revanche trocando de modo (rode com PV_LIVE=1 e PV_NTFY no ntfy de teste). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class Rodada8FlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val words = Words(File("../shared/words.js").readText())

    private fun ntfy() = Ntfy(OkHttpClient(), System.getenv("PV_NTFY") ?: "https://ntfy.sh")
    private fun shown(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Anda o relógio do Compose de 50 em 50 ms (com o relógio de verdade correndo) até [cond] valer. */
    private fun frames(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.mainClock.advanceTimeBy(50)
            if (cond()) return true
            Thread.sleep(20)
        }
        return false
    }

    /**
     * O app entra como convidado num Termo. O anfitrião (de mentira) manda uma reação velha (do histórico)
     * e uma fresca: só a fresca vira balão. Depois o app reage e o anfitrião recebe a mensagem certa.
     */
    @Test
    fun reacoes() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = ntfy()
        val room = Multi.newRoom('t')
        val hostId = "host0001"
        runBlocking {
            ntfy.publish(room.topic, Multi.encode(Msg.Hello(hostId, "Daniel", true)))
            // Reação velha no histórico (de uma partida anterior): não pode aparecer.
            ntfy.publish(room.topic, Multi.encode(Msg.React(hostId, "😈", System.currentTimeMillis() - 120_000)))
        }
        Thread.sleep(800)
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val host = Thread {
            runCatching {
                runBlocking {
                    withTimeout(60_000) {
                        ntfy.events(room.topic).collect { e ->
                            if (e !is Ntfy.Event.Message) return@collect
                            val m = Multi.decode(e.text) ?: return@collect
                            got += m
                            when {
                                m is Msg.Hello && !m.host -> {
                                    ntfy.publish(room.topic, Multi.encode(Msg.Start(System.currentTimeMillis(), m.id)))
                                    delay(1500)
                                    // Velha (passou dos 10 s) e fresca, uma atrás da outra.
                                    ntfy.publish(room.topic, Multi.encode(Msg.React(hostId, "😱", System.currentTimeMillis() - 15_000)))
                                    ntfy.publish(room.topic, Multi.encode(Msg.React(hostId, "🔥", System.currentTimeMillis())))
                                }
                                m is Msg.React && m.id != hostId -> throw CancellationException("ok")
                            }
                        }
                    }
                }
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        rule.waitUntil(20_000) { shown("Daniel te chamou para um Termo") }
        host.start()
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        rule.waitUntil(30_000) { shown("Você × Daniel") }
        // Os balões são animações curtas: aqui o relógio do Compose anda à mão (senão o waitUntil
        // roda a animação inteira de uma vez e o balão some antes de ser visto).
        rule.mainClock.autoAdvance = false
        // O balão da reação fresca aparece; as velhas, não.
        assertTrue(frames(20_000) { shown("🔥") })
        assertFalse(shown("😱"))
        assertFalse(shown("😈"))
        // O balão some sozinho (2,5 s).
        assertTrue(frames(10_000) { !shown("🔥") })
        // Reagir: abre a fileira, escolhe e manda (e o meu balão aparece com "Você").
        rule.onNode(hasContentDescription("Reagir")).performClick()
        assertTrue(frames(5_000) { shown("😂") && shown("👏") })
        val before = System.currentTimeMillis()
        rule.onNode(hasText("😂")).performClick()
        assertTrue(frames(5_000) { shown("Você") && !shown("👏") })
        rule.mainClock.autoAdvance = true
        host.join(20_000)
        val mine = got.filterIsInstance<Msg.React>().first { it.id != hostId }
        assertEquals("😂", mine.emoji)
        assertTrue(kotlin.math.abs(mine.at - before) < 10_000)
    }

    /**
     * O app cria um Termo; o convidado (de mentira) acerta logo. Na folha de fim o anfitrião escolhe
     * "Bomba" e toca em "Jogar Bomba-Relógio": vai o "again" com "m":"b" e os dois começam a Bomba-Relógio.
     */
    @Test
    fun revancheTrocandoDeModo() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = ntfy()
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        rule.onNodeWithText("Termo").performClick()
        rule.onNodeWithText("Seu nome").performTextInput("Teste")
        rule.onNodeWithText("Criar partida").performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("https://dmwnezes.github.io", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val link = rule.onAllNodes(hasText("https://dmwnezes.github.io", substring = true)).fetchSemanticsNodes().first()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        val room = Multi.fromPasted(link)!!
        assertEquals('t', room.mode)
        val guestId = "guest001"
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val guest = Thread {
            runCatching {
                runBlocking {
                    withTimeout(90_000) {
                        ntfy.events(room.topic).collect { e ->
                            if (e !is Ntfy.Event.Message) return@collect
                            val m = Multi.decode(e.text) ?: return@collect
                            got += m
                            when (m) {
                                is Msg.Start -> if (m.guest == guestId) {
                                    delay(3500)
                                    ntfy.publish(room.topic, Multi.encode(Msg.End(guestId, true, 2, 9000)))
                                }
                                is Msg.Again -> throw CancellationException("ok")
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
        guest.start()
        Thread.sleep(800)
        runBlocking { ntfy.publish(room.topic, Multi.encode(Msg.Hello(guestId, "Ana", false))) }
        rule.waitUntil(30_000) { shown("Você × Ana") }
        rule.waitUntil(30_000) { shown("Ana venceu a rodada") }
        assertTrue(shown("Próximo jogo"))
        assertTrue(shown("Próxima rodada"))
        rule.onNode(hasText("Bomba")).performClick()
        rule.waitUntil(5_000) { shown("Jogar Bomba-Relógio") }
        rule.onNodeWithText("Jogar Bomba-Relógio").performClick()
        // A Bomba-Relógio começa (série zerada) depois da contagem.
        rule.waitUntil(30_000) { shown("Sua vez!") || shown("Vez de Ana…") }
        assertTrue(shown("0 × 0"))
        guest.join(10_000)
        val again = got.filterIsInstance<Msg.Again>().first()
        assertEquals('b', again.mode)
        assertTrue(again.newSeries)
    }
}
