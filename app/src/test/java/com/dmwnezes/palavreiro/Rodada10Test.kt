package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.game.Checkers
import com.dmwnezes.palavreiro.game.DefineDuel
import com.dmwnezes.palavreiro.game.HuntDuel
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.TicTacToe
import com.dmwnezes.palavreiro.game.WordSearchData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Rodada 10: Caça-Palavras, Qual é a Palavra?, Jogo da Velha e Damas com amigo, rodadas e mensagens (igual ao site). */
class Rodada10Test {
    private fun keys(json: String): Map<String, Any> = JSONObject(json).let { o ->
        o.keySet().associateWith { k -> o.get(k).let { if (it is org.json.JSONArray) (0 until it.length()).map { i -> it.get(i) } else it } }
    }

    // ---------------- Damas ----------------
    @Test
    fun damasPosicoesDeReferencia() {
        // P1: posição inicial, vez das brancas → 7 jogadas.
        val p1 = Checkers.legalMoves(Checkers.initial(), Checkers.WHITE)
        println("Damas P1 (${p1.size} jogadas): $p1")
        assertEquals(7, p1.size)
        assertEquals(12, Checkers.count(Checkers.initial(), Checkers.WHITE)); assertEquals(12, Checkers.count(Checkers.initial(), Checkers.BLACK))
        assertEquals(setOf(40, 42, 44, 46), p1.map { it[0] }.toSet())
        // P2: lei da maioria → só [42,28,14].
        val p2 = Checkers.legalMoves(Checkers.board(whiteMen = listOf(42), blackMen = listOf(35, 21, 33)), Checkers.WHITE)
        println("Damas P2 (${p2.size} jogada): $p2")
        assertEquals(listOf(listOf(42, 28, 14)), p2)
        // P3: dama voadora → [56,28],[56,21],[56,14],[56,7].
        val p3 = Checkers.legalMoves(Checkers.board(whiteKings = listOf(56), blackMen = listOf(35, 1)), Checkers.WHITE)
        println("Damas P3 (${p3.size} jogadas): $p3")
        assertEquals(setOf(listOf(56, 28), listOf(56, 21), listOf(56, 14), listOf(56, 7)), p3.toSet())
        assertEquals(4, p3.size)
    }

    @Test
    fun damasRegras() {
        // Pretas começam embaixo? Não: pretas nas linhas 0–2 andam para baixo.
        val b1 = Checkers.legalMoves(Checkers.initial(), Checkers.BLACK)
        assertEquals(7, b1.size); assertTrue(b1.all { it[1] / 8 == 3 })
        // Pedra captura para trás também.
        assertEquals(listOf(listOf(28, 46)), Checkers.legalMoves(Checkers.board(whiteMen = listOf(28), blackMen = listOf(37)), Checkers.WHITE))
        // Captura simples e a peça sai no fim.
        val after = Checkers.apply(Checkers.board(whiteMen = listOf(42), blackMen = listOf(35, 21, 33)), listOf(42, 28, 14))
        assertEquals(1, after[14]); assertEquals(0, after[42]); assertEquals(0, after[35]); assertEquals(0, after[21]); assertEquals(-1, after[33])
        // Pedra que termina na última linha vira dama.
        val prom = Checkers.board(whiteMen = listOf(18), blackMen = listOf(11))
        assertEquals(listOf(listOf(18, 4)), Checkers.legalMoves(prom, Checkers.WHITE))
        assertEquals(2, Checkers.apply(prom, listOf(18, 4))[4])
        // Passa pela última linha no meio da sequência e não termina nela: continua pedra.
        val pass = Checkers.board(whiteMen = listOf(18), blackMen = listOf(11, 13))
        assertEquals(listOf(listOf(18, 4, 22)), Checkers.legalMoves(pass, Checkers.WHITE))
        assertEquals(1, Checkers.apply(pass, listOf(18, 4, 22))[22])
        // Pedra simples só anda para frente.
        assertEquals(setOf(listOf(36, 27), listOf(36, 29)), Checkers.legalMoves(Checkers.board(whiteMen = listOf(36)), Checkers.WHITE).toSet())
        // Dama anda em qualquer distância (sem peças: 9 casas a partir de 56? 7 na diagonal).
        assertEquals(7, Checkers.legalMoves(Checkers.board(whiteKings = listOf(56)), Checkers.WHITE).size)
        // Peças capturadas ficam até o fim da jogada e não podem ser puladas de novo (nem atravessadas).
        val turk = Checkers.board(whiteKings = listOf(63), blackMen = listOf(45, 29, 13))
        val tm = Checkers.legalMoves(turk, Checkers.WHITE)
        println("Damas dama com várias capturas: $tm")
        assertTrue(tm.all { Checkers.captured(turk, it).toSet().size == Checkers.captured(turk, it).size })
        assertEquals(tm.map { Checkers.captured(turk, it).size }.toSet().size, 1)
    }

    @Test
    fun damasEstadoPelasMensagens() {
        val w = "white001"; val b = "black001"
        // Jogada fora da vez, n errado e jogada ilegal são ignoradas.
        val s = Checkers.state(
            listOf(
                Msg.Move(b, 0, listOf(21, 28)),
                Msg.Move(w, 1, listOf(42, 35)),
                Msg.Move(w, 0, listOf(42, 34)),
                Msg.Move(w, 0, listOf(42, 35)),
                Msg.Move("intruso", 1, listOf(21, 28)),
                Msg.Move(b, 1, listOf(21, 28)),
            ), w, b,
        )
        assertEquals(2, s.moves); assertEquals(Checkers.WHITE, s.turn); assertEquals(listOf(21, 28), s.last)
        // Agora as brancas têm captura obrigatória (35 x 28? não: 35 → 21 por cima de 28).
        assertTrue(s.mustCapture)
        println("Damas depois de 42-35 21-28: ${s.legal}")
        assertTrue(s.legal.all { Checkers.captured(s.board, it).isNotEmpty() })
        // Desistir = derrota.
        val r = Checkers.state(listOf(Msg.Resign(b)), w, b)
        assertEquals(Checkers.WHITE, r.winner); assertEquals("me", Checkers.outcome(r, Checkers.WHITE)); assertEquals("opp", Checkers.outcome(r, Checkers.BLACK))
        // Sem peças → vitória.
        val last = Checkers.play(Checkers.start(Checkers.board(whiteMen = listOf(42), blackMen = listOf(35))), Checkers.WHITE, 0, listOf(42, 28))!!
        assertEquals(Checkers.WHITE, last.winner)
        // Sem jogadas → vitória (pedra preta presa no canto).
        val stuck = Checkers.play(Checkers.start(Checkers.board(whiteMen = listOf(54, 61, 40), blackMen = listOf(47))), Checkers.WHITE, 0, listOf(40, 33))!!
        assertEquals(Checkers.WHITE, stuck.winner)
        // Empate: 20 lances seguidos só de damas, sem captura.
        var st = Checkers.start(Checkers.board(whiteKings = listOf(63), blackKings = listOf(7)))
        val cycle = listOf(listOf(63, 54), listOf(7, 14), listOf(54, 63), listOf(14, 7))
        for (i in 0 until 20) {
            assertFalse(st.over)
            st = Checkers.play(st, st.turn, st.moves, cycle[i % 4])!!
        }
        assertTrue(st.draw); assertEquals("draw", Checkers.outcome(st, Checkers.WHITE))
        assertTrue(Checkers.hostIsWhite(0)); assertFalse(Checkers.hostIsWhite(1))
    }

    // ---------------- Jogo da Velha ----------------
    @Test
    fun velha() {
        val x = "host0001"; val o = "guest001"
        fun st(vararg m: Msg) = TicTacToe.state(m.toList(), x, o)
        // Vitória do X na diagonal.
        val win = st(Msg.Cell(x, 0, 0), Msg.Cell(o, 1, 1), Msg.Cell(x, 2, 4), Msg.Cell(o, 3, 2), Msg.Cell(x, 4, 8))
        assertEquals('X', win.winner); assertEquals(listOf(0, 4, 8), win.line)
        assertEquals("me", TicTacToe.outcome(win, 'X')); assertEquals("opp", TicTacToe.outcome(win, 'O'))
        // Depois do fim, nada mais vale.
        assertEquals(5, st(Msg.Cell(x, 0, 0), Msg.Cell(o, 1, 1), Msg.Cell(x, 2, 4), Msg.Cell(o, 3, 2), Msg.Cell(x, 4, 8), Msg.Cell(o, 5, 3)).moves)
        // Velha.
        val draw = st(*listOf(0, 1, 2, 4, 3, 5, 7, 6, 8).mapIndexed { i, c -> Msg.Cell(if (i % 2 == 0) x else o, i, c) }.toTypedArray())
        assertTrue(draw.draw); assertNull(draw.winner); assertEquals("draw", TicTacToe.outcome(draw, 'O'))
        // Inválidas: fora da vez, n errado, casa ocupada, casa fora, id estranho.
        val bad = st(
            Msg.Cell(o, 0, 0), Msg.Cell(x, 1, 0), Msg.Cell(x, 0, 9), Msg.Cell("intruso", 0, 3),
            Msg.Cell(x, 0, 4), Msg.Cell(o, 1, 4), Msg.Cell(x, 1, 5), Msg.Cell(o, 1, 0),
        )
        assertEquals(2, bad.moves); assertEquals('X', bad.board[4]); assertEquals('O', bad.board[0]); assertNull(bad.board[5]); assertEquals('X', bad.turn)
        assertNull(TicTacToe.outcome(bad, 'X'))
        // Símbolos trocam a cada rodada.
        assertTrue(TicTacToe.hostIsX(0)); assertFalse(TicTacToe.hostIsX(1)); assertTrue(TicTacToe.hostIsX(2))
    }

    // ---------------- Caça-Palavras ----------------
    @Test
    fun cacaDuelo() {
        val pool = WordSearchData.parse(File("../shared/caca.txt").readText()) + WordSearchData.parse(File("../shared/caca-extra.txt").readText())
        val g = HuntDuel.build(pool, emptySet(), 0, Random(11))
        assertTrue(HuntDuel.valid(g)); assertEquals(10, g.rows.size); assertTrue(g.words.size in 3..8)
        println("Caça grade (${g.theme}): ${g.rows.joinToString("|")} · ${g.words}")
        // Tema sem repetir.
        val g2 = HuntDuel.build(pool, setOf(g.theme), 1, Random(11))
        assertTrue(g2.theme != g.theme)
        val k = g.words.map(HuntDuel::key)
        // Seleção na grade: nos dois sentidos.
        val cells = HuntDuel.locate(g.rows, g.words[0])!!
        val (a, z) = cells.first() to cells.last()
        assertEquals(k[0], HuntDuel.match(g, a.first, a.second, z.first, z.second, emptySet()))
        assertEquals(k[0], HuntDuel.match(g, z.first, z.second, a.first, a.second, emptySet()))
        assertNull(HuntDuel.match(g, a.first, a.second, z.first, z.second, setOf(k[0])))
        val me = "me000001"; val op = "op000001"; val players = setOf(me, op)
        // Dono = o primeiro "found" na ordem do tópico; outra rodada, palavra fora da lista e id estranho são ignorados.
        val s = HuntDuel.state(
            g, listOf(
                Msg.Found(op, 0, k[0]), Msg.Found(me, 0, k[0]), Msg.Found(me, 0, k[1]), Msg.Found(me, 1, k[2]),
                Msg.Found(me, 0, "XYZXYZ"), Msg.Found("intruso", 0, k[3]), Msg.Found(me, 0, g.words[4].lowercase()),
            ), 0, players,
        )
        assertEquals(op, s.owners[k[0]]); assertEquals(me, s.owners[k[1]]); assertNull(s.owners[k[2]]); assertNull(s.owners[k[3]])
        assertEquals(me, s.owners[k[4]]) // com acento/minúscula também vale
        assertEquals(2, s.count(me)); assertEquals(1, s.count(op)); assertFalse(s.over); assertNull(s.outcome(me, op))
        // Tempo esgotado: acaba e o que vier depois não conta.
        val t = HuntDuel.state(g, listOf(Msg.Found(me, 0, k[0]), Msg.TimeUp(1), Msg.TimeUp(0), Msg.Found(op, 0, k[1]), Msg.Found(op, 0, k[2])), 0, players)
        assertTrue(t.over); assertEquals(1, t.count(me)); assertEquals(0, t.count(op)); assertEquals("me", t.outcome(me, op))
        // Empate quando acaba o tempo igual.
        assertEquals("draw", HuntDuel.state(g, listOf(Msg.TimeUp(0)), 0, players).outcome(me, op))
        // Todas pegas: acaba sem timeup.
        val all = HuntDuel.state(g, k.mapIndexed { i, w -> Msg.Found(if (i % 3 == 0) op else me, 0, w) }, 0, players)
        assertTrue(all.over); assertEquals("me", all.outcome(me, op))
        // Sem grade ainda: nada acontece.
        assertFalse(HuntDuel.state(null, listOf(Msg.Found(me, 0, k[0])), 0, players).over)
        assertFalse(HuntDuel.valid(g.copy(rows = g.rows.drop(1))))
    }

    // ---------------- Qual é a Palavra? ----------------
    @Test
    fun palavraValoresDeReferencia() {
        val defs = DefineDuel.parse(File("../shared/definicoes.txt").readText())
        val list = DefineDuel.pick(defs, 123456789, 10)
        println("Qual é a Palavra? SEED 123456789 (M = ${defs.size}): ${list.map { it.word }}")
        assertEquals(10, list.size)
        assertEquals(list.map { it.word }.distinct(), list.map { it.word })
        // Primeiro índice pela fórmula.
        val m = defs.size.toLong()
        assertEquals(defs[(((123456789L % m) * 7919 % m + 104729) % m).toInt()], list[0])
        assertEquals(list.take(5), DefineDuel.pick(defs, 123456789, 5))
        assertEquals(15, DefineDuel.pick(defs, 123456789, 15).size)
        // Comparação sem acento e só letras; dicas aos 20 s e aos 40 s.
        assertTrue(DefineDuel.solves("relogio", "RELÓGIO")); assertTrue(DefineDuel.solves("GUARDACHUVA", "GUARDA-CHUVA")); assertFalse(DefineDuel.solves("RELOGIOS", "RELÓGIO"))
        assertEquals(emptySet<Int>(), DefineDuel.hints("ARCO-ÍRIS", 19_999))
        assertEquals(setOf(0), DefineDuel.hints("ARCO-ÍRIS", 20_000))
        assertEquals(setOf(0, 8), DefineDuel.hints("ARCO-ÍRIS", 40_000))
        // Linhas inválidas ficam de fora.
        assertEquals(listOf("CASA"), DefineDuel.parse("# x\nCASA|Lugar onde se mora.\n|sem palavra\nSEM DEF|\nsem barra\n").map { it.word })
    }

    // ---------------- Rodadas, links e mensagens ----------------
    @Test
    fun rodadasEModos() {
        assertEquals(listOf('t', 'd', 'q', 'a', 'c', 'p', 'f', 's', 'g', 'v', 'x', 'k', 'n'), Multi.CODES)
        assertEquals(
            listOf("Termo", "Dueto", "Quarteto", "Anagrama", "Caça", "Palavra?", "Forca", "Adedonha", "Trivia", "Velha", "Damas", "Xeque", "Naval"),
            Multi.CODES.map(Multi::chipName),
        )
        assertEquals("um Caça-Palavras", Multi.withArticle('c')); assertEquals("um Jogo da Velha", Multi.withArticle('v'))
        assertEquals("uma partida de Damas", Multi.withArticle('x')); assertEquals("um Qual é a Palavra?", Multi.withArticle('p'))
        for (m in "tdqcvx") {
            assertEquals(listOf(1, 3, 5), Multi.roundOptions(m)); assertEquals(3, Multi.defaultRounds(m))
            assertEquals(listOf("1 partida", "Melhor de 3", "Melhor de 5"), Multi.roundOptions(m).map { Multi.roundsLabel(m, it) })
        }
        for (m in "ap") { assertEquals(listOf(5, 10, 15), Multi.roundOptions(m)); assertEquals(10, Multi.defaultRounds(m)) }
        // "r" do hello: valor válido para o modo, ou o padrão.
        fun r(mode: Char, json: String) = Multi.rounds(mode, (Multi.decode(json) as Msg.Hello).rounds)
        assertEquals(5, r('t', """{"t":"hello","id":"h1","n":"Daniel","host":true,"r":5}"""))
        assertEquals(1, r('x', """{"t":"hello","id":"h1","n":"Daniel","host":true,"r":1}"""))
        assertEquals(3, r('t', """{"t":"hello","id":"h1","n":"Daniel","host":true}"""))
        assertEquals(3, r('t', """{"t":"hello","id":"h1","n":"Daniel","host":true,"r":10}"""))
        assertEquals(15, r('p', """{"t":"hello","id":"h1","n":"Daniel","host":true,"r":15}"""))
        assertEquals(10, r('a', """{"t":"hello","id":"h1","n":"Daniel","host":true,"r":3}"""))
        assertEquals(10, r('a', """{"t":"hello","id":"h1","n":"Daniel","host":true,"r":"x"}"""))
        assertEquals(" · melhor de 3", Multi.roundsSuffix('t', 3)); assertEquals(" · 15 rodadas", Multi.roundsSuffix('a', 15)); assertEquals("", Multi.roundsSuffix('v', 1))
        // Série melhor de N.
        assertEquals(1, Multi.Series(best = 1).wins); assertTrue(Multi.Series(best = 1).plus("me").over); assertEquals("", Multi.Series(best = 1).label)
        val five = Multi.Series(best = 5).plus("me").plus("opp").plus("me").plus("draw")
        assertEquals("2 × 1", five.score); assertFalse(five.over); assertEquals("melhor de 5", five.label)
        assertEquals("me", five.plus("me").winner)
        // Link da Bomba-Relógio: inválido, com aviso próprio.
        assertNull(Multi.parse("abc123xyz789-b-21i3v9")); assertNull(Multi.fromPasted("https://dmwnezes.com/termo/?mp=abc123xyz789-b-21i3v9"))
        assertTrue(Multi.removedGame("https://dmwnezes.com/termo/?mp=abc123xyz789-b-21i3v9"))
        assertTrue(Multi.removedGame("Bora? abc123xyz789-b-21i3v9"))
        assertFalse(Multi.removedGame("abc123xyz789-t-21i3v9")); assertFalse(Multi.removedGame("oi"))
        for (m in "cpvx") assertNotNull(Multi.fromPasted("entra aí: abc123xyz789-$m-21i3v9"))
        // Revanche com rodadas: "r" vai quando muda o modo ou as rodadas; série zera.
        assertEquals(Msg.Again(7, 1, true, 'x', 5), Multi.againFor('t', 'x', false, 7, 1, rounds = 3, nextRounds = 5))
        assertEquals(Msg.Again(7, 1, true, 't', 5), Multi.againFor('t', 't', false, 7, 1, rounds = 3, nextRounds = 5))
        assertEquals(Msg.Again(7, 1, false, 't', null), Multi.againFor('t', 't', false, 7, 1, rounds = 3, nextRounds = 3))
        val room = Multi.Room("abc123xyz789", 't', 1)
        assertEquals(Multi.AgainResult(room.copy(mode = 'p', seed = 7), true, 15), Multi.applyAgain(room, Msg.Again(7, 1, true, 'p', 15), false, 3))
        assertEquals(10, Multi.applyAgain(room, Msg.Again(7, 1, true, 'a'), false, 3).rounds) // modo novo sem "r" → padrão
        assertEquals(5, Multi.applyAgain(room, Msg.Again(7, 1, false, 't'), false, 5).rounds) // mesmo modo sem "r" → mantém
        assertEquals(1, Multi.applyAgain(room, Msg.Again(7, 1, true, 't', 1), false, 5).rounds)
    }

    @Test
    fun mensagensFormatoExato() {
        val hello = Msg.Hello("h1", "Daniel", true, 5)
        assertEquals(mapOf("t" to "hello", "id" to "h1", "n" to "Daniel", "host" to true, "r" to 5), keys(Multi.encode(hello)))
        assertFalse(keys(Multi.encode(Msg.Hello("g1", "Ana", false))).containsKey("r"))
        assertEquals(hello, Multi.decode("""{"t":"hello","id":"h1","n":"Daniel","host":true,"r":5}"""))
        val again = Msg.Again(99, 5, true, 'c', 1)
        assertEquals(mapOf("t" to "again", "seed" to "2r", "at" to 5, "s" to 1, "m" to "c", "r" to 1), keys(Multi.encode(again)))
        assertEquals(again, Multi.decode("""{"t":"again","seed":"2r","at":5,"s":1,"m":"c","r":1}"""))
        val grid = Msg.Grid(2, "Esportes", List(10) { "ABCDEFGHIJ" }, listOf("FUTEBOL", "NATAÇÃO"))
        assertEquals(
            mapOf("t" to "grid", "n" to 2, "th" to "Esportes", "g" to List(10) { "ABCDEFGHIJ" }.joinToString("|"), "w" to listOf("FUTEBOL", "NATAÇÃO")),
            keys(Multi.encode(grid)),
        )
        assertEquals(grid, Multi.decode(Multi.encode(grid)))
        assertEquals(grid, Multi.decode("""{"t":"grid","n":2,"th":"Esportes","g":"${List(10) { "ABCDEFGHIJ" }.joinToString("|")}","w":["FUTEBOL","NATAÇÃO"]}"""))
        val found = Msg.Found("q1", 0, "FUTEBOL")
        assertEquals(mapOf("t" to "found", "id" to "q1", "n" to 0, "w" to "FUTEBOL"), keys(Multi.encode(found)))
        assertEquals(found, Multi.decode("""{"t":"found","id":"q1","n":0,"w":"FUTEBOL"}"""))
        assertEquals(mapOf("t" to "timeup", "n" to 3), keys(Multi.encode(Msg.TimeUp(3))))
        assertEquals(Msg.TimeUp(3), Multi.decode("""{"t":"timeup","n":3}"""))
        val cell = Msg.Cell("q1", 4, 8)
        assertEquals(mapOf("t" to "move", "id" to "q1", "n" to 4, "c" to 8), keys(Multi.encode(cell)))
        assertEquals(cell, Multi.decode("""{"t":"move","id":"q1","n":4,"c":8}"""))
        val move = Msg.Move("q1", 7, listOf(42, 28, 14))
        assertEquals(mapOf("t" to "move", "id" to "q1", "n" to 7, "p" to listOf(42, 28, 14)), keys(Multi.encode(move)))
        assertEquals(move, Multi.decode("""{"t":"move","id":"q1","n":7,"p":[42,28,14]}"""))
        assertEquals(mapOf("t" to "resign", "id" to "q1"), keys(Multi.encode(Msg.Resign("q1"))))
        assertEquals(Msg.Resign("q1"), Multi.decode("""{"t":"resign","id":"q1"}"""))
        println("JSON: " + listOf(hello, again, grid, found, Msg.TimeUp(3), cell, move, Msg.Resign("q1")).joinToString("\n      ") { Multi.encode(it) })
    }
}
