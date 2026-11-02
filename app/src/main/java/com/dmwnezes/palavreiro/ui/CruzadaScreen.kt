package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Cruzada
import com.dmwnezes.palavreiro.game.CruzadaGame
import com.dmwnezes.palavreiro.game.CruzadaQ
import com.dmwnezes.palavreiro.game.Words
import java.time.LocalDate
import kotlin.random.Random

private const val CRUZADA_HELP = "Toque numa casa e digite. Toque de novo na mesma casa para trocar entre horizontal e vertical, ou toque numa pista.\n\n" +
    "Conferir deixa verdes e travadas as letras certas e marca as erradas. Revelar mostra a letra da casa escolhida. Termine com a grade toda verde."

/** Palavra Cruzada: uma grade por dia e infinitas sorteadas. */
@Composable
fun CruzadaScreen(list: List<CruzadaQ>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("cruz_mode") == "inf") }
    var round by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val key = if (infinite) "cruz_inf_state" else "cruz_state"
    val game = remember(infinite, round) {
        if (list.isEmpty()) return@remember null
        // Guardado: "marca;índice;estado". No dia, a marca é a data; no Infinito, vale até pedir outra grade.
        val saved = store?.text(key)?.split(';', limit = 3)?.takeIf { it.size == 3 && (infinite || it[0] == day) }
        val idx = saved?.get(1)?.toIntOrNull()?.takeIf { it in list.indices }
            ?: if (infinite) Random.nextInt(list.size) else Cruzada.dailyIndex(Words.dayIndex(date), list.size)
        CruzadaGame(list[idx]).also { g ->
            saved?.get(2)?.let(g::restore)
            g.firstFree()
            store?.setText(key, "$day;$idx;${g.save()}")
        }
    }
    val idx = game?.let { list.indexOf(it.q) } ?: 0
    fun changed() {
        tick++
        store?.setText(key, "$day;$idx;${game!!.save()}")
    }
    fun maybeWon(wasWon: Boolean) {
        val g = game ?: return
        if (g.won && !wasWon) {
            confetti++
            feedback?.win()
            store?.let { s ->
                s.add("cruz_won")
                if (g.checks <= 1) s.add("cruz_perfect")
                s.logActivity()
                if (!infinite) { s.setText("cruz_done", day); s.setResult("cruzada", day, true) }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Palavra Cruzada", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v ->
                infinite = v
                store?.setText("cruz_mode", if (v) "inf" else "day")
            }
            if (game == null) {
                Text("Não foi possível carregar as grades.", color = Night.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            tick.let { _ ->
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (game.won) "Resolvida com ${game.checks} ${if (game.checks == 1) "conferência" else "conferências"}! ✏️"
                        else "${if (game.across) "Horizontal" else "Vertical"} · ${game.q.clues[game.clue]}",
                        color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center, minLines = 2,
                    )
                    Spacer(Modifier.height(10.dp))
                    CruzadaGrid(game) { y, x -> game.select(y, x); feedback?.type(); tick++ }
                    Spacer(Modifier.height(12.dp))
                    if (game.won) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                                val t = "Palavra Cruzada ${if (infinite) "" else shortDay(date) + " "}✏️ resolvida com ${game.checks} ${if (game.checks == 1) "conferência" else "conferências"}\nhttps://dmwnezes.com/termo/"
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                            }
                            PillButton(if (infinite) "Outra grade" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                                store?.setText("cruz_inf_state", null)
                                store?.setText("cruz_mode", "inf")
                                infinite = true
                                round++
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Revelar letra", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                                val was = game.won
                                game.reveal(); feedback?.type(); maybeWon(was); changed()
                            }
                            PillButton("Conferir", Night.correct, modifier = Modifier.weight(1f)) {
                                val was = game.won
                                val bad = game.check()
                                if (bad > 0) feedback?.invalid()
                                maybeWon(was); changed()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    CruzadaClues(game) { i -> game.selectClue(i); feedback?.type(); tick++ }
                    Spacer(Modifier.height(8.dp))
                }
                if (!game.won) {
                    LetterKeyboard(
                        enterLabel = "OK",
                        onLetter = { ch -> game.type(ch); feedback?.type(); changed() },
                        onEnter = {
                            val was = game.won
                            val bad = game.check()
                            if (bad > 0) feedback?.invalid()
                            maybeWon(was); changed()
                        },
                        onDelete = { game.delete(); feedback?.type(); changed() },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar a Palavra Cruzada")
                Spacer(Modifier.height(12.dp))
                Text(CRUZADA_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

@Composable
// O jogo não é um estado do Compose: sem isso, a grade não se redesenha a cada jogada.
@androidx.compose.runtime.NonSkippableComposable
private fun CruzadaGrid(g: CruzadaGame, onCell: (Int, Int) -> Unit) {
    val numbers = mapOf((0 to 0) to 1, (0 to 2) to 2, (0 to 4) to 3, (2 to 0) to 4, (4 to 0) to 5)
    Column(Modifier.widthIn(max = 240.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (y in 0 until Cruzada.SIZE) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (x in 0 until Cruzada.SIZE) {
                val open = g.q.letter(y, x) != null
                val here = !g.won && y == g.r && x == g.c
                val bg = when {
                    !open -> Color.Transparent
                    g.locked[y][x] -> Night.correct
                    g.wrong[y][x] -> Night.red.copy(alpha = 0.6f)
                    !g.won && g.inWord(y, x) -> Night.outline
                    else -> Night.surfaceHigh
                }
                val letter = g.letters[y][x]
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(shape).background(bg)
                        .then(if (here) Modifier.border(2.dp, Night.accent, shape) else Modifier)
                        .then(if (open) Modifier.clickable { onCell(y, x) }.semantics { contentDescription = "Linha ${y + 1}, coluna ${x + 1}, ${if (letter == ' ') "vazia" else letter}" } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    numbers[y to x]?.let {
                        Text("$it", fontSize = 10.sp, color = if (g.locked[y][x]) Night.text else Night.muted, modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 2.dp))
                    }
                    if (open && letter != ' ') Text("$letter", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                }
            }
        }
    }
}

@Composable
@androidx.compose.runtime.NonSkippableComposable
private fun CruzadaClues(g: CruzadaGame, onClue: (Int) -> Unit) {
    @Composable fun block(title: String, range: IntRange, label: (Int) -> Int) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Night.muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        for (i in range) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (!g.won && g.clue == i) Night.surfaceHigh else Color.Transparent)
                    .clickable { onClue(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text("${label(i)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Night.accent)
                Spacer(Modifier.width(8.dp))
                Text(g.q.clues[i], fontSize = 14.sp, color = Night.text)
            }
        }
    }
    block("Horizontais", 0..2) { listOf(1, 4, 5)[it] }
    Spacer(Modifier.height(8.dp))
    block("Verticais", 3..5) { it - 2 }
}
