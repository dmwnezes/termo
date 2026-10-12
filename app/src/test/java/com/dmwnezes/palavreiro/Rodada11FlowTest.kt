package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import com.dmwnezes.palavreiro.game.Forca
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.TriviaDuel
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

/** Rodada 11 pela rede (Adedonha, Trivia e Forca): rode com PV_LIVE=1 e PV_NTFY apontando para o ntfy de teste. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
@LooperMode(LooperMode.Mode.PAUSED)
class Rodada11FlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val words = Words(File("../shared/words.js").readText())

    private fun ntfy(): Ntfy {
        assumeTrue(System.getenv("PV_LIVE") == "1" && System.getenv("PV_NTFY") != null)
        return Ntfy(OkHttpClient(), System.getenv("PV_NTFY")!!)
    }
    private fun shown(text: String, sub: Boolean = false) = rule.onAllNodes(hasText(text, substring = sub)).fetchSemanticsNodes().isNotEmpty()
    private fun tap(desc: String) = rule.onNode(hasContentDescription(desc)).performClick()
    private fun waitShown(text: String, ms: Long = 20_000, sub: Boolean = false, got: List<Msg>? = null) = try {
        rule.waitUntil(ms) { shown(text, sub) }
    } catch (e: Throwable) {
        println("DEBUG esperando \"$text\" got=$got"); println(rule.onRoot(useUnmergedTree = false).printToString()); throw e
    }

    /** Jogador de mentira: ouve a sala numa linha separada e responde; [handle] devolve true para parar. */
    private fun bot(ntfy: Ntfy, topic: String, got: MutableList<Msg>, handle: suspend (Msg) -> Boolean): Thread = Thread {
        runCatching {
            runBlocking {
                withTimeout(150_000) {
                    ntfy.events(topic).collect { e ->
                        if (e !is Ntfy.Event.Message) return@collect
                        val m = Multi.decode(e.text) ?: return@collect
                        got += m
                        if (handle(m)) throw CancellationException("fim")
                    }
                }
            }
        }
    }.also { it.start() }

    private fun pub(ntfy: Ntfy, topic: String, m: Msg) = runBlocking { ntfy.publish(topic, Multi.encode(m)) }

    /** O app cria a sala do jogo [label] com [rounds] e devolve a sala lida do link. */
    private fun createRoom(label: String, rounds: String): Multi.Room {
        rule.onNodeWithText(label).performScrollTo().performClick()
        rule.onNodeWithText(rounds).performScrollTo().performClick()
        rule.onNodeWithText("Seu nome").performTextInput("Teste")
        rule.onNodeWithText("Criar partida").performScrollTo().performClick()
        rule.waitUntil(15_000) { shown("https://dmwnezes.github.io", true) }
        val link = rule.onAllNodes(hasText("https://dmwnezes.github.io", substring = true)).fetchSemanticsNodes().first()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        return Multi.fromPasted(link)!!
    }

    /**
     * Adedonha: o app (anfitrião) sorteia a letra, preenche as 6 categorias e dá STOP; o convidado de mentira
     * responde (uma igual, uma vazia, uma sem a letra), os dois validam e o placar sai igual ao calculado.
     */
    @Test
    fun adedonhaRodada() {
        val ntfy = ntfy()
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        val room = createRoom("Adedonha", "3 rodadas")
        assertEquals('s', room.mode)
        val guestId = "guest001"
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        var mine: List<String> = emptyList()
        val t = bot(ntfy, room.topic, got) { m ->
            when {
                m is Msg.Stop && m.id != guestId -> {
                    val r = got.filterIsInstance<Msg.SRound>().first()
                    val l = r.letter
                    val bot = listOf(mine[0], "", "Xyz", "${l}bot3", "${l}bot4", "${l}bot5")
                    delay(300)
                    ntfy.publish(room.topic, Multi.encode(Msg.Ans(guestId, r.n, 0, bot)))
                    false
                }
                m is Msg.Ans && m.id != guestId -> {
                    delay(800)
                    ntfy.publish(room.topic, Multi.encode(Msg.Vote(guestId, m.n, 0, listOf(1, 1, 1, 1, 0, 1))))
                    false
                }
                else -> m is Msg.Vote && m.id != guestId
            }
        }
        rule.waitUntil(5_000) { got.any { it is Msg.Hello } }
        assertEquals(3, (got.first { it is Msg.Hello && it.host } as Msg.Hello).rounds)
        pub(ntfy, room.topic, Msg.Hello(guestId, "Ana", false))
        waitShown("Preencha tudo e dê STOP!", 30_000, got = got)
        val sr = got.filterIsInstance<Msg.SRound>().first()
        assertEquals(0, sr.n); assertEquals(0, sr.k); assertEquals(6, sr.cats.size)
        assertTrue(sr.letter in com.dmwnezes.palavreiro.game.Adedonha.LETTERS)
        val l = sr.letter
        mine = sr.cats.indices.map { "${l}teste$it" }
        sr.cats.forEachIndexed { i, c ->
            rule.onAllNodes(hasText(c) and hasSetTextAction()).onFirst().performScrollTo().performTextInput(mine[i])
        }
        rule.onNodeWithText("STOP!").performClick()
        waitShown("Você deu STOP!", got = got)
        waitShown("Valide as respostas de Ana", got = got)
        tap("Validar ${sr.cats[3]}")
        rule.onNodeWithText("Confirmar").performClick()
        waitShown("+45 × +25", 20_000, got = got)
        assertTrue(shown("45 × 25"))
        t.join(10_000)
        val ans = got.filterIsInstance<Msg.Ans>().first { it.id != guestId }
        assertEquals(mine, ans.answers)
        assertEquals(listOf(1, 0, 0, 0, 1, 1), got.filterIsInstance<Msg.Vote>().first { it.id != guestId }.votes)
        assertEquals(1, got.count { it is Msg.Stop })
    }

    /** Trivia: o app entra como convidado; três perguntas (acerta, erra, acerta) contra o anfitrião de mentira. */
    @Test
    fun trivia3Rodadas() {
        val ntfy = ntfy()
        val room = Multi.Room(Multi.randomId(12), 'g', 4242)
        val hostId = "host0001"
        val pool = TriviaDuel.parse(File("../shared/trivia.txt").readText())
        val items = TriviaDuel.pick(pool, room.seed, 5)
        pub(ntfy, room.topic, Msg.Hello(hostId, "Daniel", true, 5))
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val t = bot(ntfy, room.topic, got) { m ->
            when {
                m is Msg.Hello && !m.host -> { ntfy.publish(room.topic, Multi.encode(Msg.Start(System.currentTimeMillis(), m.id))); false }
                m is Msg.Pick && m.id != hostId -> {
                    val c = TriviaDuel.correctIndex(room.seed, m.round)
                    delay(300)
                    ntfy.publish(room.topic, Multi.encode(Msg.Pick(hostId, m.round, if (m.round == 2) (c + 1) % 4 else c, 3000L + m.round)))
                    m.round == 2
                }
                else -> false
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        waitShown("Daniel te chamou para uma Trivia · 5 rodadas", got = got)
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        val expect = listOf("Os dois acertaram!", "Daniel acertou", "Você acertou!")
        for (k in 0 until 3) {
            val q = items[k]
            waitShown(q.text, 30_000, got = got)
            val opts = TriviaDuel.options(q, room.seed, k)
            val c = TriviaDuel.correctIndex(room.seed, k)
            val pick = if (k == 1) (c + 2) % 4 else c
            rule.waitUntil(10_000) { rule.onAllNodes(hasText(opts[pick]) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodes(hasText(opts[pick]) and hasClickAction()).onFirst().performClick()
            waitShown(expect[k], 20_000, got = got)
        }
        assertTrue(shown("2 × 2"))
        t.join(10_000)
        val picks = got.filterIsInstance<Msg.Pick>().filter { it.id != hostId }
        assertEquals(listOf(0, 1, 2), picks.map { it.round })
        assertEquals(
            listOf(TriviaDuel.correctIndex(room.seed, 0), (TriviaDuel.correctIndex(room.seed, 1) + 2) % 4, TriviaDuel.correctIndex(room.seed, 2)),
            picks.map { it.option },
        )
        assertTrue(picks.all { it.ms in 0..TriviaDuel.ANSWER_MS })
    }

    /** Forca: o app adivinha (convidado na RODADA 0) contra o anfitrião de mentira que escolheu "Pão de queijo". */
    @Test
    fun forcaAdivinhando() {
        val ntfy = ntfy()
        val room = Multi.Room(Multi.randomId(12), 'f', 99)
        val hostId = "host0001"
        val w = "Pão de queijo"
        pub(ntfy, room.topic, Msg.Hello(hostId, "Daniel", true, 1))
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val t = bot(ntfy, room.topic, got) { m ->
            when {
                m is Msg.Hello && !m.host -> {
                    ntfy.publish(room.topic, Multi.encode(Msg.Start(System.currentTimeMillis(), m.id)))
                    delay(4000)
                    ntfy.publish(room.topic, Multi.encode(Msg.Secret(hostId, 0, "Comida", Forca.mask(w))))
                    false
                }
                m is Msg.Guess -> { ntfy.publish(room.topic, Multi.encode(Msg.Reveal(hostId, 0, m.letter, Forca.positions(w, m.letter)))); false }
                m is Msg.Try -> {
                    val ok = Forca.tryOk(m.word, w)
                    ntfy.publish(room.topic, Multi.encode(Msg.TryRes(0, ok)))
                    if (ok) ntfy.publish(room.topic, Multi.encode(Msg.Word(0, w)))
                    ok
                }
                else -> false
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        waitShown("Daniel te chamou para uma Forca", got = got)
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        waitShown("Tema: Comida", 30_000, got = got)
        tap("Letra A")
        rule.waitUntil(10_000) { got.count { it is Msg.Reveal } >= 1 }
        tap("Letra Z")
        rule.waitUntil(10_000) { got.count { it is Msg.Reveal } >= 2 }
        waitShown("Erros 1/6", got = got)
        tap("Letra O")
        rule.waitUntil(10_000) { got.count { it is Msg.Reveal } >= 3 }
        rule.onNodeWithText("Chutar a palavra").performClick()
        rule.onNode(hasText("Chute a palavra") and hasSetTextAction()).performTextInput("pao de mel")
        rule.onNodeWithText("Chutar").performClick()
        waitShown("Erros 2/6", got = got)
        rule.onNodeWithText("Chutar a palavra").performClick()
        rule.onNode(hasText("Chute a palavra") and hasSetTextAction()).performTextInput("pão-de-queijo")
        rule.onNodeWithText("Chutar").performClick()
        waitShown("Você adivinhou!", got = got)
        waitShown("Você venceu! 🏆", 10_000, got = got)
        assertTrue(shown("A palavra era PÃO DE QUEIJO"))
        t.join(10_000)
        assertEquals(listOf('A', 'Z', 'O'), got.filterIsInstance<Msg.Guess>().map { it.letter })
        assertEquals(listOf("PAO DE MEL", "PÃO-DE-QUEIJO"), got.filterIsInstance<Msg.Try>().map { it.word })
        assertTrue(got.filterIsInstance<Msg.Guess>().all { it.n == 0 })
    }

    /** Forca: o app (anfitrião) escolhe "Guarda-chuva"; o convidado de mentira tenta U, X e chuta certo. */
    @Test
    fun forcaEscolhendo() {
        val ntfy = ntfy()
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        val room = createRoom("Forca", "1 partida")
        val guestId = "guest001"
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val t = bot(ntfy, room.topic, got) { m ->
            when {
                m is Msg.Secret -> { delay(300); ntfy.publish(room.topic, Multi.encode(Msg.Guess(guestId, 0, 'U'))); false }
                m is Msg.Reveal && m.letter == 'U' -> { ntfy.publish(room.topic, Multi.encode(Msg.Guess(guestId, 0, 'X'))); false }
                m is Msg.Reveal && m.letter == 'X' -> { ntfy.publish(room.topic, Multi.encode(Msg.Try(guestId, 0, "GUARDA CHUVA"))); false }
                else -> m is Msg.Word
            }
        }
        rule.waitUntil(5_000) { got.any { it is Msg.Hello } }
        assertEquals(1, (got.first { it is Msg.Hello && it.host } as Msg.Hello).rounds)
        pub(ntfy, room.topic, Msg.Hello(guestId, "Ana", false))
        waitShown("Escolha a palavra", 30_000, got = got)
        rule.onNode(hasText("Palavra") and hasSetTextAction()).performTextInput("Guarda-chuva")
        rule.onNode(hasText("Tema") and hasSetTextAction()).performTextInput("Objeto")
        // A contagem 3-2-1 fica por cima e segura os toques.
        rule.waitUntil(10_000) { listOf("1", "2", "3").none { shown(it) } }
        rule.onNodeWithText("Pronto").performScrollTo().performClick()
        waitShown("Ana adivinhou!", 20_000, got = got)
        waitShown("Ana venceu", 10_000, got = got)
        t.join(10_000)
        val secret = got.filterIsInstance<Msg.Secret>().single()
        assertEquals(Msg.Secret(secret.id, 0, "Objeto", "......-....."), secret)
        val reveals = got.filterIsInstance<Msg.Reveal>()
        assertEquals(listOf('U' to listOf(1, 9), 'X' to emptyList<Int>()), reveals.map { it.letter to it.pos })
        assertTrue(reveals.all { it.id == secret.id })
        assertEquals(listOf(Msg.TryRes(0, true)), got.filterIsInstance<Msg.TryRes>())
        assertEquals(Msg.Word(0, "Guarda-chuva"), got.filterIsInstance<Msg.Word>().single())
    }
}
