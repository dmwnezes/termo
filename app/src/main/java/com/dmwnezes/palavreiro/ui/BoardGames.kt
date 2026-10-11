package com.dmwnezes.palavreiro.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.game.Checkers
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.TicTacToe
import kotlinx.coroutines.delay
import kotlin.math.abs

// ======================================================================
// Jogo da Velha
// ======================================================================

/** Jogo da Velha com amigo. [msgs] = mensagens da rodada na ordem do tópico. */
@Composable
internal fun TicTacPlay(msgs: List<Msg>, d: Duel) {
    val hostX = TicTacToe.hostIsX(d.n)
    val opp = d.oppId ?: "?"
    val xId = if (hostX == d.isHost) d.myId else opp
    val oId = if (xId == d.myId) opp else d.myId
    val mine = if (xId == d.myId) 'X' else 'O'
    val theirs = if (mine == 'X') 'O' else 'X'
    val state = TicTacToe.state(msgs, xId, oId)
    var sentAt by remember(d.round) { mutableIntStateOf(-1) }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    // A contagem fica por cima e segura os toques; aqui não depende do relógio (que anda de 200 em 200 ms).
    val myTurn = !state.over && state.turn == mine
    val canPlay = myTurn && sentAt != state.moves
    val outcome = TicTacToe.outcome(state, mine)

    LaunchedEffect(myTurn, state.moves) { if (myTurn && state.moves > 0) d.feedback?.turn() }
    LaunchedEffect(outcome, d.round) {
        val o = outcome ?: return@LaunchedEffect
        d.onDecided(o)
        if (o == "me") d.feedback?.win() else if (o == "opp") d.feedback?.lose()
        delay(1300)
        showEnd = true
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); SeriesPill(d.series) })
            OppBar(d.oppName, when { !d.online -> "Reconectando…"; d.oppLeft -> "saiu"; else -> "joga de $theirs" }, false)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val status = when {
                    outcome == "me" -> "Você venceu!"
                    outcome == "opp" -> "${d.oppName} venceu"
                    outcome == "draw" -> "Deu velha!"
                    state.turn == mine -> "Sua vez ($mine)"
                    else -> "Vez de ${d.oppName} ($theirs)"
                }
                Text(
                    status, color = when { outcome == "me" || (outcome == null && state.turn == mine) -> Night.correct; outcome == "opp" -> Night.red; else -> Night.text },
                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1,
                )
                Spacer(Modifier.height(20.dp))
                TicTacBoard(state, enabled = canPlay) { c ->
                    sentAt = state.moves
                    d.feedback?.type()
                    d.send(Msg.Cell(d.myId, state.moves, c))
                }
                Spacer(Modifier.height(18.dp))
                Text("Você joga de $mine · X sempre começa", color = Night.muted, fontSize = 14.sp)
                if (outcome != null && !showEnd) {
                    Spacer(Modifier.height(14.dp))
                    PillButton("Ver resultado", Night.correct) { showEnd = true }
                }
            }
        }
        if (outcome != null && showEnd) {
            val sub = when (outcome) { "me" -> "Você fez a linha com $mine"; "opp" -> "${d.oppName} fez a linha com $theirs"; else -> "Ninguém fez a linha" }
            SeriesEndSheet(
                mode = Multi.TICTAC, outcome = outcome, oppName = d.oppName, subtitle = sub,
                myLine = "jogou de $mine" + if (outcome == "me") " · fez a linha" else "",
                oppLine = "jogou de $theirs" + if (outcome == "opp") " · fez a linha" else "",
                isHost = d.isHost, series = d.series, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                story = MatchStory.series(Multi.TICTAC, d.myName, d.oppName, d.series, outcome, sub),
                shareDetail = sub, myName = d.myName, next = d.next,
            )
        }
    }
}

/** Tabuleiro 3×3 arredondado: X verde, O amarelo; a linha vencedora fica destacada. */
@Composable
internal fun TicTacBoard(state: TicTacToe.State, enabled: Boolean, onTap: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 340.dp)
        Column(Modifier.size(side).clip(RoundedCornerShape(28.dp)).background(Night.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0 until 3) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 3) {
                        val i = r * 3 + c
                        val v = state.board[i]
                        val win = state.line?.contains(i) == true
                        val color = if (v == 'X') Night.correct else Night.present
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(20.dp))
                                .background(if (win) color.copy(alpha = 0.28f) else Night.surfaceHigh)
                                .then(if (win) Modifier.border(2.dp, color, RoundedCornerShape(20.dp)) else Modifier)
                                .clickable(enabled = enabled && v == null) { onTap(i) }
                                .semantics { contentDescription = "Casa $i" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (v != null) Canvas(Modifier.fillMaxSize(0.52f)) {
                                val w = size.minDimension * 0.17f
                                if (v == 'X') {
                                    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
                                    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
                                } else drawCircle(color, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================
// Damas
// ======================================================================

private val SquareLight = Color(0xFF8B80C8)
private val SquareDark = Color(0xFF3A3170)
private val PieceWhite = Color(0xFFF4F1FF)
private val PieceBlack = Color(0xFF17122E)

/**
 * Damas com amigo. [msgs] = mensagens da rodada na ordem do tópico.
 * [initialSel] = peça (e paradas) já selecionadas (para a imagem de teste).
 */
@Composable
internal fun CheckersPlay(msgs: List<Msg>, d: Duel, initialSel: List<Int> = emptyList()) {
    val now = rememberNow(d.clock, d.round, 200)
    val hostWhite = Checkers.hostIsWhite(d.n)
    val opp = d.oppId ?: "?"
    val whiteId = if (hostWhite == d.isHost) d.myId else opp
    val blackId = if (whiteId == d.myId) opp else d.myId
    val mine = if (whiteId == d.myId) Checkers.WHITE else Checkers.BLACK
    val state = Checkers.state(msgs, whiteId, blackId)
    val counting = now < d.localStart
    var sentAt by remember(d.round) { mutableIntStateOf(-1) }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    var confirm by remember(d.round) { mutableStateOf(false) }
    // A contagem fica por cima e segura os toques; aqui não depende do relógio (que anda de 200 em 200 ms).
    val myTurn = !state.over && state.turn == mine
    val canPlay = myTurn && sentAt != state.moves
    val legal = if (canPlay) state.legal else emptyList()
    var sel by remember(d.round, state.moves) { mutableStateOf(initialSel) }
    val outcome = Checkers.outcome(state, mine)
    fun colorName(side: Int) = if (side == Checkers.WHITE) "brancas" else "pretas"

    LaunchedEffect(myTurn, state.moves) { if (myTurn && state.moves > 0) d.feedback?.turn() }
    LaunchedEffect(outcome, d.round) {
        val o = outcome ?: return@LaunchedEffect
        d.onDecided(o)
        if (o == "me") d.feedback?.win() else if (o == "opp") d.feedback?.lose()
        delay(1300)
        showEnd = true
    }

    // O toque usa sempre o estado mais novo (a função pode ter sido guardada numa composição antiga).
    val live by rememberUpdatedState(Triple(state, canPlay, legal))
    fun send(path: List<Int>) {
        val st = live.first
        sentAt = st.moves; sel = emptyList()
        d.feedback?.type()
        d.send(Msg.Move(d.myId, st.moves, path))
    }
    fun tap(sq: Int) {
        val (_, can, legal) = live
        if (!can) return
        val cands = if (sel.isEmpty()) emptyList() else legal.filter { it.size > sel.size && it.subList(0, sel.size) == sel }
        if (sel.isNotEmpty()) {
            if (cands.any { it[sel.size] == sq }) {
                val ns = sel + sq
                val c2 = legal.filter { it.size >= ns.size && it.subList(0, ns.size) == ns }
                if (c2.size == 1) send(c2[0]) else sel = ns
                return
            }
            val finals = cands.filter { it.last() == sq }
            if (finals.size == 1) { send(finals[0]); return }
        }
        sel = if (sel.size <= 1 && legal.any { it[0] == sq }) listOf(sq) else emptyList()
    }

    val myCount = Checkers.count(state.board, mine)
    val oppCount = Checkers.count(state.board, -mine)
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); SeriesPill(d.series) })
            OppBar(
                d.oppName,
                when { !d.online -> "Reconectando…"; d.oppLeft -> "saiu"; else -> "${colorName(-mine)} · " + (if (oppCount == 1) "1 peça" else "$oppCount peças") },
                false,
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val status = when {
                    outcome == "me" -> if (state.resigned == -mine) "${d.oppName} desistiu. Você venceu!" else "Você venceu!"
                    outcome == "opp" -> if (state.resigned == mine) "Você desistiu" else "${d.oppName} venceu"
                    outcome == "draw" -> "Empate"
                    state.turn == mine -> "Sua vez (${colorName(mine)})"
                    else -> "Vez de ${d.oppName} (${colorName(-mine)})"
                }
                Text(
                    status, color = when { outcome == "me" || (outcome == null && state.turn == mine) -> Night.correct; outcome == "opp" -> Night.red; else -> Night.text },
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1,
                )
                if (outcome == null && state.turn == mine && state.mustCapture) {
                    Text("Captura obrigatória", color = Night.present, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                } else Spacer(Modifier.height(18.dp))
                Spacer(Modifier.height(8.dp))
                CheckersBoard(
                    state = state, flipped = mine == Checkers.BLACK, legal = legal, sel = sel,
                    showLast = state.last != null && state.turn == mine, mustCapture = canPlay && state.mustCapture, onTap = { tap(it) },
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Você: ${colorName(mine)} · " + (if (myCount == 1) "1 peça" else "$myCount peças"),
                        color = Night.muted, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    if (outcome == null) {
                        Text(
                            "Desistir", color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(Shapes.pill).background(Night.surfaceHigh).clickable(enabled = !counting) { confirm = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    } else if (!showEnd) PillButton("Ver resultado", Night.correct) { showEnd = true }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (confirm && outcome == null) {
            BottomSheet({ confirm = false }) {
                SheetTitle("Desistir da rodada?", "${d.oppName} ganha esta rodada.")
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Continuar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { confirm = false }
                    PillButton("Desistir", Night.red, modifier = Modifier.weight(1f)) { confirm = false; d.send(Msg.Resign(d.myId)) }
                }
            }
        }
        if (outcome != null && showEnd) {
            val detail = "Peças: $myCount × $oppCount"
            val sub = when {
                state.resigned == -mine -> "${d.oppName} desistiu"
                state.resigned == mine -> "Você desistiu"
                state.draw -> "20 lances só de damas sem captura"
                else -> detail
            }
            SeriesEndSheet(
                mode = Multi.CHECKERS, outcome = outcome, oppName = d.oppName, subtitle = sub,
                myLine = colorName(mine) + " · " + (if (myCount == 1) "1 peça" else "$myCount peças"),
                oppLine = colorName(-mine) + " · " + (if (oppCount == 1) "1 peça" else "$oppCount peças"),
                isHost = d.isHost, series = d.series, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                story = MatchStory.series(Multi.CHECKERS, d.myName, d.oppName, d.series, outcome, detail),
                shareDetail = detail, myName = d.myName, next = d.next,
            )
        }
    }
}

/**
 * Tabuleiro 8×8 (suas peças embaixo: o das pretas é girado 180°). Destaca a peça escolhida, os destinos,
 * as peças que podem capturar (captura obrigatória) e a última jogada do amigo.
 */
@Composable
internal fun CheckersBoard(
    state: Checkers.State, flipped: Boolean, legal: List<List<Int>>, sel: List<Int>, showLast: Boolean, mustCapture: Boolean,
    onTap: (Int) -> Unit,
) {
    val cands = if (sel.isEmpty()) emptyList() else legal.filter { it.size > sel.size && it.subList(0, sel.size) == sel }
    val nextSteps = cands.map { it[sel.size] }.toSet()
    val finals = cands.map { it.last() }.toSet()
    val capturers = if (mustCapture) legal.map { it[0] }.toSet() else emptySet()
    val last = if (showLast) state.last.orEmpty().toSet() else emptySet()
    val pulse = rememberInfiniteTransition(label = "captura")
    val a by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "a")
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 440.dp)
        Column(Modifier.size(side).clip(RoundedCornerShape(18.dp)).border(3.dp, Night.surfaceHigh, RoundedCornerShape(18.dp))) {
            for (vr in 0 until 8) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (vc in 0 until 8) {
                        val sq = if (flipped) 63 - (vr * 8 + vc) else vr * 8 + vc
                        val dark = Checkers.dark(sq)
                        val p = state.board[sq]
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(
                                    when {
                                        sq in sel -> Night.accent.copy(alpha = 0.85f)
                                        sq in last -> Night.present.copy(alpha = 0.45f)
                                        dark -> SquareDark
                                        else -> SquareLight
                                    }
                                )
                                .clickable(enabled = dark) { onTap(sq) }
                                .semantics { contentDescription = "Casa $sq" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (p != 0) {
                                val white = p > 0
                                Box(
                                    Modifier.fillMaxSize(0.78f).shadow(4.dp, CircleShape).clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                if (white) listOf(Color.White, PieceWhite, Color(0xFFD3CCF2)) else listOf(Color(0xFF3D3468), PieceBlack, Color(0xFF0B0819)),
                                            )
                                        )
                                        .border(
                                            if (sq in capturers) 3.dp else 1.5.dp,
                                            if (sq in capturers) Night.present.copy(alpha = a) else if (white) Color(0xFFB8AEE6) else Color(0xFF6A5FA8),
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (abs(p) == 2) Text("♛", color = if (white) Color(0xFF8A6A10) else Color(0xFFF5C84C), fontSize = (side.value / 8 * 0.42f).sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (sq in nextSteps || sq in finals) {
                                Box(
                                    Modifier.fillMaxSize(if (sq in nextSteps) 0.34f else 0.5f).clip(CircleShape)
                                        .then(
                                            if (sq in nextSteps) Modifier.background(Night.correct.copy(alpha = 0.9f))
                                            else Modifier.border(2.dp, Night.correct, CircleShape)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
