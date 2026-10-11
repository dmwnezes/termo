package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.SearchTheme
import com.dmwnezes.palavreiro.game.WordSearch
import com.dmwnezes.palavreiro.game.WordSearchData
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Caça-Palavras do Dia: arraste o dedo sobre as letras para marcar cada palavra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordSearchScreen(
    themes: List<SearchTheme>,
    infiniteThemes: List<SearchTheme>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    date: LocalDate = LocalDate.now(),
    startInfinite: Boolean = false,
    /** Grade antiga aberta pelo Arquivo (data = [date]). */
    archive: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = rememberToast()
    val day = date.toString()
    var infinite by remember { mutableStateOf(startInfinite && !archive) }
    var infSeed by remember {
        mutableLongStateOf(store?.text("ws_inf_state")?.substringBefore('|')?.toLongOrNull() ?: Random.nextLong())
    }
    val stateKey = if (infinite) "ws_inf_state" else if (archive) "ws_arch_state" else "ws_state"
    val tag = if (infinite) infSeed.toString() else day
    val ws = remember(infinite, infSeed, day) {
        val built = if (infinite) WordSearchData.infinite(infiniteThemes, infSeed)
        else WordSearch(themes[WordSearchData.dailyIndex(date, themes.size)], seed = Words.dayIndex(date) * 31 + 17)
        built.also { w ->
            val saved = store?.text(stateKey)
            if (saved != null && saved.substringBefore('|') == tag) {
                saved.substringAfter('|').split(',').filter { it.isNotBlank() }.forEach { if (it !in w.found) w.found += it }
            }
        }
    }
    val theme = ws.theme
    var seconds by remember(ws) {
        mutableIntStateOf(
            if (infinite) store?.text("ws_inf_time")?.takeIf { it.substringBefore('|') == tag }?.substringAfter('|')?.toIntOrNull() ?: 0
            else if (archive) store?.text("ws_arch_time")?.takeIf { it.substringBefore('|') == day }?.substringAfter('|')?.toIntOrNull() ?: 0
            else if (store?.text("ws_day") == day) store.int("ws_elapsed") else 0
        )
    }
    var showResult by remember(ws) { mutableStateOf(ws.done && !infinite) }
    var confetti by remember { mutableIntStateOf(0) }

    /** Próxima grade infinita, sempre com um tema diferente da atual. */
    fun next() {
        var s: Long
        do { s = Random.nextLong() } while (infiniteThemes.size > 1 && WordSearchData.infinite(infiniteThemes, s).theme.name == theme.name)
        infSeed = s
    }
    LaunchedEffect(ws) { if (infinite && ws.done) next() }

    // Cronômetro: conta enquanto a tela está aberta e a grade não terminou.
    LaunchedEffect(ws, ws.done) {
        while (!ws.done) {
            delay(1000)
            seconds++
            if (infinite) store?.setText("ws_inf_time", "$tag|$seconds")
            else if (archive) store?.setText("ws_arch_time", "$day|$seconds")
            else { store?.setText("ws_day", day); store?.setInt("ws_elapsed", seconds) }
        }
    }

    fun onFound() {
        store?.setText(stateKey, tag + "|" + ws.found.joinToString(","))
        if (ws.done) {
            feedback?.win()
            confetti++
            if (store != null && archive) {
                store.archiveDone("caca", day, true)
            } else if (store != null && infinite) {
                store.add("ws_inf_played")
                store.logActivity(day)
                store.min("ws_inf_best", seconds)
            } else if (store != null && store.text("ws_done") != day) {
                store.setText("ws_done", day)
                store.setResult("caca", day, true)
                store.setHistory(day, "caca", seconds)
                store.add("ws_played")
                store.logActivity(day)
                store.min("ws_best", seconds)
            }
            scope.launch { delay(1600); showResult = true }
        }
    }
    val bestKey = if (infinite) "ws_inf_best" else "ws_best"
    val playedKey = if (infinite) "ws_inf_played" else "ws_played"

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(if (archive) "Caça · ${shortDay(date)}" else "Caça-Palavras", onBack, trailing = {
                Text(formatTime(seconds), color = Night.muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
            })
            if (!archive) {
                ModeSwitch(infinite, Modifier.padding(horizontal = 14.dp)) { infinite = it }
                Spacer(Modifier.height(10.dp))
            }
            Column(
                // Sem rolagem: o arrasto na grade não pode virar rolagem da tela.
                Modifier.weight(1f).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (infinite) "Tema sorteado" else if (archive) "Tema do dia ${shortDay(date)}" else "Tema de hoje", color = Night.muted, fontSize = 13.sp)
                Text(theme.name, color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                SearchGrid(ws) { p ->
                    if (p != null) { feedback?.reveal(2, Mark.CORRECT); toast.show(p.display); onFound() }
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ws.placed.forEach { p ->
                        val found = p.word in ws.found
                        Text(
                            p.display,
                            color = if (found) Night.muted else Night.text,
                            textDecoration = if (found) TextDecoration.LineThrough else null,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.clip(Shapes.pill)
                                .background(if (found) colorFor(ws.placed.indexOf(p)).copy(alpha = 0.25f) else Night.surfaceHigh)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("${ws.found.size} de ${ws.placed.size} palavras", color = Night.muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                ToastView(toast)
                if (ws.done) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Ver resultado", if (infinite || archive) Night.surfaceHigh else Night.correct, if (infinite || archive) Night.text else Color(0xFF14102C)) { showResult = true }
                        if (infinite) PillButton("Próxima grade", Night.correct) { next() }
                        if (archive) PillButton("Voltar ao arquivo", Night.correct, onClick = onBack)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (confetti > 0) Confetti(confetti) { confetti = 0 }
        if (showResult && ws.done) {
            BottomSheet({ showResult = false }) {
                SheetTitle("Você achou todas!", "Tema: ${theme.name}")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(formatTime(seconds), "Tempo")
                    StatItem(store?.int(bestKey)?.takeIf { it > 0 }?.let(::formatTime) ?: "—", "Melhor tempo")
                    StatItem("${store?.int(playedKey) ?: 0}", "Grades")
                }
                Spacer(Modifier.height(16.dp))
                if (archive) PillButton("Voltar ao arquivo", Night.present, modifier = Modifier.fillMaxWidth(), onClick = onBack)
                else if (infinite) PillButton("Próxima grade", Night.present, modifier = Modifier.fillMaxWidth()) { showResult = false; next() }
                else Text("Uma grade nova aparece amanhã.", fontSize = 13.sp, color = Night.muted)
                Spacer(Modifier.height(12.dp))
                PillButton("Cartão para Stories", Night.accent, modifier = Modifier.fillMaxWidth()) {
                    val colors = ws.placed.indices.map { colorFor(it) }
                    StoryCard.share(
                        context,
                        StoryData(
                            game = if (infinite) "Caça-Palavras Infinito" else "Caça-Palavras do dia",
                            headline = "Achei tudo em ${formatTime(seconds)}",
                            detail = "Tema: ${theme.name}",
                            grids = listOf(colors.chunked(4)),
                            stats = listOf((store?.int(bestKey)?.takeIf { it > 0 }?.let(::formatTime) ?: "—") to "Melhor tempo", "${store?.int(playedKey) ?: 0}" to "Grades"),
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                PillButton("Fechar", Night.correct, modifier = Modifier.fillMaxWidth()) { showResult = false }
            }
        }
    }
}

private val highlightColors = listOf(Night.correct, Night.present, Night.blue, Night.purple, Night.red, Color(0xFF7FD1C4), Color(0xFFF2A65A), Night.accent)
private fun colorFor(i: Int) = highlightColors[i % highlightColors.size]

@Composable
private fun SearchGrid(ws: WordSearch, onResult: (com.dmwnezes.palavreiro.game.Placed?) -> Unit) {
    var start by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var end by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 440.dp)
        val n = ws.size
        Box(
            Modifier.size(side).clip(RoundedCornerShape(24.dp)).background(Night.surface)
                .pointerInput(ws) {
                    val cell = size.width / n.toFloat()
                    fun at(o: Offset): Pair<Int, Int> =
                        (o.y / cell).toInt().coerceIn(0, n - 1) to (o.x / cell).toInt().coerceIn(0, n - 1)
                    detectDragGestures(
                        onDragStart = { o -> start = at(o); end = start },
                        onDrag = { change, _ ->
                            val s = start ?: return@detectDragGestures
                            val raw = at(change.position)
                            end = WordSearch.snap(s.first, s.second, raw.first, raw.second)
                        },
                        onDragEnd = {
                            val s = start; val e = end
                            if (s != null && e != null) onResult(ws.check(s.first, s.second, e.first, e.second))
                            start = null; end = null
                        },
                        onDragCancel = { start = null; end = null },
                    )
                },
        ) {
            // Marcações: palavras achadas e a seleção atual, como cápsulas arredondadas.
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / n
                fun center(rc: Pair<Int, Int>) = Offset(rc.second * cell + cell / 2, rc.first * cell + cell / 2)
                ws.placed.forEachIndexed { i, p ->
                    if (p.word in ws.found) {
                        drawLine(colorFor(i).copy(alpha = 0.55f), center(p.cells.first()), center(p.cells.last()), strokeWidth = cell * 0.78f, cap = StrokeCap.Round)
                    }
                }
                val s = start; val e = end
                if (s != null && e != null) {
                    drawLine(Night.accent.copy(alpha = 0.6f), center(s), center(e), strokeWidth = cell * 0.78f, cap = StrokeCap.Round)
                }
            }
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until n) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until n) {
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(ws.grid[r][c].toString(), color = Night.text, fontWeight = FontWeight.SemiBold, fontSize = (side.value / n * 0.5f).sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
