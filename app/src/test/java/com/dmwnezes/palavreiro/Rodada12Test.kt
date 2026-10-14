package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.game.Achievements
import com.dmwnezes.palavreiro.game.Chess
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.SudokuData
import com.dmwnezes.palavreiro.game.SudokuGame
import com.dmwnezes.palavreiro.game.SudokuLevel
import com.dmwnezes.palavreiro.data.Stats
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Rodada 12: Quadrante (Sudoku) e Xeque (Xadrez) — motor, mensagens e dados iguais aos do site. */
class Rodada12Test {
    private fun play(vararg moves: String): Chess.State {
        var s = Chess.newGame()
        for (m in moves) s = Chess.play(s, m) ?: error("lance ilegal $m")
        return s
    }

    // ---------- Xadrez: perft ----------
    @Test
    fun perftPosicaoInicial() {
        val p = Chess.start()
        val r = (1..3).map { Chess.perft(p, it) }
        println("xadrez perft inicial: 1=${r[0]} 2=${r[1]} 3=${r[2]}")
        assertEquals(listOf(20L, 400L, 8902L), r)
        // Kiwipete: roques, en passant e promoções no meio.
        val kiwi = Chess.fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, Chess.perft(kiwi, 1)); assertEquals(2039L, Chess.perft(kiwi, 2))
        println("xadrez perft kiwipete: 1=48 2=2039")
    }

    // ---------- Xadrez: regras ----------
    @Test
    fun roque() {
        val livre = Chess.legalUci(Chess.fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        assertTrue("e1g1" in livre && "e1c1" in livre)
        // Torre preta em f8 ataca f1: o curto passa por casa atacada; o longo pode (b1 atacada não importa).
        val atacado = Chess.legalUci(Chess.fen("1r2kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
        assertFalse("e1g1" in atacado); assertTrue("e1c1" in atacado)
        // Rei em xeque não roca.
        assertFalse(Chess.legalUci(Chess.fen("4r1k1/8/8/8/8/8/8/R3K2R w KQ - 0 1")).any { it == "e1g1" || it == "e1c1" })
        val s = Chess.play(Chess.newGame(Chess.fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")), "e1g1")!!
        assertEquals(Chess.ROOK, s.pos.b[Chess.square("f1")]); assertEquals(0, s.pos.b[Chess.square("h1")])
        assertEquals(Chess.KING, s.pos.b[Chess.square("g1")])
        assertEquals(12, s.pos.castle) // brancas perderam os dois roques
    }

    @Test
    fun enPassantEPromocao() {
        val s = play("e2e4", "a7a6", "e4e5", "d7d5")
        assertTrue("e5d6" in s.legal.map(Chess::uci))
        val ep = Chess.play(s, "e5d6")!!
        assertEquals(0, ep.pos.b[Chess.square("d5")]); assertEquals(Chess.PAWN, ep.pos.b[Chess.square("d6")])
        // En passant só no lance seguinte.
        assertFalse("e5d6" in play("e2e4", "a7a6", "e4e5", "d7d5", "h2h3", "h7h6").legal.map(Chess::uci))

        val pr = Chess.newGame(Chess.fen("8/P7/8/8/8/8/8/k6K w - - 0 1"))
        val ups = pr.legal.map(Chess::uci).filter { it.startsWith("a7a8") }.toSet()
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), ups)
        assertNull(Chess.play(pr, "a7a8")) // sem a peça não vale
        assertEquals(Chess.KNIGHT, Chess.play(pr, "a7a8n")!!.pos.b[Chess.square("a8")])
    }

    @Test
    fun mateAfogamentoEEmpates() {
        val mate = play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(Chess.End.MATE, mate.end); assertEquals(Chess.BLACK, mate.winner); assertTrue(mate.check)
        assertEquals("me", Chess.outcome(mate, Chess.BLACK)); assertEquals("opp", Chess.outcome(mate, Chess.WHITE))

        val afog = Chess.play(Chess.newGame(Chess.fen("k7/8/2Q5/8/8/8/8/7K w - - 0 1")), "c6c7")!!
        assertEquals(Chess.End.STALEMATE, afog.end); assertEquals("draw", Chess.outcome(afog, Chess.WHITE))

        val mat = Chess.play(Chess.newGame(Chess.fen("k7/8/8/8/8/8/1q6/KB6 w - - 0 1")), "a1b2")!!
        assertEquals(Chess.End.MATERIAL, mat.end)
        assertTrue(Chess.insufficient(Chess.fen("k7/8/8/8/8/8/8/KN6 w - - 0 1").b))
        assertFalse(Chess.insufficient(Chess.fen("k7/8/8/8/8/8/8/KNN5 w - - 0 1").b))
        assertFalse(Chess.insufficient(Chess.fen("k7/8/8/8/8/8/8/KP6 w - - 0 1").b))

        val cinquenta = Chess.play(Chess.newGame(Chess.fen("k7/8/8/8/8/8/8/KR6 w - - 99 60")), "b1b2")!!
        assertEquals(Chess.End.FIFTY, cinquenta.end)

        val rep = play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(Chess.End.REPETITION, rep.end)
        assertNull(play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1").end)
    }

    // ---------- Xadrez: computador ----------
    @Test
    fun computadorJogaLancesLegaisRapido() {
        // Mate em 1 na última fileira: os três níveis veem.
        val back = Chess.fen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        for (l in listOf(Chess.Level.MEDIO, Chess.Level.DIFICIL)) assertEquals("a1a8", Chess.uci(Chess.bestMove(back, l)!!))
        // Meio de jogo: Difícil (profundidade 3) responde com lance legal em ~1 s.
        var s = play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "d2d3", "f8c5")
        val rnd = Random(7)
        repeat(4) {
            val t0 = System.currentTimeMillis()
            val m = Chess.bestMove(s.pos, Chess.Level.DIFICIL, rnd)!!
            val ms = System.currentTimeMillis() - t0
            println("xadrez difícil: ${Chess.uci(m)} em $ms ms")
            assertTrue(m in s.legal)
            assertTrue("demorou $ms ms", ms < 2500)
            s = Chess.play(s, Chess.uci(m))!!
        }
        // Fácil: sempre legal (com aleatoriedade).
        repeat(10) { assertTrue(Chess.bestMove(s.pos, Chess.Level.FACIL, Random(it))!! in s.legal) }
        // Captura a dama largada.
        assertEquals("e4d5", Chess.uci(Chess.bestMove(Chess.fen("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1"), Chess.Level.MEDIO)!!))
    }

    // ---------- Xeque com amigo (modo k) ----------
    @Test
    fun mensagensIguaisAoSite() {
        val m = Msg.UMove("h1", 0, "e2e4")
        val js = JSONObject(Multi.encode(m))
        assertEquals("move", js.getString("t")); assertEquals("e2e4", js.getString("u")); assertEquals(0, js.getInt("n")); assertEquals("h1", js.getString("id"))
        assertEquals(m, Multi.decode("""{"t":"move","id":"h1","n":0,"u":"e2e4"}"""))
        assertEquals(Msg.UMove("g1", 9, "e7e8q"), Multi.decode("""{"t":"move","id":"g1","n":9,"u":"E7E8Q"}"""))
        assertNull(Multi.decode("""{"t":"move","id":"g1","n":9,"u":"e7e9"}"""))
        // Damas e Velha continuam iguais.
        assertTrue(Multi.decode("""{"t":"move","id":"g1","n":1,"p":[1,2]}""") is Msg.Move)
        assertTrue(Multi.decode("""{"t":"move","id":"g1","n":1,"c":4}""") is Msg.Cell)
    }

    @Test
    fun modoKNoLinkENosTextos() {
        val r = Multi.parse("abc123xyz789-k-21i3v9")!!
        assertEquals('k', r.mode)
        assertEquals(r, Multi.fromPasted("joga comigo abc123xyz789-k-21i3v9 valeu"))
        assertEquals("Xeque", Multi.modeName('k'))
        assertEquals("uma partida de Xeque", Multi.withArticle('k'))
        assertEquals(listOf(1, 3, 5), Multi.roundOptions('k')); assertEquals(1, Multi.defaultRounds('k'))
        assertEquals("1 partida", Multi.roundsLabel('k', 1))
        assertTrue('k' in Multi.SECTIONS.first { it.first == "Tabuleiro" }.second)
    }

    @Test
    fun partidaPelasMensagens() {
        assertTrue(Chess.hostIsWhite(0)); assertFalse(Chess.hostIsWhite(1))
        val msgs = listOf(
            Msg.UMove("b", 0, "e7e5"), // pretas fora da vez: ignorado
            Msg.UMove("w", 0, "e2e4"),
            Msg.UMove("w", 1, "d2d4"), // brancas de novo: ignorado
            Msg.UMove("b", 1, "e7e5"),
            Msg.UMove("b", 2, "e7e5"), // repetido: ignorado
            Msg.UMove("w", 2, "e1e3"), // ilegal: ignorado
            Msg.UMove("w", 2, "g1f3"),
        )
        val s = Chess.state(msgs, "w", "b")
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), s.history); assertEquals(Chess.BLACK, s.turn)
        val res = Chess.state(msgs + Msg.Resign("b"), "w", "b")
        assertEquals(Chess.End.RESIGN, res.end); assertEquals("me", Chess.outcome(res, Chess.WHITE))
        val mate = Chess.state(listOf("f2f3", "e7e5", "g2g4", "d8h4").mapIndexed { i, u -> Msg.UMove(if (i % 2 == 0) "w" else "b", i, u) } + Msg.Resign("b"), "w", "b")
        assertEquals(Chess.End.MATE, mate.end) // desistência depois do fim não muda nada
    }

    @Test
    fun materialECapturadas() {
        val s = play("e2e4", "d7d5", "e4d5", "d8d5")
        assertEquals(listOf(Chess.PAWN), Chess.captured(s.pos.b, Chess.WHITE))
        assertEquals(listOf(Chess.PAWN), Chess.captured(s.pos.b, Chess.BLACK))
        assertEquals(0, Chess.advantage(s.pos.b, Chess.WHITE))
        val q = Chess.fen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1")
        assertEquals(9, Chess.advantage(q.b, Chess.WHITE))
        assertEquals(-9, Chess.advantage(q.b, Chess.BLACK))
    }

    // ---------- Quadrante (Sudoku) ----------
    private val sudoku = SudokuData.parse(File("../shared/sudoku.txt").readText())

    private fun validSolution(s: String): Boolean {
        val d = s.map { it - '0' }
        val groups = (0 until 9).flatMap { k ->
            listOf((0 until 9).map { k * 9 + it }, (0 until 9).map { it * 9 + k }, (0 until 9).map { (k / 3 * 3 + it / 3) * 9 + k % 3 * 3 + it % 3 })
        }
        return groups.all { g -> g.map { d[it] }.toSet() == (1..9).toSet() }
    }

    @Test
    fun sudokusValidosContraASolucao() {
        for (lvl in SudokuLevel.entries) {
            val list = sudoku[lvl].orEmpty()
            assertEquals("366 de ${lvl.key}", 366, list.size)
            for (p in list) {
                assertTrue(validSolution(p.solution))
                assertTrue(p.puzzle.indices.all { p.puzzle[it] == '0' || p.puzzle[it] == p.solution[it] })
            }
            val givens = list.map { p -> p.puzzle.count { it != '0' } }.average()
            println("quadrante ${lvl.key}: ${list.size} jogos, média de ${"%.1f".format(givens)} números dados")
        }
        val easy = sudoku[SudokuLevel.FACIL]!!
        assertEquals(easy[5], SudokuData.daily(easy, 5)); assertEquals(easy[0], SudokuData.daily(easy, 366))
    }

    @Test
    fun partidaDeQuadrante() {
        val p = sudoku[SudokuLevel.FACIL]!![0]
        val g = SudokuGame(p)
        val empty = (0 until 81).filter { !g.given(it) }
        val i = empty[0]
        val right = p.solution[i] - '0'
        val wrong = (1..9).first { it != right }
        g.selected = i
        g.notesMode = true
        assertEquals(SudokuGame.Input.NOTE, g.input(right)); assertEquals(1 shl right, g.notes[i])
        g.notesMode = false
        assertEquals(SudokuGame.Input.WRONG, g.input(wrong)); assertEquals(1, g.errors); assertTrue(g.wrong(i))
        g.erase(); assertEquals(0, g.values[i])
        // Nota do mesmo número num vizinho some quando o certo entra.
        val peer = empty.first { it != i && it in SudokuGame.peers(i) }
        g.selected = peer; g.notesMode = true; g.input(right); g.notesMode = false
        g.selected = i
        assertEquals(SudokuGame.Input.RIGHT, g.input(right))
        assertEquals(0, g.notes[peer] and (1 shl right))
        g.erase(); assertEquals(right, g.values[i]) // número certo não sai
        // Salva e volta igual.
        val back = SudokuGame.fromJson(g.toJson())!!
        assertEquals(g.values.toList(), back.values.toList()); assertEquals(1, back.errors)
        // Completa: vence.
        for (k in empty) { g.selected = k; g.input(p.solution[k] - '0') }
        assertTrue(g.won); assertFalse(g.lost)
        // 3 erros: fim.
        val l = SudokuGame(p)
        l.selected = i
        repeat(3) { n -> l.input((1..9).filter { it != right }[n]) }
        assertTrue(l.lost); assertEquals(SudokuGame.Input.IGNORED, l.input(right))
        l.reset(); assertEquals(0, l.errors); assertFalse(l.over)
    }

    @Test
    fun conquistasERecordes() {
        for (k in listOf("sudoku_best_facil", "sudoku_best_medio", "sudoku_best_dificil", "sudoku_won", "sudoku_perfect", "xadrez_won_facil", "xadrez_won_medio", "xadrez_won_dificil")) {
            assertTrue(k, k in Records.KEYS)
        }
        assertTrue("sudoku_best_dificil" in Records.LOWER_IS_BETTER && "xadrez_won_dificil" !in Records.LOWER_IS_BETTER)
        val none = Achievements.all(Stats(), Stats())
        val got = Achievements.all(Stats(), Stats(), r = Records(mapOf("sudoku_perfect" to 1, "xadrez_won_dificil" to 2)))
        for (t in listOf("Mestre do Quadrante", "Xeque-mate")) {
            assertFalse(none.first { it.title == t }.unlocked); assertTrue(got.first { it.title == t }.unlocked)
        }
        assertNotNull(got.first { it.emoji == "♟️" })
    }
}
