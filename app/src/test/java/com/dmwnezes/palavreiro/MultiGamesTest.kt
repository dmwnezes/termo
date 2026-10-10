package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.game.Anagram
import com.dmwnezes.palavreiro.game.Bomb
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

/** Bomba-Relógio e Anagrama da partida com amigo (lógica pura, igual ao site). */
class MultiGamesTest {
    private val words = Words(File("../shared/words.js").readText())
    private val seed = 123456789L

    @Test
    fun valoresDeReferencia() {
        val f = Bomb.fuse(seed)
        println("SEED $seed · pavio F = $f ms · começa: ${if (Bomb.hostStarts(seed)) "anfitrião" else "convidado"}")
        assertEquals(25000L + ((seed % 35001) * 7919 % 35001), f)
        assertTrue(f in 25000L..60000L)
        assertFalse(Bomb.hostStarts(seed)) // ímpar → convidado
        assertTrue(Bomb.hostStarts(2))
        for (s in listOf(0L, 1L, 35000L, 2147483646L)) assertTrue(Bomb.fuse(s) in 25000L..60000L)
        val list = Anagram.words(words.answers, seed)
        assertEquals(10, list.size)
        assertEquals(listOf("LENHA", "MOVER", "BUCHO", "FEBRE"), list.take(4))
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
    fun bombaMaquinaDeEstados() {
        val host = "host0001"; val guest = "guest001"
        val s = seed // ímpar: o convidado começa
        val f = Bomb.fuse(s)
        fun st(vararg m: Msg) = Bomb.state(m.toList(), s, host, guest)
        assertEquals(guest, st().turn)
        // Jogada válida passa a vez.
        val a = st(Msg.Word(guest, "CARRO", 0, 1000))
        assertEquals(host, a.turn); assertEquals(1, a.count)
        // Fora da vez, n errado, repetida (com acento/minúscula) e atrasada: ignoradas.
        val b = st(
            Msg.Word(host, "MUNDO", 0, 500),           // não é a vez do anfitrião
            Msg.Word(guest, "CARRO", 0, 1000),
            Msg.Word(host, "TERMO", 0, 2000),          // n errado
            Msg.Word(host, "cárro", 1, 2100),          // repetida
            Msg.Word(host, "PORTA", 1, f),             // depois do pavio
            Msg.Word("intruso", "LIVRO", 1, 3000),
        )
        assertEquals(1, b.count); assertEquals(host, b.turn); assertNull(b.loser)
        // Palavra válida de última hora (ms < F) que chega antes do boom ainda passa a vez.
        val c = st(Msg.Word(guest, "CARRO", 0, 1000), Msg.Word(host, "PORTA", 1, f - 1))
        assertEquals(guest, c.turn); assertEquals(listOf("CARRO", "PORTA"), c.plays.map { it.word })
        // O primeiro boom decide; depois tudo é ignorado.
        val d = st(Msg.Word(guest, "CARRO", 0, 1000), Msg.Boom(host), Msg.Boom(guest), Msg.Word(host, "PORTA", 1, 2000))
        assertEquals(host, d.loser); assertEquals(1, d.count)
        // Boom de quem não está na sala é ignorado.
        assertNull(st(Msg.Boom("outro")).loser)
        // Avisos ao mandar.
        assertEquals("Palavra não aceita", Bomb.check("XXXXX", a, words::isAccepted))
        assertEquals("Essa palavra já foi", Bomb.check("CARRO", a, words::isAccepted))
        assertNull(Bomb.check("PORTA", a, words::isAccepted))
        // Pulso: de ~1,2 s até ~0,25 s.
        assertEquals(1200, Bomb.pulseMs(0, f)); assertEquals(250, Bomb.pulseMs(f, f))
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
    fun mensagensNovasELink() {
        val all = listOf(Msg.Word("ab12cd34", "CARRO", 3, 12345), Msg.Boom("ab12cd34"), Msg.Solve("ab12cd34", 7, 8000), Msg.Skip(9))
        all.forEach { assertEquals(it, Multi.decode(Multi.encode(it))) }
        // Formato exato do spec.
        assertEquals(Msg.Word("q1", "CARRO", 0, 1500), Multi.decode("""{"t":"word","id":"q1","w":"CARRO","n":0,"ms":1500}"""))
        assertEquals(Msg.Boom("q1"), Multi.decode("""{"t":"boom","id":"q1"}"""))
        assertEquals(Msg.Solve("q1", 4, 9000), Multi.decode("""{"t":"solve","id":"q1","r":4,"ms":9000}"""))
        assertEquals(Msg.Skip(2), Multi.decode("""{"t":"skip","r":2}"""))
        val o = org.json.JSONObject(Multi.encode(Msg.Word("q1", "CARRO", 0, 1500)))
        assertEquals(listOf("word", "q1", "CARRO", 0, 1500), listOf(o.get("t"), o.get("id"), o.get("w"), o.getInt("n"), o.getInt("ms")))
        assertEquals(2, org.json.JSONObject(Multi.encode(Msg.Skip(2))).getInt("r"))
        for (m in listOf('b', 'a')) {
            val r = Multi.Room("abc123xyz789", m, 123456789)
            assertEquals("https://dmwnezes.github.io/termo/?mp=abc123xyz789-$m-21i3v9", r.link)
            assertEquals(r, Multi.parse(r.code))
            assertEquals(r, Multi.fromPasted(r.link))
            assertEquals(r, Multi.fromPasted("Bora jogar ${Multi.modeName(m)} no Palavreiro? Quem acertar primeiro ganha! ${r.code}"))
            assertEquals(1, Multi.boards(m))
            assertFalse(Multi.isTermo(m))
        }
        assertNull(Multi.fromPasted("abc123xyz789-x-21i3v9"))
        assertEquals("Bomba-Relógio", Multi.modeName('b')); assertEquals("Anagrama", Multi.modeName('a'))
        assertEquals("uma Bomba-Relógio", Multi.withArticle('b')); assertEquals("um Anagrama", Multi.withArticle('a')); assertEquals("um Dueto", Multi.withArticle('d'))
        assertEquals(listOf("Termo", "Dueto", "Quarteto", "Bomba", "Anagrama"), Multi.CODES.map(Multi::chipName))
        assertTrue(Multi.isTermo('t') && Multi.isTermo('d') && Multi.isTermo('q'))
        // Sessão guardada com os modos novos continua valendo.
        val sess = Multi.Session("abc123xyz789-b-21i3v9", true, "id", "Daniel", System.currentTimeMillis())
        assertEquals('b', Multi.Session.decode(sess.encode())!!.room!!.mode)
        assertTrue(sess.valid())
    }
}
