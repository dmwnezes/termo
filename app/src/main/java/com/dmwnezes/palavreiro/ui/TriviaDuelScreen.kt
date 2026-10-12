package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Timed
import com.dmwnezes.palavreiro.game.TriviaDuel
import kotlinx.coroutines.delay

private fun secs(ms: Long): String = "%d,%d s".format(ms / 1000, (ms % 1000) / 100)

/** Trivia com amigo (modo "g"): N perguntas, roleta, 15 s para responder, 3 s mostrando a certa. */
@Composable
internal fun TriviaPlay(pool: List<TriviaDuel.Question>, seed: Long, rounds: Int, msgs: List<Timed>, d: Duel) {
    val now = rememberNow(d.clock, d.round, 50)
    val items = remember(pool, seed, rounds) { TriviaDuel.pick(pool, seed, rounds) }
    val total = items.size.coerceAtLeast(1)
    val st = TriviaDuel.state(msgs, d.myId, d.oppId, total, seed, d.localStart, now, d.oppLeft)
    val k = st.current
    val start = if (st.over) Long.MAX_VALUE else st.startOf(k, d.localStart)
    val inGap = k > 0 && (st.over || now < start)
    val shown = (if (inGap) k - 1 else k).coerceIn(0, total - 1)
    val qStart = if (st.over) Long.MAX_VALUE else start + TriviaDuel.WHEEL_MS
    val spinning = !inGap && !st.over && now < qStart
    val answering = !inGap && !st.over && now >= qStart
    val elapsed = (now - qStart).coerceIn(0, TriviaDuel.ANSWER_MS)
    val sent = remember(d.round) { mutableSetOf<Int>() }
    var picked by remember(d.round, k) { mutableStateOf<Int?>(null) }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    val oppAnswered = msgs.any { val m = it.msg; m is Msg.Pick && m.id == d.oppId && m.round == k }

    fun pick(o: Int, ms: Long) {
        if (!answering || items.isEmpty() || !sent.add(k)) return
        picked = o
        d.send(Msg.Pick(d.myId, k, o, ms))
    }
    // Acabou o tempo sem resposta: manda -1.
    val timeUp = answering && now >= qStart + TriviaDuel.ANSWER_MS
    LaunchedEffect(k, timeUp) { if (timeUp) pick(-1, TriviaDuel.ANSWER_MS) }
    LaunchedEffect(k, d.round) {
        if (k == 0) return@LaunchedEffect
        if (st.iHit(k - 1)) d.feedback?.win() else d.feedback?.lose()
    }
    LaunchedEffect(st.over, d.round) {
        if (!st.over) return@LaunchedEffect
        d.onDecided(st.outcome() ?: "draw")
        delay(TriviaDuel.REVEAL_MS)
        showEnd = true
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); AnagramPill(st.myHits, st.oppHits, shown + 1, total) })
            val lastOpp = inGap && st.oppHit(k - 1)
            OppBar(
                d.oppName,
                when {
                    !d.online -> "Reconectando…"
                    d.oppLeft -> "saiu"
                    inGap -> if (lastOpp) "acertou!" else "errou"
                    answering && oppAnswered -> "respondeu"
                    answering -> "pensando…"
                    else -> if (st.oppHits == 1) "1 acerto" else "${st.oppHits} acertos"
                },
                lastOpp,
            )
            val q = items.getOrNull(shown)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (q == null) {
                    Spacer(Modifier.height(40.dp))
                    Text("Sem perguntas por aqui.", color = Night.muted, fontSize = 16.sp)
                } else if (spinning) {
                    Spacer(Modifier.height(20.dp))
                    Text("Pergunta ${k + 1} de $total", color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(18.dp))
                    val t = ((now - start).toFloat() / TriviaDuel.WHEEL_MS).coerceIn(0f, 1f)
                    DuelCategoryWheel(q.category, t)
                    Spacer(Modifier.height(18.dp))
                    if (t >= 1f || now >= qStart - 150) DuelCategoryChip(q.category, big = true)
                    else Text("Girando…", color = Night.muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    val k2 = shown
                    val opts = remember(q, seed, k2) { TriviaDuel.options(q, seed, k2) }
                    val right = TriviaDuel.correctIndex(seed, k2)
                    val dec = if (inGap || st.over) st.decisions.getOrNull(k2) else null
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        DuelCategoryChip(q.category, big = false)
                        Spacer(Modifier.weight(1f))
                        Text("${k2 + 1}/$total", color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        q.text, color = Night.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 28.sp,
                        modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface)
                            .border(1.5.dp, triviaColor(q.category).copy(alpha = 0.5f), Shapes.card).padding(horizontal = 18.dp, vertical = 22.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    val mine = dec?.mine?.option ?: picked
                    opts.forEachIndexed { i, text ->
                        val badges = buildList {
                            if (dec != null && dec.mine?.option == i) add("Você")
                            if (dec != null && dec.theirs?.option == i) add(d.oppName)
                        }
                        val look = when {
                            dec != null && i == right -> OptLook.RIGHT
                            dec != null && i == mine -> OptLook.WRONG
                            dec != null -> OptLook.DIM
                            mine == i -> OptLook.PICKED
                            mine != null -> OptLook.DIM
                            else -> OptLook.NORMAL
                        }
                        OptionButton("ABCD"[i], text, look, badges, enabled = answering && mine == null) { pick(i, elapsed) }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    when {
                        dec != null -> {
                            val me = st.iHit(k2)
                            Text(
                                when { me && st.oppHit(k2) -> "Os dois acertaram!"; me -> "Você acertou!"; st.oppHit(k2) -> "${d.oppName} acertou"; else -> "Ninguém acertou" },
                                color = if (me) Night.correct else Night.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                            )
                            if (st.over && !showEnd) {
                                Spacer(Modifier.height(10.dp))
                                PillButton("Ver resultado", Night.correct) { showEnd = true }
                            }
                        }
                        mine != null -> Text("Esperando ${d.oppName}…", color = Night.muted, fontSize = 15.sp)
                        else -> {
                            val left = ((TriviaDuel.ANSWER_MS - elapsed + 999) / 1000)
                            Text("$left s", color = if (left <= 5) Night.red else Night.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TimeBar(if (dec != null || mine != null) 0f else 1f - elapsed.toFloat() / TriviaDuel.ANSWER_MS)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        if (st.over && showEnd) {
            val o = st.outcome() ?: "draw"
            PointsEndSheet(
                mode = Multi.TRIVIA, oppName = d.oppName, me = st.myHits, opp = st.oppHits,
                rows = items.map { it.text to null },
                isHost = d.isHost, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                myName = d.myName, next = d.next, outcome = o,
                note = if (st.myHits == st.oppHits && st.myHits > 0) "Desempate pelo tempo: ${secs(st.myMs)} × ${secs(st.oppMs)}" else "acertos",
                storyDetail = "${st.myHits} × ${st.oppHits} acertos",
                table = {
                    Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surfaceHigh).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                            Spacer(Modifier.weight(1f))
                            Text("Você", color = Night.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                            Text(d.oppName, color = Night.muted, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(52.dp))
                        }
                        items.forEachIndexed { i, q ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(triviaColor(q.category)))
                                Spacer(Modifier.width(8.dp))
                                Text(q.text, color = Night.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Mark(st.iHit(i), Modifier.width(44.dp))
                                Mark(st.oppHit(i), Modifier.width(52.dp))
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun Mark(ok: Boolean, modifier: Modifier) {
    Text(if (ok) "✓" else "✗", color = if (ok) Night.correct else Night.red.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = modifier)
}

private enum class OptLook { NORMAL, PICKED, RIGHT, WRONG, DIM }

@Composable
private fun OptionButton(letter: Char, text: String, look: OptLook, badges: List<String>, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val bg = when (look) { OptLook.RIGHT -> Night.correct; OptLook.WRONG -> Night.red; OptLook.PICKED -> Night.accent; else -> Night.surfaceHigh }
    val fg = if (look == OptLook.RIGHT || look == OptLook.WRONG || look == OptLook.PICKED) InkDark else Night.text
    Row(
        Modifier.fillMaxWidth().alpha(if (look == OptLook.DIM) 0.5f else 1f).clip(shape).background(bg)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(if (fg == InkDark) InkDark.copy(alpha = 0.14f) else Night.surface), contentAlignment = Alignment.Center) {
            Text(letter.toString(), color = fg, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), lineHeight = 21.sp)
        badges.forEach { b ->
            Spacer(Modifier.width(6.dp))
            Text(
                b, color = if (fg == InkDark) Night.text else InkDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(Shapes.pill).background(if (fg == InkDark) InkDark.copy(alpha = 0.75f) else Night.text).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Selo da categoria: cor e ícone fixos. */
@Composable
internal fun DuelCategoryChip(cat: String, big: Boolean) {
    val c = triviaColor(cat)
    Row(
        Modifier.clip(Shapes.pill).background(c.copy(alpha = 0.2f)).border(1.dp, c, Shapes.pill)
            .padding(horizontal = if (big) 18.dp else 12.dp, vertical = if (big) 8.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(triviaEmoji(cat), fontSize = if (big) 22.sp else 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(cat, color = c, fontSize = if (big) 20.sp else 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Roleta das 6 categorias girando e parando com a categoria [cat] embaixo do ponteiro.
 * [t] = progresso de 0 a 1 (desacelera no fim).
 */
@Composable
internal fun DuelCategoryWheel(cat: String, t: Float) {
    val idx = TriviaDuel.CATEGORIES.indexOf(cat).coerceAtLeast(0)
    // Fatia i ocupa [-90 + 60i, -30 + 60i]; para o meio dela ficar em cima (-90°), gira -(60i + 30).
    val target = -(idx * 60f + 30f)
    val ease = 1f - (1f - t) * (1f - t) * (1f - t)
    val angle = target - 3 * 360f * (1f - ease)
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val r = size.minDimension / 2 - 14.dp.toPx()
            val c = Offset(size.width / 2, size.height / 2 + 6.dp.toPx())
            drawCircle(Night.surface, r + 8.dp.toPx(), c)
            rotate(angle, c) {
                TriviaDuel.CATEGORIES.forEachIndexed { i, name ->
                    drawArc(triviaColor(name), -90f + i * 60f, 60f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
                }
                for (i in 0 until 6) {
                    val a = Math.toRadians(-90.0 + i * 60)
                    drawLine(Night.surface, c, Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat()), 3.dp.toPx())
                }
            }
            drawCircle(Night.surface, 26.dp.toPx(), c)
            drawCircle(Night.outline, 26.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            val p = Path().apply {
                moveTo(c.x, c.y - r + 18.dp.toPx())
                lineTo(c.x - 13.dp.toPx(), c.y - r - 12.dp.toPx())
                lineTo(c.x + 13.dp.toPx(), c.y - r - 12.dp.toPx())
                close()
            }
            drawPath(p, Night.text)
        }
        // Ícones das categorias acompanham o giro.
        TriviaDuel.CATEGORIES.forEachIndexed { i, name ->
            val a = Math.toRadians((-60.0 + i * 60) + angle)
            val rr = 62f
            Text(
                triviaEmoji(name), fontSize = 22.sp,
                modifier = Modifier.offset((rr * kotlin.math.cos(a)).toFloat().dp, (rr * kotlin.math.sin(a) + 6).toFloat().dp),
            )
        }
    }
}
