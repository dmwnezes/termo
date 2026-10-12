package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.game.Adedonha
import com.dmwnezes.palavreiro.game.Adedonha.Stage
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Timed
import kotlinx.coroutines.delay

/** Categorias de reserva (se o arquivo não abrir). */
private val FALLBACK_CATS = listOf("Nome", "Animal", "Fruta", "Cor", "Objeto", "Cidade, estado ou país")

private fun pts(n: Int) = if (n == 1) "1 ponto" else "$n pontos"

/**
 * Adedonha com amigo (modo "s"). [pool] = categorias do arquivo; [rounds] = 3/5/7 rodadas.
 * [prefill] = respostas já digitadas (para as imagens de teste).
 */
@Composable
internal fun AdedonhaPlay(pool: List<String>, rounds: Int, msgs: List<Timed>, d: Duel, prefill: List<String> = emptyList(), prefillMarks: List<Boolean> = emptyList()) {
    val now = rememberNow(d.clock, d.round, 100)
    val st = Adedonha.state(msgs, d.n, d.myId, d.oppId, rounds, now, d.oppLeft)
    val cur = st.current
    val stage = cur?.stage(now)
    val hostCats = remember(d.round, pool) { Adedonha.pickCategories(pool.ifEmpty { FALLBACK_CATS }) }
    val sentRounds = remember(d.round) { mutableSetOf<Int>() }
    val stopSent = remember(d.round) { mutableSetOf<Int>() }
    val ansSent = remember(d.round) { mutableSetOf<Int>() }
    val voteSent = remember(d.round) { mutableSetOf<Int>() }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    val catCount = cur?.cats?.size ?: 0
    val fields = remember(d.round, cur?.k, catCount) { mutableStateListOf<String>().apply { repeat(catCount) { add(prefill.getOrElse(it) { "" }) } } }
    val marks = remember(d.round, cur?.k, catCount) { mutableStateListOf<Boolean>().apply { repeat(catCount) { add(prefillMarks.getOrElse(it) { true }) } } }

    // Anfitrião: letra da rodada (a primeira depois da contagem; as outras 6 s depois do resultado).
    val wantRound: Int? = if (!d.isHost || st.over) null else when {
        cur == null -> if (now >= d.localStart) 0 else null
        cur.resolvedAt != null && st.rounds.size < rounds && now >= cur.resolvedAt + Adedonha.NEXT_MS -> st.rounds.size
        else -> null
    }
    LaunchedEffect(wantRound) {
        val k = wantRound ?: return@LaunchedEffect
        if (sentRounds.add(k)) d.send(Msg.SRound(d.n, k, Adedonha.pickLetter(st.usedLetters), st.cats.ifEmpty { hostCats }))
    }
    // Tempo esgotado: o anfitrião manda o stop "tempo" (o convidado se o anfitrião saiu ou depois de 100 s).
    val wantStop = cur != null && stage == Stage.FILL &&
        (if (d.isHost) now >= cur.fillEnd else now >= cur.fillStart + Adedonha.GUEST_STOP_MS || (d.oppLeft && now >= cur.fillEnd))
    LaunchedEffect(cur?.k, wantStop) {
        if (wantStop && cur != null && stopSent.add(cur.k)) d.send(Msg.Stop(Adedonha.TEMPO, d.n, cur.k))
    }
    // Primeiro STOP da rodada: trava e manda as respostas.
    val stopped = cur?.stop != null
    LaunchedEffect(cur?.k, stopped) {
        if (stopped && cur != null && ansSent.add(cur.k)) d.send(Msg.Ans(d.myId, d.n, cur.k, List(catCount) { fields.getOrElse(it) { "" }.trim() }))
    }
    fun sendVote() {
        val c = cur ?: return
        val theirs = c.theirs ?: return
        if (voteSent.add(c.k)) d.send(Msg.Vote(d.myId, d.n, c.k, Adedonha.votesFor(theirs, c.letter, marks)))
    }
    // Limite da validação (ou o amigo não mandou respostas): vota sozinho.
    val voteDue = cur != null && stage == Stage.VOTE && cur.myVote == null && (cur.oppGone || now >= cur.voteStart + Adedonha.VOTE_MS)
    LaunchedEffect(cur?.k, voteDue) { if (voteDue) sendVote() }
    LaunchedEffect(cur?.k, stage == Stage.RESULT) {
        if (stage == Stage.RESULT && cur != null) {
            if (cur.myTotal > cur.oppTotal) d.feedback?.win() else if (cur.oppTotal > cur.myTotal) d.feedback?.lose()
        }
    }
    LaunchedEffect(st.over, d.round) {
        if (!st.over) return@LaunchedEffect
        d.onDecided(st.outcome() ?: "draw")
        delay(3500)
        showEnd = true
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val roundNo = ((cur?.k ?: 0) + 1).coerceAtMost(rounds)
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); AnagramPill(st.me, st.opp, roundNo, rounds) })
            val oppStatus = when {
                !d.online -> "Reconectando…"
                d.oppLeft -> "saiu"
                stage == Stage.FILL -> "preenchendo…"
                stage == Stage.LOCKED -> if (cur?.theirs != null) "respostas enviadas" else "enviando…"
                stage == Stage.VOTE -> if (cur?.theirVote != null) "já validou" else "validando…"
                else -> pts(st.opp)
            }
            OppBar(d.oppName, oppStatus, false)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    cur == null || stage == Stage.SPIN -> LetterSpin(cur?.letter, now, cur?.fillStart ?: Long.MAX_VALUE)
                    stage == Stage.FILL || stage == Stage.LOCKED -> FillView(
                        cur, stage, now, fields, d.oppName, d.myId,
                        onStop = { if (stopSent.add(cur.k)) { d.feedback?.win(); d.send(Msg.Stop(d.myId, d.n, cur.k)) } },
                    )
                    stage == Stage.VOTE -> VoteView(cur, now, marks, d.oppName, voted = cur.myVote != null || cur.k in voteSent, onConfirm = ::sendVote)
                    else -> ResultView(cur, st, d.oppName, now, rounds, showEnd) { showEnd = true }
                }
            }
        }
        if (st.over && showEnd) {
            PointsEndSheet(
                mode = Multi.ADEDONHA, oppName = d.oppName, me = st.me, opp = st.opp,
                rows = st.rounds.map { "Letra ${it.letter}" to null },
                isHost = d.isHost, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                myName = d.myName, next = d.next,
                storyDetail = "Pontos ${st.me} × ${st.opp}",
                table = {
                    Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surfaceHigh).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        st.rounds.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                LetterBadge(r.letter, 26)
                                Spacer(Modifier.width(10.dp))
                                Text("Rodada ${r.k + 1}", color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(
                                    "${r.myTotal} × ${r.oppTotal}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    color = when { r.myTotal > r.oppTotal -> Night.correct; r.oppTotal > r.myTotal -> Night.accent; else -> Night.muted },
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

/** Quadradinho amarelo com a letra da rodada. */
@Composable
private fun LetterBadge(letter: Char, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(Night.present), contentAlignment = Alignment.Center) {
        Text(letter.toString(), color = InkDark, fontSize = (size * 0.55f).sp, fontWeight = FontWeight.ExtraBold, lineHeight = (size * 0.6f).sp)
    }
}

/** Letra girando ("Sorteando a letra…") e parando na letra da rodada. */
@Composable
private fun LetterSpin(letter: Char?, now: Long, fillStart: Long) {
    val settled = letter != null && now >= fillStart - 450
    val shown = if (settled) letter!! else Adedonha.LETTERS[((now / 70) % Adedonha.LETTERS.length).toInt()]
    val pop = if (settled) (1f + 0.12f * ((fillStart - now).coerceIn(0, 450) / 450f)) else 1f
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(136.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(RoundedCornerShape(40.dp))
                .background(if (settled) Night.present else Night.surfaceHigh)
                .border(2.dp, if (settled) Night.present else Night.outline, RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(shown.toString(), color = if (settled) InkDark else Night.text, fontSize = 80.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 84.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (settled) "Letra $shown!" else "Sorteando a letra…", color = Night.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** Preenchimento: um campo por categoria, a barra de 90 s e o botão STOP!. */
@Composable
private fun FillView(
    r: Adedonha.Round, stage: Stage, now: Long, fields: MutableList<String>, oppName: String, myId: String, onStop: () -> Unit,
) {
    val locked = stage == Stage.LOCKED
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(r.letter, 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Letra ${r.letter}", color = Night.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (locked) "Respostas travadas" else "Preencha tudo e dê STOP!",
                    color = Night.muted, fontSize = 13.sp,
                )
            }
            val left = ((r.fillEnd - now).coerceAtLeast(0) + 999) / 1000
            if (!locked) Text("${left}s", color = if (left <= 15) Night.red else Night.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        TimeBar(if (locked) 0f else ((r.fillEnd - now).toFloat() / Adedonha.FILL_MS).coerceIn(0f, 1f))
        if (locked) {
            Spacer(Modifier.height(10.dp))
            val who = r.stop?.id
            Text(
                when (who) { myId -> "Você deu STOP!"; Adedonha.TEMPO -> "Tempo!"; else -> "$oppName deu STOP!" },
                color = InkDark, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(Shapes.pill).background(if (who == myId) Night.correct else Night.red).padding(vertical = 9.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.cats.forEachIndexed { i, cat ->
                val v = fields.getOrElse(i) { "" }
                val ok = Adedonha.autoValid(v, r.letter)
                OutlinedTextField(
                    value = v, onValueChange = { if (!locked && i < fields.size) fields[i] = it.take(40) }, singleLine = true, enabled = !locked,
                    label = { Text(cat, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (ok) Night.present else Night.surfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) { Text(r.letter.toString(), color = if (ok) InkDark else Night.present, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                    },
                    trailingIcon = if (ok) ({ Text("✓", color = Night.correct, fontSize = 18.sp, fontWeight = FontWeight.Bold) }) else null,
                    isError = v.isNotBlank() && !ok,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Night.text, unfocusedTextColor = Night.text, disabledTextColor = Night.text.copy(alpha = 0.8f),
                        focusedBorderColor = Night.accent, unfocusedBorderColor = Night.outline, disabledBorderColor = Night.outline.copy(alpha = 0.4f),
                        cursorColor = Night.accent, focusedLabelColor = Night.accent, unfocusedLabelColor = Night.muted, disabledLabelColor = Night.muted,
                        errorBorderColor = Night.red, errorLabelColor = Night.red, errorTextColor = Night.text, errorCursorColor = Night.red,
                        focusedContainerColor = Night.surface.copy(alpha = 0.6f), unfocusedContainerColor = Night.surface.copy(alpha = 0.6f),
                        disabledContainerColor = Night.surface.copy(alpha = 0.3f), errorContainerColor = Night.surface.copy(alpha = 0.6f),
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (locked) {
            Text(
                "Esperando as respostas…", color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            )
        } else {
            val can = Adedonha.canStop(fields, r.letter)
            Box(
                Modifier.fillMaxWidth().height(64.dp).clip(Shapes.pill).background(if (can) Night.red else Night.red.copy(alpha = 0.28f))
                    .clickable(enabled = can, onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Text("STOP!", color = if (can) InkDark else Night.text.copy(alpha = 0.5f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Validação: as respostas do amigo com ✓ por padrão; tocar marca ✗. As automáticas já vêm ✗ travadas. */
@Composable
private fun VoteView(r: Adedonha.Round, now: Long, marks: MutableList<Boolean>, oppName: String, voted: Boolean, onConfirm: () -> Unit) {
    val theirs = r.theirs.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(r.letter, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Valide as respostas de $oppName", color = Night.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Toque para marcar ✗ o que não vale", color = Night.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        TimeBar(((r.voteStart + Adedonha.VOTE_MS - now).toFloat() / Adedonha.VOTE_MS).coerceIn(0f, 1f))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.cats.forEachIndexed { i, cat ->
                val a = theirs.getOrElse(i) { "" }
                val auto = Adedonha.autoValid(a, r.letter)
                val on = auto && marks.getOrElse(i) { true }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Night.surface)
                        .clickable(enabled = auto && !voted) { if (i < marks.size) marks[i] = !marks[i] }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics { contentDescription = "Validar $cat" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cat, color = Night.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            a.ifBlank { "(em branco)" }, color = if (a.isBlank()) Night.muted else Night.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (!on && a.isNotBlank()) TextDecoration.LineThrough else null,
                        )
                        if (!auto && a.isNotBlank()) Text("não começa com ${r.letter}", color = Night.red, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (on) Night.correct else Night.red.copy(alpha = if (auto) 1f else 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (on) "✓" else "✗", color = InkDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (voted) {
            Text(
                "Esperando $oppName validar…", color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        } else PillButton("Confirmar", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = onConfirm)
        Spacer(Modifier.height(10.dp))
    }
}

/** Resultado da rodada: categoria × (sua resposta | do amigo) com os pontos coloridos, total da rodada e placar geral. */
@Composable
private fun ResultView(r: Adedonha.Round, st: Adedonha.State, oppName: String, now: Long, rounds: Int, showEnd: Boolean, onShowEnd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(r.letter, 40)
            Spacer(Modifier.width(12.dp))
            Text("Rodada ${r.k + 1} de $rounds", color = Night.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("Você", color = Night.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(oppName, color = Night.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.cats.forEachIndexed { i, cat ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Night.surface).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(cat, color = Night.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnswerCell(r.mine?.getOrNull(i).orEmpty(), r.myPoints.getOrElse(i) { 0 }, Modifier.weight(1f))
                        AnswerCell(r.theirs?.getOrNull(i).orEmpty(), r.oppPoints.getOrElse(i) { 0 }, Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surfaceHigh).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Nesta rodada", color = Night.muted, fontSize = 12.sp)
                Text("+${r.myTotal} × +${r.oppTotal}", color = Night.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Placar geral", color = Night.muted, fontSize = 12.sp)
                Text("${st.me} × ${st.opp}", color = Night.accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (st.over) {
            if (!showEnd) PillButton("Ver resultado", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = onShowEnd)
            else Text("Fim de jogo!", color = Night.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        } else {
            val left = ((r.resolvedAt ?: now) + Adedonha.NEXT_MS - now).coerceAtLeast(0)
            Text(
                if (left > 0) "Próxima rodada em ${(left + 999) / 1000} s" else "Sorteando a próxima letra…",
                color = Night.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun AnswerCell(a: String, p: Int, modifier: Modifier) {
    val c = when (p) { 10 -> Night.correct; 5 -> Night.present; else -> Night.red }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            a.ifBlank { "—" }, color = if (p == 0) Night.muted else Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            textDecoration = if (p == 0 && a.isNotBlank()) TextDecoration.LineThrough else null,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$p", color = if (p == 0) Night.text else InkDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            modifier = Modifier.clip(Shapes.pill).background(if (p == 0) c.copy(alpha = 0.35f) else c).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

