package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dmwnezes.palavreiro.game.Anagram
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
import com.dmwnezes.palavreiro.ui.MultiScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

/** App (convidado) contra o site (anfitrião): rode com PV_INTEROP=<arquivo com o link> e PV_NTFY. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class InteropTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val words = Words(File("../shared/words.js").readText())
    private fun shown(t: String, sub: Boolean = false) = rule.onAllNodes(hasText(t, substring = sub)).fetchSemanticsNodes().isNotEmpty()
    private fun typeWord(w: String) {
        w.forEach { ch -> rule.onAllNodes(hasText(ch.toString()) and hasClickAction()).onFirst().performClick() }
        rule.onAllNodes(hasText("ENTER") and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }
    private fun clear() = repeat(5) { runCatching { rule.onAllNodes(hasContentDescription("Apagar")).onFirst().performClick() } }
    /** Espera de verdade bombeando a tela (Thread.sleep congelaria o relógio do Compose). */
    private fun pause(ms: Long) { val t = System.currentTimeMillis() + ms; rule.waitUntil(ms + 5_000) { System.currentTimeMillis() >= t } }
    private fun log(s: String) = File(System.getenv("PV_INTEROP") + ".app.log").appendText(s + "\n")

    @Test
    fun appContraSite() {
        val path = System.getenv("PV_INTEROP"); assumeTrue(path != null)
        val f = File(path!!)
        val deadline = System.currentTimeMillis() + 60_000
        while (!f.exists() && System.currentTimeMillis() < deadline) Thread.sleep(300)
        val room = Multi.fromPasted(f.readText())!!
        log("sala ${room.code}")
        val ntfy = Ntfy(OkHttpClient(), System.getenv("PV_NTFY")!!)
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        rule.waitUntil(20_000) { shown("te chamou", true) }
        rule.onNodeWithText("Seu nome").performTextInput("App")
        rule.onNodeWithText("Entrar").performClick()
        rule.waitUntil(30_000) { shown("Você × Site") }
        log("partida começou")
        if (System.getenv("PV_R8") == "1") {
            // Reação do site chega como balão (avança o relógio à mão para ver o balão antes de sumir).
            rule.mainClock.autoAdvance = false
            var saw = false
            val until = System.currentTimeMillis() + 30_000
            while (!saw && System.currentTimeMillis() < until) { rule.mainClock.advanceTimeBy(100); Thread.sleep(100); saw = shown("🔥") }
            rule.mainClock.autoAdvance = true
            log("app viu 🔥 do site: $saw")
            rule.onNode(androidx.compose.ui.test.hasContentDescription("Reagir")).performClick()
            rule.onAllNodes(hasText("😂") and hasClickAction()).onFirst().performClick()
            log("app mandou 😂")
            rule.waitUntil(90_000) { shown("venceu a rodada", true) }
            log("fim da bomba no app")
            rule.waitUntil(30_000) { shown("rodada 1/10") }
            log("app trocou para o Anagrama: ${shown("Novo jogo: Anagrama")} (toast) ")
            return
        }
        if (room.mode == 'b') {
            val bank = ArrayDeque(listOf("PORTA", "MUNDO", "TEMPO", "LIVRO", "PEDRA", "FESTA", "PRAIA", "NOITE", "CAMPO", "PLANO", "GENTE", "FORTE", "VERDE", "LARGO", "NUVEM", "FOLHA", "TERRA", "CORPO", "BARCO", "PRATO"))
            val end = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < end && !shown("A bomba explodiu com", true)) {
                if (shown("Sua vez!") && bank.isNotEmpty()) {
                    val w = bank.removeFirst(); typeWord(w); pause(400)
                    val msgs = listOf("Essa palavra já foi", "Palavra não aceita", "Só palavras de 5 letras").filter { shown(it) }
                    val tiles = rule.onAllNodes(hasText(w.substring(0, 1))).fetchSemanticsNodes().size
                    if (msgs.isNotEmpty()) { log("recusada $w $msgs"); clear() } else log("app jogou $w (nós com ${w[0]}: $tiles)")
                }
                pause(300)
            }
            rule.waitUntil(15_000) { shown("venceu a rodada", true) }
            log("resultado: " + (if (shown("A bomba explodiu com você!")) "explodiu com o app" else "explodiu com o site") +
                " | " + (if (shown("Você venceu a rodada!")) "app venceu" else "site venceu") + " | " +
                (if (shown("palavras na rodada", true)) rule.onAllNodes(hasText("palavras na rodada", substring = true)).fetchSemanticsNodes().first().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "?"))
        } else {
            val answers = Anagram.words(words.answers, room.seed)
            // O app tenta as rodadas ímpares; as pares são do site.
            val end = System.currentTimeMillis() + 200_000
            var r = 1
            while (r < 10 && System.currentTimeMillis() < end) {
                if (shown("rodada ${r + 1}/10")) {
                    pause(1200); typeWord(answers[r]); log("app acertou rodada $r? ${answers[r]}"); r += 2
                }
                pause(300)
            }
            rule.waitUntil(80_000) { shown("Você venceu", true) || shown("Site venceu") || shown("Empate") }
            log("fim anagrama: " + listOf("Você venceu! 🏆", "Site venceu", "Empate", "5 × 5", "4 × 6", "6 × 4").filter { shown(it, true) })
        }
    }
}
