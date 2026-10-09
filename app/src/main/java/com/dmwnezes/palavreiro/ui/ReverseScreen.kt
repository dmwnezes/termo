package com.dmwnezes.palavreiro.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.ReverseGame
import com.dmwnezes.palavreiro.game.Words

/** Reverso: o app tenta adivinhar a palavra que você pensou. */
@Composable
fun ReverseScreen(words: Words, store: Store?, feedback: Feedback?, onBack: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { ReverseGame(words) }
    var started by remember(round) { mutableStateOf(false) }
    var recorded by remember(round) { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }

    LaunchedEffect(game, game.over) {
        if (!game.over || game.contradiction || recorded) return@LaunchedEffect
        recorded = true
        store?.add("rev_played")
        store?.logActivity()
        if (game.appWon) { store?.add("rev_appwins"); feedback?.win() } else { store?.add("rev_userwins"); feedback?.lose(); confetti++ }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Reverso", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!started) {
                    Spacer(Modifier.height(30.dp))
                    FiveSquares(square = 24.dp, gap = 6.dp, animated = true)
                    Spacer(Modifier.height(24.dp))
                    Text("Pense numa palavra de 5 letras", color = Night.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Agora é o Palavreiro que tenta adivinhar. A cada chute, toque nos quadrados para marcar as cores: " +
                            "verde se a letra está no lugar certo, amarelo se está na palavra em outro lugar, cinza se não está.",
                        color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("O app tem 6 chances.", color = Night.muted, fontSize = 15.sp)
                    Spacer(Modifier.height(28.dp))
                    PillButton("Já pensei!", Night.correct, modifier = Modifier.fillMaxWidth()) { started = true }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("Chute ${minOf(game.history.size + 1, game.maxTries)} de ${game.maxTries}", color = Night.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    game.history.forEach { h ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            h.display.forEachIndexed { i, ch ->
                                LetterBox(ch, 46.dp, bg = Night.mark(h.marks[i]), border = null, textColor = Night.onMark(h.marks[i]))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (!game.over) {
                        Spacer(Modifier.height(10.dp))
                        Text("Meu chute é…", color = Night.text, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            words.display(game.guess).forEachIndexed { i, ch ->
                                MarkTile(ch, game.marks[i], 58.dp) { game.cycle(i); feedback?.type() }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Toque nas letras para trocar a cor", color = Night.muted, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${game.remaining} palavras ainda possíveis", color = Night.muted, fontSize = 13.sp)
                    } else {
                        Spacer(Modifier.height(18.dp))
                        val (title, sub) = when {
                            game.appWon -> "Acertei!" to "Adivinhei em ${game.history.size} ${if (game.history.size == 1) "chute" else "chutes"}."
                            game.contradiction -> "Hmm, não conheço essa…" to "Nenhuma palavra que eu conheço combina com essas cores. Confere se marcou tudo certo? Você pode desfazer a última."
                            else -> "Você venceu!" to "Não consegui adivinhar em ${game.maxTries} chutes. Boa escolha de palavra!"
                        }
                        Text(title, color = Night.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text(sub, color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("${store?.int("rev_appwins") ?: 0}", "O app acertou")
                        StatItem("${store?.int("rev_userwins") ?: 0}", "Você venceu")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (started) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!game.over) {
                        PillButton("Desfazer", Night.surfaceHigh, Night.text, Modifier.weight(1f), enabled = game.history.isNotEmpty()) { game.undo() }
                        PillButton("Enviar cores", Night.correct, modifier = Modifier.weight(1.4f)) { game.answer() }
                    } else {
                        if (game.contradiction) PillButton("Desfazer", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { game.undo() }
                        PillButton("Pensar em outra", Night.correct, modifier = Modifier.weight(1.4f)) { round++ }
                    }
                }
            }
        }
        if (confetti > 0) Confetti(confetti) { confetti = 0 }
    }
}

@Composable
private fun MarkTile(ch: Char, mark: Mark, size: Dp, onClick: () -> Unit) {
    val bg by animateColorAsState(Night.mark(mark), label = "cor")
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        Modifier.size(size).clip(shape).background(bg).border(2.dp, Night.text.copy(alpha = 0.25f), shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(ch.toString(), fontSize = (size.value * 0.48f).sp, fontWeight = FontWeight.SemiBold, color = Night.onMark(mark))
    }
}
