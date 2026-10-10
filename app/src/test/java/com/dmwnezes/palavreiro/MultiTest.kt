package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.game.FirstGuessStats
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class MultiTest {
    private val words = Words(File("../shared/words.js").readText())

    @Test
    fun palavrasIguaisAoSite() {
        // Valores calculados pelo site (P.mpWords) para SEED 123456789.
        assertEquals(listOf("LENHA"), Multi.words(words.answers, 123456789, 1))
        assertEquals(listOf("LENHA", "MOVER"), Multi.words(words.answers, 123456789, 2))
        assertEquals(listOf("LENHA", "MOVER", "BUCHO", "FEBRE"), Multi.words(words.answers, 123456789, 4))
    }

    @Test
    fun linkEMensagens() {
        val r = Multi.Room("abc123xyz789", 'd', 123456789)
        assertEquals("https://dmwnezes.github.io/termo/?mp=abc123xyz789-d-21i3v9", r.link)
        assertEquals(r, Multi.parse(r.code))
        assertNull(Multi.parse("x-d-1")); assertNull(Multi.parse("abc123xyz789-z-1")); assertNull(Multi.parse(null))
        val all = listOf(
            Msg.Hello("ab12cd34", "Daniel", true), Msg.Start(1791611604000, "zz99yy88"), Msg.Row("ab12cd34", 2, "ccapa|"),
            Msg.End("ab12cd34", true, 4, 83000), Msg.Again(987654321, 1791611700000), Msg.Bye("ab12cd34"),
        )
        all.forEach { assertEquals(it, Multi.decode(Multi.encode(it))) }
        // Formato exato que o site manda.
        assertEquals(Msg.Hello("q1w2e3r4", "Ana", false), Multi.decode("""{"t":"hello","id":"q1w2e3r4","n":"Ana","host":false}"""))
        assertEquals(Msg.Again(Multi.parse("abc123xyz789-d-21i3v9")!!.seed, 5), Multi.decode("""{"t":"again","seed":"21i3v9","at":5}"""))
        // Colar o link: inteiro, só o código, ou no meio de uma mensagem.
        assertEquals(r, Multi.fromPasted(r.link))
        assertEquals(r, Multi.fromPasted(r.code))
        assertEquals(r, Multi.fromPasted("Bora jogar Dueto no Palavreiro? ${r.link} vem!"))
        assertEquals(r, Multi.fromPasted("https://dmwnezes.github.io/termo/index.html?mp=ABC123XYZ789-D-21I3V9&x=1"))
        assertNull(Multi.fromPasted("https://dmwnezes.github.io/termo/")); assertNull(Multi.fromPasted("")); assertNull(Multi.fromPasted(null))
        assertNull(Multi.decode("oi")); assertNull(Multi.decode("""{"t":"outro"}"""))
        assertTrue(Multi.validName("Ana")); assertFalse(Multi.validName("A")); assertFalse(Multi.validName("x".repeat(17)))
    }

    @Test
    fun quemGanha() {
        val me = "me"; val op = "op"
        assertNull(Multi.outcome(emptyList(), me, op, false))
        assertEquals("opp", Multi.outcome(listOf(Msg.End(op, true, 3, 1)), me, op, false))
        assertEquals("me", Multi.outcome(listOf(Msg.End(op, false, 6, 1), Msg.End(me, true, 5, 1)), me, op, false))
        assertNull(Multi.outcome(listOf(Msg.End(op, false, 6, 1)), me, op, false)) // eu ainda jogo
        assertEquals("draw", Multi.outcome(listOf(Msg.End(op, false, 6, 1), Msg.End(me, false, 6, 1)), me, op, false))
        assertEquals("draw", Multi.outcome(listOf(Msg.End(me, false, 6, 1)), me, op, true))
        // O primeiro que acertou vence, mesmo que o outro acerte depois.
        assertEquals("me", Multi.outcome(listOf(Msg.End(me, true, 4, 1), Msg.End(op, true, 3, 1)), me, op, false))
    }

    @Test
    fun melhorDeTres() {
        var s = Multi.Series()
        s = s.plus("me"); assertEquals("1 × 0", s.score); assertFalse(s.over)
        s = s.plus("draw"); assertEquals("1 × 0", s.score)
        s = s.plus("opp"); assertEquals("1 × 1", s.score); assertNull(s.winner)
        s = s.plus("me"); assertTrue(s.over); assertEquals("me", s.winner); assertEquals("2 × 1", s.score)
        assertEquals("2 × 1", s.plus("opp").score) // depois de acabar, não conta mais
        // "again" com série nova leva "s":1; sem ele continua a série. Apps antigos ignoram o campo.
        val novo = Multi.encode(Msg.Again(99, 5, newSeries = true))
        assertTrue(novo.contains("\"s\":1"))
        assertEquals(Msg.Again(99, 5, true), Multi.decode(novo))
        assertEquals(Msg.Again(99, 5, false), Multi.decode("""{"t":"again","seed":"2r","at":5}"""))
    }

    @Test
    fun qrDoLink() {
        val link = Multi.newRoom('d').link
        val m = com.dmwnezes.palavreiro.ui.qrMatrix(link)
        // Lê de volta com o leitor do ZXing (com margem branca, como na tela).
        val q = 4; val border = 4 * q; val w = m.width * q + 2 * border
        val px = IntArray(w * w) { -1 }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y])
            for (dy in 0 until q) for (dx in 0 until q) px[(border + y * q + dy) * w + border + x * q + dx] = 0xFF000000.toInt()
        val bmp = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(com.google.zxing.RGBLuminanceSource(w, w, px)))
        assertEquals(link, com.google.zxing.qrcode.QRCodeReader().decode(bmp).text)
    }

    @Test
    fun marcasSemLetras() {
        val ans = Multi.words(words.answers, 42, 2)
        val g = TermoGame(Mode.DUETO, words, store = null, fixed = ans)
        ans[0].forEach(g::type); g.submit(); g.finishReveal()
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        val r0 = Multi.rowMarks(g, 0).split('|')
        assertEquals("ccccc", r0[0]); assertEquals(5, r0[1].length)
        assertEquals("", Multi.rowMarks(g, 1).split('|')[0]) // tabuleiro 1 já resolvido
        assertTrue(Multi.rowMarks(g, 1).all { it in "cpa|" })
    }

    @Test
    fun seusChutes() {
        val st = FirstGuessStats.from(listOf("CARRO" to 4, "CARRO" to 3, "CARRO" to 5, "PEDRA" to 2, "PEDRA" to 3, "PEDRA" to 2, "MUNDO" to 7))
        assertEquals(7, st.total)
        assertEquals("CARRO" to 3, st.favorite)
        assertEquals("PEDRA", st.best!!.first); assertEquals(7.0 / 3, st.best!!.second, 0.001)
        assertEquals('R', st.letters.first().first) // R aparece 2×3 + 1×3 = 9 vezes
    }

    /** Teste ao vivo pelo ntfy.sh (rode com PV_LIVE=1). */
    @Test
    fun aoVivoPeloNtfy() = runBlocking {
        assumeTrue(System.getenv("PV_LIVE") == "1")
        val ntfy = Ntfy(OkHttpClient())
        val room = Multi.newRoom('t')
        assertTrue(ntfy.publish(room.topic, Multi.encode(Msg.Hello("host0001", "Host", true))))
        val got = withTimeout(30_000) {
            ntfy.events(room.topic).filterIsInstance<Ntfy.Event.Message>().mapNotNull { Multi.decode(it.text) }.first()
        }
        assertEquals(Msg.Hello("host0001", "Host", true), got)
    }

    /** Partida real app × site: este teste é o anfitrião (rode com PV_ROOM=codigo e PV_HOST=1; o site entra pelo link). */
    @Test
    fun anfitriaoContraOSite() = runBlocking {
        val code = System.getenv("PV_ROOM"); assumeTrue(code != null && System.getenv("PV_HOST") == "1")
        val room = Multi.parse(code)!!
        val ntfy = Ntfy(OkHttpClient())
        val me = "kotlin01"
        ntfy.publish(room.topic, Multi.encode(Msg.Hello(me, "Daniel", true)))
        val log = StringBuilder()
        withTimeout(120_000) {
            var guest: String? = null
            ntfy.events(room.topic).filterIsInstance<Ntfy.Event.Message>().mapNotNull { Multi.decode(it.text) }.first { m ->
                log.append(m).append('\n')
                when {
                    m is Msg.Hello && !m.host && guest == null -> {
                        guest = m.id
                        ntfy.publish(room.topic, Multi.encode(Msg.Start(System.currentTimeMillis(), m.id)))
                        // Uma tentativa minha (só cores) para o site mostrar na faixa.
                        ntfy.publish(room.topic, Multi.encode(Msg.Row(me, 0, "capap")))
                        false
                    }
                    m is Msg.Row && m.id == guest -> { assertTrue(m.marks.matches(Regex("[cpa]{5}"))); false }
                    m is Msg.End && m.id == guest -> {
                        ntfy.publish(room.topic, Multi.encode(Msg.End(me, false, 6, 90_000)))
                        true
                    }
                    else -> false
                }
            }
        }
        File("build/mp-host-log.txt").writeText(log.toString())
    }
}
