package com.dmwnezes.palavreiro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.MemLevel
import com.dmwnezes.palavreiro.game.MemoryGame
import com.dmwnezes.palavreiro.game.SynPair
import kotlinx.coroutines.delay
import kotlin.random.Random

private val MInk = Color(0xFF14102C)

private const val MEMORIA_HELP = "As cartas estão viradas para baixo. Vire duas de cada vez: se forem uma palavra e o seu sinônimo " +
    "(ex.: BONITO e BELO), o par fica verde. Se não forem, as cartas voltam a se esconder — tente lembrar onde estavam!\n\n" +
    "Cada par virado conta uma jogada. Ache todos os pares no menor tempo e com menos jogadas. " +
    "Fácil: 6 pares · Médio: 8 pares · Difícil: 12 pares."

/** Jogo da Memória: ache os pares de sinônimos. */
@Composable
fun MemoryScreen(
    pairs: List<SynPair>,
    families: List<Set<String>>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    seed: Long = System.nanoTime(),
    initial: MemoryGame? = null,
    initialSeconds: Int = 0,
) {
    var level by remember { mutableStateOf(initial?.level ?: runCatching { MemLevel.valueOf(store?.text("mem_level") ?: "") }.getOrDefault(MemLevel.FACIL)) }
    var round by remember { mutableIntStateOf(0) }
    val game = remember(level, round) {
        if (round == 0 && initial != null && initial.level == level) initial else MemoryGame(level, pairs, families, Random(seed + round * 31 + level.ordinal))
    }
    val fresh = initial == null || game !== initial
    var seconds by remember(game) { mutableIntStateOf(if (game === initial) initialSeconds else 0) }
    var showWin by remember(game) { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var newRecord by remember(game) { mutableStateOf(false) }
    val won = game.won

    // Relógio: começa no primeiro toque e para ao terminar.
    LaunchedEffect(game, game.started, won) {
        if (!game.started || won) return@LaunchedEffect
        while (true) { delay(1000); seconds++ }
    }
    // Par errado: fica à mostra ~0,9 s e vira de volta.
    LaunchedEffect(game, game.locked) {
        if (!game.locked) return@LaunchedEffect
        delay(900)
        game.hideMismatch()
    }
    LaunchedEffect(game, won) {
        if (!won) return@LaunchedEffect
        if (fresh) {
            val before = store?.int(level.timeKey) ?: 0
            newRecord = before == 0 || seconds < before
            store?.min(level.timeKey, maxOf(1, seconds)); store?.min(level.movesKey, game.moves); store?.logActivity()
            feedback?.win()
        }
        delay(700)
        showWin = true
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Jogo da Memória", onBack, onHelp = { showHelp = true })
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                LevelSwitch(level) { level = it; round++; store?.setText("mem_level", it.name) }
                Spacer(Modifier.height(12.dp))
                val bestTime = store?.int(level.timeKey) ?: 0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${game.moves}", "Jogadas")
                    StatItem(formatTime(seconds), "Tempo")
                    StatItem(if (bestTime > 0) formatTime(bestTime) else "—", "Recorde")
                }
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    val gap = 8.dp
                    val cols = level.cols
                    val rows = level.rows
                    val w = (maxWidth - gap * (cols - 1)) / cols
                    val h = (maxHeight - gap * (rows - 1) - 8.dp) / rows
                    val cellH = min(h, w * 1.3f)
                    val cellW = min(w, cellH * 1.1f)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        (0 until rows).forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                (0 until cols).forEach { c ->
                                    val i = r * cols + c
                                    if (i < game.cards.size) {
                                        MemCardView(game, i, cellW, cellH) {
                                            when (game.flip(i)) {
                                                MemoryGame.Flip.MATCH -> feedback?.reveal(2, Mark.CORRECT)
                                                MemoryGame.Flip.MISS -> feedback?.reveal(0, Mark.ABSENT)
                                                MemoryGame.Flip.FIRST -> feedback?.type()
                                                MemoryGame.Flip.IGNORED -> {}
                                            }
                                        }
                                    } else Spacer(Modifier.size(cellW, cellH))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showWin) {
            BottomSheet({ showWin = false }) {
                SheetTitle("Você achou todos os pares! 🎉", "Nível ${level.label} · ${level.pairs} pares")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(formatTime(seconds), "Tempo")
                    StatItem("${game.moves}", "Jogadas")
                    val bt = store?.int(level.timeKey) ?: 0
                    StatItem(if (bt > 0) formatTime(bt) else formatTime(seconds), "Recorde")
                }
                if (newRecord) {
                    Spacer(Modifier.height(10.dp))
                    Text("Novo recorde de tempo! 🏅", color = Night.present, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(20.dp))
                PillButton("Jogar de novo", Night.correct, modifier = Modifier.fillMaxWidth()) { round++ }
            }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar a Memória")
                Spacer(Modifier.height(12.dp))
                Text(MEMORIA_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

@Composable
private fun LevelSwitch(level: MemLevel, onChange: (MemLevel) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.surface).padding(4.dp)) {
        MemLevel.entries.forEach { l ->
            val on = l == level
            Box(
                Modifier.weight(1f).clip(Shapes.pill).background(if (on) Night.accent else Color.Transparent)
                    .clickable { if (!on) onChange(l) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(l.label, color = if (on) MInk else Night.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/** Uma carta: vira em 3D; par achado fica verde com um "pulinho". */
@Composable
private fun MemCardView(game: MemoryGame, i: Int, w: Dp, h: Dp, onClick: () -> Unit) {
    val up = game.isUp(i)
    val matched = i in game.matched
    val wrong = game.locked && i in game.faceUp
    val rot by animateFloatAsState(if (up) 180f else 0f, tween(320), label = "virar")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(matched) {
        if (matched) { pop.animateTo(1.12f, tween(140)); pop.animateTo(1f, tween(180)) }
    }
    val face by animateColorAsState(
        when { matched -> Night.correct; wrong -> Night.red; else -> Night.surfaceHigh }, label = "cor",
    )
    val shape = RoundedCornerShape(16.dp)
    val word = game.cards[i].word
    Box(
        Modifier.size(w, h)
            .graphicsLayer { rotationY = rot; cameraDistance = 12f * density; scaleX = pop.value; scaleY = pop.value }
            .clip(shape)
            .clickable(enabled = !up && !game.locked, onClick = onClick)
            .semantics { contentDescription = if (up) "Carta $word${if (matched) ", par achado" else ""}" else "Carta ${i + 1}, virada para baixo" },
        contentAlignment = Alignment.Center,
    ) {
        if (rot <= 90f) {
            // Verso: roxo com a marquinha de quadrados.
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Night.accent, Color(0xFF6A58D8))))
                    .border(2.dp, Color.White.copy(alpha = 0.25f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.85f))
            }
        } else {
            Box(
                Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.background(face)
                    .border(2.dp, if (matched) Color.White.copy(alpha = 0.5f) else Night.outline, shape).padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                FitWord(word, if (matched || wrong) MInk else Night.text)
            }
        }
    }
}

/** Palavra da carta: começa em 15sp e encolhe até caber (até 2 linhas). */
@Composable
private fun FitWord(text: String, color: Color) {
    var size by remember(text) { mutableFloatStateOf(15f) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text, fontSize = size.sp, lineHeight = (size + 2).sp, fontWeight = FontWeight.SemiBold, color = color, textAlign = TextAlign.Center,
        maxLines = 2,
        onTextLayout = { r -> if ((r.didOverflowWidth || r.didOverflowHeight || r.lineCount > 1 && ' ' !in text && '-' !in text) && size > 8f) size -= 0.5f else ready = true },
        modifier = Modifier.drawWithContent { if (ready) drawContent() },
    )
}
