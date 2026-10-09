package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

/** Tela do Termo, Infinito, Dueto e Quarteto. */
@Composable
fun GameScreen(
    game: TermoGame,
    feedback: Feedback?,
    onBack: () -> Unit,
    onPlayInfinite: () -> Unit,
    onHelp: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    var confettiKey by remember { mutableIntStateOf(0) }
    var showConfetti by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    LaunchedEffect(game) {
        game.refreshIfNewDay()
        game.events = object : GameEvents {
            override fun onType() { feedback?.type() }
            override fun onInvalid(message: String) { feedback?.invalid(); toast.show(message) }
            override fun onRevealTile(index: Int, mark: Mark) { feedback?.reveal(index, mark) }
            override fun onBoardSolved(board: Int) { feedback?.win(); toast.show("Palavra ${board + 1} certa!") }
            override fun onWin(tries: Int) {
                feedback?.win()
                val spare = game.maxTries - tries
                toast.show(
                    when {
                        tries == 1 -> "Genial!"
                        spare >= 4 -> "Magnífico!"
                        spare >= 2 -> "Muito bem!"
                        spare == 1 -> "Acertou!"
                        else -> "Ufa, por pouco!"
                    }
                )
                confettiKey++
                showConfetti = true
                scope.launch { delay(1800); showResult = true }
            }
            override fun onLose(answers: List<String>) {
                feedback?.lose()
                toast.show(if (answers.size == 1) "A palavra era ${answers[0]}" else "Faltou: ${answers.joinToString(", ")}", 3500)
                scope.launch { delay(2200); showResult = true }
            }
        }
        if (game.over) showResult = true
    }

    // Revelação: vira cada quadrado com 250 ms de intervalo e então fecha a rodada.
    val revealing = game.revealingRow
    LaunchedEffect(revealing) {
        if (revealing < 0) return@LaunchedEffect
        val word = game.rows[revealing]
        val firstOpen = game.answers.indices.firstOrNull { !game.isSolved(it, revealing) } ?: 0
        val marks = Rules.evaluate(word, game.answers[firstOpen])
        for (i in 0 until Words.WORD_LENGTH) {
            delay(if (i == 0) FLIP_HALF.toLong() else FLIP_STEP)
            game.events.onRevealTile(i, marks[i])
        }
        delay(FLIP_HALF + 120L)
        game.finishReveal()
    }

    val title = buildString {
        append(game.mode.title)
        if (game.mode.daily && game.mode.free && !game.isDaily) append(" · livre")
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(title = title, onBack = onBack, onHelp = onHelp)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Boards(game)
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ToastView(toast)
                    if (game.over && game.mode.free && toast.text == null && !showResult) {
                        PillButton(if (game.boards == 1) "Nova palavra" else "Jogar de novo", Night.correct) { game.newWord() }
                    }
                }
            }
            LetterKeyboard(
                colorsFor = { ch -> keyMarks(game, ch) },
                onLetter = game::type,
                onEnter = game::submit,
                onDelete = game::delete,
            )
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

/** Cor da tecla em cada tabuleiro, só com linhas já reveladas. Tabuleiro resolvido fica sem cor. */
private fun keyMarks(game: TermoGame, ch: Char): List<Mark?> {
    val revealed = game.revealedRows
    return game.answers.indices.map { b ->
        val list = game.boardGuesses(b).take(revealed)
        if (game.boards > 1 && game.isSolved(b, revealed)) null
        else Rules.keyboardMarks(list)[ch]
    }.let { if (game.boards == 1) it else it }
}

@Composable
private fun Boards(game: TermoGame) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = if (game.boards == 1) 24.dp else 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        val gap = if (game.boards == 1) 6.dp else 3.dp
        val boardGap = 12.dp
        val cols = if (game.boards == 1) 1 else 2
        val gridRows = if (game.boards == 4) 2 else 1
        val tries = game.maxTries
        val byWidth = (maxWidth - boardGap * (cols - 1) - gap * 4 * cols) / (5 * cols)
        val byHeight = (maxHeight - 50.dp - boardGap * (gridRows - 1) - gap * (tries - 1) * gridRows) / (tries * gridRows)
        val tile = minOf(byWidth, byHeight, 68.dp)
        Column(verticalArrangement = Arrangement.spacedBy(boardGap), horizontalAlignment = Alignment.CenterHorizontally) {
            for (gr in 0 until gridRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(boardGap)) {
                    for (gc in 0 until cols) {
                        val b = gr * cols + gc
                        if (b < game.boards) SingleBoard(game, b, tile, gap)
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleBoard(game: TermoGame, board: Int, tile: Dp, gap: Dp) {
    val guesses = game.boardGuesses(board)
    val solved = game.isSolved(board, game.revealedRows)
    Column(
        Modifier.alpha(if (solved && game.boards > 1 && !game.over) 0.75f else 1f),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (r in 0 until game.maxTries) {
            val g = guesses.getOrNull(r)
            val isCurrent = r == game.currentRow && !game.over && !game.isSolved(board)
            val shake = remember { Animatable(0f) }
            if (isCurrent) {
                LaunchedEffect(game.shakeTick) {
                    if (game.shakeTick == 0) return@LaunchedEffect
                    for (x in listOf(-10f, 10f, -7f, 7f, -3f, 0f)) shake.animateTo(x, tween(45))
                }
            }
            Row(
                Modifier.graphicsLayer { translationX = if (isCurrent) shake.value * density else 0f },
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (c in 0 until Words.WORD_LENGTH) {
                    when {
                        g != null -> RevealTile(g, c, tile, animate = r == game.revealingRow)
                        isCurrent -> InputTile(
                            letter = game.current[c],
                            selected = c == game.cursor && !game.busy,
                            size = tile,
                        ) { game.select(c) }
                        else -> EmptyTile(tile, faded = game.isSolved(board) && r > game.solvedAt(board))
                    }
                }
            }
        }
    }
}

private fun tileShape(size: Dp) = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.22f)

@Composable
private fun EmptyTile(size: Dp, faded: Boolean = false) {
    Box(Modifier.size(size).clip(tileShape(size)).background(Night.surface.copy(alpha = if (faded) 0.25f else 0.55f)))
}

@Composable
private fun InputTile(letter: Char?, selected: Boolean, size: Dp, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(letter) {
        if (letter != null) { pop.snapTo(1.1f); pop.animateTo(1f, tween(110)) }
    }
    val shape = tileShape(size)
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(shape)
            .background(Night.surfaceHigh)
            .border(if (selected) (if (size > 40.dp) 3.dp else 2.dp) else 1.5.dp, if (selected) Night.accent else Night.outline, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter?.toString().orEmpty(), fontSize = (size.value * 0.48f).sp, fontWeight = FontWeight.SemiBold, color = Night.text)
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.1f)
                    .width(size * 0.36f).height(if (size > 40.dp) 3.dp else 2.dp).clip(Shapes.pill).background(Night.accent)
            )
        }
    }
}

/** Quadrado já enviado. Se [animate], vira no seu tempo e só então mostra a cor. */
@Composable
private fun RevealTile(g: Guess, index: Int, size: Dp, animate: Boolean) {
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
    val shape = tileShape(size)
    Box(
        Modifier
            .size(size)
            .graphicsLayer { rotationX = rot.value; cameraDistance = 12f * density }
            .clip(shape)
            .background(if (mark != null) Night.mark(mark) else Night.surfaceHigh)
            .then(if (mark == null) Modifier.border(1.5.dp, Night.outline, shape) else Modifier),
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

/** Janela do fim da partida: resultado, estatísticas do modo e próximos passos. */
@Composable
private fun ResultSheet(game: TermoGame, onClose: () -> Unit, onNewWord: () -> Unit, onPlayInfinite: () -> Unit) {
    val context = LocalContext.current
    val stats = remember(game.over, game.rows.size) { game.stats() }
    BottomSheet(onClose) {
        val plural = game.boards > 1
        SheetTitle(
            if (game.won) "Você acertou!" else "Não foi dessa vez",
            (if (plural) "As palavras eram " else "A palavra era ") + game.answerDisplay(),
        )
        Spacer(Modifier.height(18.dp))
        StatsRow(stats)
        Spacer(Modifier.height(16.dp))
        Distribution(stats.dist, highlight = if (game.won) game.rows.size else -1, rows = game.maxTries)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                val grids = game.answers.indices.joinToString("\n\n") { b ->
                    game.boardGuesses(b).joinToString("\n") { g ->
                        g.marks.joinToString("") { when (it) { Mark.CORRECT -> "🟩"; Mark.PRESENT -> "🟨"; Mark.ABSENT -> "⬛" } }
                    }
                }
                val score = if (game.won) "${game.rows.size}/${game.maxTries}" else "X/${game.maxTries}"
                val text = "Palavreiro · ${game.mode.title} $score\n\n$grids"
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar")
                )
            }
            when {
                game.mode == Mode.INFINITO -> PillButton("Nova palavra", Night.correct, modifier = Modifier.weight(1f), onClick = onNewWord)
                game.mode.free -> PillButton("Jogar de novo", Night.correct, modifier = Modifier.weight(1f), onClick = onNewWord)
                else -> PillButton("Jogar Infinito", Night.correct, modifier = Modifier.weight(1f), onClick = onPlayInfinite)
            }
        }
        if (game.mode.daily && game.isDaily) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (game.mode.free) "Desafio do dia concluído. Novas partidas são livres." else "Uma palavra nova aparece amanhã.",
                fontSize = 13.sp, color = Night.muted,
            )
        }
    }
}

/** Ajuda com exemplos. */
@Composable
fun HelpSheet(onClose: () -> Unit) {
    BottomSheet(onClose) {
        Column(Modifier.fillMaxWidth()) {
            Text("Como jogar", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
            Spacer(Modifier.height(10.dp))
            Text("Descubra a palavra de 5 letras em até 6 tentativas. Os acentos aparecem sozinhos.", color = Night.text, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            Example("PEDRA", 0, Mark.CORRECT, "O P está no lugar certo.")
            Example("CAMPO", 2, Mark.PRESENT, "O M está na palavra, mas em outro lugar.")
            Example("TERMO", 4, Mark.ABSENT, "O O não está na palavra.")
            Spacer(Modifier.height(8.dp))
            Text("Toque num quadrado da linha para escolher onde a próxima letra entra.", color = Night.muted, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("No Dueto (7 tentativas) e no Quarteto (9) você descobre 2 ou 4 palavras ao mesmo tempo. Cada tecla mostra as cores de cada tabuleiro.", color = Night.muted, fontSize = 14.sp)
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
            LetterBox(
                ch, 34.dp,
                bg = if (m != null) Night.mark(m) else Color.Transparent,
                border = if (m == null) Night.outline else null,
                textColor = if (m != null) Night.onMark(m) else Night.text,
            )
        }
    }
    Text(text, color = Night.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
}
