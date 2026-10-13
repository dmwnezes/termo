package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.game.Anagram
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Timed
import com.dmwnezes.palavreiro.game.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Anagrama da partida com amigo (lógica pura, igual ao site). */
class MultiGamesTest {
    private val words = Words(File("../shared/words.js").readText())
    private val seed = 123456789L

    @Test
    fun valoresDeReferencia() {
        val list = Anagram.words(words.answers, seed)
        assertEquals(10, list.size)
        assertEquals(listOf("LENHA", "MOVER", "BUCHO", "FEBRE"), list.take(4))
        assertEquals(list.take(5), Anagram.words(words.answers, seed, 5))
        assertEquals(15, Anagram.words(words.answers, seed, 15).size)
        list.forEachIndexed { k, w ->
            val s = Anagram.shuffle(w, seed, k)
            println("anagrama $k: $w → $s")
            assertNotEquals(w, s)
            assertTrue(Anagram.sameLetters(w, s))
            assertEquals(s, Anagram.shuffle(w, seed, k))
        }
    }

    @Test
    fun embaralhaGirandoQuandoFicaIgual() {
        // Palavra com letras todas iguais não tem como mudar: no máximo 4 giros e devolve igual.
        assertEquals("AAAAA", Anagram.shuffle("AAAAA", 7, 0))
        // Procura um caso em que o embaralhamento dá a própria palavra e confere o giro.
        var found = false
        for (s in 0L until 5000L) {
            val a = "PORTA".toCharArray(); var x = (s % 2147483646L) + 1
            for (i in 4 downTo 1) { x = x * 48271 % 2147483647; val j = (x % (i + 1)).toInt(); val t = a[i]; a[i] = a[j]; a[j] = t }
            if (String(a) == "PORTA") { assertEquals("ORTAP", Anagram.shuffle("PORTA", s, 0)); found = true; break }
        }
        assertTrue(found)
    }

    @Test
    fun anagramaAceitaOutraPalavraComAsMesmasLetras() {
        val ok = words::isAccepted
        assertTrue(Anagram.solves("PORTA", "PORTA", ok))
        assertTrue(Anagram.solves("PRATO", "PORTA", ok))
        assertTrue(Anagram.solves("TROPA", "PORTA", ok))
        assertFalse(Anagram.solves("PARTO", "PORTO", ok))
        assertFalse(Anagram.solves("ATROP", "PORTA", ok)) // mesmas letras, mas não é palavra
        assertFalse(Anagram.solves("CARRO", "PORTA", ok))
        assertTrue(Anagram.sameLetters("ARARA", "RAARA")); assertFalse(Anagram.sameLetters("ARARA", "ARRRA"))
    }

    @Test
    fun anagramaEstado() {
        val me = "me000001"; val op = "op000001"; val players = setOf(me, op)
        fun st(vararg m: Pair<Long, Msg>) = Anagram.state(m.map { Timed(it.first, it.second) }, players)
        assertEquals(0, st().current)
        // Primeiro solve/skip da rodada vale; os seguintes são ignorados.
        val a = st(
            100L to Msg.Solve(op, 0, 5000), 110L to Msg.Solve(me, 0, 5100), 120L to Msg.Skip(0),
            3000L to Msg.Skip(1), 3010L to Msg.Solve(me, 1, 46000),
            6000L to Msg.Solve(me, 2, 3000),
        )
        assertEquals(3, a.current)
        assertEquals(listOf(op, null, me), a.decisions.map { it.winner })
        assertEquals(1, a.points(me)); assertEquals(1, a.points(op))
        assertEquals(500L, a.startOf(0, 500)); assertEquals(100L + Anagram.GAP_MS, a.startOf(1, 500)); assertEquals(3000L + Anagram.GAP_MS, a.startOf(2, 500))
        assertNull(a.outcome(me, op))
        // Rodada fora de ordem só conta quando as anteriores forem decididas; id estranho e rodada inválida não contam.
        val b = st(1L to Msg.Solve(me, 1, 1), 2L to Msg.Solve("x", 0, 1), 3L to Msg.Solve(me, 10, 1), 4L to Msg.Skip(-1))
        assertEquals(0, b.current)
        val c = st(1L to Msg.Solve(me, 1, 1), 2L to Msg.Solve(op, 0, 1))
        assertEquals(2, c.current); assertEquals(listOf(op, me), c.decisions.map { it.winner })
        // Partida completa: 6 × 4 para mim.
        val all = (0 until 10).map { r -> r.toLong() to (if (r < 6) Msg.Solve(me, r, 1) else Msg.Solve(op, r, 1)) as Msg }
        val d = st(*all.toTypedArray())
        assertTrue(d.over); assertEquals("me", d.outcome(me, op)); assertEquals("opp", d.outcome(op, me))
        val e = st(*(0 until 10).map { r -> r.toLong() to (if (r % 2 == 0) Msg.Solve(me, r, 1) else Msg.Skip(r)) as Msg }.toTypedArray())
        assertEquals(5, e.points(me)); assertEquals("me", e.outcome(me, op))
        val g = st(*(0 until 10).map { r -> r.toLong() to Msg.Skip(r) as Msg }.toTypedArray())
        assertEquals("draw", g.outcome(me, op))
    }

    @Test
    fun anagramaComRodadas() {
        val me = "me000001"; val op = "op000001"
        val msgs = (0 until 6).map { r -> Timed(r.toLong(), Msg.Solve(if (r < 3) me else op, r, 1)) }
        val five = Anagram.state(msgs, setOf(me, op), 5)
        assertTrue(five.over); assertEquals(5, five.current); assertEquals("me", five.outcome(me, op))
        assertFalse(Anagram.state(msgs, setOf(me, op), 10).over)
    }

    @Test
    fun mensagensNovasELink() {
        val all = listOf(Msg.Solve("ab12cd34", 7, 8000), Msg.Skip(9))
        all.forEach { assertEquals(it, Multi.decode(Multi.encode(it))) }
        assertEquals(Msg.Solve("q1", 4, 9000), Multi.decode("""{"t":"solve","id":"q1","r":4,"ms":9000}"""))
        assertEquals(Msg.Skip(2), Multi.decode("""{"t":"skip","r":2}"""))
        assertEquals(2, org.json.JSONObject(Multi.encode(Msg.Skip(2))).getInt("r"))
        // Mensagens da Bomba-Relógio (que saiu) são ignoradas.
        // ("word" voltou na Rodada 11 como a palavra final da Forca; só vale dentro de uma rodada de Forca.)
        assertEquals(Msg.Word(0, "CARRO"), Multi.decode("""{"t":"word","id":"q1","w":"CARRO","n":0,"ms":1500}"""))
        assertNull(Multi.decode("""{"t":"boom","id":"q1"}"""))
        for (m in listOf('a', 'c', 'p', 'v', 'x')) {
            val r = Multi.Room("abc123xyz789", m, 123456789)
            assertEquals("https://dmwnezes.github.io/termo/?mp=abc123xyz789-$m-21i3v9", r.link)
            assertEquals(r, Multi.parse(r.code))
            assertEquals(r, Multi.fromPasted(r.link))
            assertEquals(r, Multi.fromPasted("Bora jogar ${Multi.modeName(m)} no Palavreiro? ${r.code}"))
            assertEquals(1, Multi.boards(m))
            assertFalse(Multi.isTermo(m))
        }
        assertNull(Multi.fromPasted("abc123xyz789-z-21i3v9"))
        assertEquals("Anagrama", Multi.modeName('a'))
        assertEquals("um Anagrama", Multi.withArticle('a')); assertEquals("um Dueto", Multi.withArticle('d'))
        assertTrue(Multi.isTermo('t') && Multi.isTermo('d') && Multi.isTermo('q'))
        val sess = Multi.Session("abc123xyz789-x-21i3v9", true, "id", "Daniel", System.currentTimeMillis())
        assertEquals('x', Multi.Session.decode(sess.encode())!!.room!!.mode)
        assertTrue(sess.valid())
    }
}
