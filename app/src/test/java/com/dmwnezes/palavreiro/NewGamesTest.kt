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
        // O desafio do dia não vira partida livre; as livres ficam na aba Infinito.
        q.newWord(); assertTrue(q.over); assertTrue(q.isDaily)
        val free = TermoGame(Mode.QUARTETO, words, store = null, freePlay = true) { LocalDate.of(2026, 10, 9) }
        assertFalse(free.isDaily); free.newWord(); assertFalse(free.over)
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

    @Test
    fun sinonimoOuAntonimo() {
        val (syn, sf) = QuizData.synonyms(asset("sinonimos.txt"))
        val (ant, af) = QuizData.antonyms(asset("antonimos.txt"), sf)
        val synOf = syn.groupBy({ it.word }, { it.synonym }); val antOf = ant.groupBy({ it.word }, { it.synonym })
        val g = com.dmwnezes.palavreiro.game.SynAntGame(syn, sf, ant, af, seed = 11)
        val kinds = ArrayList<com.dmwnezes.palavreiro.game.SaKind>()
        var tricks = 0
        repeat(2000) {
            val q = g.question
            kinds += q.kind
            assertEquals(4, g.options.size); assertEquals(4, g.options.toSet().size)
            assertTrue(q.answer in g.options)
            val other = (if (q.kind == com.dmwnezes.palavreiro.game.SaKind.SINONIMO) antOf else synOf)[q.word].orEmpty().toSet()
            if (q.trick != null) { tricks++; assertTrue(q.trick in g.options); assertTrue(q.trick in other); assertTrue(q.trick != q.answer) }
            // Sem pegadinha, o par do outro tipo nunca aparece por acaso.
            else assertTrue(g.options.none { it in other && it != q.answer })
            assertTrue(g.answer(q.answer)); g.next()
        }
        // Nunca 3 do mesmo tipo seguidos, os dois tipos aparecem e há pegadinhas.
        assertTrue(kinds.windowed(3).none { it.toSet().size == 1 })
        assertTrue(kinds.count { it == com.dmwnezes.palavreiro.game.SaKind.ANTONIMO } in 600..1400)
        assertTrue("pegadinhas: $tricks", tricks in 200..1200)
        val t = com.dmwnezes.palavreiro.game.SynAntGame(syn, sf, ant, af, seed = 3)
        t.timeUp(); assertTrue(t.over)
    }

    @Test
    fun desistir() {
        val words = com.dmwnezes.palavreiro.game.Words(File("../shared/words.js").readText())
        val g = com.dmwnezes.palavreiro.game.TermoGame(com.dmwnezes.palavreiro.data.Mode.QUARTETO, words, store = null, freePlay = true)
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        "MUN".forEach(g::type)
        g.giveUp()
        assertTrue(g.over); assertFalse(g.won); assertTrue(g.gaveUp); assertEquals(1, g.rows.size)
        g.giveUp(); assertEquals(1, g.rows.size)
    }

    @Test
    fun mestreMandou() {
        val (syn, synFam) = QuizData.synonyms(asset("sinonimos.txt"))
        val (ant, antFam) = QuizData.antonyms(asset("antonimos.txt"), synFam)
        val data = com.dmwnezes.palavreiro.game.MestreData(
            ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), syn, synFam, ant, antFam,
        )
        assertTrue(data.bank.size > 300)
        val g = com.dmwnezes.palavreiro.game.MestreGame(data, kotlin.random.Random(9))
        val seen = HashSet<com.dmwnezes.palavreiro.game.OrderType>()
        val L = com.dmwnezes.palavreiro.game.MestreData::letters
        repeat(400) {
            val r = g.round
            seen += r.type
            assertEquals(4, r.options.size); assertEquals(4, r.options.map { L(it) }.toSet().size)
            assertEquals(1, r.targets.size)
            assertTrue(r.text.startsWith("O mestre mandou") || r.text.startsWith("O mestre NÃO mandou"))
            // Confere as ordens de letra de forma independente.
            Regex("começa com ([A-Z])$|começam com ([A-Z])$").find(r.text)?.let { m ->
                val c = (m.groupValues[1] + m.groupValues[2])[0]
                assertEquals(r.targets, r.options.filter { L(it).first() == c }.toSet())
            }
            Regex("de (\\d+) letras").find(r.text)?.let { m ->
                assertEquals(r.targets, r.options.filter { L(it).length == m.groupValues[1].toInt() }.toSet())
            }
            if (r.text.endsWith("com acento") || r.text.endsWith("com acento!"))
                assertEquals(r.targets, r.options.filter { com.dmwnezes.palavreiro.game.MestreData.hasAccent(it) }.toSet())
            // Joga do jeito certo para a rodada.
            when (r.type) {
                com.dmwnezes.palavreiro.game.OrderType.NORMAL -> g.tap(r.targets.first())
                com.dmwnezes.palavreiro.game.OrderType.NAO_TOQUE -> g.tap(r.options.first { it !in r.targets })
                else -> g.timeUp()
            }
            assertEquals(true, g.result)
            g.next()
        }
        assertEquals(400, g.score); assertEquals(3, seen.size)
        // Erros: tocar numa pegadinha e deixar o tempo acabar numa ordem real.
        while (!g.round.type.isTrick) { g.timeUp(); if (g.over) break; g.next() }
        if (!g.over) { g.tap(g.round.options.first()); assertEquals(false, g.result) }
    }
}
