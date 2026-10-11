package com.dmwnezes.palavreiro

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.dmwnezes.palavreiro.game.HuntDuel
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.WordSearchData
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
import kotlin.random.Random

/** Rodada 10 pela rede (Velha, Damas e Caça-Palavras): rode com PV_LIVE=1 e PV_NTFY apontando para o ntfy de teste. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
@LooperMode(LooperMode.Mode.PAUSED)
class Rodada10FlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val words = Words(File("../shared/words.js").readText())

    private fun ntfy(): Ntfy {
        assumeTrue(System.getenv("PV_LIVE") == "1" && System.getenv("PV_NTFY") != null)
        return Ntfy(OkHttpClient(), System.getenv("PV_NTFY")!!)
    }
    private fun shown(text: String, sub: Boolean = false) = rule.onAllNodes(hasText(text, substring = sub)).fetchSemanticsNodes().isNotEmpty()
    private fun tap(desc: String) = rule.onNode(hasContentDescription(desc)).performClick()
    /** Espera a contagem 3-2-1 sumir. */
    private fun waitCountdown() = rule.waitUntil(10_000) { listOf("1", "2", "3").none { shown(it) } }

    /** Jogador de mentira: ouve a sala numa linha separada e responde; [handle] devolve true para parar. */
    private fun bot(ntfy: Ntfy, topic: String, got: MutableList<Msg>, handle: suspend (Msg) -> Boolean): Thread = Thread {
        runCatching {
            runBlocking {
                withTimeout(120_000) {
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

    /** O app cria a sala do modo [label] e devolve a sala lida do link. */
    private fun createRoom(label: String): Multi.Room {
        rule.onNodeWithText(label).performClick()
        rule.onNodeWithText("Seu nome").performTextInput("Teste")
        rule.onNodeWithText("Criar partida").performClick()
        rule.waitUntil(15_000) { shown("https://dmwnezes.github.io", true) }
        val link = rule.onAllNodes(hasText("https://dmwnezes.github.io", substring = true)).fetchSemanticsNodes().first()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        return Multi.fromPasted(link)!!
    }

    /**
     * Velha: o app cria a sala (anfitrião = X na RODADA 0), o convidado de mentira responde 3 e 4
     * e o app fecha a linha de cima (0, 1, 2).
     */
    @Test
    fun velhaAteAVitoria() {
        val ntfy = ntfy()
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}) } }
        val room = createRoom("Velha")
        assertEquals('v', room.mode)
        val guestId = "guest001"
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val replies = ArrayDeque(listOf(3, 4))
        val t = bot(ntfy, room.topic, got) { m ->
            if (m is Msg.Cell && m.id != guestId && replies.isNotEmpty()) {
                delay(400)
                ntfy.publish(room.topic, Multi.encode(Msg.Cell(guestId, m.n + 1, replies.removeFirst())))
            }
            m is Msg.Cell && m.id != guestId && m.n == 4
        }
        rule.waitUntil(5_000) { got.any { it is Msg.Hello } }
        pub(ntfy, room.topic, Msg.Hello(guestId, "Ana", false))
        rule.waitUntil(30_000) { shown("Sua vez (X)") }
        waitCountdown()
        assertTrue(shown("melhor de 3"))
        tap("Casa 0")
        rule.waitUntil(15_000) { shown("Vez de Ana (O)") }
        rule.waitUntil(15_000) { shown("Sua vez (X)") }
        tap("Casa 1")
        rule.waitUntil(15_000) { shown("Sua vez (X)") && got.count { it is Msg.Cell } >= 4 }
        tap("Casa 2")
        rule.waitUntil(15_000) { shown("Você venceu!") }
        rule.waitUntil(10_000) { shown("Você venceu a rodada!") }
        assertTrue(shown("Série: Você 1 × 0 Ana · melhor de 3"))
        t.join(10_000)
        val cells = got.filterIsInstance<Msg.Cell>()
        assertEquals(listOf(0, 3, 1, 4, 2), cells.map { it.cell })
        assertEquals(listOf(0, 1, 2, 3, 4), cells.map { it.n })
        assertEquals(3, (got.first { it is Msg.Hello && it.host } as Msg.Hello).rounds)
    }

    /**
     * Damas: o app entra como convidado (pretas, tabuleiro girado). Brancas 44-35, o app joga 21-28,
     * brancas capturam 35x21 e o app é obrigado a capturar de volta (14x28).
     */
    @Test
    fun damasAbertura() {
        val ntfy = ntfy()
        val room = Multi.Room(Multi.randomId(12), 'x', 4242)
        val hostId = "host0001"
        pub(ntfy, room.topic, Msg.Hello(hostId, "Daniel", true, 1))
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val t = bot(ntfy, room.topic, got) { m ->
            when {
                m is Msg.Hello && !m.host -> {
                    ntfy.publish(room.topic, Multi.encode(Msg.Start(System.currentTimeMillis(), m.id)))
                    delay(4000)
                    ntfy.publish(room.topic, Multi.encode(Msg.Move(hostId, 0, listOf(44, 35))))
                    false
                }
                m is Msg.Move && m.id != hostId && m.n == 1 -> {
                    delay(500)
                    ntfy.publish(room.topic, Multi.encode(Msg.Move(hostId, 2, listOf(35, 21))))
                    false
                }
                else -> m is Msg.Move && m.id != hostId && m.n == 3
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        rule.waitUntil(20_000) { shown("Daniel te chamou para uma partida de Damas") }
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        rule.waitUntil(30_000) { shown("Vez de Daniel (brancas)") }
        rule.waitUntil(20_000) { shown("Sua vez (pretas)") }
        waitCountdown()
        tap("Casa 21"); tap("Casa 28")
        try { rule.waitUntil(15_000) { shown("Captura obrigatória") } } catch (e: Throwable) {
            println("DEBUG got=$got"); println(rule.onRoot(useUnmergedTree = false).printToString()); throw e
        }
        assertTrue(shown("Sua vez (pretas)"))
        // Tocar numa peça que não pode capturar não faz nada; a captura vai em dois toques.
        tap("Casa 23"); tap("Casa 14"); tap("Casa 28")
        rule.waitUntil(15_000) { shown("Vez de Daniel (brancas)") && got.count { it is Msg.Move } >= 4 }
        assertTrue(shown("pretas · 11 peças", true) || shown("Você: pretas · 11 peças"))
        t.join(10_000)
        val moves = got.filterIsInstance<Msg.Move>()
        assertEquals(listOf(listOf(44, 35), listOf(21, 28), listOf(35, 21), listOf(14, 28)), moves.map { it.path })
        assertEquals(listOf(0, 1, 2, 3), moves.map { it.n })
    }

    /**
     * Caça-Palavras: o app entra como convidado; o anfitrião de mentira manda o start e a grade,
     * acha duas palavras, o app acha uma arrastando na grade e o anfitrião manda o "timeup".
     */
    @Test
    fun cacaDuelo() {
        val ntfy = ntfy()
        val room = Multi.Room(Multi.randomId(12), 'c', 777)
        val hostId = "host0001"
        val pool = WordSearchData.parse(File("../shared/caca.txt").readText()) + WordSearchData.parse(File("../shared/caca-extra.txt").readText())
        val grid = HuntDuel.build(pool, emptySet(), 0, Random(21))
        val keys = grid.words.map(HuntDuel::key)
        pub(ntfy, room.topic, Msg.Hello(hostId, "Daniel", true))
        val got = Collections.synchronizedList(mutableListOf<Msg>())
        val t = bot(ntfy, room.topic, got) { m ->
            when {
                m is Msg.Hello && !m.host -> {
                    ntfy.publish(room.topic, Multi.encode(Msg.Start(System.currentTimeMillis(), m.id)))
                    // A grade chega um pouco depois do start ("Preparando a grade…").
                    delay(4500)
                    ntfy.publish(room.topic, Multi.encode(grid))
                    delay(1500)
                    ntfy.publish(room.topic, Multi.encode(Msg.Found(hostId, 0, keys[1])))
                    ntfy.publish(room.topic, Multi.encode(Msg.Found(hostId, 0, keys[2])))
                    false
                }
                m is Msg.Found && m.id != hostId -> {
                    delay(500)
                    ntfy.publish(room.topic, Multi.encode(Msg.TimeUp(0)))
                    false
                }
                else -> m is Msg.TimeUp
            }
        }
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, ntfy, null, {}, joinCode = room.code) } }
        rule.waitUntil(20_000) { shown("Daniel te chamou para um Caça-Palavras · melhor de 3") }
        rule.onNodeWithText("Seu nome").performTextInput("Convidado")
        rule.onNodeWithText("Entrar").performClick()
        rule.waitUntil(30_000) { shown("Preparando a grade…") }
        rule.waitUntil(30_000) { shown(grid.theme) && !shown("Preparando a grade…") }
        rule.waitUntil(15_000) { shown("0 × 2") }
        // Arrasta da última letra para a primeira (de trás para frente também vale).
        val cells = HuntDuel.locate(grid.rows, grid.words[0])!!
        val node = rule.onNode(hasContentDescription("Grade"))
        node.performTouchInput {
            val c = width / 10f
            fun at(rc: Pair<Int, Int>) = Offset(rc.second * c + c / 2, rc.first * c + c / 2)
            down(at(cells.last()))
            val steps = 12
            for (i in 1..steps) moveTo(at(cells.last()) + (at(cells.first()) - at(cells.last())) * (i / steps.toFloat()))
            up()
        }
        try { rule.waitUntil(15_000) { got.any { it is Msg.Found && it.id != hostId } } } catch (e: Throwable) {
            println("DEBUG got=$got cells=$cells"); println(rule.onRoot().printToString()); throw e
        }
        rule.waitUntil(20_000) { shown("Daniel venceu a rodada") }
        assertTrue(shown("1 palavra") && shown("2 palavras"))
        t.join(10_000)
        val mine = got.filterIsInstance<Msg.Found>().first { it.id != hostId }
        assertEquals(Msg.Found(mine.id, 0, keys[0]), mine)
    }
}
