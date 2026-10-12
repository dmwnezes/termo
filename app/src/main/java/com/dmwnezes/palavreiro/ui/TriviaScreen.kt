package com.dmwnezes.palavreiro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.TriviaCat
import com.dmwnezes.palavreiro.game.TriviaGame
import com.dmwnezes.palavreiro.game.TriviaGame.Phase
import com.dmwnezes.palavreiro.game.TriviaQ
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val TInk = Color(0xFF14102C)
val TriviaCat.tint: Color get() = Color(color)

private const val TRIVIA_HELP = "A roleta sorteia uma categoria: Ciência, História, Geografia, Esportes, Arte ou Entretenimento. " +
    "Responda a pergunta antes de os 20 segundos acabarem.\n\n" +
    "Acerte 3 seguidas para ganhar uma coroa 👑: escolha uma categoria e acerte a pergunta da coroa para levar a medalha dela. " +
    "Junte as 6 medalhas para vencer. Cada erro custa uma das 3 vidas, e nenhuma pergunta se repete na partida."

/** Trivia: roleta de categorias, perguntas com tempo, coroas e as 6 medalhas. */
@Composable
fun TriviaScreen(
    questions: List<TriviaQ>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    seed: Long = System.nanoTime(),
    initial: TriviaGame? = null,
) {
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { if (round == 0 && initial != null) initial else TriviaGame(questions, Random(seed + round)) }
    var showHelp by remember { mutableStateOf(false) }
    var showEnd by remember(round) { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(store?.int("trivia_best") ?: 0) }
    val timer = remember(round) { Animatable(1f) }
    val wheel = remember(round) { Animatable(0f) }
    var landed by remember(round) { mutableStateOf(false) }
    val phase = game.phase

    // Roleta: gira ~1,5 s e para na categoria sorteada; depois mostra a pergunta.
    LaunchedEffect(game.turn, round) {
        if (game.phase != Phase.SPIN) return@LaunchedEffect
        val c = game.category ?: return@LaunchedEffect
        landed = false
        val i = c.ordinal
        val desired = ((-(i * 60f + 30f)) % 360f + 360f) % 360f
        val cur = wheel.value
        val delta = ((desired - cur % 360f) % 360f + 360f) % 360f
        wheel.animateTo(cur + 360f * 4 + delta, tween(1500, easing = FastOutSlowInEasing))
        landed = true
        feedback?.turn()
        delay(650)
        game.ask()
    }
    // Tempo de cada pergunta: 20 s.
    LaunchedEffect(game.question, phase == Phase.QUESTION) {
        if (game.phase != Phase.QUESTION) return@LaunchedEffect
        timer.snapTo(1f)
        timer.animateTo(0f, tween(20_000, easing = LinearEasing))
        game.answer(null)
    }
    // Depois da resposta: mostra o certo por um instante e segue.
    LaunchedEffect(phase, game.asked.size) {
        if (phase != Phase.ANSWERED) return@LaunchedEffect
        if (game.lastRight) { feedback?.reveal(2, Mark.CORRECT); store?.add("trivia_right") } else feedback?.invalid()
        store?.max("trivia_best", game.bestRun)
        best = maxOf(best, game.bestRun)
        delay(1400)
        game.next()
    }
    LaunchedEffect(phase == Phase.WON || phase == Phase.OVER, round) {
        if (!game.over) return@LaunchedEffect
        if (initial == null || round > 0) {
            if (game.won) { store?.add("trivia_won"); feedback?.win() } else feedback?.lose()
            store?.logActivity()
        }
        delay(500)
        showEnd = true
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Trivia", onBack, onHelp = { showHelp = true })
            Column(Modifier.weight(1f).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MedalRow(game)
                Spacer(Modifier.height(12.dp))
                StatusRow(game, best)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    when (phase) {
                        Phase.SPIN -> SpinView(game, wheel.value, landed)
                        Phase.QUESTION, Phase.ANSWERED -> QuestionView(game, timer.value)
                        Phase.CROWN -> CrownChoice(game)
                        Phase.WON, Phase.OVER -> if (game.question != null) QuestionView(game, 0f)
                    }
                }
            }
        }
        if (showEnd) {
            BottomSheet({ showEnd = false }) {
                SheetTitle(
                    if (game.won) "Você venceu! 🏆" else "Fim de jogo",
                    when {
                        game.won -> "Juntou as 6 medalhas"
                        game.exhausted -> "Acabaram as perguntas desta partida"
                        else -> "Você ganhou ${game.medals.size} de 6 medalhas"
                    },
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TriviaCat.entries.forEach { Medal(it, it in game.medals, 34.dp) } }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${game.right}", "Acertos")
                    StatItem("${game.bestRun}", "Seguidas")
                    StatItem("${maxOf(best, game.bestRun)}", "Recorde")
                }
                Spacer(Modifier.height(20.dp))
                PillButton("Jogar de novo", Night.correct, modifier = Modifier.fillMaxWidth()) { round++ }
            }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Trivia")
                Spacer(Modifier.height(12.dp))
                Text(TRIVIA_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(14.dp))
                TriviaCat.entries.chunked(3).forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        line.forEach { c ->
                            Row(
                                Modifier.weight(1f).clip(Shapes.pill).background(c.tint.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.emoji, fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(c.label, fontSize = 11.sp, color = Night.text, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

/** As 6 medalhas no topo: coloridas quando ganhas, cinza quando não; o anel mostra os acertos da categoria. */
@Composable
private fun MedalRow(game: TriviaGame) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TriviaCat.entries.forEach { c ->
            val p = (game.progress[c] ?: 0).coerceAtMost(3)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                if (c !in game.medals && p > 0) {
                    Canvas(Modifier.size(48.dp)) {
                        drawArc(c.tint, -90f, 360f * p / 3f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                Medal(c, c in game.medals, 40.dp)
            }
        }
    }
}

@Composable
private fun Medal(c: TriviaCat, won: Boolean, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (won) c.tint else Night.surfaceHigh)
            .then(if (won) Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape) else Modifier)
            .semantics { contentDescription = "Medalha de ${c.label}: ${if (won) "ganha" else "falta"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(c.emoji, fontSize = (size.value * 0.48f).sp, modifier = Modifier.alpha(if (won) 1f else 0.3f))
    }
}

/** Vidas, medidor da coroa (0/3) e acertos. */
@Composable
private fun StatusRow(game: TriviaGame, best: Int) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.surface).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (0 until game.maxLives).joinToString(" ") { if (it < game.lives) "❤️" else "🤍" }, fontSize = 15.sp,
            modifier = Modifier.semantics { contentDescription = "${game.lives} vidas" },
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Coroa: ${game.meter} de 3" },
        ) {
            repeat(3) { i ->
                Box(Modifier.padding(horizontal = 2.dp).size(width = 16.dp, height = 8.dp).clip(Shapes.pill).background(if (i < game.meter) Night.present else Night.surfaceHigh))
            }
            Spacer(Modifier.width(6.dp))
            Text("👑 ${game.meter}/3", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (game.meter >= 3) Night.present else Night.text)
        }
        Spacer(Modifier.weight(1f))
        Text("✓ ${game.right}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Night.correct)
    }
}

/** Roleta colorida com as 6 categorias; o ponteiro fica no topo. */
@Composable
private fun SpinView(game: TriviaGame, angle: Float, landed: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        TriviaWheel(angle, 250.dp)
        Spacer(Modifier.height(20.dp))
        val c = game.category
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            if (landed && c != null) {
                Text(
                    "${c.emoji}  ${c.label}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TInk,
                    modifier = Modifier.clip(Shapes.pill).background(c.tint).padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else Text("Girando…", fontSize = 18.sp, color = Night.muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TriviaWheel(angle: Float, size: Dp, pointer: Boolean = true) {
    Box(Modifier.size(size + if (pointer) 14.dp else 0.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.size(size).semantics { contentDescription = "Roleta de categorias" }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sweep = 360f / TriviaCat.entries.size
                    TriviaCat.entries.forEachIndexed { i, c ->
                        drawArc(c.tint, -90f + i * sweep, sweep, true)
                    }
                    TriviaCat.entries.indices.forEach { i ->
                        val a = Math.toRadians((-90.0 + i * sweep))
                        val r = this.size.minDimension / 2
                        drawLine(Color.White.copy(alpha = 0.55f), center, center + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()), strokeWidth = 2.dp.toPx())
                    }
                    drawCircle(Color.White.copy(alpha = 0.8f), style = Stroke(4.dp.toPx()))
                }
                TriviaCat.entries.forEachIndexed { i, c ->
                    val a = Math.toRadians(-90.0 + i * 60.0 + 30.0)
                    val r = size.value * 0.32f
                    Text(
                        c.emoji, fontSize = (size.value * 0.12f).sp,
                        modifier = Modifier.offset((cos(a) * r).toFloat().dp, (sin(a) * r).toFloat().dp),
                    )
                }
            }
            Box(Modifier.size(size * 0.22f).clip(CircleShape).background(Night.surface).border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape), contentAlignment = Alignment.Center) {
                Text("?", fontSize = (size.value * 0.1f).sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
            }
        }
        if (pointer) Pointer()
    }
}

@Composable
private fun BoxScope.Pointer() {
    Canvas(Modifier.align(Alignment.TopCenter).size(width = 26.dp, height = 30.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2, h); close() }
        drawPath(p, Night.text)
    }
}

@Composable
private fun QuestionView(game: TriviaGame, time: Float) {
    val q = game.question ?: return
    val answered = game.phase != Phase.QUESTION
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${q.cat.emoji}  ${q.cat.label}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TInk, maxLines = 1, softWrap = false,
                modifier = Modifier.clip(Shapes.pill).background(q.cat.tint).padding(horizontal = 14.dp, vertical = 6.dp),
            )
            if (game.crownRound) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "👑 Coroa", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TInk, maxLines = 1, softWrap = false,
                    modifier = Modifier.clip(Shapes.pill).background(Night.present).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.pill).background(Night.surface)) {
            Box(Modifier.fillMaxWidth(time.coerceIn(0f, 1f)).height(8.dp).clip(Shapes.pill).background(if (time < 0.25f) Night.red else q.cat.tint))
        }
        Spacer(Modifier.height(4.dp))
        Text("${kotlin.math.ceil(time * 20).toInt()} s", fontSize = 12.sp, color = Night.muted, modifier = Modifier.align(Alignment.End))
        Spacer(Modifier.height(8.dp))
        Text(
            q.text, color = Night.text, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface)
                .border(2.dp, q.cat.tint.copy(alpha = 0.6f), Shapes.card).padding(horizontal = 18.dp, vertical = 22.dp),
        )
        Spacer(Modifier.height(14.dp))
        game.options.forEach { opt ->
            val color = when {
                !answered -> Night.surfaceHigh
                opt == q.right -> Night.correct
                opt == game.picked -> Night.red
                else -> Night.surface
            }
            val bg by animateColorAsState(color, label = "opcao")
            Text(
                opt, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                color = if (bg == Night.correct || bg == Night.red) TInk else Night.text,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 54.dp).clip(RoundedCornerShape(20.dp)).background(bg)
                    .clickable(enabled = !answered) { game.answer(opt) }.padding(horizontal = 14.dp, vertical = 15.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            if (answered) {
                val msg = when {
                    game.lastRight && game.crownRound -> "Medalha de ${q.cat.label}! ${q.cat.emoji}"
                    game.lastRight && game.meter >= 3 -> "Certo! Você ganhou uma coroa 👑"
                    game.lastRight -> "Certo!"
                    game.picked == null -> "O tempo acabou!"
                    else -> "Errou! Era ${q.right}"
                }
                Text(msg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (game.lastRight) Night.correct else Night.red, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Coroa: escolha a categoria da medalha que quer tentar. */
@Composable
private fun CrownChoice(game: TriviaGame) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("👑", fontSize = 46.sp)
        Text("Você ganhou uma coroa!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
        Spacer(Modifier.height(4.dp))
        Text("Escolha a medalha que quer tentar", fontSize = 15.sp, color = Night.muted)
        Spacer(Modifier.height(18.dp))
        TriviaCat.entries.chunked(2).forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { c ->
                    val ok = c in game.crownChoices
                    val has = c in game.medals
                    Column(
                        Modifier.weight(1f).height(82.dp).clip(Shapes.card)
                            .background(if (ok) c.tint else Night.surface)
                            .clickable(enabled = ok) { game.chooseCrown(c) }
                            .semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(c.emoji, fontSize = 26.sp, modifier = Modifier.alpha(if (ok) 1f else 0.4f))
                        Text(
                            if (has) "${c.label} ✓" else c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            color = if (ok) TInk else Night.muted,
                        )
                    }
                }
            }
        }
    }
}
