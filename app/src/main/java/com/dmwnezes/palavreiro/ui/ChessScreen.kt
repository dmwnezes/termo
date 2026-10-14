package com.dmwnezes.palavreiro.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Chess
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

// Casas nos dois tons do tema (os mesmos das Damas).
private val ChessLight = Color(0xFF8B80C8)
private val ChessDark = Color(0xFF3A3170)
private val PieceLight = Color(0xFFF7F4FF)
private val PieceDarkFill = Color(0xFF17122E)
private val CInk = Color(0xFF14102C)

/** Símbolo cheio de cada peça (com seletor de texto para o peão não virar emoji). */
private fun glyph(type: Int): String = when (type) {
    Chess.KING -> "♚"; Chess.QUEEN -> "♛"; Chess.ROOK -> "♜"; Chess.BISHOP -> "♝"; Chess.KNIGHT -> "♞"; else -> "♟︎"
}

/** Peça desenhada: símbolo cheio na cor do lado com contorno do outro tom (legível nas duas casas). */
@Composable
internal fun ChessPiece(piece: Int, size: TextUnit) {
    val white = piece > 0
    val g = glyph(abs(piece))
    Box(contentAlignment = Alignment.Center) {
        Text(
            g, fontSize = size, lineHeight = size, textAlign = TextAlign.Center,
            style = TextStyle(drawStyle = Stroke(width = size.value * 0.16f, join = androidx.compose.ui.graphics.StrokeJoin.Round)),
            color = if (white) CInk else Color(0xFFCFC7F5),
        )
        Text(g, fontSize = size, lineHeight = size, textAlign = TextAlign.Center, color = if (white) PieceLight else PieceDarkFill)
    }
}

private val PIECE_NAMES = mapOf(Chess.PAWN to "peão", Chess.KNIGHT to "cavalo", Chess.BISHOP to "bispo", Chess.ROOK to "torre", Chess.QUEEN to "dama", Chess.KING to "rei")

/**
 * Tabuleiro de xadrez com toques: escolha a peça (mostra os destinos) e toque no destino. Promoção abre a escolha da peça.
 * [enabled] = pode jogar agora; [onMove] recebe o lance em UCI. Suas peças ficam embaixo ([flipped] = você de pretas).
 */
@Composable
internal fun ChessBoard(
    state: Chess.State, flipped: Boolean, enabled: Boolean, onMove: (String) -> Unit,
    initialSel: Int = -1, initialPromo: String? = null,
) {
    var sel by remember(state.moves) { mutableIntStateOf(initialSel) }
    var promo by remember(state.moves) { mutableStateOf(initialPromo) }
    val legal = if (enabled) state.legal else emptyList()
    val targets = if (sel >= 0) legal.filter { Chess.from(it) == sel }.map { Chess.to(it) }.toSet() else emptySet()
    val last = state.last?.let { setOf(Chess.square(it.substring(0, 2)), Chess.square(it.substring(2, 4))) }.orEmpty()
    val checkSq = if (state.check) Chess.kingSq(state.pos.b, state.turn) else -1
    val live by rememberUpdatedState(legal)

    fun tap(sq: Int) {
        val lg = live
        if (lg.isEmpty() || promo != null) return
        val p = state.pos.b[sq]
        val moves = lg.filter { Chess.from(it) == sel && Chess.to(it) == sq }
        when {
            moves.size > 1 -> promo = Chess.name(sel) + Chess.name(sq)
            moves.size == 1 -> { onMove(Chess.uci(moves[0])); sel = -1 }
            p != 0 && Integer.signum(p) == state.turn && lg.any { Chess.from(it) == sq } -> sel = if (sel == sq) -1 else sq
            else -> sel = -1
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 440.dp)
        val cell = side / 8
        Box(Modifier.size(side)) {
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).border(3.dp, Night.surfaceHigh, RoundedCornerShape(16.dp))) {
                for (vr in 0 until 8) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (vc in 0 until 8) {
                        val rank = if (flipped) vr else 7 - vr
                        val file = if (flipped) 7 - vc else vc
                        val sq = rank * 8 + file
                        val light = (rank + file) % 2 == 1
                        val p = state.pos.b[sq]
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(
                                    when {
                                        sq == sel -> Night.accent.copy(alpha = 0.9f)
                                        sq == checkSq -> Night.red
                                        sq in last -> if (light) Color(0xFFC9B76A) else Color(0xFF7D6A2E)
                                        light -> ChessLight
                                        else -> ChessDark
                                    }
                                )
                                .clickable(enabled = enabled) { tap(sq) }
                                .semantics { contentDescription = "Casa ${Chess.name(sq)}" + if (p != 0) " ${PIECE_NAMES[abs(p)]} ${if (p > 0) "branco" else "preto"}" else "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (vc == 0) Text(
                                "${rank + 1}", color = if (light) ChessDark else ChessLight, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp),
                            )
                            if (vr == 7) Text(
                                "${'a' + file}", color = if (light) ChessDark else ChessLight, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp),
                            )
                            if (p != 0) ChessPiece(p, (cell.value * 0.68f).sp)
                            if (sq in targets) {
                                if (p != 0) Box(Modifier.fillMaxSize(0.92f).border(3.dp, Night.correct, CircleShape))
                                else Box(Modifier.fillMaxSize(0.3f).clip(CircleShape).background(Night.correct.copy(alpha = 0.9f)))
                            }
                        }
                    }
                }
            }
            promo?.let { base ->
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f)).clickable { promo = null; sel = -1 }, contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.clip(Shapes.card).background(Night.surface).clickable(enabled = false) {}.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Promover a", color = Night.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Chess.QUEEN to 'q', Chess.ROOK to 'r', Chess.BISHOP to 'b', Chess.KNIGHT to 'n').forEach { (t, ch) ->
                                Box(
                                    Modifier.size(cell * 1.15f).clip(RoundedCornerShape(12.dp)).background(Night.surfaceHigh)
                                        .clickable { promo = null; sel = -1; onMove(base + ch) }
                                        .semantics { contentDescription = "Promover a ${PIECE_NAMES[t]}" },
                                    contentAlignment = Alignment.Center,
                                ) { ChessPiece(t * state.turn, (cell.value * 0.8f).sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Peças capturadas do adversário (pequenas) e a vantagem de material ("+3"). */
@Composable
private fun CapturedRow(state: Chess.State, side: Int, label: String) {
    val caps = Chess.captured(state.pos.b, -side)
    val adv = Chess.advantage(state.pos.b, side)
    Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Night.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.size(8.dp))
        caps.forEach { ChessPiece(it * -side, 15.sp) }
        if (adv > 0) Text(" +$adv", color = Night.correct, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private const val XADREZ_HELP = "Xadrez com as regras completas: roque, en passant e promoção (você escolhe a peça).\n\n" +
    "Toque numa peça sua para ver os destinos e toque no destino. O rei em xeque fica vermelho e o último lance fica dourado.\n\n" +
    "Xeque-mate vence. É empate no afogamento (sem lances e sem xeque), com material insuficiente (rei contra rei, " +
    "ou rei e bispo/cavalo contra rei), após 50 lances sem captura nem peão e na tripla repetição.\n\n" +
    "Níveis: Fácil, Médio e Difícil. \"Desfazer\" volta o seu último lance e o do computador."

/** Partida salva do solo: nível, sua cor, lances e se o resultado já foi contado. */
private data class ChessSave(val level: Chess.Level, val color: Int, val moves: List<String>, val counted: Boolean) {
    fun encode(): String = JSONObject().put("l", level.key).put("c", color).put("m", moves.joinToString(" ")).put("k", counted).toString()
    companion object {
        fun decode(t: String?): ChessSave? = runCatching {
            val o = JSONObject(t ?: return null)
            ChessSave(
                Chess.Level.entries.first { it.key == o.getString("l") }, if (o.getInt("c") == Chess.BLACK) Chess.BLACK else Chess.WHITE,
                o.optString("m").split(' ').filter { it.isNotBlank() }, o.optBoolean("k"),
            )
        }.getOrNull()
    }
}

/** Xadrez contra o computador (Fácil / Médio / Difícil), com a partida salva ao sair. */
@Composable
fun ChessScreen(
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    /** Para as imagens de teste: partida pronta, peça escolhida e a escolha de promoção aberta. */
    initial: Triple<Chess.Level, Int, List<String>>? = null,
    initialSel: Int = -1,
    initialPromo: String? = null,
    rnd: Random = Random.Default,
) {
    val saved = remember { initial?.let { ChessSave(it.first, it.second, it.third, false) } ?: ChessSave.decode(store?.text("xadrez_save")) }
    var game by remember { mutableStateOf(saved) }
    var setupLevel by remember { mutableStateOf(saved?.level ?: runCatching { Chess.Level.valueOf(store?.text("xadrez_level") ?: "") }.getOrDefault(Chess.Level.FACIL)) }
    var setupColor by remember { mutableIntStateOf(store?.int("xadrez_color") ?: 0) } // 1 brancas, -1 pretas, 0 sorteio
    var showHelp by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }

    fun persist(g: ChessSave?) { game = g; store?.setText("xadrez_save", g?.encode()) }

    val g = game
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Xadrez", onBack, onHelp = { showHelp = true })
            if (g == null) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♞", fontSize = 72.sp, color = Night.text)
                    Spacer(Modifier.height(8.dp))
                    Text("Nova partida contra o computador", color = Night.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(22.dp))
                    Text("Nível", color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Segmented(Chess.Level.entries.map { it.label }, setupLevel.ordinal) { setupLevel = Chess.Level.entries[it] }
                    Spacer(Modifier.height(16.dp))
                    Text("Suas peças", color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    val colors = listOf(Chess.WHITE, Chess.BLACK, 0)
                    Segmented(listOf("Brancas", "Pretas", "Sorteio"), colors.indexOf(setupColor).coerceAtLeast(0)) { setupColor = colors[it] }
                    Spacer(Modifier.height(26.dp))
                    PillButton("Começar", Night.correct, modifier = Modifier.fillMaxWidth()) {
                        store?.setText("xadrez_level", setupLevel.name); store?.setInt("xadrez_color", setupColor)
                        val c = if (setupColor == 0) (if (rnd.nextBoolean()) Chess.WHITE else Chess.BLACK) else setupColor
                        persist(ChessSave(setupLevel, c, emptyList(), false))
                    }
                }
            } else {
                val me = g.color
                val state = remember(g.moves) {
                    val s = Chess.replay(g.moves.filter { it != "resign" })
                    if (g.moves.lastOrNull() == "resign" && !s.over) s.copy(end = Chess.End.RESIGN, winner = -me, resigned = me) else s
                }
                val outcome = Chess.outcome(state, me)
                // Vez do computador: pensa fora da linha principal.
                LaunchedEffect(g.moves, g.level) {
                    if (state.over || state.turn == me) return@LaunchedEffect
                    thinking = true
                    val t0 = System.currentTimeMillis()
                    val m = withContext(Dispatchers.Default) { Chess.bestMove(state.pos, g.level, rnd) }
                    delay(maxOf(0L, 450 - (System.currentTimeMillis() - t0)))
                    thinking = false
                    if (m != null && game === g) { persist(g.copy(moves = g.moves + Chess.uci(m))); feedback?.type() }
                }
                LaunchedEffect(outcome) {
                    val o = outcome ?: return@LaunchedEffect
                    if (!g.counted) {
                        if (o == "me") store?.add("xadrez_won_${g.level.key}")
                        store?.logActivity()
                        persist(g.copy(counted = true))
                        if (o == "me") feedback?.win() else if (o == "opp") feedback?.lose()
                    }
                    delay(900)
                    showEnd = true
                }
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val status = when {
                        outcome == "me" -> "Você venceu!"
                        outcome == "opp" -> if (state.end == Chess.End.RESIGN) "Você desistiu" else "O computador venceu"
                        outcome == "draw" -> "Empate"
                        state.turn == me -> if (state.check) "Xeque! Sua vez" else "Sua vez"
                        else -> "Computador pensando…"
                    }
                    Text(
                        status, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        color = when { outcome == "me" || (outcome == null && state.turn == me) -> if (state.check && outcome == null) Night.red else Night.correct; outcome == "opp" -> Night.red; else -> Night.text },
                    )
                    Text(
                        "Nível ${g.level.label} · você joga de ${if (me == Chess.WHITE) "brancas" else "pretas"}",
                        color = Night.muted, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    CapturedRow(state, -me, "Computador")
                    ChessBoard(
                        state, flipped = me == Chess.BLACK, enabled = outcome == null && state.turn == me && !thinking,
                        onMove = { u -> feedback?.type(); persist(g.copy(moves = g.moves + u)) },
                        initialSel = initialSel, initialPromo = initialPromo,
                    )
                    CapturedRow(state, me, "Você")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val myLast = g.moves.indices.lastOrNull { (if (it % 2 == 0) Chess.WHITE else Chess.BLACK) == me }
                        PillButton("Desfazer", Night.surfaceHigh, Night.text, Modifier.weight(1f), enabled = outcome == null && myLast != null && !thinking) {
                            myLast?.let { persist(g.copy(moves = g.moves.take(it))) }
                        }
                        if (outcome == null) PillButton("Desistir", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { confirm = true }
                        else PillButton("Nova partida", Night.correct, modifier = Modifier.weight(1f)) { showEnd = false; persist(null) }
                    }
                }
                if (confirm && outcome == null) {
                    BottomSheet({ confirm = false }) {
                        SheetTitle("Desistir da partida?", "O computador ganha esta.")
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Continuar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { confirm = false }
                            PillButton("Desistir", Night.red, modifier = Modifier.weight(1f)) {
                                confirm = false
                                // Desistência: guarda como lance especial no fim da lista.
                                persist(g.copy(moves = g.moves + "resign"))
                            }
                        }
                    }
                }
                if (showEnd && outcome != null) {
                    BottomSheet({ showEnd = false }) {
                        SheetTitle(
                            when (outcome) { "me" -> "Você venceu! 🏆"; "opp" -> "O computador venceu"; else -> "Empate" },
                            Chess.endText(state.end) + " · nível ${g.level.label}",
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem("${(state.moves + 1) / 2}", "Lances")
                            StatItem("${store?.int("xadrez_won_${g.level.key}") ?: 0}", "Vitórias no ${g.level.label}")
                        }
                        Spacer(Modifier.height(20.dp))
                        PillButton("Nova partida", Night.correct, modifier = Modifier.fillMaxWidth()) { showEnd = false; persist(null) }
                    }
                }
            }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Xadrez")
                Spacer(Modifier.height(12.dp))
                Text(XADREZ_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.surface).padding(4.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(Shapes.pill).background(if (on) Night.accent else Color.Transparent)
                    .clickable { onPick(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (on) CInk else Night.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        }
    }
}

// ======================================================================
// Xadrez com amigo (modo "k")
// ======================================================================

/** Xadrez com amigo. [msgs] = mensagens da rodada na ordem do tópico. */
@Composable
internal fun ChessPlay(msgs: List<Msg>, d: Duel, initialSel: Int = -1) {
    val now = rememberNow(d.clock, d.round, 200)
    val hostWhite = Chess.hostIsWhite(d.n)
    val opp = d.oppId ?: "?"
    val whiteId = if (hostWhite == d.isHost) d.myId else opp
    val blackId = if (whiteId == d.myId) opp else d.myId
    val mine = if (whiteId == d.myId) Chess.WHITE else Chess.BLACK
    val state = remember(msgs, whiteId, blackId) { Chess.state(msgs, whiteId, blackId) }
    val counting = now < d.localStart
    var sentAt by remember(d.round) { mutableIntStateOf(-1) }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    var confirm by remember(d.round) { mutableStateOf(false) }
    val myTurn = !state.over && state.turn == mine
    val canPlay = myTurn && sentAt != state.moves
    val outcome = Chess.outcome(state, mine)
    fun colorName(side: Int) = if (side == Chess.WHITE) "brancas" else "pretas"

    LaunchedEffect(myTurn, state.moves) { if (myTurn && state.moves > 0) d.feedback?.turn() }
    LaunchedEffect(outcome, d.round) {
        val o = outcome ?: return@LaunchedEffect
        d.onDecided(o)
        if (o == "me") d.feedback?.win() else if (o == "opp") d.feedback?.lose()
        delay(1300)
        showEnd = true
    }
    val live by rememberUpdatedState(state)

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); SeriesPill(d.series) })
            OppBar(
                d.oppName,
                when { !d.online -> "Reconectando…"; d.oppLeft -> "saiu"; else -> "joga de ${colorName(-mine)}" },
                false,
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val status = when {
                    outcome == "me" -> if (state.resigned == -mine) "${d.oppName} desistiu. Você venceu!" else "Xeque-mate! Você venceu!"
                    outcome == "opp" -> if (state.resigned == mine) "Você desistiu" else "Xeque-mate. ${d.oppName} venceu"
                    outcome == "draw" -> "Empate"
                    state.turn == mine -> if (state.check) "Xeque! Sua vez (${colorName(mine)})" else "Sua vez (${colorName(mine)})"
                    else -> "Vez de ${d.oppName} (${colorName(-mine)})"
                }
                Text(
                    status, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1,
                    color = when {
                        outcome == "me" -> Night.correct
                        outcome == null && state.turn == mine -> if (state.check) Night.red else Night.correct
                        outcome == "opp" -> Night.red
                        else -> Night.text
                    },
                )
                if (outcome == "draw") Text(Chess.endText(state.end), color = Night.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                CapturedRow(state, -mine, d.oppName)
                ChessBoard(
                    state, flipped = mine == Chess.BLACK, enabled = canPlay && !counting, initialSel = initialSel,
                    onMove = { u ->
                        val st = live
                        sentAt = st.moves
                        d.feedback?.type()
                        d.send(Msg.UMove(d.myId, st.moves, u))
                    },
                )
                CapturedRow(state, mine, "Você")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Você: ${colorName(mine)}", color = Night.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
            val lances = (state.moves + 1) / 2
            val detail = "${Chess.endText(state.end)} em $lances ${if (lances == 1) "lance" else "lances"}"
            val sub = when {
                state.resigned == -mine -> "${d.oppName} desistiu"
                state.resigned == mine -> "Você desistiu"
                else -> detail
            }
            SeriesEndSheet(
                mode = Multi.CHESS, outcome = outcome, oppName = d.oppName, subtitle = sub,
                myLine = colorName(mine) + if (outcome == "me") " · venceu" else "",
                oppLine = colorName(-mine) + if (outcome == "opp") " · venceu" else "",
                isHost = d.isHost, series = d.series, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                story = MatchStory.series(Multi.CHESS, d.myName, d.oppName, d.series, outcome, detail),
                shareDetail = detail, myName = d.myName, next = d.next,
            )
        }
    }
}

/** Ícone do Xadrez (tela inicial): cavalo sobre um quadradinho do tabuleiro. */
@Composable
internal fun ChessHomeIcon(size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(ChessDark), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxSize()) {
            repeat(2) { c ->
                Column(Modifier.weight(1f).fillMaxSize()) {
                    repeat(2) { r -> Box(Modifier.weight(1f).fillMaxWidth().background(if ((r + c) % 2 == 0) ChessLight else ChessDark)) }
                }
            }
        }
        ChessPiece(Chess.KNIGHT, (size.value * 0.62f).sp)
    }
}
