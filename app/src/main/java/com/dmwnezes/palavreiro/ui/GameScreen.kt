package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.game.GameEvents
import com.dmwnezes.palavreiro.game.Guess
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.Rules
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FLIP_STEP = 250L
private const val FLIP_HALF = 160

@Composable
fun GameScreen(
    game: TermoGame,
    feedback: Feedback?,
    onBack: () -> Unit,
    onPlayInfinite: () -> Unit,
    onHelp: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }
    var toastId by remember { mutableIntStateOf(0) }
    var confettiKey by remember { mutableIntStateOf(0) }
    var showConfetti by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    fun say(msg: String, ms: Long = 1600) {
        toast = msg
        val id = ++toastId
        scope.launch { delay(ms); if (toastId == id) toast = null }
    }

    LaunchedEffect(game) {
        game.refreshIfNewDay()
        game.events = object : GameEvents {
            override fun onType() { feedback?.type() }
            override fun onInvalid(message: String) { feedback?.invalid(); say(message) }
            override fun onRevealTile(index: Int, mark: Mark) { feedback?.reveal(index, mark) }
            override fun onWin(tries: Int) {
                feedback?.win()
                say(listOf("Genial!", "Magnífico!", "Muito bem!", "Boa!", "Acertou!", "Ufa, por pouco!")[tries - 1])
                confettiKey++
                showConfetti = true
                scope.launch { delay(1800); showResult = true }
            }
            override fun onLose(answer: String) {
                feedback?.lose()
                say("A palavra era $answer", 3500)
                scope.launch { delay(2200); showResult = true }
            }
        }
        if (game.over) showResult = true
    }

    // Revelação: vira cada quadrado com 250 ms de intervalo e então fecha a rodada.
    val revealing = game.revealingRow
    LaunchedEffect(revealing) {
        if (revealing < 0) return@LaunchedEffect
        val g = game.guesses[revealing]
        for (i in 0 until Words.WORD_LENGTH) {
            delay(if (i == 0) FLIP_HALF.toLong() else FLIP_STEP)
            game.events.onRevealTile(i, g.marks[i])
        }
        delay(FLIP_HALF + 120L)
        game.finishReveal()
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(
                title = if (game.mode == Mode.DIARIO) "Termo" else "Infinito",
                onBack = onBack,
                onHelp = onHelp,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Board(game)
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(toast != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            toast.orEmpty(),
                            color = Color(0xFF1A1438),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(Shapes.pill).background(Night.text).padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                    if (game.over && game.mode == Mode.INFINITO && toast == null) {
                        PillButton("Nova palavra", Night.correct) { showResult = false; game.newWord() }
                    }
                }
            }
            Keyboard(game)
            Spacer(Modifier.height(10.dp))
        }
        if (showConfetti) Confetti(confettiKey) { showConfetti = false }
        if (showResult && game.over) {
            ResultSheet(
                game = game,
                onClose = { showResult = false },
                onNewWord = { showResult = false; game.newWord() },
                onPlayInfinite = { showResult = false; onPlayInfinite() },
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, onHelp: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Night.text) }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, modifier = Modifier.weight(1f).padding(start = 4.dp))
        IconButton(onClick = onHelp) { Icon(Icons.Rounded.HelpOutline, "Como jogar", tint = Night.muted) }
    }
}

@Composable
private fun Board(game: TermoGame) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        val byWidth = (maxWidth - 6.dp * 4) / 5
        val byHeight = (maxHeight - 6.dp * 5 - 60.dp) / 6
        val tile = minOf(byWidth, byHeight, 68.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until Rules.MAX_TRIES) {
                val g = game.guesses.getOrNull(r)
                val isCurrent = r == game.currentRow && !game.over
                val shake = remember { Animatable(0f) }
                if (isCurrent) {
                    LaunchedEffect(game.shakeTick) {
                        if (game.shakeTick == 0) return@LaunchedEffect
                        for (x in listOf(-10f, 10f, -7f, 7f, -3f, 0f)) shake.animateTo(x, tween(45))
                    }
                }
                Row(
                    Modifier.graphicsLayer { translationX = if (isCurrent) shake.value * density else 0f },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (c in 0 until Words.WORD_LENGTH) {
                        when {
                            g != null -> RevealTile(g, c, tile, animate = r == game.revealingRow)
                            isCurrent -> InputTile(
                                letter = game.current[c],
                                selected = c == game.cursor && !game.busy,
                                size = tile,
                            ) { game.select(c) }
                            else -> EmptyTile(tile)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTile(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(Shapes.tile).background(Night.surface.copy(alpha = 0.55f)))
}

@Composable
private fun InputTile(letter: Char?, selected: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(letter) {
        if (letter != null) { pop.snapTo(1.1f); pop.animateTo(1f, tween(110)) }
    }
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(Shapes.tile)
            .background(Night.surfaceHigh)
            .border(if (selected) 3.dp else 1.5.dp, if (selected) Night.accent else Night.outline, Shapes.tile)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter?.toString().orEmpty(), fontSize = (size.value * 0.48f).sp, fontWeight = FontWeight.SemiBold, color = Night.text)
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                    .width(size * 0.36f).height(3.dp).clip(Shapes.pill).background(Night.accent)
            )
        }
    }
}

/** Quadrado já enviado. Se [animate], vira no seu tempo e só então mostra a cor. */
@Composable
private fun RevealTile(g: Guess, index: Int, size: androidx.compose.ui.unit.Dp, animate: Boolean) {
    val rot = remember(g) { Animatable(0f) }
    // Decide só uma vez se este quadrado anima, para o fim da rodada não interromper a virada.
    val shouldAnimate = remember(g) { animate }
    var shown by remember(g) { mutableStateOf(!shouldAnimate) }
    LaunchedEffect(g) {
        if (!shouldAnimate) return@LaunchedEffect
        delay(index * FLIP_STEP)
        rot.animateTo(90f, tween(FLIP_HALF))
        shown = true
        rot.animateTo(0f, tween(FLIP_HALF))
    }
    val mark = if (shown) g.marks[index] else null
    Box(
        Modifier
            .size(size)
            .graphicsLayer { rotationX = rot.value; cameraDistance = 12f * density }
            .clip(Shapes.tile)
            .background(if (mark != null) Night.mark(mark) else Night.surfaceHigh)
            .then(if (mark == null) Modifier.border(1.5.dp, Night.outline, Shapes.tile) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            g.display[index].toString(),
            fontSize = (size.value * 0.48f).sp,
            fontWeight = FontWeight.SemiBold,
            color = if (mark != null) Night.onMark(mark) else Night.text,
        )
    }
}

@Composable
private fun Keyboard(game: TermoGame) {
    // Só pinta o teclado com linhas já reveladas.
    val revealed = if (game.revealingRow >= 0) game.guesses.take(game.revealingRow) else game.guesses.toList()
    val marks = Rules.keyboardMarks(revealed)
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEachIndexed { idx, letters ->
            Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (idx == 1) Spacer(Modifier.weight(0.5f))
                if (idx == 2) KeyButton("ENTER", Modifier.weight(1.6f), null, small = true) { game.submit() }
                letters.forEach { ch -> KeyButton(ch.toString(), Modifier.weight(1f), marks[ch]) { game.type(ch) } }
                if (idx == 2) KeyButton("⌫", Modifier.weight(1.6f), null, icon = true) { game.delete() }
                if (idx == 1) Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier,
    mark: Mark?,
    small: Boolean = false,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (mark != null) Night.mark(mark) else Night.key
    Box(
        modifier.height(56.dp).clip(Shapes.key).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(Icons.AutoMirrored.Rounded.Backspace, "Apagar", tint = Night.text, modifier = Modifier.size(22.dp))
        } else {
            Text(
                label,
                fontSize = if (small) 13.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (mark != null) Night.onMark(mark) else Night.text,
            )
        }
    }
}

@Composable
fun PillButton(text: String, color: Color, textColor: Color = Color(0xFF14102C), modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(Shapes.pill).background(color).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

/** Janela do fim da partida: resultado, estatísticas do modo e próximos passos. */
@Composable
private fun ResultSheet(game: TermoGame, onClose: () -> Unit, onNewWord: () -> Unit, onPlayInfinite: () -> Unit) {
    val context = LocalContext.current
    val stats = remember(game.over, game.guesses.size) { game.stats() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.sheet)
                .background(Night.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(Shapes.pill).background(Night.outline))
            Spacer(Modifier.height(16.dp))
            Text(if (game.won) "Você acertou!" else "Não foi dessa vez", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
            Spacer(Modifier.height(4.dp))
            Text("A palavra era ${game.answerDisplay()}", fontSize = 16.sp, color = Night.muted)
            Spacer(Modifier.height(18.dp))
            StatsRow(stats)
            Spacer(Modifier.height(16.dp))
            Distribution(stats.dist, highlight = if (game.won) game.guesses.size else -1)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                    val text = Rules.shareText(game.mode.title, game.guesses, game.won)
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar")
                    )
                }
                if (game.mode == Mode.INFINITO) PillButton("Nova palavra", Night.correct, modifier = Modifier.weight(1f), onClick = onNewWord)
                else PillButton("Jogar Infinito", Night.correct, modifier = Modifier.weight(1f), onClick = onPlayInfinite)
            }
            if (game.mode == Mode.DIARIO) {
                Spacer(Modifier.height(12.dp))
                Text("Uma palavra nova aparece amanhã.", fontSize = 13.sp, color = Night.muted)
            }
        }
    }
}

@Composable
fun StatsRow(stats: com.dmwnezes.palavreiro.data.Stats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem("${stats.played}", "Jogos")
        StatItem("${stats.winPct}%", "Vitórias")
        StatItem("${stats.streak}", "Sequência")
        StatItem("${stats.maxStreak}", "Melhor")
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
        Text(label, fontSize = 12.sp, color = Night.muted)
    }
}

@Composable
fun Distribution(dist: List<Int>, highlight: Int) {
    val max = maxOf(1, dist.maxOrNull() ?: 1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        dist.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 13.sp, color = Night.muted, modifier = Modifier.width(16.dp))
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(maxOf(0.08f, v.toFloat() / max))
                            .clip(Shapes.pill)
                            .background(if (i + 1 == highlight) Night.correct else Night.absent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text("$v", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Night.text)
                    }
                }
            }
        }
    }
}

/** Ajuda com exemplos. */
@Composable
fun HelpSheet(onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(Shapes.sheet).background(Night.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding().padding(24.dp),
        ) {
            Text("Como jogar", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
            Spacer(Modifier.height(10.dp))
            Text("Descubra a palavra de 5 letras em até 6 tentativas. Os acentos aparecem sozinhos.", color = Night.text, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            Example("PEDRA", 0, Mark.CORRECT, "O P está no lugar certo.")
            Example("CAMPO", 2, Mark.PRESENT, "O M está na palavra, mas em outro lugar.")
            Example("TERMO", 4, Mark.ABSENT, "O O não está na palavra.")
            Spacer(Modifier.height(8.dp))
            Text("Toque num quadrado da linha para escolher onde a próxima letra entra.", color = Night.muted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

@Composable
private fun Example(word: String, index: Int, mark: Mark, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        word.forEachIndexed { i, ch ->
            val m = if (i == index) mark else null
            Box(
                Modifier.size(34.dp).clip(RoundedTile)
                    .background(if (m != null) Night.mark(m) else Color.Transparent)
                    .then(if (m == null) Modifier.border(1.5.dp, Night.outline, RoundedTile) else Modifier),
                contentAlignment = Alignment.Center,
            ) { Text("$ch", color = if (m != null) Night.onMark(m) else Night.text, fontWeight = FontWeight.SemiBold) }
        }
    }
    Text(text, color = Night.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
}

private val RoundedTile = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
