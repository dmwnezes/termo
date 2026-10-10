package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dmwnezes.palavreiro.game.Anagram
import com.dmwnezes.palavreiro.game.Bomb
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

/** Bomba-Relógio e Anagrama pela rede (rode com PV_LIVE=1 e PV_NTFY apontando para o ntfy de teste). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MultiGamesFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val words = Words(File("../shared/words.js").readText())

    private fun ntfy() = Ntfy(OkHttpClient(), System.getenv("PV_NTFY") ?: "https://ntfy.sh")
    private fun shown(text: String, substring: Boolean = false) = rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun typeWord(w: String) {
        w.forEach { ch -> rule.onAllNodes(hasText(ch.toString()) and hasClickAction()).onFirst().performClick() }
        rule.onAllNodes(hasText("ENTER") and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    /**
     * Bomba: o app entra como convidado; o anfitrião (de mentira) joga uma palavra, o app responde,
     * o anfitrião joga outra e a bomba explode com o app (que manda o "boom" dele).
     */
    @Test
    fun bombaExplode() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = ntfy()
        // SEED 0: pavio de 25 s e o anfitrião começa.
        val room = Multi.Room(Multi.randomId(12), 'b', 0)
        assertEquals(25_000L, Bomb.fuse(0)); assertTrue(Bomb.hostStarts(0))
        val hostId = "host0001"
        runBlocking { ntfy.publish(room.topic, Multi.encode(Msg.Hello(hostId, "Daniel", true))) }
        Thread.sleep(1000)
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val host = Thread {
            runCatching {
                runBlocking {
                    withTimeout(70_000) {
                        var start = 0L
                        ntfy.events(room.topic).collect { e ->
                            if (e !is Ntfy.Event.Message) return@collect
                            val m = Multi.decode(e.text) ?: return@collect
                            got += m
                            when {
                                m is Msg.Hello && !m.host -> {
                                    val at = System.currentTimeMillis(); start = at + 3000
                                    ntfy.publish(room.topic, Multi.encode(Msg.Start(at, m.id)))
                                    delay(4000)
                                    ntfy.publish(room.topic, Multi.encode(Msg.Word(hostId, "CARRO", 0, System.currentTimeMillis() - start)))
                                }
                                m is Msg.Word && m.id != hostId && m.n == 1 -> {
                                    delay(800)
                                    ntfy.publish(room.topic, Multi.encode(Msg.Word(hostId, "MUNDO", 2, System.currentTimeMillis() - start)))
                                }
                                m is Msg.Boom -> throw CancellationException("ok")
                            }
                        }
                    }
                }
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        rule.waitUntil(20_000) { shown("Daniel te chamou para uma Bomba-Relógio") }
        host.start()
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        rule.waitUntil(30_000) { shown("Você × Daniel") }
        rule.waitUntil(20_000) { shown("Sua vez!") && shown("CARRO") }
        // Palavra repetida: aviso e não manda.
        typeWord("CARRO")
        rule.waitUntil(5_000) { shown("Essa palavra já foi") }
        // Apaga e manda uma palavra nova.
        repeat(5) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Apagar")).onFirst().performClick() }
        typeWord("PORTA")
        rule.waitUntil(15_000) { shown("MUNDO") && shown("3 palavras") }
        rule.waitUntil(15_000) { shown("Sua vez!") }
        // Ninguém mais joga: no pavio (25 s) a bomba explode com o app.
        rule.waitUntil(40_000) { shown("A bomba explodiu com você!") }
        rule.waitUntil(10_000) { shown("Daniel venceu a rodada") && shown("3 palavras na rodada") }
        host.join(10_000)
        val words = got.filterIsInstance<Msg.Word>()
        assertEquals(listOf("CARRO", "PORTA", "MUNDO"), words.map { it.word })
        val guestId = words[1].id
        assertEquals(1, words[1].n)
        assertEquals(Msg.Boom(guestId), got.filterIsInstance<Msg.Boom>().first())
    }

    /**
     * Anagrama: o app cria a sala; o convidado (de mentira) acerta todas as rodadas menos a 2ª,
     * que o app acerta digitando a palavra. No fim aparece a folha com o placar 1 × 9.
     */
    @Test
    fun anagramaAteOFim() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = ntfy()
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        rule.onNodeWithText("Anagrama").performClick()
        rule.onNodeWithText("Seu nome").performTextInput("Teste")
        rule.onNodeWithText("Criar partida").performClick()
        rule.waitUntil(15_000) { shown("https://dmwnezes.github.io", substring = true) }
        val link = rule.onAllNodes(hasText("https://dmwnezes.github.io", substring = true)).fetchSemanticsNodes().first()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        val room = Multi.fromPasted(link)!!
        assertEquals('a', room.mode)
        val answers = Anagram.words(words.answers, room.seed)
        val guestId = "guest001"
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val guest = Thread {
            runCatching {
                runBlocking {
                    withTimeout(120_000) {
                        val decided = HashSet<Int>()
                        ntfy.events(room.topic).collect { e ->
                            if (e !is Ntfy.Event.Message) return@collect
                            val m = Multi.decode(e.text) ?: return@collect
                            got += m
                            suspend fun solve(r: Int, wait: Long) {
                                if (r >= Anagram.ROUNDS || r == 1) return
                                delay(wait)
                                ntfy.publish(room.topic, Multi.encode(Msg.Solve(guestId, r, wait)))
                            }
                            when (m) {
                                is Msg.Start -> if (m.guest == guestId) solve(0, 4000)
                                is Msg.Solve -> if (decided.add(m.round)) { if (m.round == Anagram.ROUNDS - 1) throw CancellationException("fim"); solve(m.round + 1, Anagram.GAP_MS + 600) }
                                is Msg.Skip -> if (decided.add(m.round)) solve(m.round + 1, Anagram.GAP_MS + 600)
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
        // Rodada 1 é do convidado; a 2ª o app acerta (com um erro antes).
        rule.waitUntil(20_000) { shown("Ana acertou: ${words.display(answers[0])}") }
        rule.waitUntil(15_000) { shown("rodada 2/10") && shown("🔀 Embaralhar") }
        typeWord("CARRO".takeIf { !Anagram.sameLetters(it, answers[1]) } ?: "PORTA")
        rule.waitUntil(5_000) { shown("Não é essa") }
        typeWord(answers[1])
        rule.waitUntil(15_000) { shown("Você acertou! ${words.display(answers[1])}") }
        rule.waitUntil(90_000) { shown("Ana venceu") }
        assertTrue(shown("1 × 9"))
        assertTrue(shown("✓ você"))
        assertTrue(shown("Revanche"))
        guest.join(10_000)
        val solves = got.filterIsInstance<Msg.Solve>()
        assertEquals(1, solves.count { it.id != guestId && it.round == 1 })
    }
}
