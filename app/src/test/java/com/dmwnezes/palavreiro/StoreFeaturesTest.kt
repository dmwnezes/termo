package com.dmwnezes.palavreiro


import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.data.Sync
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.Evolution
import com.dmwnezes.palavreiro.game.IntruderGame
import com.dmwnezes.palavreiro.game.SpellingData
import com.dmwnezes.palavreiro.game.SpellingGame
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.DailyStatus
import com.dmwnezes.palavreiro.system.Reminder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreFeaturesTest {
    private val words = Words(File("../shared/words.js").readText())
    private fun asset(n: String) = File("../shared/$n").readText()
    private fun freshStore(): Store {
        val ctx: android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("palavreiro", 0).edit().clear().commit()
        return Store(ctx)
    }
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun arquivoNaoMexeNasEstatisticas() {
        val store = freshStore()
        val date = LocalDate.of(2026, 3, 12)
        val g = TermoGame(Mode.DIARIO, words, store, archive = date) { today }
        assertEquals(words.dailySet(date, 1, 0), g.answers.toList())
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        // Salvo em espaço próprio: reabrir continua a partida.
        val again = TermoGame(Mode.DIARIO, words, store, archive = date) { today }
        assertEquals(1, again.rows.size)
        g.answers[0].forEach(g::type); g.submit(); g.finishReveal()
        assertTrue(g.over && g.won)
        assertEquals(0, store.stats(Mode.DIARIO).played)
        assertNull(store.text("daily_done_diario"))
        assertEquals("w", store.results()["termo|2026-03-12"])
        assertEquals(1, store.int("arch_played")); assertEquals(1, store.int("arch_won"))
        // O jogo do dia continua intacto.
        val daily = TermoGame(Mode.DIARIO, words, store) { today }
        assertTrue(daily.isDaily); assertEquals(0, daily.rows.size)
    }

    @Test
    fun diaGravaResultadoEHistorico() {
        val store = freshStore()
        val g = TermoGame(Mode.DIARIO, words, store) { today }
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        g.answers[0].forEach(g::type); g.submit(); g.finishReveal()
        assertEquals("w", store.results()["termo|2026-10-09"])
        assertEquals(2, store.history().getJSONObject("2026-10-09").getInt("termo"))
    }

    @Test
    fun abasDoDiaEInfinitoNaoSeMisturam() {
        val store = freshStore()
        val daily = TermoGame(Mode.DUETO, words, store) { today }
        "CARRO".forEach(daily::type); daily.submit(); daily.finishReveal()
        val free = TermoGame(Mode.DUETO, words, store, freePlay = true) { today }
        assertFalse(free.isDaily); assertEquals(0, free.rows.size)
        "MUNDO".forEach(free::type); free.submit(); free.finishReveal()
        // Reabrindo: cada aba com o seu tabuleiro.
        assertEquals(listOf("CARRO"), TermoGame(Mode.DUETO, words, store) { today }.rows.toList())
        assertEquals(listOf("MUNDO"), TermoGame(Mode.DUETO, words, store, freePlay = true) { today }.rows.toList())
        // A partida do dia não vira livre.
        daily.newWord(); assertTrue(daily.isDaily)
    }

    @Test
    fun contagemDaProximaPalavra() {
        val store = freshStore()
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val nm = app.getSystemService(android.app.NotificationManager::class.java)
        val shadow = org.robolectric.Shadows.shadowOf(nm)
        // Desligada ou sem o Termo do dia feito: nada aparece.
        com.dmwnezes.palavreiro.system.NextWordLive.update(app, store)
        assertEquals(0, shadow.allNotifications.size)
        store.liveCountdown = true
        com.dmwnezes.palavreiro.system.NextWordLive.update(app, store)
        assertEquals(0, shadow.allNotifications.size)
        // Termo do dia feito: aparece a contagem até a meia-noite, contínua e pedindo para virar "ao vivo".
        store.setText("daily_done_diario", LocalDate.now().toString())
        com.dmwnezes.palavreiro.system.NextWordLive.update(app, store)
        val n = shadow.allNotifications.single()
        assertTrue(n.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.extras.getString(android.app.Notification.EXTRA_TITLE)!!.startsWith("Nova palavra em "))
        assertTrue(n.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals(com.dmwnezes.palavreiro.system.NextWordLive.DAY_MINUTES, n.extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX))
        assertFalse(n.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
        // Desligando, some.
        store.liveCountdown = false
        com.dmwnezes.palavreiro.system.NextWordLive.update(app, store)
        assertEquals(0, shadow.allNotifications.size)
    }

    @Test
    fun barraDoDia() {
        val m = com.dmwnezes.palavreiro.system.NextWordLive
        assertEquals(0, m.minutesElapsed(java.time.LocalDateTime.of(2026, 10, 9, 0, 0)))
        assertEquals(18 * 60 + 35, m.minutesElapsed(java.time.LocalDateTime.of(2026, 10, 9, 18, 35)))
        assertEquals(5, m.SEGMENT_COLORS.size)
        assertEquals("4h 56min", m.remaining((4 * 60 + 55) * 60_000L + 30_000))
        assertEquals("5h", m.remaining(5 * 3_600_000L))
        assertEquals("38 min", m.remaining(38 * 60_000L))
        assertEquals("1 min", m.remaining(5_000))
        assertEquals("Nova palavra em 2h 5min", m.title((2 * 60 + 5) * 60_000L))
        assertEquals("4h56", m.chip((4 * 60 + 56) * 60_000L)); assertEquals("38min", m.chip(38 * 60_000L))
        assertEquals(0, m.DAY_MINUTES % m.SEGMENT_COLORS.size)
    }

    @Test
    fun sincronizacaoIdaEVolta() {
        val a = freshStore()
        a.saveStats(Mode.DIARIO, Stats(played = 9, won = 8, streak = 3, maxStreak = 5, lastWinDay = 280, firstTry = 1, dist = listOf(1, 2, 3, 2, 0, 0, 0, 0, 0)))
        a.setInt("conn_won", 4); a.setInt("ws_best", 130); a.setInt("intr_best", 7)
        a.logActivity("2026-10-01"); a.logTermo("2026-10-01", true)
        a.setResult("dueto", "2026-10-01", false); a.setHistory("2026-10-01", "termo", 3)
        val code = Sync.export(a)
        assertTrue(code.startsWith("PV1-")); assertFalse(code.contains("="))
        // Outro aparelho, com um tempo melhor no caça e nenhuma partida do Termo.
        val b = freshStore()
        b.setInt("ws_best", 100); b.setInt("conn_won", 1)
        assertTrue(Sync.import(b, code))
        assertEquals(9, b.stats(Mode.DIARIO).played)
        assertEquals(280, b.stats(Mode.DIARIO).lastWinDay)
        assertEquals(4, b.int("conn_won")); assertEquals(100, b.int("ws_best")); assertEquals(7, b.int("intr_best"))
        assertEquals(1, b.activity()["2026-10-01"]); assertEquals("w", b.termoResults()["2026-10-01"])
        assertEquals("l", b.results()["dueto|2026-10-01"]); assertEquals(3, b.history().getJSONObject("2026-10-01").getInt("termo"))
        assertFalse(Sync.import(b, "qualquer coisa")); assertFalse(Sync.import(b, "PV1-%%%"))
        // Sem vitória: Long.MIN_VALUE vira -999999 no código e volta como "sem vitória".
        val c = freshStore(); c.saveStats(Mode.INFINITO, Stats(played = 2))
        val back = freshStore().also { Sync.import(it, Sync.export(c)) }
        assertEquals(Long.MIN_VALUE, back.stats(Mode.INFINITO).lastWinDay)
    }

    @Test
    fun codigoDoSiteEhAceito() {
        // JSON no formato do site (chaves e -999999), codificado como o site faz.
        val json = """{"v":1,"stats":{"termo":{"played":3,"won":2,"streak":1,"maxStreak":2,"lastWinDay":-999999,"firstTry":0,"dist":[0,1,1,0,0,0,0,0,0]}},"n":{"ort_best":12,"rev_userwins":2},"activity":{"2026-09-30":2},"termoDays":{},"results":{"caca|2026-09-30":"w"},"history":{"2026-09-30":{"caca":95}}}"""
        val code = "PV1-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val s = freshStore()
        assertTrue(Sync.import(s, code))
        assertEquals(3, s.stats(Mode.DIARIO).played); assertEquals(Long.MIN_VALUE, s.stats(Mode.DIARIO).lastWinDay)
        assertEquals(12, s.int("ort_best")); assertEquals(2, s.int("rev_userwins"))
        assertEquals("w", s.results()["caca|2026-09-30"])
    }

    @Test
    fun intruso() {
        val fam = ConnectionsData.families(asset("conexoes-familias.txt"))
        val g = IntruderGame(ConnectionsData.parse(asset("conexoes.txt")), fam, Random(1))
        val seen = HashSet<String>()
        repeat(60) {
            val r = g.round
            assertEquals(5, r.options.size); assertEquals(5, r.options.toSet().size)
            assertTrue(r.intruder in r.options && r.intruder !in r.group.words)
            val f = fam.getValue(r.group.name)
            assertTrue(f.none { it in setOf("lacuna", "palavra", "fora") })
            seen += r.group.name
            g.pick(r.intruder); g.next()
        }
        assertEquals(60, g.score); assertTrue(seen.size >= 50)
        g.pick(g.round.options.first { it != g.round.intruder }); assertEquals(2, g.lives)
    }

    @Test
    fun certoOuErrado() {
        val pairs = SpellingData.parse(asset("ortografia.txt"))
        assertTrue(pairs.size > 120)
        assertTrue(pairs.all { it.right != it.wrong && it.right.isNotBlank() && it.wrong.isNotBlank() })
        val g = SpellingGame(pairs, Random(2))
        repeat(5) { g.answer(g.showsRight); g.next() }
        assertEquals(5, g.score)
        repeat(3) { g.answer(!g.showsRight); g.next() }
        assertTrue(g.over); assertEquals(5, g.score)
    }

    @Test
    fun evolucaoSemanal() {
        val h = JSONObject()
            .put("2026-10-05", JSONObject().put("termo", 3)).put("2026-10-07", JSONObject().put("termo", 5))
            .put("2026-09-28", JSONObject().put("caca", 90))
        val w = Evolution.weeks(h, "termo", today)
        assertEquals(8, w.size); assertEquals(LocalDate.of(2026, 10, 5), w.last().first)
        assertEquals(4.0, w.last().second!!, 0.001); assertNull(w[6].second)
        assertEquals(90.0, Evolution.weeks(h, "caca", today)[6].second!!, 0.001)
    }

    @Test
    fun lembrete() {
        val all = DailyStatus(true, true, true, true, 4)
        assertNull(Reminder.message(all, null)); assertNull(Reminder.message(all, Reminder.KIND_STREAK))
        val some = DailyStatus(termo = false, dueto = true, quarteto = true, conexoes = false, streak = 4)
        val (t, x) = Reminder.message(some, null)!!
        assertEquals("Faltam 2 desafios hoje", t); assertTrue(x.contains("Termo e Conexões") && x.contains("sequência de 4"))
        assertTrue(Reminder.message(some, Reminder.KIND_STREAK)!!.second.contains("4 dias"))
        assertNull(Reminder.message(some.copy(streak = 0), Reminder.KIND_STREAK))
    }
}
