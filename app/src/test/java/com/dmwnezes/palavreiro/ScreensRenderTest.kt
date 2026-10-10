package com.dmwnezes.palavreiro

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.ui.GameScreen
import com.dmwnezes.palavreiro.ui.HelpSheet
import com.dmwnezes.palavreiro.ui.HomeScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.ProfileScreen
import com.dmwnezes.palavreiro.ui.SplashCredits
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.ConnectionsScreen
import com.dmwnezes.palavreiro.ui.ReverseScreen
import com.dmwnezes.palavreiro.ui.DefineScreen
import com.dmwnezes.palavreiro.ui.SynAntScreen
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.QuizData
import org.junit.Rule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/** Desenha as telas principais e salva imagens em app/build/frames para conferência. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class ScreensRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val words = Words(File("../shared/words.js").readText())

    private fun save(name: String, advance: Long = 1500) {
        rule.mainClock.advanceTimeBy(advance)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun abertura() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { SplashCredits {} } }
        save("1a-bandeira", 500)
        save("1b-tremulando", 400)
        save("1c-transformando", 600)
        save("1d-quase", 300)
        save("1-abertura", 1000)
    }

    @Test
    fun inicio() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { HomeScreen(mapOf(Dest.TERMO to "Novo", Dest.DUETO to "Feito hoje ✓", Dest.CONEXOES to "Novo"), {}, {}) } }
        save("2-inicio")
    }

    @Test
    fun jogoEmAndamento() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DIARIO, words, store = null) { java.time.LocalDate.of(2026, 10, 8) } // resposta: ÚNICO
        for (w in listOf("CARRO", "MUNDO")) { w.forEach(g::type); g.submit(); g.finishReveal() }
        g.select(1); g.type('N'); g.select(4); g.type('O')
        g.select(2)
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        save("3-jogo")
    }

    @Test
    fun vitoria() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.INFINITO, words, store = null)
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnUiThread { g.answer.forEach(g::type); g.submit() }
        save("4-revelando", 700)
        save("5a-t1500", 800)
        save("5b-t1900", 400)
        save("5c-t2400", 500)
        save("6-resultado", 2500)
    }

    @Test
    fun perfil() {
        rule.mainClock.autoAdvance = false
        val d = Stats(played = 12, won = 10, streak = 4, maxStreak = 7, firstTry = 1, dist = listOf(1, 2, 4, 2, 1, 0))
        val i = Stats(played = 30, won = 26, streak = 9, maxStreak = 11, dist = listOf(0, 3, 9, 8, 4, 2))
        rule.setContent { PalavreiroTheme { ProfileScreen(null, mapOf(Mode.DIARIO to d, Mode.INFINITO to i), Records(mapOf("conn_won" to 3, "ws_best" to 95, "syn_best" to 12, "intr_best" to 4)), {}, {}, {}) } }
        save("7-perfil")
    }

    private fun profileTab(tab: Int, name: String) {
        rule.mainClock.autoAdvance = false
        val d = Stats(played = 12, won = 10, streak = 4, maxStreak = 7, firstTry = 1, dist = listOf(1, 2, 4, 2, 1, 0, 0, 0, 0))
        val i = Stats(played = 30, won = 26, streak = 9, maxStreak = 11, dist = listOf(0, 3, 9, 8, 4, 2, 0, 0, 0))
        rule.setContent { PalavreiroTheme { ProfileScreen(null, mapOf(Mode.DIARIO to d, Mode.INFINITO to i), Records(mapOf("conn_won" to 3, "ws_inf_played" to 4, "intr_best" to 6)), {}, {}, {}, startTab = tab) } }
        save(name)
    }

    @Test fun perfilConquistas() = profileTab(1, "7b-perfil-conquistas")
    @Test fun perfilAjustes() = profileTab(2, "7c-perfil-ajustes")

    @Test
    fun evolucao() {
        rule.mainClock.autoAdvance = false
        val h = org.json.JSONObject()
        var dd = day.minusDays(50)
        var k = 0
        while (!dd.isAfter(day)) { if (k % 3 != 0) h.put(dd.toString(), org.json.JSONObject().put("termo", 3 + (k % 4)).put("caca", 80 + k)); dd = dd.plusDays(1); k++ }
        rule.setContent { PalavreiroTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.dmwnezes.palavreiro.ui.Night.surface).padding(18.dp)) { com.dmwnezes.palavreiro.ui.EvolutionChart(h, day) } } }
        save("7d-evolucao")
    }

    @Test
    fun inicioHoje() {
        rule.mainClock.autoAdvance = false
        val st = com.dmwnezes.palavreiro.system.DailyStatus(termo = true, dueto = true, quarteto = false, conexoes = true, streak = 3)
        rule.setContent { PalavreiroTheme { HomeScreen(emptyMap(), {}, {}, today = st) } }
        save("2b-inicio-hoje")
    }

    @Test
    fun intruso() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.IntruderScreen(ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), null, null, {}, seed = 3) } }
        save("19-intruso")
    }

    @Test
    fun ortografia() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.SpellingScreen(com.dmwnezes.palavreiro.game.SpellingData.parse(asset("ortografia.txt")), null, null, {}, seed = 5) } }
        save("20-ortografia")
    }

    @Test
    fun mestre() {
        rule.mainClock.autoAdvance = false
        val (syn, sf) = QuizData.synonyms(asset("sinonimos.txt"))
        val (ant, af) = QuizData.antonyms(asset("antonimos.txt"), sf)
        val data = com.dmwnezes.palavreiro.game.MestreData(ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), syn, sf, ant, af)
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.MestreScreen(data, null, null, {}, seed = 21, autoStart = true) } }
        save("24-mestre", 1200)
    }

    @Test
    fun mestreInicio() {
        rule.mainClock.autoAdvance = false
        val (syn, sf) = QuizData.synonyms(asset("sinonimos.txt"))
        val (ant, af) = QuizData.antonyms(asset("antonimos.txt"), sf)
        val data = com.dmwnezes.palavreiro.game.MestreData(ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), syn, sf, ant, af)
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.MestreScreen(data, null, null, {}, seed = 21) } }
        save("24b-mestre-inicio")
    }

    @Test
    fun termoComAbas() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DUETO, words, store = null, freePlay = true) { day }
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}, infiniteTab = true) } }
        save("25-dueto-infinito")
    }

    private val offline = com.dmwnezes.palavreiro.system.Ntfy(okhttp3.OkHttpClient(), "http://127.0.0.1:9")

    @Test
    fun amigoCriar() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.MultiScreen(words, null, offline, null, {}) } }
        save("26-amigo-criar")
    }

    @Test
    fun amigoEntrar() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.MultiScreen(words, null, offline, null, {}, joinCode = "abc123xyz789-d-21i3v9") } }
        save("27-amigo-entrar")
    }

    @Test
    fun amigoPartida() {
        rule.mainClock.autoAdvance = false
        val ans = com.dmwnezes.palavreiro.game.Multi.words(words.answers, 77, 2)
        val g = TermoGame(Mode.DUETO, words, store = null, fixed = ans)
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        rule.setContent {
            PalavreiroTheme {
                GameScreen(g, null, {}, {}, {}, multiplayer = true, titleOverride = "Você × Ana", header = {
                    com.dmwnezes.palavreiro.ui.OpponentStrip("Ana", 2, 7, listOf("apaca|aacpa", "ccccc|apcca", "|ccacc"), "jogando · 3/7", true)
                })
            }
        }
        save("28-amigo-partida")
    }

    @Test
    fun amigoSalaComQr() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.MultiScreen(words, null, offline, null, {}) } }
        rule.onNode(androidx.compose.ui.test.hasText("Seu nome")).performTextInput("Daniel")
        rule.onNode(androidx.compose.ui.test.hasText("Criar partida")).performClick()
        save("31-amigo-sala-qr")
    }

    @Test
    fun amigoSerie() {
        rule.mainClock.autoAdvance = false
        val ans = com.dmwnezes.palavreiro.game.Multi.words(words.answers, 77, 1)
        val g = TermoGame(Mode.DIARIO, words, store = null, fixed = ans)
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        ans[0].forEach(g::type); g.submit(); g.finishReveal()
        val series = com.dmwnezes.palavreiro.game.Multi.Series(1, 0)
        rule.setContent {
            PalavreiroTheme {
                GameScreen(g, null, {}, {}, {}, multiplayer = true, titleOverride = "Você × Ana",
                    titleTrailing = { com.dmwnezes.palavreiro.ui.SeriesPill(series) })
            }
        }
        save("32-amigo-serie-placar")
    }

    @Test
    fun amigoTrofeu() {
        rule.mainClock.autoAdvance = false
        val ans = com.dmwnezes.palavreiro.game.Multi.words(words.answers, 77, 1)
        val g = TermoGame(Mode.DIARIO, words, store = null, fixed = ans)
        ans[0].forEach(g::type); g.submit(); g.finishReveal()
        rule.setContent {
            PalavreiroTheme {
                com.dmwnezes.palavreiro.ui.EndSheet(
                    "me", "Ana", g, com.dmwnezes.palavreiro.game.Multi.Msg.End("a", true, 1, 41000), com.dmwnezes.palavreiro.game.Multi.Msg.End("b", false, 6, 90000), true, emptyMap(),
                    com.dmwnezes.palavreiro.game.Multi.Series(2, 1), {}, {}, {}, {},
                )
            }
        }
        save("33-amigo-trofeu")
    }

    @Test
    fun replayNoMeio() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DIARIO, words, store = null) { day }
        for (w in listOf("CARRO", "MUNDO", g.answers[0])) { w.forEach(g::type); g.submit(); g.finishReveal() }
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}, multiplayer = true, replayTrigger = 1) } }
        save("29-replay", 2950)
    }

    @Test
    fun seusChutes() {
        rule.mainClock.autoAdvance = false
        val st = com.dmwnezes.palavreiro.game.FirstGuessStats.from(listOf("CARRO" to 4, "CARRO" to 3, "CARRO" to 5, "PEDRA" to 2, "PEDRA" to 3, "PEDRA" to 2, "MUNDO" to 7, "SERTÃO" to 4))
        rule.setContent { PalavreiroTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.dmwnezes.palavreiro.ui.Night.surface).padding(18.dp)) { com.dmwnezes.palavreiro.ui.FirstGuessSection(st) { it } } } }
        save("30-seus-chutes")
    }

    @Test
    fun sinonimoOuAntonimoPegadinha() {
        rule.mainClock.autoAdvance = false
        val (syn, sf) = QuizData.synonyms(asset("sinonimos.txt"))
        val (ant, af) = QuizData.antonyms(asset("antonimos.txt"), sf)
        // Uma semente cuja primeira pergunta tem pegadinha.
        val seed = (1L..500L).first { com.dmwnezes.palavreiro.game.SynAntGame(syn, sf, ant, af, it).question.trick != null }
        rule.setContent { PalavreiroTheme { SynAntScreen(syn, sf, ant, af, null, null, {}, seed = seed, autoStart = true) } }
        save("23-sinant-pegadinha", 1200)
    }

    @Test
    fun giveUpConfirm() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DUETO, words, store = null) { day }
        "CARRO".forEach(g::type); g.submit(); g.finishReveal()
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Desistir")).performClick()
        save("34-desistir")
        rule.onNode(androidx.compose.ui.test.hasText("Desistir") and androidx.compose.ui.test.hasClickAction()).performClick()
        save("35-desistiu", 3000)
        assert(g.over && !g.won && g.gaveUp)
    }

    @Test
    fun arquivo() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { com.dmwnezes.palavreiro.ui.ArchiveScreen(null, {}, { _, _ -> }, today = day) } }
        save("21-arquivo")
    }

    @Test
    fun arquivoTermo() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DIARIO, words, store = null, archive = java.time.LocalDate.of(2026, 3, 12)) { day }
        for (w in listOf("CARRO", g.answers[0])) { w.forEach(g::type); g.submit(); g.finishReveal() }
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        save("22-arquivo-termo", 4000)
    }

    @Test
    fun ajuda() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { HomeScreen(emptyMap(), {}, {}); HelpSheet {} } }
        save("8-ajuda")
    }

    private fun asset(n: String) = File("../shared/$n").readText()
    private val day = java.time.LocalDate.of(2026, 10, 9)

    @Test
    fun dueto() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.DUETO, words, store = null) { day }
        for (w in listOf("CARRO", g.answers[0], "MUNDO")) { w.forEach(g::type); g.submit(); g.finishReveal() }
        g.type('P'); g.type('E')
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        save("9-dueto")
    }

    @Test
    fun quarteto() {
        rule.mainClock.autoAdvance = false
        val g = TermoGame(Mode.QUARTETO, words, store = null) { day }
        for (w in listOf("CARRO", "MUNDO", g.answers[2], "PEDRA")) { w.forEach(g::type); g.submit(); g.finishReveal() }
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}) } }
        save("10-quarteto")
    }

    @Test
    fun conexoes() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { ConnectionsScreen(ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), null, null, {}, day) } }
        save("11-conexoes")
    }

    @Test
    fun conexoesInfinito() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { ConnectionsScreen(ConnectionsData.parse(asset("conexoes.txt")), ConnectionsData.families(asset("conexoes-familias.txt")), null, null, {}, day, startInfinite = true) } }
        save("11b-conexoes-infinito")
    }

    @Test
    fun reverso() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { ReverseScreen(words, null, null, {}) } }
        save("13-reverso")
    }

    @Test
    fun definicao() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { DefineScreen(QuizData.definitions(asset("definicoes.txt")), null, null, {}, seed = 4) } }
        save("14-definicao")
    }

    @Test
    fun sinonimos() {
        rule.mainClock.autoAdvance = false
        val (syn, sf) = QuizData.synonyms(asset("sinonimos.txt"))
        val (ant, af) = QuizData.antonyms(asset("antonimos.txt"), sf)
        rule.setContent { PalavreiroTheme { SynAntScreen(syn, sf, ant, af, null, null, {}, seed = 4) } }
        save("15-sinant-inicio")
    }

    @Test
    fun calendario() {
        rule.mainClock.autoAdvance = false
        val today = java.time.LocalDate.of(2026, 10, 9)
        val act = (1..9).associate { today.withDayOfMonth(it).toString() to (it % 4 + 1) } - today.withDayOfMonth(4).toString()
        val termo = (1..9).associate { today.withDayOfMonth(it).toString() to if (it == 6) "l" else "w" } - today.withDayOfMonth(4).toString()
        rule.setContent {
            PalavreiroTheme {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.background(com.dmwnezes.palavreiro.ui.Night.surface).padding(18.dp)
                ) { com.dmwnezes.palavreiro.ui.StreakCalendar(act, termo, today) }
            }
        }
        save("16-calendario")
    }

    @Test
    fun cartaoStories() {
        val g = TermoGame(Mode.DUETO, words, store = null) { day }
        for (w in listOf("CARRO", g.answers[0], "MUNDO", g.answers[1])) { w.forEach(g::type); g.submit(); g.finishReveal() }
        val bmp = com.dmwnezes.palavreiro.ui.StoryCard.render(rule.activity, com.dmwnezes.palavreiro.ui.storyFor(g, Stats(played = 9, won = 8, streak = 5, maxStreak = 6)))
        File(out, "17-stories.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Test
    fun resultadoComSignificado() {
        rule.mainClock.autoAdvance = false
        val meanings = com.dmwnezes.palavreiro.game.QuizData.meanings(asset("significados.txt"))
        val g = TermoGame(Mode.DIARIO, words, store = null) { day }
        g.setHard(true)
        g.hint()
        for (i in 0 until 5) if (g.current[i] == null) { g.select(i); g.type(g.answer[i]) }
        g.submit(); g.finishReveal()
        rule.setContent { PalavreiroTheme { GameScreen(g, null, {}, {}, {}, meanings) } }
        save("18-resultado")
    }
}
