package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.data.Sync
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.Cruzada
import com.dmwnezes.palavreiro.game.CruzadaGame
import com.dmwnezes.palavreiro.game.PodioData
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.Retro
import com.dmwnezes.palavreiro.game.Achievements
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.game.WordSearchData
import com.dmwnezes.palavreiro.system.Ntfy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** Correções de 02/11/2026: palavras, palavra do dia, Naval, sincronização, reserva do ntfy, retrospectiva e Palavra Cruzada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Rodada13Test {
    private val words = Words(File("../shared/words.js").readText())
    private fun day(i: Long) = LocalDate.of(2026, 1, 1).plusDays(i)
    private fun store() = Store(RuntimeEnvironment.getApplication())

    /** Servidor mínimo no formato do ntfy.sh (só POST e histórico; o SSE responde vazio). */
    private class FakeNtfy {
        val messages = CopyOnWriteArrayList<String>()
        private val socket = ServerSocket(0)
        val url get() = "http://127.0.0.1:${socket.localPort}"
        init {
            Thread {
                while (!socket.isClosed) {
                    val c = runCatching { socket.accept() }.getOrNull() ?: break
                    c.use {
                        val input = it.getInputStream()
                        fun line(): String { val b = StringBuilder(); while (true) { val x = input.read(); if (x < 0 || x == 10) break; if (x != 13) b.append(x.toChar()) }; return b.toString() }
                        val first = line()
                        var len = 0
                        while (true) { val h = line(); if (h.isEmpty()) break; if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(':').trim().toInt() }
                        val out = when {
                            first.startsWith("POST") -> { messages += String(input.readNBytes(len), Charsets.UTF_8); "{}" }
                            "/json" in first -> messages.mapIndexed { i, m -> JSONObject().put("id", "m$i").put("event", "message").put("message", m).toString() }.joinToString("\n")
                            else -> ""
                        }.toByteArray()
                        it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${out.size}\r\nConnection: close\r\n\r\n".toByteArray() + out)
                        it.getOutputStream().flush()
                    }
                }
            }.apply { isDaemon = true }.start()
        }
        fun stop() = socket.close()
    }
    private val dead = "http://127.0.0.1:9"

    @Test fun tentativasSemInglesNemNomes() {
        listOf("ABOUT", "AFTER", "AARON", "ABDUL", "AEGON", "WORLD").forEach { assertFalse(it, words.isAccepted(it)) }
        listOf("CANTA", "FALEI", "DUNAS", "ACOES", "ROSAS").forEach { assertTrue(it, words.isAccepted(it)) }
        words.answers.forEach { assertTrue(it, words.isAccepted(it)) }
    }

    @Test fun palavraDoDiaIgualAoSite() {
        // Valores calculados por P.daily no site.
        mapOf(0L to "CHOPE", 190L to "GASTO", 284L to "GASTO", 305L to "DUCHA", 306L to "ATLAS", 307L to "AUDAZ", 500L to "FOGAO", 1000L to "VERBO", 1599L to "MACHO")
            .forEach { (i, w) -> assertEquals("dia $i", w, words.daily(day(i))) }
    }

    @Test fun palavraDoDiaNaoRepeteEmMilDias() {
        val last = HashMap<String, Long>()
        for (i in 0L until 1600L) {
            val w = words.daily(day(i))
            if (i >= 306) last[w]?.let { assertTrue("$w em $it e $i", i - it >= 1000) }
            last[w] = i
        }
    }

    @Test fun conteudoNovoSoDeTresDeNovembroEmDiante() {
        val d305 = day(305); val d306 = day(306)
        assertEquals(((305L * 13 + 7) % 46).toInt(), ConnectionsData.dailyIndex(d305, 98))
        assertEquals(((306L * 13 + 7) % 98).toInt(), ConnectionsData.dailyIndex(d306, 98))
        assertEquals(((305L * 7919 + 104729) % 155).toInt(), PodioData.dailyIndex(305, 175))
        assertEquals(((306L * 7919 + 104729) % 175).toInt(), PodioData.dailyIndex(306, 175))
        val themes = WordSearchData.parse(File("../shared/caca.txt").readText())
        val all = themes + WordSearchData.parse(File("../shared/caca-extra.txt").readText())
        assertEquals(themes[WordSearchData.dailyIndex(d305, themes.size)].name, WordSearchData.daily(d305, themes, all).theme.name)
        assertEquals(all[WordSearchData.dailyIndex(d306, all.size)].name, WordSearchData.daily(d306, themes, all).theme.name)
        for (i in 306L until 800L) assertTrue(WordSearchData.daily(day(i), themes, all).placed.size >= 6)
        assertEquals(98, ConnectionsData.parse(File("../shared/conexoes.txt").readText()).size)
        assertEquals(175, PodioData.parse(File("../shared/podio.txt").readText()).size)
    }

    @Test fun novosDesafiosDeConexoesTemDezesseisPalavrasDiferentes() {
        ConnectionsData.parse(File("../shared/conexoes.txt").readText()).forEach { p ->
            assertEquals(4, p.groups.size)
            val w = p.groups.flatMap { it.words }
            assertEquals(p.groups.first().name, 16, w.toSet().size)
        }
    }

    @Test fun navalNoProgresso() {
        val s = store()
        s.setInt("naval_won_dificil", 2)
        val code = Sync.export(s)
        val b = Store(RuntimeEnvironment.getApplication())
        assertTrue(Sync.import(b, code))
        assertEquals(2, b.int("naval_won_dificil"))
        val r = Records(Records.KEYS.associateWith { s.int(it) })
        assertTrue(Achievements.all(com.dmwnezes.palavreiro.data.Stats(), com.dmwnezes.palavreiro.data.Stats(), r = r).first { it.title == "Almirante" }.unlocked)
        assertTrue(com.dmwnezes.palavreiro.ui.recordCards(com.dmwnezes.palavreiro.data.Stats(), r).any { it.title == "Batalha Naval" && it.value == "2" })
    }

    @Test fun codigoCurto() {
        assertEquals("7K2MQX", Sync.short(" 7k2mqx "))
        assertEquals("7K2MQX", Sync.short("https://dmwnezes.com/termo/?sync=7K2MQX"))
        assertNull(Sync.short("PV1-abc"))
        assertNull(Sync.short("7K2MQ0")) // 0 não existe no alfabeto
    }

    @Test fun sincronizaEmPartesPeloServidorReserva() = runBlocking {
        val reserve = FakeNtfy()
        val ntfy = Ntfy(OkHttpClient(), dead, reserve.url)
        val s = store()
        s.setInt("mp_won", 7)
        s.setActivity((0L until 700L).associate { LocalDate.of(2024, 1, 1).plusDays(it).toString() to 1 })
        val code = Sync.send(ntfy, s)
        assertNotNull(code)
        assertTrue("foi em partes", reserve.messages.size > 1)
        s.setInt("mp_won", 0)
        assertTrue(Sync.receive(ntfy, s, code!!))
        assertEquals(7, s.int("mp_won"))
        reserve.messages.clear()
        assertFalse(Sync.receive(ntfy, s, "ZZZZZZ"))
        reserve.stop()
    }

    @Test fun ouveOReservaQuandoOPrincipalCai() = runBlocking {
        val reserve = FakeNtfy()
        reserve.messages += """{"_u":"um","t":"x"}"""
        reserve.messages += """{"_u":"um","t":"x"}""" // repetida: entrega uma vez só
        reserve.messages += """{"_u":"dois","t":"y"}"""
        val got = withTimeout(30_000) { Ntfy(OkHttpClient(), dead, reserve.url).events("sala").filterIsInstance<Ntfy.Event.Message>().take(2).toList() }
        assertEquals(listOf("um", "dois"), got.map { JSONObject(it.text).getString("_u") })
        reserve.stop()
    }

    @Test fun retrospectivaIgualAoSite() {
        val r = Retro.of(
            2026,
            mapOf("2026-03-01" to 2, "2026-03-02" to 1, "2026-03-03" to 4, "2026-07-10" to 1, "2025-03-04" to 9),
            mapOf("2026-03-01" to "w", "2026-03-02" to "l", "2025-01-01" to "w"),
            listOf("termo|2026-03-01", "termo|2026-03-02", "conn|2026-03-01"),
            listOf("AUDIO" to "2026-03-01", "AUDIO" to "2026-03-02", "PEDRA" to "2025-03-02"),
        )
        assertEquals(Retro(2026, 4, 8, 3, "março", 2, 1, "Termo", "AUDIO"), r)
        assertEquals(50, r.termoPct)
        assertEquals(0, Retro.of(2030, emptyMap(), emptyMap(), emptyList(), emptyList()).days)
    }

    @Test fun palavraCruzada() {
        val list = Cruzada.parse(File("../shared/cruzada.txt").readText())
        assertEquals(366, list.size)
        assertEquals(347, Cruzada.dailyIndex(306, list.size)) // mesmo valor de P.cruzadaIdx no site
        list.forEach { q ->
            for (y in listOf(0, 2, 4)) for (x in listOf(0, 2, 4)) assertEquals(q.across[y / 2][x], q.down[x / 2][y])
            assertEquals(6, q.clues.count { it.isNotBlank() })
        }
        val g = CruzadaGame(list[0])
        g.type(if (list[0].across[0][0] == 'Q') 'Z' else 'Q')
        assertEquals(1, g.check())
        assertTrue(g.wrong[0][0])
        for (i in 0..5) {
            val word = if (i < 3) g.q.across[i] else g.q.down[i - 3]
            g.selectClue(i)
            for (k in 0 until 5) {
                val (y, x) = if (i < 3) i * 2 to k else k to (i - 3) * 2
                g.select(y, x)
                if (g.across != (i < 3)) g.select(y, x)
                if (!g.locked[y][x]) g.type(word[k])
            }
        }
        assertEquals(0, g.check())
        assertTrue(g.won)
        val back = CruzadaGame(list[0]).apply { restore(g.save()) }
        assertTrue(back.won)
        assertEquals(g.checks, back.checks)
        assertEquals(g.letters[2].toList(), back.letters[2].toList())
    }

    @Test fun revelarTerminaAGrade() {
        val g = CruzadaGame(Cruzada.parse(File("../shared/cruzada.txt").readText())[3])
        for (y in 0 until 5) for (x in 0 until 5) if (g.q.letter(y, x) != null) { g.select(y, x); g.reveal() }
        assertTrue(g.won)
    }
}
