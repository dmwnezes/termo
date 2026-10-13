package com.dmwnezes.palavreiro

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.dmwnezes.palavreiro.game.Forca
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Timed
import com.dmwnezes.palavreiro.game.TriviaDuel
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
import com.dmwnezes.palavreiro.ui.AdedonhaPlay
import com.dmwnezes.palavreiro.ui.Duel
import com.dmwnezes.palavreiro.ui.ForcaPlay
import com.dmwnezes.palavreiro.ui.MatchStory
import com.dmwnezes.palavreiro.ui.MultiScreen
import com.dmwnezes.palavreiro.ui.NextGame
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.PointsEndSheet
import com.dmwnezes.palavreiro.ui.SeriesEndSheet
import com.dmwnezes.palavreiro.ui.StoryCard
import com.dmwnezes.palavreiro.ui.TriviaPlay
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rodada 11: telas novas desenhadas em app/build/frames para conferência. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class Rodada11RenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val words = Words(File("../shared/words.js").readText())
    private val offline = Ntfy(OkHttpClient(), "http://127.0.0.1:9")
    private val T = 1_800_000_000_000L
    private val cats = listOf("Nome", "Animal", "Fruta", "Cor", "Objeto", "Cidade, estado ou país")

    private fun save(name: String, advance: Long = 1500) {
        rule.mainClock.advanceTimeBy(advance)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun shown(t: String, sub: Boolean = false) = rule.onAllNodes(hasText(t, substring = sub)).fetchSemanticsNodes().isNotEmpty()

    private fun duel(
        n: Int = 0, localStart: Long = T - 60_000, host: Boolean = true, series: Multi.Series = Multi.Series(1, 0),
        sent: MutableList<Msg>? = null, next: NextGame? = null,
    ) = Duel(
        myId = "me", oppId = "op", oppName = "Ana", isHost = host, round = n + 1, n = n, localStart = localStart, series = series,
        online = true, oppLeft = false, send = { sent?.add(it) }, onDecided = {}, onAgain = {}, onLeave = {}, myName = "Daniel", next = next, clock = { T },
    )

    private fun tm(at: Long, m: Msg) = Timed(at, m)

    // ---------- tela de criar ----------
    @Test
    fun criar() {
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, offline, null, {}) } }
        save("60-criar")
        rule.onNode(hasText("Trivia")).performScrollTo().performClick()
        rule.onNode(hasText("15 rodadas")).performScrollTo().performClick()
        rule.onNode(hasText("Seu nome")).performTextInput("Daniel")
        rule.onNode(hasText("Criar partida")).performScrollTo()
        assertTrue(shown("15 s por pergunta", true))
        save("60b-criar-rolado")
        rule.onNode(hasText("ou entre numa partida", true)).performScrollTo()
        save("60c-criar-fim")
    }

    @Test
    fun entrarAdedonha() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { MultiScreen(words, null, offline, null, {}, joinCode = "abc123xyz789-s-21i3v9") } }
        save("60d-entrar-adedonha")
        assertTrue(shown("Você foi chamado para uma Adedonha · 5 rodadas"))
    }

    // ---------- Adedonha ----------
    private val sround = tm(T - 20_000, Msg.SRound(0, 1, 'M', cats))
    private val round0 = listOf(
        tm(T - 200_000, Msg.SRound(0, 0, 'C', cats)),
        tm(T - 150_000, Msg.Stop("me", 0, 0)),
        tm(T - 149_900, Msg.Ans("me", 0, 0, listOf("Carla", "Cavalo", "Caju", "Cinza", "Cadeira", "Curitiba"))),
        tm(T - 149_800, Msg.Ans("op", 0, 0, listOf("Carlos", "Cavalo", "Coco", "", "Copo", "Crato"))),
        tm(T - 140_000, Msg.Vote("me", 0, 0, listOf(1, 1, 1, 0, 1, 1))),
        tm(T - 139_000, Msg.Vote("op", 0, 0, listOf(1, 1, 1, 1, 1, 1))),
    )

    @Test
    fun adedonhaPreenchendo() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme { AdedonhaPlay(cats, 5, round0 + sround, duel(), prefill = listOf("Marcos", "Macaco", "Manga", "", "Bola", "")) }
        }
        save("61-adedonha-preenchendo")
    }

    @Test
    fun adedonhaLetra() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { AdedonhaPlay(cats, 5, listOf(tm(T - 900, Msg.SRound(0, 0, 'R', cats))), duel()) } }
        save("61a-adedonha-letra")
    }

    private val ans = listOf(
        tm(T - 15_000, Msg.Stop("op", 0, 1)),
        tm(T - 14_800, Msg.Ans("me", 0, 1, listOf("Marcos", "Macaco", "Manga", "Marrom", "Mesa", "Maceió"))),
        tm(T - 14_700, Msg.Ans("op", 0, 1, listOf("Maria", "macaco", "", "Marrom", "Bola", "Manaus"))),
    )

    @Test
    fun adedonhaValidacao() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme { AdedonhaPlay(cats, 5, round0 + sround + ans, duel(), prefillMarks = listOf(true, true, true, false, true, true)) }
        }
        save("61b-adedonha-validacao")
    }

    @Test
    fun adedonhaResultado() {
        rule.mainClock.autoAdvance = false
        val votes = listOf(tm(T - 5_000, Msg.Vote("me", 0, 1, listOf(1, 1, 0, 0, 0, 1))), tm(T - 3_000, Msg.Vote("op", 0, 1, listOf(1, 1, 1, 1, 0, 1))))
        rule.setContent { PalavreiroTheme { AdedonhaPlay(cats, 5, round0 + sround + ans + votes, duel()) } }
        save("61c-adedonha-resultado")
    }

    @Test
    fun adedonhaFim() {
        rule.mainClock.autoAdvance = false
        val votes = listOf(tm(T - 50_000, Msg.Vote("me", 0, 1, listOf(1, 1, 0, 0, 0, 1))), tm(T - 49_000, Msg.Vote("op", 0, 1, listOf(1, 1, 1, 1, 0, 1))))
        rule.setContent { PalavreiroTheme { AdedonhaPlay(cats, 2, round0 + sround + ans + votes, duel(next = NextGame('s', 's', {}, 2, 5))) } }
        save("61d-adedonha-fim", 5000)
    }

    // ---------- Trivia ----------
    private val pool by lazy { TriviaDuel.parse(File("../shared/trivia.txt").readText()) }

    @Test
    fun triviaRoleta() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { TriviaPlay(pool, 123456789, 10, emptyList(), duel(localStart = T - 1_000)) } }
        save("62-trivia-roleta")
    }

    @Test
    fun triviaPergunta() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { TriviaPlay(pool, 123456789, 10, emptyList(), duel(localStart = T - 5_500)) } }
        save("62b-trivia-pergunta")
    }

    @Test
    fun triviaRevela() {
        rule.mainClock.autoAdvance = false
        val k0 = TriviaDuel.correctIndex(123456789, 0)
        val msgs = listOf(tm(T - 8_000, Msg.Pick("me", 0, (k0 + 1) % 4, 4200)), tm(T - 1_000, Msg.Pick("op", 0, k0, 6100)))
        rule.setContent { PalavreiroTheme { TriviaPlay(pool, 123456789, 10, msgs, duel(localStart = T - 15_000)) } }
        save("62c-trivia-revela")
    }

    @Test
    fun triviaFim() {
        rule.mainClock.autoAdvance = false
        val msgs = ArrayList<Timed>()
        var at = T - 100_000
        for (k in 0 until 5) {
            val c = TriviaDuel.correctIndex(123456789, k)
            msgs += tm(at, Msg.Pick("me", k, if (k == 2) (c + 1) % 4 else c, 3000L + k * 100))
            msgs += tm(at + 500, Msg.Pick("op", k, if (k % 2 == 0) c else -1, 5000))
            at += 10_000
        }
        rule.setContent { PalavreiroTheme { TriviaPlay(pool, 123456789, 5, msgs, duel(localStart = T - 110_000)) } }
        save("62d-trivia-fim", 5000)
    }

    // ---------- Forca ----------
    @Test
    fun forcaEscolher() {
        rule.mainClock.autoAdvance = false
        val sent = mutableListOf<Msg>()
        rule.setContent { PalavreiroTheme { ForcaPlay(emptyList(), duel(sent = sent)) } }
        rule.onNode(hasText("Palavra")).performTextInput("Pão de queijo")
        rule.onNode(hasText("Tema")).performTextInput("Comida")
        save("63-forca-escolher")
        rule.onNode(hasText("Pronto")).performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf(Msg.Secret("me", 0, "Comida", "... .. ......")), sent)
    }

    private val forcaMsgs: List<Msg> by lazy {
        val w = "Pão de queijo"
        val m = mutableListOf<Msg>(Msg.Secret("op", 0, "Comida", Forca.mask(w)))
        for (l in "AEZTO") { m += Msg.Guess("me", 0, l); m += Msg.Reveal("op", 0, l, Forca.positions(w, l)) }
        m += Msg.Guess("me", 0, 'R')
        m
    }

    @Test
    fun forcaAdivinhando() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalavreiroTheme { ForcaPlay(forcaMsgs, duel(host = false)) } }
        save("63b-forca-adivinhando")
    }

    @Test
    fun forcaEscolheu() {
        rule.mainClock.autoAdvance = false
        val m = forcaMsgs.map { if (it is Msg.Secret) it.copy(id = "me") else if (it is Msg.Reveal) it.copy(id = "me") else if (it is Msg.Guess) it.copy(id = "op") else it }
        rule.setContent { PalavreiroTheme { ForcaPlay(m, duel(host = true), initialWord = "Pão de queijo") } }
        save("63c-forca-quem-escolheu")
    }

    @Test
    fun forcaFim() {
        rule.mainClock.autoAdvance = false
        val m = forcaMsgs + listOf(Msg.Try("me", 0, "PAO DE QUEIJO"), Msg.TryRes(0, true), Msg.Word(0, "Pão de queijo"))
        rule.setContent { PalavreiroTheme { ForcaPlay(m, duel(host = false, series = Multi.Series(1, 0))) } }
        save("63d-forca-fim", 3000)
    }

    // ---------- folhas de fim: grade compacta e sugestão ----------
    @Test
    fun fimAnfitriaoComSugestao() {
        rule.mainClock.autoAdvance = false
        val ser = Multi.Series(1, 0)
        var accepted = false
        rule.setContent {
            PalavreiroTheme {
                var sel by remember { mutableStateOf('f') }
                SeriesEndSheet(
                    'f', "me", "Ana", "A palavra era PÃO DE QUEIJO", "adivinhou com 2 erros", "escolheu a palavra", true, ser, {}, {}, {},
                    MatchStory.series('f', "Daniel", "Ana", ser, "me", "Palavra: PÃO DE QUEIJO"), "Palavra: PÃO DE QUEIJO",
                    myName = "Daniel",
                    next = NextGame('f', sel, { sel = it }, 3, 3, {}, suggestion = 'x', suggestFrom = "Ana", onAccept = { accepted = true }),
                )
            }
        }
        assertTrue(shown("Ana quer jogar Damas"))
        save("64-fim-anfitriao-sugestao")
        rule.onNode(hasText("Aceitar")).performClick()
        assertTrue(accepted)
    }

    @Test
    fun fimConvidadoSugerir() {
        rule.mainClock.autoAdvance = false
        val sent = mutableListOf<Char>()
        rule.setContent {
            PalavreiroTheme {
                var s by remember { mutableStateOf<Char?>(null) }
                PointsEndSheet(
                    's', "Ana", 230, 180, List(5) { "Letra ${"MPCAR"[it]}" to null }, false, {}, {}, {}, myName = "Daniel",
                    next = NextGame('s', 's', {}, 5, 5, suggest = { sent += it; s = it }, sent = s),
                    storyDetail = "Pontos 230 × 180",
                )
            }
        }
        save("64b-fim-convidado")
        rule.onNode(hasText("Sugerir outro jogo")).performScrollTo().performClick()
        save("64c-fim-convidado-grade", 300)
        rule.onAllNodes(hasText("Damas") and hasClickAction()).onFirst().performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals(listOf('x'), sent)
        assertTrue(shown("Sugestão enviada"))
        save("64d-fim-convidado-enviada", 300)
    }

    @Test
    fun stories() {
        val ctx = rule.activity
        fun card(name: String, s: MatchStory) {
            val bmp = StoryCard.renderMatch(ctx, s)
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val a = MatchStory.points('s', "Daniel", "Ana", 230, 180, 5, "Pontos 230 × 180")
        assertEquals("Pontos 230 × 180", a.detail)
        card("65-stories-adedonha", a)
        val g = MatchStory.points('g', "Daniel", "Ana", 8, 6, 10, "8 × 6 acertos")
        assertEquals("8 × 6 acertos", g.detail)
        card("65b-stories-trivia", g)
        val f = MatchStory.series('f', "Daniel", "Ana", Multi.Series(2, 1), "me", "Palavra: PÃO DE QUEIJO")
        card("65c-stories-forca", f)
        assertEquals("Forca 🎯", f.modeLabel)
    }
}
