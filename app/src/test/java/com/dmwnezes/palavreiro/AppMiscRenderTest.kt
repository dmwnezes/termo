package com.dmwnezes.palavreiro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.Achievement
import com.dmwnezes.palavreiro.game.Flash
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.system.DailyStatus
import com.dmwnezes.palavreiro.system.FlashWidget
import com.dmwnezes.palavreiro.system.NextWordData
import com.dmwnezes.palavreiro.system.NextWordWidget
import com.dmwnezes.palavreiro.system.StreakData
import com.dmwnezes.palavreiro.system.StreakWidget
import com.dmwnezes.palavreiro.system.Widget
import com.dmwnezes.palavreiro.system.WidgetKit
import com.dmwnezes.palavreiro.ui.AchievementQueue
import com.dmwnezes.palavreiro.ui.CreditsEgg
import com.dmwnezes.palavreiro.ui.CreditsEggOverlay
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.HomeScreen
import com.dmwnezes.palavreiro.ui.Night
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.ProfileScreen
import com.dmwnezes.palavreiro.ui.RecordsSection
import com.dmwnezes.palavreiro.update.AutoUpdate
import com.dmwnezes.palavreiro.update.UpdateReadyPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** Telas e widgets da rodada: cartão do Sinônimo ou Antônimo, easter egg, conquista, recordes e widgets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class AppMiscRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }

    private fun save(name: String, advance: Long = 1500) {
        rule.mainClock.advanceTimeBy(advance)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val badges = mapOf(Dest.TERMO to "Novo", Dest.DUETO to "Feito hoje ✓", Dest.CONEXOES to "Novo", Dest.CACA to "Novo")

    @Test
    fun inicioSinonimos() {
        rule.mainClock.autoAdvance = false
        // Rola até o fim: os últimos cartões (Sinônimo ou Antônimo, Jogar com amigo) e os créditos.
        rule.setContent { PalavreiroTheme { HomeScreen(badges, {}, {}, scroll = androidx.compose.foundation.ScrollState(100_000)) } }
        save("50-inicio-sinonimos")
    }

    @Test
    fun creditosExplosao() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(badges, {}, {})
                    // Origem: centro dos créditos no rodapé (px em xhdpi).
                    CreditsEggOverlay(CreditsEgg.Burst(1, Offset(360f, 1480f))) {}
                }
            }
        }
        save("51a-creditos-explosao", 450)
        save("51b-creditos-explosao-meio", 700)
        save("51c-creditos-obrigado", 1600)
    }

    @Test
    fun conquistaDesbloqueada() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(badges, {}, {})
                    val q = remember { mutableStateListOf(Achievement("📅", "Uma semana", "Sequência de 7 no Termo", 7, 7)) }
                    AchievementQueue(q)
                }
            }
        }
        save("52-conquista", 900)
    }

    @Test
    fun atualizacaoBaixada() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(badges, {}, {})
                    UpdateReadyPill(AutoUpdate.Ready("v1.0.42", File("x.apk")), {}, {}, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 72.dp))
                }
            }
        }
        save("53-atualizacao-baixada")
    }

    private val rec = Records(
        mapOf(
            "conn_won" to 14, "conn_perfect" to 6, "ws_best" to 95, "ws_played" to 9, "def_best" to 12, "def_right" to 57,
            "sa_best" to 23, "sa_tricks" to 8, "intr_best" to 9, "intr_right" to 40, "ort_best" to 18, "ort_right" to 66,
            "mestre_best" to 0, "arch_played" to 5, "arch_won" to 4, "mp_won" to 3, "mp_played" to 7,
        )
    )
    private val daily = Stats(played = 31, won = 27, streak = 4, maxStreak = 12, firstTry = 1, dist = listOf(1, 2, 4, 2, 1, 0, 0, 0, 0))
    private val inf = Stats(played = 60, won = 48, streak = 3, maxStreak = 9, dist = listOf(0, 3, 9, 8, 4, 2, 0, 0, 0))

    @Test
    fun recordes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme {
                ProfileScreen(null, mapOf(Mode.DIARIO to daily, Mode.INFINITO to inf), rec, {}, {}, {}, scroll = androidx.compose.foundation.ScrollState(100_000))
            }
        }
        save("54a-recordes-perfil")
    }

    @Test
    @Config(qualifiers = "w360dp-h1300dp-xhdpi")
    fun recordesCompleto() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PalavreiroTheme {
                Box(Modifier.fillMaxSize().background(Night.background)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                        Column(Modifier.clip(com.dmwnezes.palavreiro.ui.Shapes.card).background(Night.surface).padding(18.dp)) { RecordsSection(daily, inf, rec) }
                    }
                }
            }
        }
        save("54b-recordes")
    }

    // ---------- Widgets ----------

    private val ctx get() = rule.activity

    /** Desenha um RemoteViews no tamanho do widget (dp) sobre um fundo de papel de parede. */
    private fun widget(name: String, rv: RemoteViews, wDp: Int, hDp: Int, target: File = out, backdrop: Int = 0xFF50607A.toInt()) {
        rule.runOnUiThread {
            val d = ctx.resources.displayMetrics.density
            val w = (wDp * d).toInt(); val h = (hDp * d).toInt(); val pad = (12 * d).toInt()
            val parent = FrameLayout(ctx)
            val v = rv.apply(ctx, parent)
            parent.addView(v, FrameLayout.LayoutParams(w, h))
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(backdrop)
            c.translate(pad.toFloat(), pad.toFloat())
            v.draw(c)
            File(target, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val small = WidgetKit.Size.SMALL
    private val medium = WidgetKit.Size.MEDIUM
    private val large = WidgetKit.Size.LARGE

    @Test
    fun widgetHoje() {
        val s = DailyStatus(termo = true, dueto = true, quarteto = false, conexoes = true, caca = false, streak = 4)
        widget("60a-widget-hoje-pequeno", Widget.views(ctx, small, s), 110, 110)
        widget("60b-widget-hoje-medio", Widget.views(ctx, medium, s), 180, 200)
        widget("60c-widget-hoje-grande", Widget.views(ctx, large, s), 280, 330)
        widget("60d-widget-hoje-pequeno-largo", Widget.views(ctx, small, s), 250, 120)
    }

    private val week = (6 downTo 0).map { LocalDate.of(2026, 10, 11).minusDays(it.toLong()) }
        .mapIndexed { i, d -> d to listOf("w", "w", null, "w", "l", "w", "w")[i] }

    @Test
    fun widgetSequencia() {
        val d = StreakData(12, 20, week)
        widget("61a-widget-sequencia-pequeno", StreakWidget.views(ctx, small, d), 110, 110)
        widget("61b-widget-sequencia-medio", StreakWidget.views(ctx, medium, d), 250, 110)
        widget("61c-widget-sequencia-grande", StreakWidget.views(ctx, large, d), 250, 260)
        widget("61d-widget-sequencia-zero", StreakWidget.views(ctx, small, StreakData(0, 20, week)), 110, 110)
    }

    @Test
    fun widgetProxima() {
        val d = NextWordData(msLeft = (4 * 60 + 56) * 60_000L + 12_000, minutesElapsed = 1143, termoDone = true)
        val now = android.os.SystemClock.elapsedRealtime()
        widget("62a-widget-proxima-pequeno", NextWordWidget.views(ctx, small, d, now), 110, 110)
        widget("62b-widget-proxima-medio", NextWordWidget.views(ctx, medium, d.copy(termoDone = false), now), 250, 130)
        widget("62c-widget-proxima-grande", NextWordWidget.views(ctx, large, d, now), 250, 260)
    }

    @Test
    fun widgetRelampago() {
        val defs = QuizData.definitions(File("../shared/definicoes.txt").readText())
        val s0 = Flash.next(defs, null, Random(4))
        val right = Flash.answer(s0, s0.correctIndex)
        val wrong = Flash.answer(s0, (s0.correctIndex + 1) % 3)
        widget("63a-widget-relampago-pequeno", FlashWidget.views(ctx, small, 1, s0, defs), 250, 180)
        widget("63b-widget-relampago-medio", FlashWidget.views(ctx, medium, 1, right.copy(streak = 5), defs), 260, 310)
        widget("63c-widget-relampago-pequeno-errou", FlashWidget.views(ctx, small, 1, wrong, defs), 250, 190)
        widget("63d-widget-relampago-grande", FlashWidget.views(ctx, large, 1, s0.copy(streak = 3, best = 8, right = 12), defs), 320, 400)
        // A definição mais longa ainda cabe no menor tamanho.
        val longest = defs.indices.maxBy { defs[it].text.length }
        val sl = Flash.next(defs, null, Random(1)).let { st -> st.copy(def = longest, options = listOf(longest) + st.options.filter { it != longest }.take(2)) }
        widget("63e-widget-relampago-pequeno-longa", FlashWidget.views(ctx, small, 1, sl, defs), 250, 180)
    }

    /** Previews (previewImage) das versões antigas do Android, geradas a partir dos próprios layouts. */
    @Test
    fun widgetPreviews() {
        val dir = File("build/widget-previews").apply { mkdirs() }
        val s = DailyStatus(termo = true, dueto = true, quarteto = false, conexoes = true, caca = false, streak = 4)
        widget("widget_preview_hoje", Widget.views(ctx, medium, s), 180, 200, dir, 0)
        widget("widget_preview_sequencia", StreakWidget.views(ctx, medium, StreakData(12, 20, week)), 250, 110, dir, 0)
        widget("widget_preview_proxima", NextWordWidget.views(ctx, medium, NextWordData(17_772_000, 1143, true)), 250, 130, dir, 0)
        val defs = QuizData.definitions(File("../shared/definicoes.txt").readText())
        val s0 = Flash.next(defs, null, Random(4))
        widget("widget_preview_relampago", FlashWidget.views(ctx, small, 1, s0.copy(streak = 3), defs), 250, 190, dir, 0)
    }

    /** Cada widget jogável guarda o próprio jogo. */
    @Test
    fun relampagoInstanciasIndependentes() {
        val defs = FlashWidget.definitions(ctx)
        // Dois widgets de verdade na tela inicial (Robolectric).
        val shadow = org.robolectric.Shadows.shadowOf(android.appwidget.AppWidgetManager.getInstance(ctx))
        val id1 = shadow.createWidget(FlashWidget::class.java, R.layout.widget_flash_small)
        val id2 = shadow.createWidget(FlashWidget::class.java, R.layout.widget_flash_small)
        val a = FlashWidget.state(ctx, id1, defs)
        val b = FlashWidget.state(ctx, id2, defs)
        FlashWidget.save(ctx, id1, Flash.answer(a, a.correctIndex))
        assertTrue(FlashWidget.state(ctx, id1, defs).answered)
        assertEquals(1, FlashWidget.state(ctx, id1, defs).streak)
        assertTrue(!FlashWidget.state(ctx, id2, defs).answered)
        assertEquals(b, FlashWidget.state(ctx, id2, defs))
        // Toque pelo broadcast do botão.
        val t = FlashWidget.state(ctx, id2, defs)
        FlashWidget().onReceive(
            ctx,
            android.content.Intent(FlashWidget.ACTION_ANSWER)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id2)
                .putExtra(FlashWidget.EXTRA_OPTION, (t.correctIndex + 1) % 3),
        )
        val after = FlashWidget.state(ctx, id2, defs)
        assertTrue(after.answered && !after.wasRight)
        FlashWidget().onReceive(ctx, android.content.Intent(FlashWidget.ACTION_NEXT).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id2))
        val next = FlashWidget.state(ctx, id2, defs)
        assertTrue(!next.answered)
        assertNotEquals(after.def, next.def)
        assertEquals(1, FlashWidget.state(ctx, id1, defs).streak)
    }
}
