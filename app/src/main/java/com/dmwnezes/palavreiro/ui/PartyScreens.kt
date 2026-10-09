package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.ConnPuzzle
import com.dmwnezes.palavreiro.game.IntruderGame
import com.dmwnezes.palavreiro.game.SpellPair
import com.dmwnezes.palavreiro.game.SpellingGame
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Ink = Color(0xFF14102C)

/** Pontos e vidas no topo dos jogos de rodada. */
@Composable
private fun ScoreLives(score: Int, lives: Int, best: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        StatItem("$score", "Pontos")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text((0 until 3).joinToString(" ") { if (it < lives) "❤️" else "🤍" }, fontSize = 20.sp)
            Text("Vidas", fontSize = 13.sp, color = Night.muted)
        }
        StatItem("$best", "Recorde")
    }
}

/** Folha de fim de jogo do Intruso e do Certo ou Errado. */
@Composable
private fun EndSheet(title: String, score: Int, best: Int, total: Int, shareText: String, onAgain: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    BottomSheet(onClose) {
        SheetTitle("Fim de jogo", title)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("$score", "Pontos")
            StatItem("$best", "Recorde")
            StatItem("$total", "Acertos")
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), "Compartilhar"))
            }
            PillButton("Jogar de novo", Night.correct, modifier = Modifier.weight(1f), onClick = onAgain)
        }
    }
}

/** Intruso: 5 palavras, uma não pertence ao grupo. */
@Composable
fun IntruderScreen(
    puzzles: List<ConnPuzzle>,
    families: Map<String, Set<String>>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    seed: Long = System.nanoTime(),
) {
    var run by remember { mutableIntStateOf(0) }
    val game = remember(run) { IntruderGame(puzzles, families, Random(seed + run)) }
    var best by remember { mutableIntStateOf(store?.int("intr_best") ?: 0) }
    var showEnd by remember(run) { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val picked = game.picked

    // Depois de responder: mostra o grupo e passa para a próxima rodada (ou termina).
    LaunchedEffect(picked, run) {
        if (picked == null) return@LaunchedEffect
        delay(1300)
        if (game.over) {
            store?.add("intr_played"); store?.max("intr_best", game.score); store?.logActivity()
            best = maxOf(best, game.score)
            feedback?.lose()
            showEnd = true
        } else game.next()
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Intruso", onBack, onHelp = { showHelp = true })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScoreLives(game.score, game.lives, maxOf(best, game.score))
                Spacer(Modifier.height(18.dp))
                Text("Qual palavra não pertence ao grupo?", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                game.round.options.forEach { w ->
                    val state = when {
                        picked == null -> 0
                        w == game.round.intruder -> 1          // a intrusa fica verde
                        w == picked -> 2                       // o erro fica vermelho
                        else -> 3
                    }
                    val bg by animateColorAsState(
                        when (state) { 1 -> Night.correct; 2 -> Night.red; else -> Night.surfaceHigh }, label = "opcao",
                    )
                    Text(
                        w,
                        color = if (state == 1 || state == 2) Ink else Night.text.copy(alpha = if (state == 3) 0.6f else 1f),
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(Shapes.card).background(bg)
                            .clickable(enabled = picked == null) {
                                if (game.pick(w)) { feedback?.reveal(2, com.dmwnezes.palavreiro.game.Mark.CORRECT); store?.add("intr_right") }
                                else feedback?.invalid()
                            }
                            .padding(vertical = 16.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (picked != null) {
                    Text(
                        "Eram todos: ${game.round.group.name}",
                        color = Night.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.clip(Shapes.pill).background(Night.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (showEnd) {
            EndSheet(
                "Você fez ${game.score} ${if (game.score == 1) "ponto" else "pontos"}", game.score, best, store?.int("intr_right") ?: 0,
                "Palavreiro · Intruso\nFiz ${game.score} ${if (game.score == 1) "ponto" else "pontos"} 🕵️",
                onAgain = { run++ },
            ) { showEnd = false }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Intruso")
                Spacer(Modifier.height(12.dp))
                Text("Quatro palavras têm algo em comum. Toque na que não pertence ao grupo. Você tem 3 vidas.", color = Night.text, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

/** Certo ou Errado: a palavra está escrita do jeito certo? */
@Composable
fun SpellingScreen(
    pairs: List<SpellPair>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    seed: Long = System.nanoTime(),
) {
    var run by remember { mutableIntStateOf(0) }
    val game = remember(run) { SpellingGame(pairs, Random(seed + run)) }
    var best by remember { mutableIntStateOf(store?.int("ort_best") ?: 0) }
    var showEnd by remember(run) { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val answered = game.answered

    LaunchedEffect(answered, game.pair, run) {
        if (answered == null) return@LaunchedEffect
        delay(1200)
        if (game.over) {
            store?.add("ort_played"); store?.max("ort_best", game.score); store?.logActivity()
            best = maxOf(best, game.score)
            feedback?.lose()
            showEnd = true
        } else game.next()
    }

    fun answer(right: Boolean) {
        if (game.answer(right)) { feedback?.reveal(2, com.dmwnezes.palavreiro.game.Mark.CORRECT); store?.add("ort_right") }
        else feedback?.invalid()
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Certo ou Errado", onBack, onHelp = { showHelp = true })
            Column(
                Modifier.weight(1f).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScoreLives(game.score, game.lives, maxOf(best, game.score))
                Spacer(Modifier.height(28.dp))
                Text("Está escrito certo?", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                val border = when (answered) { true -> Night.correct; false -> Night.red; null -> Night.surface }
                Box(
                    Modifier.fillMaxWidth().clip(Shapes.card).background(border).padding(3.dp).clip(Shapes.card).background(Night.surface)
                        .padding(vertical = 40.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        game.shown.uppercase(), color = Night.text, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        fontSize = if (game.shown.length > 11) 26.sp else 34.sp, letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                    when (answered) {
                        true -> Text("Isso!", color = Night.correct, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        false -> Text("Certo: ${game.pair.right.uppercase()}", color = Night.present, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        null -> {}
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigChoice("✗  Errado", Night.red, Modifier.weight(1f), enabled = answered == null) { answer(false) }
                    BigChoice("✓  Certo", Night.correct, Modifier.weight(1f), enabled = answered == null) { answer(true) }
                }
            }
        }
        if (showEnd) {
            EndSheet(
                "Você fez ${game.score} ${if (game.score == 1) "ponto" else "pontos"}", game.score, best, store?.int("ort_right") ?: 0,
                "Palavreiro · Certo ou Errado\nFiz ${game.score} ${if (game.score == 1) "ponto" else "pontos"} ✍️",
                onAgain = { run++ },
            ) { showEnd = false }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Certo ou Errado")
                Spacer(Modifier.height(12.dp))
                Text("Veja a palavra e diga se ela está escrita do jeito certo. Você tem 3 vidas.", color = Night.text, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

@Composable
private fun BigChoice(text: String, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.height(72.dp).clip(Shapes.card).background(if (enabled) color else color.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Mestre Mandou: obedeça só quando o mestre mandar. */
@Composable
fun MestreScreen(
    data: com.dmwnezes.palavreiro.game.MestreData,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    seed: Long = System.nanoTime(),
    autoStart: Boolean = false,
) {
    var run by remember { mutableIntStateOf(0) }
    val game = remember(run) { com.dmwnezes.palavreiro.game.MestreGame(data, Random(seed + run)) }
    var best by remember { mutableIntStateOf(store?.int("mestre_best") ?: 0) }
    var started by remember { mutableStateOf(autoStart) }
    var showEnd by remember(run) { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val timer = remember(run) { androidx.compose.animation.core.Animatable(1f) }
    val round = game.round
    val result = game.result

    // Barra de tempo de cada rodada; quando acaba, decide (pegadinha = acerto).
    LaunchedEffect(round, started, run) {
        if (!started || game.over) return@LaunchedEffect
        timer.snapTo(1f)
        timer.animateTo(0f, androidx.compose.animation.core.tween((game.seconds * 1000).toInt(), easing = androidx.compose.animation.core.LinearEasing))
        game.timeUp()
    }
    // Depois do resultado: mostra por 1,1 s e passa (ou termina).
    LaunchedEffect(result, round, run) {
        val r = result ?: return@LaunchedEffect
        if (r) { feedback?.reveal(2, com.dmwnezes.palavreiro.game.Mark.CORRECT); store?.add("mestre_right") } else feedback?.invalid()
        delay(1100)
        if (game.over) {
            store?.add("mestre_played"); store?.max("mestre_best", game.score); store?.logActivity()
            best = maxOf(best, game.score)
            feedback?.lose()
            showEnd = true
        } else game.next()
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Mestre Mandou", onBack, onHelp = { showHelp = true })
            Column(Modifier.weight(1f).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreLives(game.score, game.lives, maxOf(best, game.score))
                Spacer(Modifier.height(18.dp))
                if (!started) {
                    Spacer(Modifier.height(10.dp))
                    Text("👑", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Mestre Mandou", color = Night.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    Text(MESTRE_HELP, color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
                    Spacer(Modifier.height(26.dp))
                    PillButton("Começar", Night.correct, modifier = Modifier.fillMaxWidth()) { started = true }
                } else {
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(Shapes.pill).background(Night.surface)) {
                        Box(
                            Modifier.fillMaxWidth(timer.value).height(10.dp).clip(Shapes.pill)
                                .background(if (timer.value < 0.3f) Night.red else Night.accent)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    OrderCard(round.text)
                    Spacer(Modifier.height(18.dp))
                    round.options.chunked(2).forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            line.forEach { w ->
                                val tappedIt = w in game.tapped
                                val isTarget = w in round.targets
                                val color = when {
                                    // Durante a rodada: no TODAS, os toques certos já ficam verdes.
                                    result == null -> if (tappedIt) Night.correct else Night.surfaceHigh
                                    tappedIt && result == false -> Night.red
                                    tappedIt -> Night.correct
                                    result == false && !round.type.isTrick && round.type != com.dmwnezes.palavreiro.game.OrderType.NAO_TOQUE && isTarget -> Night.correct
                                    else -> Night.surface
                                }
                                val bg by animateColorAsState(color, label = "mestre")
                                Box(
                                    Modifier.weight(1f).height(84.dp).padding(vertical = 5.dp).clip(Shapes.card).background(bg)
                                        .clickable(enabled = result == null) { game.tap(w) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        w.uppercase(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        fontSize = if (w.length > 9) 15.sp else 18.sp,
                                        color = if (bg == Night.correct || bg == Night.red) Ink else Night.text,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                        if (result != null) {
                            Text(
                                game.message, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                color = if (result) Night.correct else Night.red,
                            )
                        }
                    }
                }
            }
        }
        if (showEnd) {
            EndSheet(
                "Você fez ${game.score} ${if (game.score == 1) "ponto" else "pontos"}", game.score, best, store?.int("mestre_right") ?: 0,
                "Palavreiro · Mestre Mandou\nFiz ${game.score} ${if (game.score == 1) "ponto" else "pontos"} 👑",
                onAgain = { run++; started = true },
            ) { showEnd = false }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Mestre Mandou")
                Spacer(Modifier.height(12.dp))
                Text(MESTRE_HELP, color = Night.text, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

private const val MESTRE_HELP = "Faça o que a ordem pede, mas só quando começar com \"O mestre mandou\". " +
    "Se o mestre não mandou, não toque em nada e espere o tempo acabar. Cuidado com o NÃO e com o TODAS. Você tem 3 vidas."

/** Cartão da ordem: "NÃO" e "TODAS" ganham destaque amarelo. */
@Composable
private fun OrderCard(text: String) {
    val styled = androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val marks = Regex("NÃO|TODAS").findAll(text).toList()
        marks.forEach { m ->
            append(text.substring(i, m.range.first))
            pushStyle(androidx.compose.ui.text.SpanStyle(color = Night.present, fontWeight = FontWeight.ExtraBold))
            append(m.value); pop()
            i = m.range.last + 1
        }
        append(text.substring(i))
    }
    Text(
        styled, color = Night.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 28.sp,
        modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(horizontal = 18.dp, vertical = 24.dp),
    )
}
