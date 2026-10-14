package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.Achievements
import com.dmwnezes.palavreiro.game.AmpEvent
import com.dmwnezes.palavreiro.game.AmpulhetaData
import com.dmwnezes.palavreiro.game.AmpulhetaGame
import com.dmwnezes.palavreiro.game.MatizColor
import com.dmwnezes.palavreiro.game.MatizGame
import com.dmwnezes.palavreiro.game.Minstd
import com.dmwnezes.palavreiro.game.PodioData
import com.dmwnezes.palavreiro.game.PodioGame
import com.dmwnezes.palavreiro.game.PodioMark.GRAY
import com.dmwnezes.palavreiro.game.PodioMark.GREEN
import com.dmwnezes.palavreiro.game.PodioMark.YELLOW
import com.dmwnezes.palavreiro.game.PodioQ
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.game.shuffledBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** Rodada 12 (app A): Matiz, Pódio e Ampulheta — lógica e fórmulas do dia (iguais às do site). */
class Rodada12ATest {
    private val dia = 300L

    // ---------- Matiz ----------
    @Test
    fun deltaENota() {
        val red = MatizColor.lab(0xFF0000)
        assertEquals(53.2408, red[0], 1e-3); assertEquals(80.0925, red[1], 1e-3); assertEquals(67.2032, red[2], 1e-3)
        assertEquals(117.327, MatizColor.deltaE(0xFF0000, 0x000000), 1e-2)
        assertEquals(100.0, MatizColor.deltaE(0xFFFFFF, 0x000000), 1e-3)
        assertEquals(100, MatizColor.score(0x123456, 0x123456))
        assertEquals(0, MatizColor.score(0xFFFFFF, 0x000000))
        // (100,150,200) × (110,150,200): ΔE ≈ 2,648 → 96; × (120,140,190): ΔE ≈ 8,706 → 87.
        assertEquals(2.6485, MatizColor.deltaE(MatizColor.rgb(100, 150, 200), MatizColor.rgb(110, 150, 200)), 1e-3)
        assertEquals(96, MatizColor.score(MatizColor.rgb(100, 150, 200), MatizColor.rgb(110, 150, 200)))
        assertEquals(87, MatizColor.score(MatizColor.rgb(100, 150, 200), MatizColor.rgb(120, 140, 190)))
        // Cores escuras (ramo linear do Lab), notas iguais às do site: 82 e 98.
        assertEquals(82, MatizColor.score(MatizColor.rgb(10, 10, 10), MatizColor.rgb(20, 25, 5)))
        assertEquals(98, MatizColor.score(MatizColor.rgb(2, 2, 2), MatizColor.rgb(5, 1, 3)))
        assertEquals("🟩", MatizColor.emoji(90)); assertEquals("🟨", MatizColor.emoji(70)); assertEquals("⬛", MatizColor.emoji(69))
    }

    @Test
    fun matizDoDia() {
        // Dia 0 (conferido com P.matizDaily(0) do site): #819FF4 #47F08B #8D662E #9072F6 #A7ED16.
        val zero = MatizColor.daily(0).map(MatizColor::hex)
        println("MATIZ dia=0: ${zero.joinToString(" ")}")
        assertEquals(listOf("#819FF4", "#47F08B", "#8D662E", "#9072F6", "#A7ED16"), zero)
        val hex = MatizColor.daily(dia).map(MatizColor::hex)
        println("MATIZ dia=$dia: ${hex.joinToString(" ")}")
        assertEquals(listOf("#E26205", "#44789D", "#E5E735", "#573019", "#26C957"), hex)
        assertEquals(1L * 48271, Minstd(0).next()) // semente 0 vira 1

        val g = MatizGame(MatizColor.daily(dia))
        g.colors.forEach { g.guess(it) }
        assertTrue(g.over)
        assertEquals(500, g.total)
        assertEquals("Matiz 500/500 🎨\n🟩🟩🟩🟩🟩", g.share())
        assertEquals(0, g.guess(0)) // depois do fim não conta
        assertEquals(5, g.scores.size)
    }

    // ---------- Pódio ----------
    private val q = PodioQ("Teste", listOf("A", "B", "C", "D", "E"), listOf("F", "G", "H", "I", "J"))

    @Test
    fun podioDoDia() {
        assertEquals(2, PodioData.dailyIndex(dia, 3))
        assertEquals(29, PodioData.dailyIndex(dia, 200))
        assertEquals("HDAEBGCFJI".map { "$it" }, PodioData.dailyOptions(q, dia))
        assertEquals("HDAEBGCFJI".map { "$it" }, ('A'..'J').map { "$it" }.shuffledBy(Minstd.ofDay(dia)))

        val list = PodioData.parse(File("../shared/podio.txt").readText())
        assertTrue(list.isNotEmpty())
        val i = PodioData.dailyIndex(dia, list.size)
        // Com os 3 rankings atuais o site dá, no dia 0, o índice 2 e esta ordem.
        if (list.size == 3) assertEquals("Marte / Plutão / Júpiter / Saturno / Netuno / Urano / Mercúrio / Terra / Vênus / Lua",
            PodioData.dailyOptions(list[PodioData.dailyIndex(0, 3)], 0).joinToString(" / "))
        println("PODIO dia=$dia N=${list.size} idx=$i: ${list[i].title} -> ${PodioData.dailyOptions(list[i], dia).joinToString(" / ")}")
        assertTrue(list.all { it.top.size == 5 && it.wrong.size == 5 })
        // Como no site: campo vazio invalida a linha; campos além do 11º são ignorados.
        assertEquals(emptyList<PodioQ>(), PodioData.parse("T|A|B||D|E|F|G|H|I|J\nT|A|B|C"))
        assertEquals(q.copy(title = "T"), PodioData.parse("T|A|B|C|D|E|F|G|H|I|J|extra")[0])
    }

    @Test
    fun podioCores() {
        assertEquals(listOf(GREEN, GREEN, GREEN, GREEN, GREEN), PodioGame.feedback(q.top, q.top))
        assertEquals(listOf(YELLOW, YELLOW, GREEN, GRAY, GRAY), PodioGame.feedback(q.top, listOf("B", "A", "C", "F", "G")))
        assertEquals(listOf(GRAY, YELLOW, GREEN, YELLOW, GREEN), PodioGame.feedback(q.top, listOf("F", "D", "C", "A", "E")))

        val g = PodioGame(q, q.all)
        assertFalse(g.submit())
        listOf("B", "A", "C", "F", "G").forEach { assertTrue(g.put(it)) }
        assertFalse(g.put("H")) // vagas cheias
        g.take(4); assertTrue(g.put("G"))
        assertTrue(g.submit())
        assertEquals(setOf("F", "G"), g.gray)
        assertEquals(listOf(null, null, "C", null, null), g.slots.toList()) // só a verde fica
        q.top.forEach { g.put(it) }
        assertEquals(listOf("A", "B", "C", "D", "E"), g.slots.toList())
        assertTrue(g.submit())
        assertTrue(g.won && g.over)
        assertEquals("Pódio 2/5 🏆\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩🟩", g.share())

        val lose = PodioGame(q, q.all)
        repeat(5) { "FGHIJ".forEach { lose.put("$it") }; lose.submit() }
        assertTrue(lose.over && !lose.won)
        assertFalse(lose.put("A"))
    }

    // ---------- Ampulheta ----------
    private val ev = List(20) { AmpEvent(1000 + it % 15, "Fato $it") }

    @Test
    fun ampulhetaDoDia() {
        val idx = AmpulhetaData.dailyIndices(ev, dia)
        assertEquals(listOf(18, 9, 16, 0, 17, 13, 19, 5, 11, 12, 14), idx)
        assertEquals(11, idx.map { ev[it].year }.toSet().size)

        val list = AmpulhetaData.parse(File("../shared/ampulheta.txt").readText())
        val real = AmpulhetaData.dailyIndices(list, dia)
        println("AMPULHETA dia=$dia N=${list.size} indices=$real")
        if (list.size == 6) { assertEquals(listOf(5, 2, 1, 3, 0, 4), AmpulhetaData.dailyIndices(list, 0)); assertEquals(listOf(0, 2, 5, 4, 1, 3), real) } // = site
        assertEquals(real.size, real.map { list[it].year }.toSet().size)
        assertEquals(AmpEvent(-753, "Roma"), AmpulhetaData.parse("-753|Roma\n# x\nlixo")[0])
        assertEquals("753 a.C.", AmpEvent(-753, "Roma").yearLabel)
        // Data do dia 300 = 2026-10-28.
        assertEquals(300L, Words.dayIndex(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun ampulhetaEncaixe() {
        val e = listOf(AmpEvent(1500, "a"), AmpEvent(1822, "b"), AmpEvent(1888, "c"), AmpEvent(-300, "d"), AmpEvent(1969, "e"))
        val g = AmpulhetaGame(e[0], e.drop(1).toMutableList(), daily = true)
        assertEquals(4, g.cards)
        assertTrue(g.place(1)) // 1822 depois de 1500
        assertFalse(g.place(0)) // 1888 não vai antes de 1500: entra no lugar certo mesmo assim
        assertEquals(listOf(1500, 1822, 1888), g.line.map { it.year })
        assertTrue(g.place(0)) // -300 antes de tudo
        assertFalse(g.place(1)) // 1969 errado
        assertTrue(g.over)
        assertEquals(listOf(-300, 1500, 1822, 1888, 1969), g.line.map { it.year })
        assertEquals(2, g.right)
        assertEquals(listOf(1, 0, 0, 1), g.placed)
        assertEquals("Ampulheta 2/4 ⏳\n🟩🟥🟩🟥", g.share())
        assertFalse(g.place(0))

        // Infinito: 3 vidas e nenhum ano repetido.
        val inf = AmpulhetaGame.infinite(ev, Random(3))
        val years = mutableListOf(inf.line[0].year)
        while (!inf.over) { years += inf.card!!.year; inf.place(-1) }
        assertEquals(0, inf.lives)
        assertEquals(3, inf.results.size)
        assertEquals(years.size, years.toSet().size)
        val all = AmpulhetaGame.infinite(ev, Random(4))
        val ys = mutableListOf(all.line[0].year)
        while (!all.over) { ys += all.card!!.year; all.place(all.slotFor(all.card!!)) }
        assertEquals(15, ys.size) // acabaram as cartas: 15 anos diferentes
        assertEquals(15, ys.toSet().size)
        assertEquals(14, all.right)
    }

    // ---------- Recordes e conquistas ----------
    @Test
    fun recordesEConquistas() {
        listOf("matiz_best", "matiz_played", "matiz_top", "podio_won", "podio_played", "podio_streak", "podio_first", "amp_best", "amp_day_best", "amp_played")
            .forEach { assertTrue(it, it in Records.KEYS) }
        val none = Achievements.all(Stats(), Stats(), r = Records(mapOf("matiz_top" to 94, "amp_day_best" to 9))).associateBy { it.title }
        assertFalse(none.getValue("Olho clínico").unlocked)
        assertFalse(none.getValue("Pódio perfeito").unlocked)
        assertFalse(none.getValue("Historiador").unlocked)
        val all = Achievements.all(Stats(), Stats(), r = Records(mapOf("matiz_top" to 95, "podio_first" to 1, "amp_day_best" to 10))).associateBy { it.title }
        assertTrue(all.getValue("Olho clínico").unlocked)
        assertTrue(all.getValue("Pódio perfeito").unlocked)
        assertTrue(all.getValue("Historiador").unlocked)
        assertEquals("⏳", all.getValue("Historiador").emoji)
    }
}
