package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.data.Sync
import com.dmwnezes.palavreiro.game.Achievements
import com.dmwnezes.palavreiro.game.MemLevel
import com.dmwnezes.palavreiro.game.MemoryDeck
import com.dmwnezes.palavreiro.game.MemoryGame
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.TriviaCat
import com.dmwnezes.palavreiro.game.TriviaData
import com.dmwnezes.palavreiro.game.TriviaGame
import com.dmwnezes.palavreiro.game.TriviaGame.Phase
import com.dmwnezes.palavreiro.game.TriviaQ
import com.dmwnezes.palavreiro.game.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** Trivia e Jogo da Memória: regras, dados, recordes e conquistas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoloGamesTest {
    private fun asset(n: String) = File("../shared/$n").readText()

    /** Arquivo grande gerado: [per] perguntas por categoria. */
    private fun generated(per: Int = 100): String = buildString {
        appendLine("# gerado")
        TriviaCat.entries.forEach { c -> repeat(per) { i -> appendLine("${c.label}|Pergunta $i de ${c.label}?|Certa $i|Errada A$i|Errada B$i|Errada C$i") } }
    }

    // ---------- Trivia ----------

    @Test
    fun triviaLeArquivoSemente() {
        val qs = TriviaData.parse(asset("trivia.txt"))
        assertTrue("a semente tem ao menos 12 perguntas", qs.size >= 12)
        // Todas as 6 categorias aparecem e cada pergunta tem 3 erradas diferentes da certa.
        assertEquals(TriviaCat.entries.toSet(), qs.map { it.cat }.toSet())
        qs.forEach { q -> assertEquals(3, q.wrong.size); assertFalse(q.right in q.wrong) }
        // Linhas quebradas e categorias desconhecidas são ignoradas; acento e caixa não importam.
        val odd = TriviaData.parse("CIENCIA|a?|b|c|d|e\nCulinária|x|y|z|w|v\nArte|só três|a|b\nhistória|q?|1|2|3|4")
        assertEquals(listOf(TriviaCat.CIENCIA, TriviaCat.HISTORIA), odd.map { it.cat })
    }

    /** Responde sempre certo (ou errado) até a partida acabar, escolhendo a primeira categoria da coroa. */
    private fun play(g: TriviaGame, right: (Int) -> Boolean = { true }, limit: Int = 2000) {
        var n = 0
        while (!g.over && n < limit) {
            when (g.phase) {
                Phase.SPIN -> g.ask()
                Phase.QUESTION -> { val q = g.question!!; g.answer(if (right(n)) q.right else q.wrong[0]); n++ }
                Phase.ANSWERED -> g.next()
                Phase.CROWN -> assertTrue(g.chooseCrown(g.crownChoices.first()))
                else -> {}
            }
        }
    }

    @Test
    fun triviaFluxoDaCoroaEMedalhas() {
        val g = TriviaGame(TriviaData.parse(generated()), Random(1))
        assertEquals(Phase.SPIN, g.phase)
        assertNotNull(g.category)
        // 3 acertos seguidos: medidor 1, 2, 3 e então a coroa.
        repeat(3) { i ->
            g.ask(); assertEquals(Phase.QUESTION, g.phase)
            val q = g.question!!
            assertEquals(g.category, q.cat)
            assertEquals(4, g.options.size); assertTrue(q.right in g.options)
            assertTrue(g.answer(q.right))
            assertEquals(i + 1, g.meter)
            assertEquals(1, g.progress.values.sum() - i)
            g.next()
        }
        assertEquals(Phase.CROWN, g.phase)
        assertEquals(TriviaCat.entries, g.crownChoices)
        assertTrue(g.chooseCrown(TriviaCat.ARTE))
        assertTrue(g.crownRound)
        assertEquals(TriviaCat.ARTE, g.question!!.cat)
        assertTrue(g.answer(g.question!!.right))
        assertEquals(listOf(TriviaCat.ARTE), g.medals.toList())
        assertEquals(0, g.meter)
        g.next()
        assertEquals(Phase.SPIN, g.phase)
        // Medalha já ganha não aparece mais para a coroa.
        repeat(3) { g.ask(); g.answer(g.question!!.right); g.next() }
        assertEquals(Phase.CROWN, g.phase)
        assertFalse(TriviaCat.ARTE in g.crownChoices)
        assertFalse(g.chooseCrown(TriviaCat.ARTE))
        // Errar a pergunta da coroa: perde uma vida, sem medalha, medidor zerado.
        assertTrue(g.chooseCrown(TriviaCat.CIENCIA))
        assertFalse(g.answer(g.question!!.wrong[1]))
        assertEquals(2, g.lives); assertEquals(1, g.medals.size); assertEquals(0, g.meter)
        assertEquals(7, g.bestRun); assertEquals(0, g.run)
    }

    @Test
    fun triviaGanhaComAsSeisMedalhas() {
        val g = TriviaGame(TriviaData.parse(generated()), Random(7))
        play(g)
        assertTrue(g.won)
        assertEquals(TriviaCat.entries.toSet(), g.medals.toSet())
        assertEquals(24, g.right) // 6 × (3 acertos + 1 da coroa)
        assertEquals(3, g.lives)
        assertEquals(24, g.bestRun)
    }

    @Test
    fun triviaTresErrosTerminam() {
        val g = TriviaGame(TriviaData.parse(generated()), Random(3))
        g.ask(); g.answer(g.question!!.wrong[0]); g.next()
        assertEquals(2, g.lives)
        g.ask(); g.answer(null) // tempo esgotado também conta como erro
        assertEquals(1, g.lives); assertNull(g.picked); assertFalse(g.lastRight)
        g.next(); g.ask(); g.answer(g.question!!.right); g.next()
        assertEquals(1, g.meter)
        g.ask(); g.answer(g.question!!.wrong[2]); g.next()
        assertEquals(0, g.lives)
        assertEquals(Phase.OVER, g.phase)
        assertFalse(g.won); assertFalse(g.exhausted)
        // Depois do fim nada muda.
        g.ask(); assertFalse(g.answer("x")); assertEquals(Phase.OVER, g.phase)
    }

    @Test
    fun triviaSemRepetirNaPartida() {
        // Arquivo grande: 600 perguntas, uma partida longa errando de vez em quando.
        val big = TriviaData.parse(generated(100))
        val g = TriviaGame(big, Random(11))
        play(g, right = { it % 9 != 8 })
        assertEquals(g.asked.size, g.asked.map { it.text }.toSet().size)
        // Semente de 12 perguntas: sempre acertando, as perguntas acabam antes das 6 medalhas, sem repetir e sem travar.
        val seed = TriviaData.parse(asset("trivia.txt"))
        val small = TriviaData.parse(asset("trivia.txt").lines().filter { it.startsWith("#") || it.isBlank() }.joinToString("\n") + "\n" +
            seed.groupBy { it.cat }.values.flatMap { it.take(2) }.joinToString("\n") { "${it.cat.label}|${it.text}|${it.right}|${it.wrong.joinToString("|")}" })
        assertEquals(12, small.size)
        for (s in 1..20L) {
            val t = TriviaGame(small, Random(s))
            play(t)
            assertTrue(t.over); assertFalse(t.won)
            assertTrue(t.exhausted)
            assertEquals(12, t.asked.size)
            assertEquals(12, t.asked.toSet().size)
            // 12 perguntas = até 3 coroas (a última só vale se sobrar pergunta de categoria sem medalha).
            assertTrue(t.medals.size in 2..3)
            assertEquals(3, t.lives)
        }
        // Semente inteira (pode ter crescido): também termina sem repetir.
        val full = TriviaGame(seed, Random(5))
        play(full, right = { it % 4 != 3 })
        assertTrue(full.over)
        assertEquals(full.asked.size, full.asked.toSet().size)
        // Sem perguntas: termina na hora.
        val none = TriviaGame(emptyList(), Random(1))
        assertEquals(Phase.OVER, none.phase); assertTrue(none.exhausted)
    }

    @Test
    fun triviaRoletaSoSorteiaCategoriaComPergunta() {
        val only = listOf(TriviaQ(TriviaCat.ESPORTES, "a?", "1", listOf("2", "3", "4")), TriviaQ(TriviaCat.ESPORTES, "b?", "1", listOf("2", "3", "4")))
        val g = TriviaGame(only, Random(2))
        assertEquals(TriviaCat.ESPORTES, g.category)
        assertEquals(listOf(TriviaCat.ESPORTES), g.spinnable)
        g.ask(); g.answer("1"); g.next(); g.ask(); g.answer("1"); g.next()
        assertTrue(g.exhausted); assertEquals(Phase.OVER, g.phase)
    }

    // ---------- Memória ----------

    private val syn = QuizData.synonyms(asset("sinonimos.txt"))

    @Test
    fun memoriaParesUnicos() {
        val (pairs, families) = syn
        val famOf = HashMap<String, MutableSet<Int>>()
        families.forEachIndexed { i, f -> f.forEach { famOf.getOrPut(Words.normalize(it)) { HashSet() } += i } }
        for (lvl in MemLevel.entries) for (s in 1..40L) {
            val picked = MemoryDeck.pick(pairs, families, lvl.pairs, Random(s))
            assertEquals(lvl.pairs, picked.size)
            assertEquals(lvl.cols * lvl.rows, lvl.pairs * 2)
            val words = picked.flatMap { listOf(Words.normalize(it.word), Words.normalize(it.synonym)) }
            assertEquals("palavras repetidas", words.size, words.toSet().size)
            // Nenhuma família de sentido é compartilhada entre pares diferentes.
            val famsPerPair = picked.map { p -> listOf(p.word, p.synonym).flatMap { famOf[Words.normalize(it)].orEmpty() }.toSet() }
            for (a in famsPerPair.indices) for (b in a + 1 until famsPerPair.size) {
                assertTrue("família repetida: ${picked[a]} × ${picked[b]}", famsPerPair[a].intersect(famsPerPair[b]).isEmpty())
            }
            val cards = MemoryDeck.deal(picked, Random(s))
            assertEquals(lvl.pairs * 2, cards.size)
            cards.groupBy { it.pair }.values.forEach { assertEquals(2, it.size) }
        }
    }

    @Test
    fun memoriaAcertoErroEVitoria() {
        val (pairs, families) = syn
        val g = MemoryGame(MemLevel.FACIL, pairs, families, Random(4))
        assertEquals(12, g.cards.size)
        assertFalse(g.started)
        val byPair = g.cards.indices.groupBy { g.cards[it].pair }
        val (a0, a1) = byPair.getValue(0)
        val b0 = byPair.getValue(1)[0]
        // Erro: duas cartas de pares diferentes ficam à mostra e travam até virar de volta.
        assertEquals(MemoryGame.Flip.FIRST, g.flip(a0))
        assertEquals(MemoryGame.Flip.IGNORED, g.flip(a0))
        assertEquals(MemoryGame.Flip.MISS, g.flip(b0))
        assertTrue(g.locked); assertEquals(1, g.moves)
        assertEquals(MemoryGame.Flip.IGNORED, g.flip(a1))
        g.hideMismatch()
        assertFalse(g.locked); assertTrue(g.faceUp.isEmpty())
        // Acerto: o par fica achado.
        g.flip(a0)
        assertEquals(MemoryGame.Flip.MATCH, g.flip(a1))
        assertTrue(a0 in g.matched && a1 in g.matched)
        assertEquals(MemoryGame.Flip.IGNORED, g.flip(a0))
        assertEquals(2, g.moves)
        assertFalse(g.won)
        // O resto, sem erros: vitória com 1 erro + 6 pares = 7 jogadas.
        byPair.filterKeys { it != 0 }.values.forEach { (x, y) -> g.flip(x); assertEquals(MemoryGame.Flip.MATCH, g.flip(y)) }
        assertTrue(g.won)
        assertEquals(7, g.moves)
        // Difícil: 24 cartas.
        assertEquals(24, MemoryGame(MemLevel.DIFICIL, pairs, families, Random(9)).cards.size)
    }

    // ---------- Recordes e conquistas ----------

    @Test
    fun recordesEConquistas() {
        listOf("trivia_won", "trivia_best", "trivia_right", "mem_best_facil", "mem_best_medio_moves", "mem_best_dificil", "mem_best_dificil_moves")
            .forEach { assertTrue(it, it in Records.KEYS) }
        MemLevel.entries.forEach { assertTrue(it.timeKey in Records.LOWER_IS_BETTER); assertTrue(it.movesKey in Records.LOWER_IS_BETTER) }

        val titles = Achievements.all(Stats(), Stats()).map { it.title }
        val i = titles.indexOf("Obediente")
        assertEquals(listOf("Sabichão", "Enciclopédia", "Memória de elefante"), titles.subList(i + 1, i + 4))
        val none = Achievements.all(Stats(), Stats(), r = Records(mapOf("trivia_right" to 99, "mem_best_facil" to 30))).associateBy { it.title }
        assertFalse(none.getValue("Sabichão").unlocked)
        assertFalse(none.getValue("Enciclopédia").unlocked)
        assertEquals(99, none.getValue("Enciclopédia").progress)
        assertFalse(none.getValue("Memória de elefante").unlocked)
        val all = Achievements.all(Stats(), Stats(), r = Records(mapOf("trivia_won" to 1, "trivia_right" to 100, "mem_best_dificil" to 95))).associateBy { it.title }
        assertTrue(all.getValue("Sabichão").unlocked)
        assertTrue(all.getValue("Enciclopédia").unlocked)
        assertTrue(all.getValue("Memória de elefante").unlocked)
        assertEquals("🧠", all.getValue("Sabichão").emoji)
        assertEquals("🃏", all.getValue("Memória de elefante").emoji)
    }

    @Test
    fun sincronizaRecordesNovos() {
        val ctx: android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("palavreiro", 0).edit().clear().commit()
        val a = Store(ctx)
        a.setInt("trivia_won", 2); a.setInt("trivia_right", 140); a.setInt("mem_best_dificil", 80); a.setInt("mem_best_dificil_moves", 30)
        val code = Sync.export(a)
        ctx.getSharedPreferences("palavreiro", 0).edit().clear().commit()
        val b = Store(ctx)
        b.setInt("mem_best_dificil", 120); b.setInt("mem_best_dificil_moves", 25); b.setInt("trivia_right", 10)
        assertTrue(Sync.import(b, code))
        assertEquals(2, b.int("trivia_won"))
        assertEquals(140, b.int("trivia_right"))
        assertEquals("tempo menor ganha", 80, b.int("mem_best_dificil"))
        assertEquals("menos jogadas ganha", 25, b.int("mem_best_dificil_moves"))
    }
}
