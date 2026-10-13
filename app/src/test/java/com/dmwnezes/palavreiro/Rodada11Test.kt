package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.game.Adedonha
import com.dmwnezes.palavreiro.game.Forca
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Timed
import com.dmwnezes.palavreiro.game.TriviaDuel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Rodada 11: Adedonha, Trivia, Forca, sugestão de jogo e as mensagens novas (iguais às do site). */
class Rodada11Test {
    // ---------- links e modos ----------
    @Test
    fun modosNovosNoLink() {
        for (c in listOf('s', 'g', 'f')) {
            val r = Multi.parse("abc123xyz789-$c-21i3v9")!!
            assertEquals(c, r.mode)
            assertEquals(r, Multi.fromPasted("Bora? https://dmwnezes.github.io/termo/?mp=abc123xyz789-$c-21i3v9"))
            assertEquals(r, Multi.fromPasted("joga comigo abc123xyz789-$c-21i3v9 valeu"))
        }
        assertEquals("uma Adedonha", Multi.withArticle('s'))
        assertEquals("uma Trivia", Multi.withArticle('g'))
        assertEquals("uma Forca", Multi.withArticle('f'))
        assertEquals("um Termo", Multi.withArticle('t'))
        assertEquals(listOf(3, 5, 7), Multi.roundOptions('s')); assertEquals(5, Multi.defaultRounds('s'))
        assertEquals(listOf(5, 10, 15), Multi.roundOptions('g')); assertEquals(10, Multi.defaultRounds('g'))
        assertEquals(listOf(1, 3, 5), Multi.roundOptions('f')); assertEquals(3, Multi.defaultRounds('f'))
        assertEquals("3 rodadas", Multi.roundsLabel('s', 3))
        assertEquals("Melhor de 3", Multi.roundsLabel('f', 3))
        assertTrue(Multi.pointsMode('s') && Multi.pointsMode('g') && !Multi.pointsMode('f'))
        assertEquals(11, Multi.CODES.size)
        assertEquals(Multi.CODES.toSet(), Multi.SECTIONS.flatMap { it.second }.toSet())
        assertEquals(listOf("Palavras", "Conhecimento", "Tabuleiro"), Multi.SECTIONS.map { it.first })
        assertEquals("Uma letra, várias categorias", Multi.blurb('s'))
        assertEquals("Perguntas de 6 temas", Multi.blurb('g'))
        assertEquals("Um escolhe a palavra, o outro adivinha", Multi.blurb('f'))
        assertEquals("Uma palavra, quem acertar primeiro", Multi.blurb('t'))
        assertEquals("Regras brasileiras", Multi.blurb('x'))
        // "again" trocando para a Adedonha zera e usa as rodadas do modo novo.
        val room = Multi.Room("abc123xyz789", 'f', 5)
        val res = Multi.applyAgain(room, Msg.Again(9, 1, true, 's', null), false, 3)
        assertEquals('s', res.room.mode); assertEquals(5, res.rounds)
    }

    // ---------- mensagens: JSON exato ----------
    private fun json(m: Msg) = JSONObject(Multi.encode(m))
    private fun same(expected: String, m: Msg) {
        val a = JSONObject(expected); val b = json(m)
        assertTrue("esperado $expected, veio $b", a.similar(b))
        assertEquals(m, Multi.decode(expected))
        assertEquals(m, Multi.decode(Multi.encode(m)))
    }

    @Test
    fun mensagensNovas() {
        same("""{"t":"suggest","id":"g1","m":"x"}""", Msg.Suggest("g1", 'x'))
        same("""{"t":"sround","n":2,"k":0,"l":"M","c":["Nome","Animal","Fruta","Cor","Objeto","Profissão"]}""",
            Msg.SRound(2, 0, 'M', listOf("Nome", "Animal", "Fruta", "Cor", "Objeto", "Profissão")))
        same("""{"t":"stop","id":"h1","n":2,"k":1}""", Msg.Stop("h1", 2, 1))
        same("""{"t":"stop","id":"tempo","n":0,"k":3}""", Msg.Stop("tempo", 0, 3))
        same("""{"t":"ans","id":"g1","n":0,"k":0,"a":["Marcos","Macaco","","Marrom","Mesa","Médico"]}""",
            Msg.Ans("g1", 0, 0, listOf("Marcos", "Macaco", "", "Marrom", "Mesa", "Médico")))
        same("""{"t":"vote","id":"g1","n":0,"k":0,"v":[1,0,1,1,0,1]}""", Msg.Vote("g1", 0, 0, listOf(1, 0, 1, 1, 0, 1)))
        same("""{"t":"pick","id":"h1","r":4,"o":2,"ms":3810}""", Msg.Pick("h1", 4, 2, 3810))
        same("""{"t":"pick","id":"h1","r":5,"o":-1,"ms":15000}""", Msg.Pick("h1", 5, -1, 15000))
        same("""{"t":"secret","id":"h1","n":0,"th":"Comida","m":"... .. ......"}""", Msg.Secret("h1", 0, "Comida", "... .. ......"))
        same("""{"t":"guess","id":"g1","n":0,"l":"A"}""", Msg.Guess("g1", 0, 'A'))
        same("""{"t":"reveal","id":"h1","n":0,"l":"A","p":[1]}""", Msg.Reveal("h1", 0, 'A', listOf(1)))
        same("""{"t":"reveal","id":"h1","n":0,"l":"Z","p":[]}""", Msg.Reveal("h1", 0, 'Z', emptyList()))
        same("""{"t":"try","id":"g1","n":0,"w":"PAO DE QUEIJO"}""", Msg.Try("g1", 0, "PAO DE QUEIJO"))
        same("""{"t":"tryres","n":0,"ok":true}""", Msg.TryRes(0, true))
        same("""{"t":"word","n":0,"w":"Pão de queijo"}""", Msg.Word(0, "Pão de queijo"))
        // Modo desconhecido na sugestão é ignorado; letra minúscula vira maiúscula.
        assertNull(Multi.decode("""{"t":"suggest","id":"g1","m":"b"}"""))
        assertEquals(Msg.Guess("g1", 0, 'C'), Multi.decode("""{"t":"guess","id":"g1","n":0,"l":"c"}"""))
        // "pick" com opção fora de 0..3 conta como -1.
        assertEquals(Msg.Pick("a", 0, -1, 0), Multi.decode("""{"t":"pick","id":"a","r":0,"o":7}"""))
        // ms fora de 0..15000 é cortado.
        assertEquals(Msg.Pick("a", 0, 1, 15_000), Multi.decode("""{"t":"pick","id":"a","r":0,"o":1,"ms":99999}"""))
        assertEquals(Msg.Pick("a", 0, 1, 0), Multi.decode("""{"t":"pick","id":"a","r":0,"o":1,"ms":-5}"""))
        // Sugestão aceita: sempre série nova e com as rodadas guardadas do modo, mesmo sem mudar nada.
        val acc = Multi.againFor('x', 'x', false, 5, 6, 3, 3, force = true)
        assertTrue(acc.newSeries); assertEquals('x', acc.mode); assertEquals(3, acc.rounds)
        val acc2 = Multi.againFor('t', 'g', false, 5, 6, 3, 10, force = true)
        assertTrue(acc2.newSeries); assertEquals('g', acc2.mode); assertEquals(10, acc2.rounds)
    }

    // ---------- Adedonha ----------
    @Test
    fun adedonhaCategoriasELetras() {
        val cats = Adedonha.parse(File("../shared/adedonha.txt").readText())
        assertTrue(cats.size >= 6)
        assertTrue(cats.none { it.startsWith("#") || it.isBlank() })
        val six = Adedonha.pickCategories(cats, Random(1))
        assertEquals(6, six.size); assertEquals(6, six.toSet().size)
        val used = ArrayList<Char>()
        repeat(20) { used += Adedonha.pickLetter(used, Random(it)) }
        assertEquals(Adedonha.LETTERS.toSet(), used.toSet())
        assertTrue(used.none { it in "KQWXYZ" })
    }

    @Test
    fun adedonhaPontos() {
        assertTrue(Adedonha.autoValid("  Óculos ", 'O'))
        assertTrue(Adedonha.autoValid("élefante", 'E'))
        assertFalse(Adedonha.autoValid("", 'M'))
        assertFalse(Adedonha.autoValid("   ", 'M'))
        assertFalse(Adedonha.autoValid("Banana", 'M'))
        assertEquals("pao de queijo", Adedonha.norm("  Pão   de QUEIJO "))
        assertTrue(Adedonha.canStop(listOf("Maria", "macaco", "Manga"), 'M'))
        assertFalse(Adedonha.canStop(listOf("Maria", "", "Manga"), 'M'))
        assertFalse(Adedonha.canStop(listOf("Maria", "Gato", "Manga"), 'M'))

        val mine = listOf("Marcos", "Macaco", "", "Marrom", "Mesa", "Banana")
        val theirs = listOf("Maria", "macaco ", "Manga", "Marrom", "Mala", "Médico")
        // Sem votos (tudo ✓): iguais valem 5, diferentes 10, vazia/sem a letra 0.
        val (a, b) = Adedonha.score(mine, theirs, 'M', null, null, 6)
        assertEquals(listOf(10, 5, 0, 5, 10, 0), a)
        assertEquals(listOf(10, 5, 10, 5, 10, 10), b)
        // O amigo anulou o meu "Mesa" e eu anulei o "Marrom" dele: o meu Marrom vira 10 (o dele não é mais válido).
        val (c, e) = Adedonha.score(mine, theirs, 'M', listOf(1, 1, 1, 1, 0, 1), listOf(1, 1, 1, 0, 1, 1), 6)
        assertEquals(listOf(10, 5, 0, 10, 0, 0), c)
        assertEquals(listOf(10, 5, 10, 0, 10, 10), e)
        // Voto 1 não salva resposta automática ✗; votos que eu mando já vêm 0 nelas.
        assertEquals(listOf(1, 0, 0, 1), Adedonha.votesFor(listOf("Mesa", "Gato", "", "Mala"), 'M', listOf(true, true, true, true)))
        assertEquals(listOf(1, 0, 0, 0), Adedonha.votesFor(listOf("Mesa", "Gato", "", "Mala"), 'M', listOf(true, true, true, false)))
        // Voto sempre do tamanho das categorias; qualquer voto diferente de 0 vale 1.
        assertEquals(listOf(1, 0, 0), Adedonha.votesFor(listOf("Mesa"), 'M', emptyList(), 3))
        assertEquals(listOf(10, 0), Adedonha.score(listOf("Mesa", "Mala"), listOf("", ""), 'M', listOf(2, 0), null, 2).first)
        // "ans": completa até as categorias, arruma espaços e corta em 40.
        assertEquals(listOf("Pão de queijo", "", ""), Adedonha.answers(listOf("  Pão   de  queijo "), 3))
        assertEquals(40, Adedonha.answers(listOf("M".repeat(60)), 1)[0].length)
        assertEquals(listOf("a", "b"), Adedonha.answers(listOf("a", "b", "c"), 2))
    }

    private fun t(at: Long, m: Msg) = Timed(at, m)

    @Test
    fun adedonhaRodadaCompleta() {
        val cats = listOf("Nome", "Animal", "Fruta")
        val msgs = mutableListOf(t(1000, Msg.SRound(0, 0, 'M', cats)))
        fun st(now: Long, left: Boolean = false) = Adedonha.state(msgs, 0, "me", "op", 2, now, left)
        assertEquals(Adedonha.Stage.SPIN, st(1200).current!!.stage(1200))
        assertEquals(Adedonha.Stage.FILL, st(3000).current!!.stage(3000))
        // Rodada de outra partida (n) é ignorada.
        msgs += t(3500, Msg.Stop("op", 1, 0))
        assertEquals(Adedonha.Stage.FILL, st(4000).current!!.stage(4000))
        msgs += t(20_000, Msg.Stop("op", 0, 0))
        msgs += t(20_100, Msg.Stop("me", 0, 0)) // o segundo STOP não muda nada
        assertEquals("op", st(20_200).current!!.stop!!.id)
        assertEquals(Adedonha.Stage.LOCKED, st(20_200).current!!.stage(20_200))
        msgs += t(20_300, Msg.Ans("me", 0, 0, listOf("Marcos", "Macaco", "")))
        msgs += t(20_500, Msg.Ans("op", 0, 0, listOf("Maria", "Macaco", "Manga")))
        val v = st(21_000).current!!
        assertEquals(Adedonha.Stage.VOTE, v.stage(21_000)); assertEquals(20_500, v.voteStart)
        msgs += t(30_000, Msg.Vote("me", 0, 0, listOf(1, 1, 0)))  // eu anulei a Manga dele
        assertNull(st(31_000).current!!.resolvedAt)
        msgs += t(32_000, Msg.Vote("op", 0, 0, listOf(1, 1, 1)))
        val r = st(33_000)
        assertEquals(32_000L, r.current!!.resolvedAt)
        assertEquals(listOf(10, 5, 0), r.current!!.myPoints)
        assertEquals(listOf(10, 5, 0), r.current!!.oppPoints)
        assertEquals(15, r.me); assertEquals(15, r.opp)
        assertFalse(r.over)
        // Segunda rodada: o amigo não vota a tempo (40 s + 5 s de folga) e conta tudo ✓.
        msgs += t(38_000, Msg.SRound(0, 1, 'P', emptyList()))
        msgs += t(50_000, Msg.Stop("me", 0, 1))
        msgs += t(50_100, Msg.Ans("me", 0, 1, listOf("Paulo", "Pato", "Pera")))
        msgs += t(50_200, Msg.Ans("op", 0, 1, listOf("Pedro", "Pato", "")))
        msgs += t(51_000, Msg.Vote("me", 0, 1, listOf(1, 1, 0)))
        assertEquals(cats, st(51_000).current!!.cats)
        assertNull(st(60_000).current!!.resolvedAt)
        val end = st(50_200 + 45_000)
        assertEquals(95_200L, end.current!!.resolvedAt)
        assertEquals(listOf(10, 5, 10), end.current!!.myPoints)
        assertEquals(listOf(10, 5, 0), end.current!!.oppPoints)
        assertTrue(end.over)
        assertEquals(40, end.me); assertEquals(30, end.opp)
        assertEquals("me", end.outcome())
        assertEquals(listOf('M', 'P'), end.usedLetters)
    }

    @Test
    fun adedonhaAmigoNaoResponde() {
        val msgs = listOf(
            t(0, Msg.SRound(0, 0, 'C', listOf("Cor", "Comida"))),
            t(5000, Msg.Stop("tempo", 0, 0)),
            t(5100, Msg.Ans("me", 0, 0, listOf("Cinza", "Cuscuz"))),
        )
        val s = Adedonha.state(msgs, 0, "me", "op", 1, 9000, false)
        assertEquals(Adedonha.Stage.LOCKED, s.current!!.stage(9000))
        val gone = Adedonha.state(msgs, 0, "me", "op", 1, 15_001, false).current!!
        assertTrue(gone.oppGone); assertEquals(listOf("", ""), gone.theirs)
        val voted = Adedonha.state(msgs + t(15_100, Msg.Vote("me", 0, 0, listOf(0, 0))), 0, "me", "op", 1, 15_200, false)
        assertEquals(listOf(10, 10), voted.current!!.myPoints)
        assertTrue(voted.over); assertEquals("me", voted.outcome())
    }

    // ---------- Trivia ----------
    @Test
    fun triviaSelecaoReferencia() {
        val all = TriviaDuel.parse(File("../shared/trivia.txt").readText())
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.wrong.size == 3 })
        val seed = 123456789L
        val q = all.size
        val idx = TriviaDuel.indices(q, seed, 10)
        println("TRIVIA Q=$q SEED=$seed")
        idx.forEachIndexed { k, i ->
            val opts = TriviaDuel.options(all[i], seed, k)
            val right = TriviaDuel.correctIndex(seed, k)
            assertEquals(all[i].right, opts[right])
            println("TRIVIA k=$k idx=$i giro=${TriviaDuel.rotation(seed, k)} certa=$right ${all[i].category} | ${all[i].text} -> ${opts.joinToString(" / ")}")
        }
        assertEquals(minOf(10, q), idx.size)
        assertEquals(idx.size, idx.toSet().size)
        // Fórmula conferida à mão: k=0 → ((SEED mod Q)·7919 mod Q + 104729) mod Q.
        assertEquals((((seed % q) * 7919 % q + 104729) % q).toInt(), idx[0])
        // Conferido com o js/multi-trivia.js do site sobre o mesmo shared/trivia.txt (Q = 669).
        if (q == 669) {
            assertEquals(listOf(191, 99, 7, 584, 492, 400, 308, 216, 124, 32), idx)
            assertEquals(listOf(3, 0, 1, 2, 3, 0, 1, 2, 3, 0), idx.indices.map { TriviaDuel.correctIndex(seed, it) })
        }
    }

    @Test
    fun triviaOpcoesGiram() {
        val q = TriviaDuel.Question("Arte", "?", "C", listOf("E1", "E2", "E3"))
        // SEED 123456789: (SEED + k·7) mod 4 → k=0: 1, k=1: 0, k=2: 3, k=3: 2.
        assertEquals(1, TriviaDuel.rotation(123456789, 0))
        assertEquals(listOf("E1", "E2", "E3", "C"), TriviaDuel.options(q, 123456789, 0))
        assertEquals(3, TriviaDuel.correctIndex(123456789, 0))
        assertEquals(listOf("C", "E1", "E2", "E3"), TriviaDuel.options(q, 123456789, 1))
        assertEquals(0, TriviaDuel.correctIndex(123456789, 1))
        assertEquals(listOf("E3", "C", "E1", "E2"), TriviaDuel.options(q, 123456789, 2))
        assertEquals(1, TriviaDuel.correctIndex(123456789, 2))
        assertEquals(listOf("E2", "E3", "C", "E1"), TriviaDuel.options(q, 123456789, 3))
        assertEquals(2, TriviaDuel.correctIndex(123456789, 3))
        // Linhas inválidas ficam de fora.
        val parsed = TriviaDuel.parse("# x\nArte|P|C|1|2|3\nArte|P|C|1|2\n\nArte|P||1|2|3\n Ciência | Q | A | B | C | D ")
        assertEquals(2, parsed.size); assertEquals("Ciência", parsed[1].category); assertEquals("Q", parsed[1].text)
        // Q pequeno: nunca repete e para no tamanho.
        assertEquals(listOf(0, 1, 2).toSet(), TriviaDuel.indices(3, 77, 10).toSet())
    }

    @Test
    fun triviaPlacarEDesempate() {
        val seed = 4L // giro (4 + 7k) mod 4: k=0 → 0 (certa 0), k=1 → 3 (certa 1), k=2 → 2 (certa 2)
        val msgs = mutableListOf<Timed>()
        fun st(now: Long, left: Boolean = false) = TriviaDuel.state(msgs, "me", "op", 3, seed, 0, now, left)
        assertEquals(0, st(100).current)
        msgs += t(4000, Msg.Pick("me", 0, 0, 2500))
        assertEquals(0, st(5000).current)
        msgs += t(6000, Msg.Pick("op", 0, 0, 4500))
        assertEquals(1, st(6100).current); assertEquals(9000, st(6100).startOf(1, 0))
        // Rodada 1: o amigo saiu, conta como -1.
        msgs += t(12_000, Msg.Pick("me", 1, 1, 1500))
        assertEquals(1, st(12_100).current)
        assertEquals(2, st(12_100, left = true).current)
        // Rodada 1 também fecha pelo prazo (início + 1,5 s + 15 s + 10 s).
        assertEquals(2, st(9000 + 26_500).current)
        msgs += t(40_000, Msg.Pick("op", 2, 2, 1000))
        msgs += t(40_500, Msg.Pick("me", 2, 1, 2000))
        val s = st(41_000)
        assertTrue(s.over)
        assertEquals(2, s.myHits); assertEquals(2, s.oppHits)
        assertEquals(4000L, s.myMs); assertEquals(5500L, s.oppMs)
        assertEquals("me", s.outcome())
        assertTrue(s.iHit(0) && s.iHit(1) && !s.iHit(2))
        assertTrue(s.oppHit(0) && !s.oppHit(1) && s.oppHit(2))
    }

    // ---------- Forca ----------
    @Test
    fun forcaPalavraEMascara() {
        assertTrue(Forca.validWord("guarda-chuva"))
        assertTrue(Forca.validWord("  pão   de queijo "))
        assertTrue(Forca.validWord("Açaí"))
        assertFalse(Forca.validWord("a"))
        assertFalse(Forca.validWord("abc1"))
        assertFalse(Forca.validWord("-casa"))
        assertFalse(Forca.validWord("abcdefghijklmnopqrstu")) // 21 letras
        assertFalse(Forca.validWord("abcdefghij klmnopqrst")) // 21 caracteres contando o espaço
        assertTrue(Forca.validWord("abcdefghi klmnopqrst")) // 20 caracteres
        assertTrue(Forca.validWord("guarda - chuva")) // espaço em volta do hífen sai
        assertEquals("guarda-chuva", Forca.clean("guarda - chuva"))
        // NFC: "a" + acento combinante vira "á".
        assertEquals("pá", Forca.clean("pa\u0301"))
        assertEquals("pão de queijo", Forca.clean("  pão   de queijo "))
        assertEquals("... .. ......", Forca.mask("Pão de queijo"))
        assertEquals("......-.....", Forca.mask("guarda-chuva"))
        assertTrue(Forca.validTheme("Comida")); assertFalse(Forca.validTheme("  ")); assertFalse(Forca.validTheme("x".repeat(25)))
        // Letras com acento contam pela letra base; C revela Ç.
        assertEquals(listOf(1), Forca.positions("Pão de queijo", 'A'))
        assertEquals(listOf(0, 2, 4), Forca.positions("ÁxÃxA", 'A'))
        assertEquals(listOf(0, 2), Forca.positions("CoÇa", 'C'))
        assertEquals(listOf(2, 12), Forca.positions("Pão de queijo", 'O'))
        assertEquals(listOf(5, 9), Forca.positions("Pão de queijo", 'E'))
        assertTrue(Forca.tryOk("PAO DE QUEIJO", "Pão de queijo"))
        assertTrue(Forca.tryOk("pão-de-queijo", "Pão de queijo"))
        assertTrue(Forca.tryOk(" guarda chuva ", "guarda-chuva"))
        assertFalse(Forca.tryOk("PAO DE QUEIJOS", "Pão de queijo"))
        assertTrue(Forca.chooserIsHost(0)); assertFalse(Forca.chooserIsHost(1)); assertTrue(Forca.chooserIsHost(2))
    }

    @Test
    fun forcaAdivinhou() {
        val w = "Açaí"
        val msgs = mutableListOf<Msg>(Msg.Secret("h", 0, "Fruta", Forca.mask(w)))
        fun st() = Forca.state(msgs, 0, "h", "g")
        assertEquals("....", st().shown)
        for (l in listOf('A', 'Z', 'C', 'I')) {
            msgs += Msg.Guess("g", 0, l)
            msgs += Msg.Reveal("h", 0, l, Forca.positions(w, l))
        }
        val s = st()
        assertEquals("ACAI", s.shown)
        assertEquals(setOf('A', 'C', 'I'), s.hits); assertEquals(setOf('Z'), s.misses)
        assertEquals(1, s.errors)
        assertTrue(s.solved && s.over && !s.lost)
        // Mensagens de outra rodada não contam.
        assertEquals("", Forca.state(msgs, 1, "h", "g").shown)
        msgs += Msg.Word(0, w)
        assertEquals("Açaí", st().word)
    }

    @Test
    fun forcaChuteEEnforcado() {
        val w = "Pão de queijo"
        val msgs = mutableListOf<Msg>(Msg.Secret("h", 1, "Comida", Forca.mask(w)))
        fun st() = Forca.state(msgs, 1, "h", "g")
        msgs += Msg.Try("g", 1, "PAO DE MEL")
        assertEquals(listOf("PAO DE MEL" to null), st().tries)
        msgs += Msg.TryRes(1, false)
        assertEquals(1, st().errors)
        msgs += Msg.Try("g", 1, "PAO DE QUEIJO"); msgs += Msg.TryRes(1, true)
        assertTrue(st().solved)
        // Outra rodada: 6 erros → enforcado.
        val m2 = mutableListOf<Msg>(Msg.Secret("g", 2, "Casa", Forca.mask("sofá")))
        for (l in "BCDGHJ") { m2 += Msg.Guess("h", 2, l); m2 += Msg.Reveal("g", 2, l, Forca.positions("sofá", l)) }
        val lost = Forca.state(m2, 2, "g", "h")
        assertEquals(6, lost.errors); assertTrue(lost.lost && lost.over && !lost.solved)
        // Depois do fim, nada mais conta.
        m2 += Msg.Reveal("g", 2, 'S', listOf(0))
        assertEquals("....", Forca.state(m2, 2, "g", "h").shown)
        // Posição inválida no reveal é ignorada; reveal repetido não conta duas vezes.
        val m3 = mutableListOf<Msg>(Msg.Secret("h", 0, "X", "..-.."), Msg.Reveal("h", 0, 'A', listOf(2, 9)), Msg.Reveal("h", 0, 'A', listOf()))
        val s3 = Forca.state(m3, 0, "h", "g")
        assertEquals(1, s3.errors); assertEquals("..-..", s3.shown)
    }

    @Test
    fun interopComOSite() {
        val c = listOf("Cor", "Comida")
        // O amigo saiu entre rodadas: a partida acaba com o placar de agora; no meio da rodada, só depois dela fechar.
        val r1 = listOf(
            t(0, Msg.SRound(0, 0, 'C', c)), t(5000, Msg.Stop("me", 0, 0)),
            t(5100, Msg.Ans("me", 0, 0, listOf("Cinza", "Cuscuz"))), t(5200, Msg.Ans("op", 0, 0, listOf("Cobre", "Caju"))),
            t(6000, Msg.Vote("me", 0, 0, listOf(1, 1))), t(6100, Msg.Vote("op", 0, 0, listOf(1, 1))),
        )
        // sround: só k = anterior + 1 e k < N; repetido, pulando ou além de N é ignorado.
        val seq = listOf(t(1, Msg.SRound(0, 1, 'B', c))) + r1 + listOf(
            t(6200, Msg.SRound(0, 0, 'D', c)), t(6300, Msg.SRound(0, 2, 'E', c)), t(6400, Msg.SRound(0, 1, 'F', c)),
            t(6500, Msg.SRound(0, 1, 'G', c)), t(6600, Msg.SRound(0, 2, 'H', c)),
        )
        assertEquals(listOf('C', 'F'), Adedonha.state(seq, 0, "me", "op", 2, 7000, false).usedLetters)
        val between = Adedonha.state(r1, 0, "me", "op", 5, 7000, true)
        assertTrue(between.over); assertEquals(20, between.me)
        val mid = Adedonha.state(r1.take(2), 0, "me", "op", 5, 5050, true)
        assertFalse(mid.over)
        // Forca: "tryres" ok:false sem chute pendente também é erro.
        val f = listOf<Msg>(Msg.Secret("h", 0, "X", "...."), Msg.TryRes(0, false))
        assertEquals(1, Forca.state(f, 0, "h", "g").errors)
        // Chute: sem acento, maiúsculo, só A–Z dos dois lados.
        assertTrue(Forca.tryOk("GUARDACHUVA", "guarda-chuva"))
        assertTrue(Forca.tryOk("pao de  queijo!", "Pão de queijo"))
    }
}
