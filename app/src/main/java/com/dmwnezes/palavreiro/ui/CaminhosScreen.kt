package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.CaminhosData
import com.dmwnezes.palavreiro.game.CaminhosGame
import com.dmwnezes.palavreiro.game.CaminhosQ
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.random.Random

private const val CAMINHOS_HELP = "Arraste a partir de um ponto colorido até o outro ponto da mesma cor. Os caminhos não podem se cruzar: passar por cima de outro caminho corta esse caminho.\n\n" +
    "Para vencer, ligue todos os pares e preencha todas as casas. Há um desafio por nível a cada dia."

private val PATH_COLORS = listOf(Color(0xFFE5737A), Color(0xFF6FA8E8), Color(0xFF5FB873), Color(0xFFE6C14F), Color(0xFFC58CFF), Color(0xFFFF9F5A), Color(0xFF5FD0C5), Color(0xFFF49AC1))

/** Caminhos: ligue os pares e preencha a grade. */
@Composable
fun CaminhosScreen(list: List<CaminhosQ>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("cam_mode") == "inf") }
    var level by remember { mutableIntStateOf(store?.int("cam_level")?.coerceIn(0, 2) ?: 0) }
    var round by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val lk = CaminhosData.LEVELS[level].first
    val key = if (infinite) "cam_inf_state_$lk" else "cam_state_$lk"
    val pool = remember(level) { CaminhosData.ofLevel(list, level) }
    // Guardado: "marca;índice;caminhos;segundos".
    val saved = remember(infinite, level, round) { store?.text(key)?.split(';')?.takeIf { it.size == 4 && (infinite || it[0] == day) } }
    val qi = remember(infinite, level, round) {
        if (pool.isEmpty()) return@remember -1
        saved?.get(1)?.toIntOrNull()?.takeIf { it in pool.indices } ?: if (infinite) Random.nextInt(pool.size) else CaminhosData.dailyIndex(Words.dayIndex(date), pool.size)
    }
    val game = remember(infinite, level, round) { pool.getOrNull(qi)?.let { q -> CaminhosGame(q).also { g -> saved?.get(2)?.let(g::restore) } } }
    var seconds by remember(infinite, level, round) { mutableIntStateOf(saved?.get(3)?.toIntOrNull() ?: 0) }
    var finished by remember(game) { mutableStateOf(game?.won == true) }
    LaunchedEffect(game, finished) {
        while (game != null && !finished) { delay(1000); seconds++ }
    }
    val current by rememberUpdatedState(seconds)
    fun save() { game?.let { store?.setText(key, "$day;$qi;${it.save()};$current") } }
    fun released() {
        val g = game ?: return
        g.end()
        if (!finished && g.won) {
            finished = true
            store?.let { s ->
                s.add("cam_won"); s.logActivity()
                val b = s.int("cam_best_$lk")
                if (b <= 0 || current < b) s.setInt("cam_best_$lk", current.coerceAtLeast(1))
                if (!infinite) { s.setText("cam_done", day); s.setResult("caminhos", day, true) }
            }
            confetti++
            feedback?.win()
        }
        save(); tick++
    }
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Caminhos", onBack, onHelp = { showHelp = true }, trailing = { Text(formatTime(seconds), color = Night.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v -> infinite = v; store?.setText("cam_mode", if (v) "inf" else "day") }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                LevelTabs(CaminhosData.LEVELS.map { it.second }, level) { level = it; store?.setInt("cam_level", it) }
            }
            if (game == null) {
                Text("Não foi possível carregar as grades.", color = Night.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                tick.let { _ ->
                    Text(
                        if (finished) "Grade completa em ${formatTime(seconds)}! 🎉"
                        else "${game.joined} de ${game.q.pairs.size} pares · ${game.filled * 100 / (game.q.n * game.q.n)}% da grade",
                        color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val n = game.q.n
                Canvas(
                    Modifier.widthIn(max = 400.dp).fillMaxWidth().aspectRatio(1f).testTag("caminhos")
                        .pointerInput(game, finished) {
                            if (finished) return@pointerInput
                            fun cell(o: Offset): Int {
                                val s = size.width.toFloat() / n
                                val x = (o.x / s).toInt()
                                val y = (o.y / s).toInt()
                                return if (o.x >= 0 && o.y >= 0 && x < n && y < n) y * n + x else -1
                            }
                            // Sem detectDragGestures: ele só avisa depois de andar um pouco, e o arrasto rápido perderia a casa inicial.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                cell(down.position).takeIf { it >= 0 }?.let { game.start(it); feedback?.type(); tick++ }
                                down.consume()
                                do {
                                    val e = awaitPointerEvent()
                                    val ch = e.changes.first()
                                    if (ch.pressed) {
                                        cell(ch.position).takeIf { it >= 0 }?.let { game.dragTo(it); tick++ }
                                        ch.consume()
                                    }
                                } while (e.changes.any { it.pressed })
                                released()
                            }
                        },
                ) {
                    tick // redesenha a cada jogada
                    val s = size.width / n
                    fun center(c: Int) = Offset((c % n + 0.5f) * s, (c / n + 0.5f) * s)
                    val gap = s * 0.04f
                    val corner = CornerRadius(s * 0.22f)
                    for (c in 0 until n * n) {
                        drawRoundRect(Night.surfaceHigh, Offset((c % n) * s + gap, (c / n) * s + gap), Size(s - 2 * gap, s - 2 * gap), corner)
                    }
                    game.paths.forEachIndexed { k, p ->
                        val col = PATH_COLORS[k % PATH_COLORS.size]
                        p.forEach { c -> drawRoundRect(col.copy(alpha = 0.22f), Offset((c % n) * s + gap, (c / n) * s + gap), Size(s - 2 * gap, s - 2 * gap), corner) }
                        if (p.size > 1) {
                            val line = Path().apply { p.forEachIndexed { i, c -> val o = center(c); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                            drawPath(line, col, style = Stroke(s * 0.34f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    game.q.pairs.forEachIndexed { k, e -> e.forEach { c -> drawCircle(PATH_COLORS[k % PATH_COLORS.size], s * 0.33f, center(c)) } }
                }
                Spacer(Modifier.height(12.dp))
                if (finished) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                            val t = "Caminhos ${CaminhosData.LEVELS[level].second} ${if (infinite) "" else shortDay(date) + " "}🔗 em ${formatTime(seconds)}\nhttps://dmwnezes.com/termo/"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                        }
                        PillButton(if (infinite) "Nova grade" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                            store?.setText("cam_inf_state_$lk", null)
                            store?.setText("cam_mode", "inf")
                            infinite = true
                            round++
                        }
                    }
                } else {
                    PillButton("Limpar", Night.surfaceHigh, Night.text, Modifier.fillMaxWidth()) { game.clear(); feedback?.type(); save(); tick++ }
                }
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Caminhos")
                Spacer(Modifier.height(12.dp))
                Text(CAMINHOS_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}
