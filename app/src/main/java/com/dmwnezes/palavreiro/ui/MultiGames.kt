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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
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
import com.dmwnezes.palavreiro.game.Bomb
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Timed
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val InkDark = Color(0xFF14102C)

/** Relógio que anda sozinho enquanto a tela está aberta (atualiza a cada [step] ms). */
@Composable
private fun rememberNow(clock: () -> Long, key: Any, step: Long = 50): Long {
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
private fun rememberShake(tick: Int): Float {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        for (x in listOf(-10f, 10f, -7f, 7f, -3f, 0f)) shake.animateTo(x, tween(45))
    }
    return shake.value
}

private fun share(context: android.content.Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar"))
}

private fun points(n: Int) = if (n == 1) "1 ponto" else "$n pontos"

// ======================================================================
// Bomba-Relógio
// ======================================================================

/**
 * Rodada da Bomba-Relógio. [msgs] = mensagens desta rodada (zeradas a cada start/again), na ordem do tópico.
 * [onDecided] recebe "me"/"opp" uma vez quando a bomba explode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BombPlay(
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
    series: Multi.Series,
    online: Boolean,
    oppLeft: Boolean,
    send: (Msg) -> Unit,
    onDecided: (String) -> Unit,
    onAgain: () -> Unit,
    onLeave: () -> Unit,
    clock: () -> Long = System::currentTimeMillis,
) {
    val now = rememberNow(clock, round)
    val fuse = remember(seed) { Bomb.fuse(seed) }
    val opp = oppId ?: "?"
    val hostId = if (isHost) myId else opp
    val guestId = if (isHost) opp else myId
    val state = Bomb.state(msgs.map { it.msg }, seed, hostId, guestId)
    val elapsed = now - localStart
    val counting = elapsed < 0
    val exploded = elapsed >= fuse || state.loser != null
    val toast = rememberToast()
    var typed by remember(round) { mutableStateOf("") }
    // Palavra mandada e ainda não confirmada pelo tópico (n, palavra).
    var sentWord by remember(round) { mutableStateOf<Pair<Int, String>?>(null) }
    val pending = sentWord?.takeIf { state.count <= it.first && state.loser == null }
    var sentBoom by remember(round) { mutableStateOf(false) }
    var showEnd by remember(round) { mutableStateOf(false) }
    var shakeTick by remember { mutableIntStateOf(0) }
    val shake = rememberShake(shakeTick)
    val myTurn = state.turn == myId && !exploded && !counting
    val canType = myTurn && pending == null

    // A bomba explodiu no meu relógio: se estou com ela, aviso na hora; senão espero 4 s pelo aviso do outro.
    val timeUp = elapsed >= fuse
    LaunchedEffect(timeUp, state.turn, state.loser, round) {
        if (!timeUp || state.loser != null || sentBoom) return@LaunchedEffect
        if (state.turn == myId) { sentBoom = true; send(Msg.Boom(myId)) }
        else {
            delay(Bomb.BOOM_WAIT_MS)
            if (!sentBoom) { sentBoom = true; send(Msg.Boom(state.turn)) }
        }
    }
    // Toque curto quando passa a ser a sua vez.
    LaunchedEffect(myTurn) { if (myTurn) feedback?.turn() }
    // Explodiu: vibração longa, conta o resultado e abre a folha.
    LaunchedEffect(state.loser, round) {
        val l = state.loser ?: return@LaunchedEffect
        feedback?.boom()
        onDecided(if (l == myId) "opp" else "me")
        delay(1800)
        showEnd = true
    }

    fun enter() {
        if (!canType) return
        val w = typed
        val err = Bomb.check(w, state, words::isAccepted)
        if (err != null) { toast.show(err); shakeTick++; feedback?.invalid(); return }
        sentWord = state.count to w
        typed = ""
        send(Msg.Word(myId, w, state.count, elapsed))
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × $oppName", onLeave, trailing = { SeriesPill(series) })
            if (!online || oppLeft) {
                Text(
                    if (!online) "Reconectando…" else "$oppName saiu", color = Night.muted, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            // Centro: a bomba pulsando (mais rápido perto do fim) ou a explosão.
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                if (exploded) {
                    val boomScale = remember(round) { Animatable(0.4f) }
                    LaunchedEffect(round) { boomScale.animateTo(1f, tween(380)) }
                    Text("💥", fontSize = 104.sp, modifier = Modifier.graphicsLayer { scaleX = boomScale.value; scaleY = boomScale.value })
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (state.loser) { null -> "BUM!"; myId -> "A bomba explodiu com você!"; else -> "A bomba explodiu com $oppName!" },
                        color = if (state.loser == myId) Night.red else Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    if (state.loser != null && !showEnd) {
                        Spacer(Modifier.height(14.dp))
                        PillButton("Ver resultado", Night.correct) { showEnd = true }
                    }
                } else {
                    // Fase do pulso acumulada: o período encolhe de ~1,2 s para ~0,25 s.
                    val e = elapsed.coerceAtLeast(0)
                    val p = (e.toDouble() / fuse).coerceIn(0.0, 1.0)
                    // Integral de 1/período(t): período = 1200 - 950p (ms).
                    val phase = if (counting) 0.0 else (fuse / 950.0) * kotlin.math.ln(1200.0 / (1200.0 - 950.0 * p))
                    val beat = ((sin(2 * PI * phase) + 1) / 2).toFloat()
                    val scale = 1f + 0.12f * beat
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(170.dp).drawBehind {
                            drawCircle(Night.red, radius = size.minDimension / 2 * (0.72f + 0.28f * beat), alpha = 0.10f + 0.18f * beat)
                        })
                        Text("💣", fontSize = 96.sp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = -8f + 6f * beat })
                    }
                    Spacer(Modifier.height(10.dp))
                    if (state.turn == myId) {
                        Text("Sua vez!", color = Night.correct, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Text("Vez de $oppName…", color = Night.muted, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
            // Palavras da rodada, mais nova em cima.
            val shown = buildList {
                pending?.let { add(Triple(it.second, true, true)) }
                state.plays.asReversed().forEach { add(Triple(it.word, it.id == myId, false)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Palavras", color = Night.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (shown.size == 1) "1 palavra" else "${shown.size} palavras", color = Night.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 34.dp, max = 92.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.forEach { (w, mine, wait) ->
                    val c = if (mine) Night.correct else Night.accent
                    Text(
                        w, color = InkDark, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.alpha(if (wait) 0.6f else 1f).clip(Shapes.pill).background(c).padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TypingRow(if (pending != null) pending.second else typed, canType, shake)
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
        if (showEnd && state.loser != null) {
            BombEndSheet(
                outcome = if (state.loser == myId) "opp" else "me", oppName = oppName, count = state.count,
                isHost = isHost, series = series, onAgain = onAgain, onLeave = onLeave, onClose = { showEnd = false },
            )
        }
    }
}

/** Fim da rodada da Bomba-Relógio (mesma folha da série do Termo, sem replay). */
@Composable
internal fun BombEndSheet(
    outcome: String, oppName: String, count: Int, isHost: Boolean, series: Multi.Series,
    onAgain: () -> Unit, onLeave: () -> Unit, onClose: () -> Unit,
) {
    val context = LocalContext.current
    BottomSheet(onClose) {
        val words = if (count == 1) "1 palavra na rodada" else "$count palavras na rodada"
        val champ = series.winner
        if (champ != null) {
            Trophy(if (champ == "me") "Você levou a série!" else "$oppName levou a série", "Melhor de 3 · ${series.score}", champ == "me")
            Spacer(Modifier.height(10.dp))
            Text(
                (if (outcome == "me") "A bomba explodiu com $oppName. " else "A bomba explodiu com você. ") + words,
                color = Night.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SheetTitle(if (outcome == "me") "Você venceu a rodada!" else "$oppName venceu a rodada", words)
            Spacer(Modifier.height(10.dp))
            Text(
                "Série: Você ${series.score} $oppName · melhor de 3",
                color = Night.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        ResultLine("Você", if (outcome == "opp") "💥 com a bomba" else "escapou", outcome == "me")
        ResultLine(oppName, if (outcome == "me") "💥 com a bomba" else "escapou", outcome == "opp")
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isHost) PillButton(if (champ != null) "Nova série" else "Próxima rodada", Night.correct, modifier = Modifier.weight(1f), onClick = onAgain)
            else PillButton("Esperando o anfitrião", Night.correct, modifier = Modifier.weight(1f), enabled = false) {}
            PillButton("Sair", Night.surfaceHigh, Night.text, Modifier.weight(0.6f), onClick = onLeave)
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Compartilhar", Night.accent, modifier = Modifier.fillMaxWidth()) {
            val res = when {
                champ == "me" -> "Levei a série por ${series.score}"
                champ == "opp" -> "Perdi a série por ${series.opp} × ${series.me}"
                outcome == "me" -> "Venci a rodada (série ${series.score})"
                else -> "Perdi a rodada (série ${series.score})"
            }
            share(context, "Palavreiro · Bomba-Relógio com $oppName\n$res 💣")
        }
    }
}

// ======================================================================
// Anagrama
// ======================================================================

/** Placar de pontos do Anagrama ao lado do título: "3 × 2" / "rodada 4/10". */
@Composable
internal fun AnagramPill(me: Int, opp: Int, round: Int) {
    Column(
        Modifier.padding(end = 10.dp).clip(Shapes.pill).background(Night.surfaceHigh).padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$me × $opp", color = Night.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text("rodada $round/${Anagram.ROUNDS}", color = Night.muted, fontSize = 9.sp, lineHeight = 10.sp)
    }
}

/** Partida de Anagrama (10 rodadas). [onDecided] recebe "me"/"opp"/"draw" uma vez no fim. */
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
    clock: () -> Long = System::currentTimeMillis,
) {
    val now = rememberNow(clock, round, 100)
    val answers = remember(seed) { Anagram.words(words.answers, seed) }
    val state = Anagram.state(msgs, setOfNotNull(myId, oppId))
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
    // Rodada decidida: avisos.
    LaunchedEffect(cur, round) {
        if (cur == 0) return@LaunchedEffect
        when (state.decisions[cur - 1].winner) {
            myId -> feedback?.win()
            null -> {}
            else -> feedback?.lose()
        }
    }
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
            TopBar("Você × $oppName", onLeave, trailing = { AnagramPill(myPts, oppPts, (shownRound + 1).coerceAtMost(Anagram.ROUNDS)) })
            // Faixa do amigo: pontos, ou "acertou!" piscando quando ele leva a rodada.
            val lastOpp = inGap && state.decisions[cur - 1].winner == oppId && oppId != null
            val blink = rememberInfiniteTransition(label = "pisca")
            val a by blink.animateFloat(0.35f, 1f, infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "a")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(Shapes.card).background(Night.surface)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(oppName, color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    when { !online -> "Reconectando…"; lastOpp -> "acertou!"; oppLeft -> "saiu"; else -> points(oppPts) },
                    color = if (lastOpp) Night.present else Night.muted, fontSize = 14.sp, fontWeight = if (lastOpp) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.alpha(if (lastOpp) a else 1f),
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val word = answers[shownRound.coerceIn(0, Anagram.ROUNDS - 1)]
                val scrambled = remember(seed, shownRound) { Anagram.shuffle(word, seed, shownRound) }
                var order by remember(seed, shownRound, round) { mutableStateOf(listOf(0, 1, 2, 3, 4)) }
                val letters = if (inGap) word else order.map { scrambled[it] }.joinToString("")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    letters.forEachIndexed { i, ch ->
                        val bg = if (inGap) Night.correct else Night.brand[i]
                        val fg = if (!inGap && (i == 1 || i == 3)) Color(0xFF2A2140) else Night.text
                        LetterBox(ch, 56.dp, bg = bg, border = null, textColor = fg)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (inGap) {
                    val d = state.decisions[cur - 1]
                    val shownWord = words.display(word)
                    Text(
                        when (d.winner) { myId -> "Você acertou! $shownWord"; null -> "Ninguém acertou: $shownWord"; else -> "$oppName acertou: $shownWord" },
                        color = if (d.winner == myId) Night.correct else Night.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    )
                    if (over && !showEnd) {
                        Spacer(Modifier.height(12.dp))
                        PillButton("Ver resultado", Night.correct) { showEnd = true }
                    }
                } else {
                    Text(
                        "🔀 Embaralhar", color = Night.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(Shapes.pill).background(Night.surfaceHigh).clickable {
                            var next = order
                            while (next.map { scrambled[it] } == order.map { scrambled[it] } && scrambled.toSet().size > 1) next = order.shuffled()
                            order = next
                        }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                // Barra dos 45 s.
                val left = if (inGap || counting) 1f else (1f - elapsed.toFloat() / Anagram.ROUND_MS).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.pill).background(Night.surfaceHigh)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(left).clip(Shapes.pill).background(if (left < 0.22f) Night.red else Night.accent))
                }
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
            AnagramEndSheet(
                oppName = oppName, me = myPts, opp = oppPts,
                rows = answers.mapIndexed { i, w -> words.display(w) to state.decisions[i].winner.let { if (it == null) null else it == myId } },
                isHost = isHost, onAgain = onAgain, onLeave = onLeave, onClose = { showEnd = false },
            )
        }
    }
}

/**
 * Fim do Anagrama. [rows] = (palavra, quem acertou: true = você, false = o amigo, null = ninguém).
 */
@Composable
internal fun AnagramEndSheet(
    oppName: String, me: Int, opp: Int, rows: List<Pair<String, Boolean?>>, isHost: Boolean,
    onAgain: () -> Unit, onLeave: () -> Unit, onClose: () -> Unit,
) {
    val context = LocalContext.current
    BottomSheet(onClose) {
        val title = when { me > opp -> "Você venceu! 🏆"; opp > me -> "$oppName venceu"; else -> "Empate" }
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
        Text("$me × $opp", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = if (me > opp) Color(0xFFF5C84C) else Night.accent)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surfaceHigh).padding(horizontal = 16.dp, vertical = 8.dp)) {
            rows.forEachIndexed { i, (w, who) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = Night.muted, fontSize = 13.sp, modifier = Modifier.size(width = 26.dp, height = 18.dp))
                    Text(w, color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isHost) PillButton("Revanche", Night.correct, modifier = Modifier.weight(1f), onClick = onAgain)
            else PillButton("Esperando o anfitrião", Night.correct, modifier = Modifier.weight(1f), enabled = false) {}
            PillButton("Sair", Night.surfaceHigh, Night.text, Modifier.weight(0.6f), onClick = onLeave)
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Compartilhar", Night.accent, modifier = Modifier.fillMaxWidth()) {
            val res = when { me > opp -> "Venci por $me × $opp"; opp > me -> "Perdi por $me × $opp"; else -> "Empate $me × $opp" }
            share(context, "Palavreiro · Anagrama com $oppName\n$res 🔤")
        }
    }
}
