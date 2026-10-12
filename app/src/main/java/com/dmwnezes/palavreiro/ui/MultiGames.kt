package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.game.Anagram
import com.dmwnezes.palavreiro.game.DefineDuel
import com.dmwnezes.palavreiro.game.Definition
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.PointRounds
import com.dmwnezes.palavreiro.game.Timed
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay

internal val InkDark = Color(0xFF14102C)

/** Relógio que anda sozinho enquanto a tela está aberta (atualiza a cada [step] ms). */
@Composable
internal fun rememberNow(clock: () -> Long, key: Any, step: Long = 50): Long {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(key) { while (true) { now = clock(); delay(step) } }
    return now
}

/** Cinco quadradinhos com a palavra que está sendo digitada. */
@Composable
private fun TypingRow(typed: String, enabled: Boolean, shake: Float) {
    Row(
        Modifier.fillMaxWidth().graphicsLayer { translationX = shake * density }.alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        for (i in 0 until 5) {
            val ch = typed.getOrNull(i)
            LetterBox(ch, 46.dp, bg = if (ch != null) Night.surfaceHigh else Color.Transparent, border = if (ch != null) Night.accent else Night.outline)
        }
    }
}

@Composable
internal fun rememberShake(tick: Int): Float {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        for (x in listOf(-10f, 10f, -7f, 7f, -3f, 0f)) shake.animateTo(x, tween(45))
    }
    return shake.value
}

internal fun share(context: android.content.Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar"))
}

private fun points(n: Int) = if (n == 1) "1 ponto" else "$n pontos"

/**
 * O que toda tela de jogo com amigo recebe da sala.
 * [round] = contador local de rodadas (chave das telas); [n] = RODADA da sala (0, 1, 2… contando start/again).
 */
class Duel(
    val myId: String,
    val oppId: String?,
    val oppName: String,
    val isHost: Boolean,
    val round: Int,
    val n: Int,
    val localStart: Long,
    val series: Multi.Series,
    val online: Boolean,
    val oppLeft: Boolean,
    val send: (Msg) -> Unit,
    /** Recebe "me"/"opp"/"draw" uma vez quando a rodada (ou a partida) acaba. */
    val onDecided: (String) -> Unit,
    val onAgain: () -> Unit,
    val onLeave: () -> Unit,
    val myName: String? = null,
    val next: NextGame? = null,
    val reactions: ReactionState? = null,
    val feedback: Feedback? = null,
    val clock: () -> Long = System::currentTimeMillis,
)

/** Faixa do amigo: nome e situação (pontos, "acertou!" piscando, "saiu"). */
@Composable
internal fun OppBar(oppName: String, status: String, flash: Boolean) {
    val blink = rememberInfiniteTransition(label = "pisca")
    val a by blink.animateFloat(0.35f, 1f, infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(Shapes.card).background(Night.surface)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(oppName, color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            status, color = if (flash) Night.present else Night.muted, fontSize = 14.sp, fontWeight = if (flash) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.alpha(if (flash) a else 1f),
        )
    }
}

/** Barra do tempo que falta ([left] de 0 a 1). */
@Composable
internal fun TimeBar(left: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.pill).background(Night.surfaceHigh)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(left).clip(Shapes.pill).background(if (left < 0.22f) Night.red else Night.accent))
    }
}

// ======================================================================
// Anagrama
// ======================================================================

/** Placar de pontos ao lado do título: "3 × 2" / "rodada 4/10". */
@Composable
internal fun AnagramPill(me: Int, opp: Int, round: Int, total: Int = Anagram.ROUNDS) {
    Column(
        Modifier.padding(end = 10.dp).clip(Shapes.pill).background(Night.surfaceHigh).padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$me × $opp", color = Night.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text("rodada $round/$total", color = Night.muted, fontSize = 9.sp, lineHeight = 10.sp)
    }
}

/** Partida de Anagrama (N rodadas). [onDecided] recebe "me"/"opp"/"draw" uma vez no fim. */
@Composable
internal fun AnagramPlay(
    words: Words,
    feedback: Feedback?,
    seed: Long,
    round: Int,
    myId: String,
    oppId: String?,
    oppName: String,
    isHost: Boolean,
    msgs: List<Timed>,
    localStart: Long,
    online: Boolean,
    oppLeft: Boolean,
    send: (Msg) -> Unit,
    onDecided: (String) -> Unit,
    onAgain: () -> Unit,
    onLeave: () -> Unit,
    /** Meu nome (cartão dos Stories), escolha do próximo jogo (anfitrião) e as reações. */
    myName: String? = null,
    next: NextGame? = null,
    reactions: ReactionState? = null,
    clock: () -> Long = System::currentTimeMillis,
    rounds: Int = Anagram.ROUNDS,
) {
    val now = rememberNow(clock, round, 100)
    val answers = remember(seed, rounds) { Anagram.words(words.answers, seed, rounds) }
    val state = Anagram.state(msgs, setOfNotNull(myId, oppId), rounds)
    val cur = state.current
    val over = state.over
    val start = if (over) Long.MAX_VALUE else state.startOf(cur, localStart)
    val counting = now < localStart
    // Entre rodadas: mostra a resposta da rodada que acabou de ser decidida.
    val inGap = cur > 0 && (over || now < start)
    val shownRound = if (inGap) cur - 1 else cur
    val elapsed = if (over) 0L else now - start
    val toast = rememberToast()
    var typed by remember(round, cur) { mutableStateOf("") }
    var solvedSent by remember(round) { mutableIntStateOf(-1) }
    var shakeTick by remember { mutableIntStateOf(0) }
    val shake = rememberShake(shakeTick)
    var showEnd by remember(round) { mutableStateOf(false) }
    val canType = !counting && !inGap && !over && solvedSent != cur
    val oppGone by rememberUpdatedState(oppLeft)

    // Tempo esgotado: o anfitrião manda o "skip" (o convidado só se o anfitrião saiu, ou depois de 50 s).
    LaunchedEffect(round, cur, over) {
        if (over) return@LaunchedEffect
        val s = state.startOf(cur, localStart)
        while (true) {
            val e = clock() - s
            if ((isHost && e >= Anagram.ROUND_MS) || (!isHost && (e >= Anagram.GUEST_SKIP_MS || (oppGone && e >= Anagram.ROUND_MS)))) {
                send(Msg.Skip(cur)); break
            }
            delay(200)
        }
    }
    LaunchedEffect(cur, round) { roundFeedback(state, cur, myId, feedback) }
    // Fim: conta o resultado uma vez e abre a folha depois de mostrar a última resposta.
    LaunchedEffect(over, round) {
        if (!over) return@LaunchedEffect
        onDecided(state.outcome(myId, oppId) ?: "draw")
        delay(Anagram.GAP_MS)
        showEnd = true
    }

    fun enter() {
        if (!canType || typed.length < 5) return
        if (Anagram.solves(typed, answers[cur], words::isAccepted)) {
            solvedSent = cur
            send(Msg.Solve(myId, cur, elapsed))
        } else {
            toast.show("Não é essa"); shakeTick++; feedback?.invalid(); typed = ""
        }
    }

    val myPts = state.points(myId)
    val oppPts = state.points(oppId)
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × $oppName", onLeave, trailing = { ReactAnchor(reactions); AnagramPill(myPts, oppPts, (shownRound + 1).coerceAtMost(rounds), rounds) })
            val lastOpp = inGap && state.decisions[cur - 1].winner == oppId && oppId != null
            OppBar(oppName, when { !online -> "Reconectando…"; lastOpp -> "acertou!"; oppLeft -> "saiu"; else -> points(oppPts) }, lastOpp)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val word = answers[shownRound.coerceIn(0, rounds - 1)]
                val scrambled = remember(seed, shownRound) { Anagram.shuffle(word, seed, shownRound) }
                var order by remember(seed, shownRound, round) { mutableStateOf(listOf(0, 1, 2, 3, 4)) }
                val letters = if (inGap) word else order.map { scrambled[it] }.joinToString("")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Quadrados neutros: só a palavra embaralhada (a resposta aparece verde entre as rodadas).
                    letters.forEach { ch ->
                        LetterBox(ch, 56.dp, bg = if (inGap) Night.correct else Night.surfaceHigh, border = if (inGap) null else Night.outline.copy(alpha = 0.7f), textColor = Night.text)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (inGap) {
                    val d = state.decisions[cur - 1]
                    RoundResult(d.winner, myId, oppName, words.display(word))
                    if (over && !showEnd) {
                        Spacer(Modifier.height(12.dp))
                        PillButton("Ver resultado", Night.correct) { showEnd = true }
                    }
                } else {
                    Text(
                        "🔀 Embaralhar", color = Night.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(Shapes.pill).background(Night.surfaceHigh).clickable {
                            var nx = order
                            while (nx.map { scrambled[it] } == order.map { scrambled[it] } && scrambled.toSet().size > 1) nx = order.shuffled()
                            order = nx
                        }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                TimeBar(if (inGap || counting) 1f else (1f - elapsed.toFloat() / Anagram.ROUND_MS).coerceIn(0f, 1f))
            }
            TypingRow(typed, canType, shake)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.alpha(if (canType) 1f else 0.4f)) {
                LetterKeyboard(
                    onLetter = { if (canType && typed.length < 5) { typed += it; feedback?.type() } },
                    onEnter = ::enter,
                    onDelete = { if (canType) typed = typed.dropLast(1) },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        ToastView(toast, Modifier.align(Alignment.TopCenter).padding(top = 110.dp))
        if (over && showEnd) {
            PointsEndSheet(
                mode = Multi.ANAGRAM, oppName = oppName, me = myPts, opp = oppPts,
                rows = answers.mapIndexed { i, w -> words.display(w) to state.decisions[i].winner.let { if (it == null) null else it == myId } },
                isHost = isHost, onAgain = onAgain, onLeave = onLeave, onClose = { showEnd = false },
                myName = myName, next = next,
            )
        }
    }
}

private fun roundFeedback(state: PointRounds.State, cur: Int, myId: String, feedback: Feedback?) {
    if (cur == 0) return
    when (state.decisions[cur - 1].winner) {
        myId -> feedback?.win()
        null -> {}
        else -> feedback?.lose()
    }
}

/** "Você acertou! CASA" / "Ana acertou: CASA" / "Ninguém acertou: CASA". */
@Composable
private fun RoundResult(winner: String?, myId: String, oppName: String, word: String) {
    Text(
        when (winner) { myId -> "Você acertou! $word"; null -> "Ninguém acertou: $word"; else -> "$oppName acertou: $word" },
        color = if (winner == myId) Night.correct else Night.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
    )
}

/**
 * Fim dos modos de pontos (Anagrama, Qual é a Palavra?).
 * [rows] = (palavra, quem acertou: true = você, false = o amigo, null = ninguém).
 */
@Composable
internal fun PointsEndSheet(
    mode: Char, oppName: String, me: Int, opp: Int, rows: List<Pair<String, Boolean?>>, isHost: Boolean,
    onAgain: () -> Unit, onLeave: () -> Unit, onClose: () -> Unit,
    myName: String? = null, next: NextGame? = null,
    /** Resultado quando não sai só dos pontos (Trivia: empate nos acertos decidido pelo tempo). */
    outcome: String? = null,
    /** Linha embaixo do placar (ex.: o desempate). */
    note: String? = null,
    /** Tabela própria do modo no lugar da lista de palavras. */
    table: (@Composable () -> Unit)? = null,
    /** Detalhe do cartão dos Stories ("Pontos 230 × 180"). */
    storyDetail: String? = null,
) {
    val context = LocalContext.current
    val res = outcome ?: when { me > opp -> "me"; opp > me -> "opp"; else -> "draw" }
    BottomSheet(onClose) {
        SuggestBanner(isHost, next)
        val title = when (res) { "me" -> "Você venceu! 🏆"; "opp" -> "$oppName venceu"; else -> "Empate" }
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
        Text("$me × $opp", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = if (res == "me") Color(0xFFF5C84C) else Night.accent)
        if (note != null) Text(note, fontSize = 13.sp, color = Night.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        if (table != null) table() else Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surfaceHigh).padding(horizontal = 16.dp, vertical = 8.dp)) {
            rows.forEachIndexed { i, (w, who) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = Night.muted, fontSize = 13.sp, modifier = Modifier.size(width = 26.dp, height = 18.dp))
                    Text(w, color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (who) { true -> "✓ você"; false -> oppName; null -> "—" },
                        color = when (who) { true -> Night.correct; false -> Night.accent; null -> Night.muted },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End, modifier = Modifier.weight(0.8f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        EndButtons(
            isHost = isHost, next = next, sameLabel = "Revanche", seriesOver = false,
            onAgain = onAgain, onLeave = onLeave,
            onShare = {
                val line = when (res) { "me" -> "Venci por $me × $opp"; "opp" -> "Perdi por $me × $opp"; else -> "Empate $me × $opp" }
                share(context, "Palavreiro · ${Multi.modeName(mode)} com $oppName\n$line ${Multi.modeEmoji(mode)}")
            },
            onStory = { StoryCard.shareMatch(context, MatchStory.points(mode, myName, oppName, me, opp, rows.size, storyDetail, outcome)) },
        )
    }
}

/** Fim de rodada dos modos com série (Caça-Palavras, Velha, Damas): troféu ou placar e as duas linhas. */
@Composable
internal fun SeriesEndSheet(
    mode: Char, outcome: String, oppName: String, subtitle: String, myLine: String, oppLine: String,
    isHost: Boolean, series: Multi.Series, onAgain: () -> Unit, onLeave: () -> Unit, onClose: () -> Unit,
    story: MatchStory, shareDetail: String, myName: String? = null, next: NextGame? = null,
) {
    val context = LocalContext.current
    BottomSheet(onClose) {
        SuggestBanner(isHost, next)
        val champ = series.winner
        val best = series.label.ifEmpty { "1 partida" }.replaceFirstChar { it.uppercase() }
        if (champ != null && series.best > 1) {
            Trophy(if (champ == "me") "Você levou a série!" else "$oppName levou a série", "$best · ${series.score}", champ == "me")
            Spacer(Modifier.height(10.dp))
            Text(
                when (outcome) { "me" -> "Você venceu a última rodada. "; "opp" -> "$oppName venceu a última rodada. "; else -> "" } + subtitle,
                color = Night.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SheetTitle(
                when (outcome) {
                    "me" -> if (series.best > 1) "Você venceu a rodada!" else "Você venceu! 🏆"
                    "opp" -> if (series.best > 1) "$oppName venceu a rodada" else "$oppName venceu"
                    else -> if (mode == Multi.TICTAC) "Deu velha!" else "Empate"
                },
                subtitle,
            )
            if (series.best > 1) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Série: Você ${series.score} $oppName · ${series.label}",
                    color = Night.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ResultLine("Você", myLine, outcome == "me")
        ResultLine(oppName, oppLine, outcome == "opp")
        Spacer(Modifier.height(18.dp))
        EndButtons(
            isHost = isHost, next = next, sameLabel = if (series.over) "Nova série" else "Próxima rodada", seriesOver = series.over,
            onAgain = onAgain, onLeave = onLeave,
            onShare = {
                val res = when {
                    series.best > 1 && champ == "me" -> "Levei a série por ${series.score}"
                    series.best > 1 && champ == "opp" -> "Perdi a série por ${series.opp} × ${series.me}"
                    outcome == "me" -> "Venci" + if (series.best > 1) " a rodada (série ${series.score})" else ""
                    outcome == "opp" -> "Perdi" + if (series.best > 1) " a rodada (série ${series.score})" else ""
                    else -> "Empate"
                }
                share(context, "Palavreiro · ${Multi.modeName(mode)} com $oppName\n$res · $shareDetail ${Multi.modeEmoji(mode)}")
            },
            onStory = { StoryCard.shareMatch(context, story) },
        )
    }
}

// ======================================================================
// Qual é a Palavra?
// ======================================================================

/** Quadradinhos da resposta: um por letra; hífen/espaço fixos; dicas em amarelo; resposta inteira entre as rodadas. */
@Composable
private fun DefineSlots(word: String, typed: List<Char>, hints: Set<Int>, reveal: Boolean, right: Boolean, shake: Float) {
    val target = Words.normalize(word)
    val shown = word.uppercase()
    BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer { translationX = shake * density }, contentAlignment = Alignment.Center) {
        val n = target.length
        val gap = 4.dp
        val size = minOf((maxWidth - gap * (n - 1)) / n, 44.dp)
        val open = target.indices.filter { target[it] in 'A'..'Z' && it !in hints }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until n) {
                if (target[i] !in 'A'..'Z') {
                    Text(target[i].toString().trim().ifEmpty { "·" }, color = Night.muted, fontSize = 22.sp, modifier = Modifier.width(size / 2), textAlign = TextAlign.Center)
                    continue
                }
                val hint = i in hints
                val ch = when {
                    reveal -> shown.getOrNull(i) ?: target[i]
                    hint -> target[i]
                    else -> typed.getOrNull(open.indexOf(i))
                }
                val bg = when { reveal && right -> Night.correct; reveal -> Night.surfaceHigh; hint -> Night.present; else -> Night.surfaceHigh }
                LetterBox(
                    ch, size, bg = bg, border = if (bg == Night.surfaceHigh && !reveal) Night.outline else null,
                    textColor = if (hint && !reveal) Night.onMark(Mark.PRESENT) else Night.text,
                )
            }
        }
    }
}

/** Partida de Qual é a Palavra? com amigo (N rodadas de 60 s). */
@Composable
internal fun DefinePlay(defs: List<Definition>, seed: Long, rounds: Int, msgs: List<Timed>, d: Duel) {
    val now = rememberNow(d.clock, d.round, 100)
    val items = remember(seed, rounds, defs) { DefineDuel.pick(defs, seed, rounds) }
    val total = items.size.coerceAtLeast(1)
    val state = DefineDuel.state(msgs, setOfNotNull(d.myId, d.oppId), total)
    val cur = state.current
    val over = state.over
    val start = if (over) Long.MAX_VALUE else state.startOf(cur, d.localStart)
    val counting = now < d.localStart
    val inGap = cur > 0 && (over || now < start)
    val shownRound = (if (inGap) cur - 1 else cur).coerceIn(0, total - 1)
    val elapsed = if (over || inGap || counting) 0L else now - start
    val toast = rememberToast()
    val typed = remember(d.round, cur) { androidx.compose.runtime.mutableStateListOf<Char>() }
    var solvedSent by remember(d.round) { mutableIntStateOf(-1) }
    var shakeTick by remember { mutableIntStateOf(0) }
    val shake = rememberShake(shakeTick)
    var showEnd by remember(d.round) { mutableStateOf(false) }
    val canType = !counting && !inGap && !over && solvedSent != cur && items.isNotEmpty()
    val oppGone by rememberUpdatedState(d.oppLeft)
    val item = items.getOrNull(shownRound)
    val hints = if (item == null || inGap) emptySet() else DefineDuel.hints(item.word, elapsed)
    val target = item?.let { Words.normalize(it.word) }.orEmpty()
    val open = target.indices.filter { target[it] in 'A'..'Z' && it !in hints }
    // Dica nova ocupou uma casa: o que passou do tamanho não conta.
    fun trim() { while (typed.size > open.size) typed.removeAt(typed.lastIndex) }

    LaunchedEffect(d.round, cur, over) {
        if (over) return@LaunchedEffect
        val s = state.startOf(cur, d.localStart)
        while (true) {
            val e = d.clock() - s
            if ((d.isHost && e >= DefineDuel.ROUND_MS) || (!d.isHost && (e >= DefineDuel.GUEST_SKIP_MS || (oppGone && e >= DefineDuel.ROUND_MS)))) {
                d.send(Msg.Skip(cur)); break
            }
            delay(200)
        }
    }
    LaunchedEffect(cur, d.round) { roundFeedback(state, cur, d.myId, d.feedback) }
    LaunchedEffect(over, d.round) {
        if (!over) return@LaunchedEffect
        d.onDecided(state.outcome(d.myId, d.oppId) ?: "draw")
        delay(DefineDuel.GAP_MS)
        showEnd = true
    }

    fun enter() {
        if (!canType || item == null) return
        trim()
        if (typed.size < open.size) { toast.show("Complete a palavra"); return }
        val guess = CharArray(target.length) { target[it] }
        open.forEachIndexed { k, pos -> guess[pos] = typed[k] }
        if (DefineDuel.solves(String(guess), item.word)) {
            solvedSent = cur
            d.send(Msg.Solve(d.myId, cur, elapsed))
        } else {
            toast.show("Não é essa"); shakeTick++; d.feedback?.invalid(); typed.clear()
        }
    }

    val myPts = state.points(d.myId)
    val oppPts = state.points(d.oppId)
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); AnagramPill(myPts, oppPts, (shownRound + 1).coerceAtMost(total), total) })
            val lastOpp = inGap && state.decisions[cur - 1].winner == d.oppId && d.oppId != null
            OppBar(d.oppName, when { !d.online -> "Reconectando…"; lastOpp -> "acertou!"; d.oppLeft -> "saiu"; else -> points(oppPts) }, lastOpp)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("Rodada ${shownRound + 1}/$total", color = Night.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    item?.text ?: "…", color = Night.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 27.sp,
                    modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(horizontal = 18.dp, vertical = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
                if (item != null) {
                    val dec = if (inGap) state.decisions[cur - 1] else null
                    DefineSlots(item.word, typed.take(open.size), hints, reveal = inGap, right = dec?.winner != null, shake = shake)
                    Spacer(Modifier.height(8.dp))
                    if (dec != null) {
                        RoundResult(dec.winner, d.myId, d.oppName, item.word.uppercase())
                        if (over && !showEnd) {
                            Spacer(Modifier.height(12.dp))
                            PillButton("Ver resultado", Night.correct) { showEnd = true }
                        }
                    } else {
                        val letters = target.count { it in 'A'..'Z' }
                        Text(
                            "$letters letras" + when {
                                elapsed >= DefineDuel.HINT_LAST_MS -> " · dica: 1ª e última letra"
                                elapsed >= DefineDuel.HINT_FIRST_MS -> " · dica: 1ª letra"
                                else -> " · dica aos 20 s"
                            },
                            color = Night.muted, fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                TimeBar(if (inGap || counting) 1f else (1f - elapsed.toFloat() / DefineDuel.ROUND_MS).coerceIn(0f, 1f))
            }
            Box(Modifier.alpha(if (canType) 1f else 0.4f)) {
                LetterKeyboard(
                    enterLabel = "ENVIAR",
                    onLetter = { trim(); if (canType && typed.size < open.size) { typed += it; d.feedback?.type() } },
                    onEnter = ::enter,
                    onDelete = { trim(); if (canType && typed.isNotEmpty()) typed.removeAt(typed.lastIndex) },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        ToastView(toast, Modifier.align(Alignment.TopCenter).padding(top = 110.dp))
        if (over && showEnd) {
            PointsEndSheet(
                mode = Multi.DEFINE, oppName = d.oppName, me = myPts, opp = oppPts,
                rows = items.mapIndexed { i, it -> it.word.uppercase() to state.decisions.getOrNull(i)?.winner.let { w -> if (w == null) null else w == d.myId } },
                isHost = d.isHost, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                myName = d.myName, next = d.next,
            )
        }
    }
}
