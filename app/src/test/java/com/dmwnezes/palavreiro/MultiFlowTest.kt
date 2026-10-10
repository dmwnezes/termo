package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
import com.dmwnezes.palavreiro.ui.MultiScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

/** Fluxo completo de "Jogar com amigo" pela rede de verdade (rode com PV_LIVE=1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MultiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val words = Words(File("../shared/words.js").readText())

    @Test
    fun anfitriaoCriaEAmigoEntra() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = Ntfy(OkHttpClient(), System.getenv("PV_NTFY") ?: "https://ntfy.sh")
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        rule.onNodeWithText("Seu nome").performTextInput("Teste")
        rule.onNodeWithText("Criar partida").performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("https://dmwnezes.github.io", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val link = rule.onAllNodes(hasText("https://dmwnezes.github.io", substring = true)).fetchSemanticsNodes().first()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        val room = Multi.fromPasted(link)!!
        runBlocking { ntfy.publish(room.topic, Multi.encode(Multi.Msg.Hello("guest001", "Amigo", false))) }
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("Você × Amigo")).fetchSemanticsNodes().isNotEmpty() }
        // Partida começou: espera a contagem e joga uma tentativa.
        Thread.sleep(3500); rule.waitForIdle()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("ENTER")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun amigoEntraPeloLink() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = Ntfy(OkHttpClient(), System.getenv("PV_NTFY") ?: "https://ntfy.sh")
        val room = Multi.newRoom('q')
        runBlocking { ntfy.publish(room.topic, Multi.encode(Multi.Msg.Hello("host0001", "Daniel", true))) }
        Thread.sleep(1500)
        // "Anfitrião" de mentira: quando o convidado mandar hello, manda o start.
        val host = Thread {
            runBlocking {
                kotlinx.coroutines.withTimeout(40_000) {
                    ntfy.events(room.topic).collect { e ->
                        if (e is Ntfy.Event.Message) {
                            val m = Multi.decode(e.text)
                            if (m is Multi.Msg.Hello && !m.host) {
                                ntfy.publish(room.topic, Multi.encode(Multi.Msg.Start(System.currentTimeMillis(), m.id)))
                                ntfy.publish(room.topic, Multi.encode(Multi.Msg.Row("host0001", 0, "capap|aaaaa|ccccc|pppaa")))
                                throw kotlinx.coroutines.CancellationException("ok")
                            }
                        }
                    }
                }
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        rule.waitUntil(20_000) { rule.onAllNodes(hasText("Daniel te chamou", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        host.start()
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("Você × Daniel")).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(4000); rule.waitForIdle()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("jogando · 1/9")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Melhor de 3: o anfitrião (de mentira) vence duas rodadas e o troféu vai para ele. */
    @Test
    fun serieComTrofeu() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = Ntfy(OkHttpClient(), System.getenv("PV_NTFY") ?: "https://ntfy.sh")
        val room = Multi.newRoom('t')
        runBlocking { ntfy.publish(room.topic, Multi.encode(Multi.Msg.Hello("host0001", "Daniel", true))) }
        Thread.sleep(1500)
        val host = Thread {
            runBlocking {
                kotlinx.coroutines.withTimeout(60_000) {
                    ntfy.events(room.topic).collect { e ->
                        if (e is Ntfy.Event.Message) {
                            val m = Multi.decode(e.text)
                            if (m is Multi.Msg.Hello && !m.host) {
                                ntfy.publish(room.topic, Multi.encode(Multi.Msg.Start(System.currentTimeMillis(), m.id)))
                                ntfy.publish(room.topic, Multi.encode(Multi.Msg.End("host0001", true, 3, 30000)))
                                kotlinx.coroutines.delay(4000)
                                ntfy.publish(room.topic, Multi.encode(Multi.Msg.Again(Multi.newSeed(), System.currentTimeMillis())))
                                ntfy.publish(room.topic, Multi.encode(Multi.Msg.End("host0001", true, 2, 20000)))
                                throw kotlinx.coroutines.CancellationException("ok")
                            }
                        }
                    }
                }
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        rule.waitUntil(20_000) { rule.onAllNodes(hasText("Daniel te chamou", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        host.start()
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("0 × 1")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("Daniel levou a série")).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText("Melhor de 3 · 0 × 2")).fetchSemanticsNodes().let { assert(it.isNotEmpty()) }
    }

    /** O app foi fechado com a sala aberta; o amigo entrou nesse meio-tempo. Ao voltar, a partida começa (um start só). */
    @Test
    fun salaRetomada() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = Ntfy(OkHttpClient(), System.getenv("PV_NTFY") ?: "https://ntfy.sh")
        val room = Multi.newRoom('t')
        val hostId = "host" + Multi.randomId(4)
        runBlocking {
            ntfy.publish(room.topic, Multi.encode(Multi.Msg.Hello(hostId, "Daniel", true)))
            ntfy.publish(room.topic, Multi.encode(Multi.Msg.Hello("guest001", "Ana", false)))
        }
        Thread.sleep(1500)
        val session = Multi.Session(room.code, true, hostId, "Daniel", System.currentTimeMillis() - 60_000)
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, resume = session) } }
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("Você × Ana")).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(3000)
        val got = mutableListOf<Multi.Msg>()
        runBlocking { kotlinx.coroutines.withTimeoutOrNull(8000) { ntfy.events(room.topic).filterIsInstanceMsg().collect { got += it } } }
        val starts = got.count { it is Multi.Msg.Start }
        assertEquals(1, starts)
    }

    private fun kotlinx.coroutines.flow.Flow<Ntfy.Event>.filterIsInstanceMsg() =
        kotlinx.coroutines.flow.flow {
            collect { e -> if (e is Ntfy.Event.Message) Multi.decode(e.text)?.let { emit(it) } }
        }
}
