package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.dmwnezes.palavreiro.game.Anagram
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

/** Anagrama pela rede (rode com PV_LIVE=1 e PV_NTFY apontando para o ntfy de teste). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
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
     * Anagrama: o app cria a sala; o convidado (de mentira) acerta todas as rodadas menos a 2ª,
     * que o app acerta digitando a palavra. No fim aparece a folha com o placar 1 × 9.
     */
    @Test
    fun anagramaAteOFim() {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = ntfy()
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        rule.onNodeWithText("Anagrama").performScrollTo().performClick()
        rule.onNodeWithText("Seu nome").performTextInput("Teste")
        rule.onNodeWithText("Criar partida").performScrollTo().performClick()
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
