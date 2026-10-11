package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.game.HuntDuel
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.WordSearch
import kotlinx.coroutines.delay

/**
 * Caça-Palavras com amigo: a mesma grade (mandada pelo anfitrião) para os dois; cada palavra é de quem achar primeiro.
 * [grid] = grade desta rodada (null enquanto não chegou); [msgs] = mensagens da rodada na ordem do tópico.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HuntPlay(grid: Msg.Grid?, msgs: List<Msg>, d: Duel) {
    val now = rememberNow(d.clock, d.round, 200)
    val state = HuntDuel.state(grid, msgs, d.n, setOfNotNull(d.myId, d.oppId))
    val elapsed = now - d.localStart
    val over = state.over
    val toast = rememberToast()
    // Palavras que mandei e ainda não voltaram pelo tópico.
    var pending by remember(d.round) { mutableStateOf(setOf<String>()) }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    var sentTimeUp by remember(d.round) { mutableStateOf(false) }
    val oppGone by rememberUpdatedState(d.oppLeft)
    val overNow by rememberUpdatedState(over)

    // Fim do tempo: o anfitrião manda o "timeup" aos 5 min; o convidado se o anfitrião saiu ou aos 5 min + 10 s.
    LaunchedEffect(d.round) {
        while (!overNow) {
            val e = d.clock() - d.localStart
            if (!sentTimeUp && ((d.isHost && e >= HuntDuel.ROUND_MS) || (!d.isHost && (e >= HuntDuel.GUEST_TIMEUP_MS || (oppGone && e >= HuntDuel.ROUND_MS))))) {
                sentTimeUp = true; d.send(Msg.TimeUp(d.n)); break
            }
            delay(250)
        }
    }
    // Palavra do amigo: aviso curto.
    val oppCount = state.count(d.oppId)
    LaunchedEffect(oppCount) { if (oppCount > 0 && !over) d.feedback?.react() }
    LaunchedEffect(over, d.round) {
        if (!over) return@LaunchedEffect
        val o = state.outcome(d.myId, d.oppId) ?: "draw"
        d.onDecided(o)
        if (o == "me") d.feedback?.win() else if (o == "opp") d.feedback?.lose()
        delay(1500)
        showEnd = true
    }

    val mine = state.count(d.myId)
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); SeriesPill(d.series) })
            // Placar de palavras da rodada e o relógio.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(Shapes.card).background(Night.surface)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Você ", color = Night.correct, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("$mine × $oppCount", color = Night.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(" ${d.oppName}", color = Night.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val left = ((HuntDuel.ROUND_MS - elapsed.coerceAtLeast(0)) / 1000).coerceAtLeast(0).toInt()
                Text(
                    when { !d.online -> "Reconectando…"; d.oppLeft -> "${d.oppName} saiu"; else -> "⏱ " + formatTime(if (over) 0 else left) },
                    color = if (left < 30 && !over) Night.red else Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(4.dp))
                Text(grid?.theme ?: " ", color = Night.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                if (grid != null) {
                    // A contagem e o "Preparando a grade…" ficam por cima e seguram os toques antes da hora.
                    DuelGrid(grid, state.owners, pending, d.myId, enabled = !over) { r1, c1, r2, c2 ->
                        val w = HuntDuel.match(grid, r1, c1, r2, c2, state.owners.keys + pending) ?: return@DuelGrid
                        pending = pending + w
                        d.feedback?.reveal(2, Mark.CORRECT)
                        toast.show(grid.words.firstOrNull { HuntDuel.key(it) == w } ?: w)
                        d.send(Msg.Found(d.myId, d.n, w))
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        grid.words.forEach { w ->
                            val k = HuntDuel.key(w)
                            val owner = state.owners[k]
                            val me = owner == d.myId || (owner == null && k in pending)
                            val them = owner != null && owner != d.myId
                            Text(
                                w,
                                color = when { me -> InkDark; them -> Night.accent; else -> Night.text },
                                textDecoration = if (them) TextDecoration.LineThrough else null,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                modifier = Modifier.clip(Shapes.pill)
                                    .background(when { me -> Night.correct.copy(alpha = if (owner == null) 0.6f else 1f); them -> Night.accent.copy(alpha = 0.2f); else -> Night.surfaceHigh })
                                    .padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ToastView(toast)
                    if (over && !showEnd) {
                        Spacer(Modifier.height(8.dp))
                        PillButton("Ver resultado", Night.correct) { showEnd = true }
                    }
                }
            }
        }
        if (grid == null && elapsed >= 0) Preparing()
        if (over && showEnd && grid != null) {
            val o = state.outcome(d.myId, d.oppId) ?: "draw"
            fun line(n: Int) = if (n == 1) "1 palavra" else "$n palavras"
            SeriesEndSheet(
                mode = Multi.HUNT, outcome = o, oppName = d.oppName, subtitle = "Tema: ${grid.theme}",
                myLine = line(mine), oppLine = line(oppCount), isHost = d.isHost, series = d.series,
                onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                story = MatchStory.series(Multi.HUNT, d.myName, d.oppName, d.series, o, "Palavras: $mine × $oppCount"),
                shareDetail = "Palavras: $mine × $oppCount", myName = d.myName, next = d.next,
            )
        }
    }
}

/** "Preparando a grade…" por cima da tela até o "grid" do anfitrião chegar. */
@Composable
private fun Preparing() {
    Box(Modifier.fillMaxSize().background(Night.bgBottom.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FiveSquares(square = 14.dp, gap = 4.dp, animated = true)
            Spacer(Modifier.height(14.dp))
            Text("Preparando a grade…", color = Night.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Grade do duelo: arrastar em linha reta marca; palavras minhas em verde, do amigo em roxo. */
@Composable
private fun DuelGrid(
    grid: Msg.Grid, owners: Map<String, String>, pending: Set<String>, myId: String, enabled: Boolean,
    onSelect: (Int, Int, Int, Int) -> Unit,
) {
    var start by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var end by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val rows = grid.rows
    val n = rows.size
    val select by rememberUpdatedState(onSelect)
    val on by rememberUpdatedState(enabled)
    val cells = remember(grid) { grid.words.associate { HuntDuel.key(it) to HuntDuel.locate(rows, it) } }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 440.dp)
        Box(
            Modifier.size(side).clip(RoundedCornerShape(24.dp)).background(Night.surface)
                .semantics { contentDescription = "Grade" }
                .pointerInput(grid) {
                    val cell = size.width / n.toFloat()
                    fun at(o: Offset): Pair<Int, Int> = (o.y / cell).toInt().coerceIn(0, n - 1) to (o.x / cell).toInt().coerceIn(0, n - 1)
                    // Começa na casa onde o dedo encostou (não onde o arrasto foi percebido) e termina ao soltar.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!on) return@awaitEachGesture
                        val s = at(down.position)
                        start = s; end = s
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val raw = at(ch.position)
                            end = WordSearch.snap(s.first, s.second, raw.first, raw.second)
                            ch.consume()
                        }
                        val e = end
                        if (e != null && on) select(s.first, s.second, e.first, e.second)
                        start = null; end = null
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / n
                fun center(rc: Pair<Int, Int>) = Offset(rc.second * cell + cell / 2, rc.first * cell + cell / 2)
                cells.forEach { (k, path) ->
                    if (path == null) return@forEach
                    val owner = owners[k]
                    val color = when {
                        owner == myId -> Night.correct
                        owner != null -> Night.accent
                        k in pending -> Night.correct.copy(alpha = 0.5f)
                        else -> return@forEach
                    }
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center(path.first()), center(path.last()), strokeWidth = cell * 0.78f, cap = StrokeCap.Round)
                }
                val s = start; val e = end
                if (s != null && e != null) drawLine(Night.present.copy(alpha = 0.55f), center(s), center(e), strokeWidth = cell * 0.78f, cap = StrokeCap.Round)
            }
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until n) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until n) {
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(rows[r].getOrNull(c)?.toString().orEmpty(), color = Night.text, fontWeight = FontWeight.SemiBold, fontSize = (side.value / n * 0.5f).sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

