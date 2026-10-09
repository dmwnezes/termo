package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.ConnectionsGame
import com.dmwnezes.palavreiro.game.DefineGame
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.ReverseGame
import com.dmwnezes.palavreiro.game.Rules
import com.dmwnezes.palavreiro.game.SynonymGame
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.WordSearch
import com.dmwnezes.palavreiro.game.WordSearchData
import com.dmwnezes.palavreiro.game.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class NewGamesTest {
    private val words = Words(File("../shared/words.js").readText())
    private fun asset(n: String) = File("../shared/$n").readText()

    @Test
    fun duetoEQuarteto() {
        val d = TermoGame(Mode.DUETO, words, store = null) { LocalDate.of(2026, 10, 9) }
        assertEquals(2, d.answers.size); assertEquals(7, d.maxTries)
        assertTrue(d.answers[0] != d.answers[1])
        // Acerta a primeira palavra: o tabuleiro 1 fecha, o jogo continua.
        d.answers[0].forEach(d::type); d.submit(); d.finishReveal()
        assertTrue(d.isSolved(0)); assertFalse(d.over)
        d.answers[1].forEach(d::type); d.submit(); d.finishReveal()
        assertTrue(d.over && d.won)
        assertEquals(1, d.boardGuesses(0).size)
        assertEquals(2, d.boardGuesses(1).size)

        val q = TermoGame(Mode.QUARTETO, words, store = null) { LocalDate.of(2026, 10, 9) }
        assertEquals(4, q.answers.toSet().size); assertEquals(9, q.maxTries)
        val wrong = words.answers.first { it !in q.answers }
        repeat(9) { wrong.forEach(q::type); q.submit(); q.finishReveal() }
        assertTrue(q.over); assertFalse(q.won)
        // Partida livre depois do desafio do dia.
        q.newWord(); assertFalse(q.over); assertFalse(q.isDaily)
    }

    @Test
    fun conexoesTemDesafiosValidos() {
        val ps = ConnectionsData.parse(asset("conexoes.txt"))
        assertTrue(ps.size >= 30)
        ps.forEachIndexed { i, p ->
            assertEquals("desafio $i", 4, p.groups.size)
            p.groups.forEach { g -> assertEquals("${g.name}", 4, g.words.size) }
            assertEquals("palavra repetida no desafio $i", 16, p.allWords.toSet().size)
        }
        val g = ConnectionsGame(ps[0], 1)
        ps[0].groups[0].words.forEach(g::toggle)
        assertTrue(g.submit() is ConnectionsGame.Result.Correct)
        // Três de um grupo + uma de outro = "falta só uma".
        (ps[0].groups[1].words.take(3) + ps[0].groups[2].words.first()).forEach(g::toggle)
        assertEquals(ConnectionsGame.Result.OneAway, g.submit())
    }

    @Test
    fun cacaPalavrasMontaTodasAsGrades() {
        val themes = WordSearchData.parse(asset("caca.txt"))
        assertTrue(themes.size >= 30)
        for ((i, t) in themes.withIndex()) {
            val ws = WordSearch(t, seed = i * 7L)
            assertEquals(t.name, t.words.size, ws.placed.size)
            for (p in ws.placed) {
                p.cells.forEachIndexed { k, (r, c) -> assertEquals(p.word[k], ws.grid[r][c]) }
            }
            val p = ws.placed.first()
            val (r2, c2) = p.cells.last()
            assertNotNull(ws.check(r2, c2, p.row, p.col)) // de trás para frente também vale
        }
    }

    @Test
    fun reversoAdivinhaQuaseSempre() {
        var ok = 0
        val sample = words.answers.filterIndexed { i, _ -> i % 10 == 0 }
        for (secret in sample) {
            val g = ReverseGame(words)
            while (!g.over) {
                val fb = Rules.evaluate(g.guess, secret)
                fb.forEachIndexed { i, m -> g.setMark(i, m) }
                g.answer()
            }
            assertFalse("contradição com $secret", g.contradiction)
            if (g.appWon) ok++
        }
        println("Reverso acertou $ok de ${sample.size}")
        assertTrue(ok >= sample.size * 0.85)
    }

    @Test
    fun definicoesESinonimos() {
        val defs = QuizData.definitions(asset("definicoes.txt"))
        assertTrue(defs.size >= 100)
        val d = DefineGame(defs, seed = 3)
        val target = d.target
        target.filter { it in 'A'..'Z' }.forEachIndexed { k, c -> if (d.slots[k] !in d.revealed) d.type(c) }
        assertEquals(true, d.submit())

        val (pairs, fams) = QuizData.synonyms(asset("sinonimos.txt"))
        assertTrue(pairs.size >= 120)
        val s = SynonymGame(pairs, fams, seed = 5)
        repeat(60) {
            assertEquals(4, s.options.toSet().size)
            assertTrue(s.question.synonym in s.options)
            // Nenhuma alternativa errada é da mesma família de sentido.
            val fam = fams.filter { s.question.word in it || s.question.synonym in it }.flatten().toSet()
            assertTrue(s.options.filter { it != s.question.synonym }.none { it in fam })
            s.answer(s.question.synonym); s.next()
        }
        assertEquals(60, s.chain)
    }

    @Test
    fun conexoesInfinito() {
        val puzzles = ConnectionsData.parse(asset("conexoes.txt"))
        val fam = ConnectionsData.families(asset("conexoes-familias.txt"))
        // Todo grupo tem família cadastrada.
        puzzles.flatMap { it.groups }.forEach { assertNotNull(it.name, fam[it.name]) }
        val seen = HashSet<String>()
        for (seed in 0L until 500L) {
            val p = ConnectionsData.remix(puzzles, fam, seed)
            assertEquals(listOf(0, 1, 2, 3), p.groups.map { it.level })
            val all = p.allWords.map { Words.normalize(it) }
            assertEquals(16, all.toSet().size)
            val fs = p.groups.flatMap { fam.getValue(it.name) }
            assertEquals(fs.size, fs.toSet().size)
            assertFalse(p.groups.any { "fora" in fam.getValue(it.name) })
            assertEquals(p, ConnectionsData.remix(puzzles, fam, seed))
            seen += p.groups.joinToString { it.name }
        }
        assertTrue(seen.size > 450)
    }

    @Test
    fun cacaInfinito() {
        val pool = WordSearchData.parse(asset("caca.txt")) + WordSearchData.parse(asset("caca-extra.txt"))
        val names = HashSet<String>()
        for (seed in 0L until 300L) {
            val w = WordSearchData.infinite(pool, seed)
            assertEquals(8, w.placed.size)
            names += w.theme.name
            assertEquals(w.grid.map { String(it) }, WordSearchData.infinite(pool, seed).grid.map { String(it) })
        }
        assertTrue(names.size > 50)
    }

    @Test
    fun antonimos() {
        val (_, synFam) = QuizData.synonyms(asset("sinonimos.txt"))
        val (pairs, fam) = QuizData.antonyms(asset("antonimos.txt"), synFam)
        assertTrue(pairs.size >= 280)
        // Cada palavra tem um único antônimo (senão a pergunta ao contrário teria duas respostas).
        assertEquals(pairs.size, pairs.map { it.word }.toSet().size)
        val g = com.dmwnezes.palavreiro.game.SynonymGame(pairs, fam, seed = 7)
        repeat(300) {
            val q = g.question
            assertEquals(4, g.options.size); assertEquals(4, g.options.toSet().size)
            assertTrue(q.synonym in g.options)
            // Nenhuma alternativa errada é antônimo registrado da palavra.
            val antOf = pairs.filter { it.word == q.word }.map { it.synonym }.toSet()
            assertEquals(1, g.options.count { it in antOf })
            assertTrue(g.answer(q.synonym)); g.next()
        }
    }
}
