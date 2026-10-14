package com.dmwnezes.palavreiro.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.SudokuData
import com.dmwnezes.palavreiro.game.SudokuGame
import com.dmwnezes.palavreiro.game.SudokuLevel
import com.dmwnezes.palavreiro.game.SudokuPuzzle
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import kotlin.random.Random

private val SInk = Color(0xFF14102C)

private const val SUDOKU_HELP = "Quadrante é um Sudoku. Preencha a grade 9×9 com os números de 1 a 9: cada linha, cada coluna e cada bloco 3×3 " +
    "tem todos os números, sem repetir.\n\n" +
    "Toque numa casa e depois num número. Os números iniciais (em negrito, brancos) não mudam; os seus ficam lilás. Número errado fica em vermelho " +
    "e conta um erro: com 3 erros a partida acaba.\n\n" +
    "Anotações: ligue o lápis para marcar números pequenos de lembrete na casa. Apagar limpa a casa escolhida.\n\n" +
    "Do dia: um Quadrante por nível, o mesmo para todo mundo. Infinito: quantos quiser."

/** Sudoku: Do dia (um por nível) ou Infinito; progresso salvo ao sair. */
@Composable
fun SudokuScreen(
    puzzles: Map<SudokuLevel, List<SudokuPuzzle>>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    day: Long = Words.dayIndex(java.time.LocalDate.now()),
    rnd: Random = Random.Default,
    initialLevel: SudokuLevel? = null,
    initialInfinite: Boolean = false,
) {
    var level by remember { mutableStateOf(initialLevel ?: runCatching { SudokuLevel.valueOf(store?.text("sudoku_level") ?: "") }.getOrDefault(SudokuLevel.FACIL)) }
    var infinite by remember { mutableStateOf(initialInfinite) }
    var round by remember { mutableIntStateOf(0) }
    fun slot() = "sudoku_save_${level.key}_${if (infinite) "inf" else "dia"}"
    val game = remember(level, infinite, round) {
        val list = puzzles[level].orEmpty()
        val saved = SudokuGame.fromJson(store?.text(slot()))
        when {
            list.isEmpty() -> null
            !infinite -> SudokuData.daily(list, day).let { p -> saved?.takeIf { it.puzzle == p } ?: SudokuGame(p) }
            round == 0 && saved != null -> saved
            else -> SudokuGame(list[rnd.nextInt(list.size)])
        }
    }
    var showHelp by remember { mutableStateOf(false) }
    var showEnd by remember(game) { mutableStateOf(false) }
    var newRecord by remember(game) { mutableStateOf(false) }
    // Vitória já contada (partida salva que já estava terminada não conta de novo).
    var counted by remember(game) { mutableStateOf(game?.won == true) }
    var confetti by remember { mutableIntStateOf(0) }

    fun save() { game?.let { store?.setText(slot(), it.toJson()) } }
    DisposableEffect(game) { onDispose { save() } }

    if (game != null) {
        LaunchedEffect(game, game.started, game.over) {
            if (!game.started || game.over) return@LaunchedEffect
            while (true) { delay(1000); game.seconds++; if (game.seconds % 5 == 0) save() }
        }
        LaunchedEffect(game, game.won, game.lost) {
            if (game.won && !counted) {
                counted = true
                val before = store?.int(level.bestKey) ?: 0
                newRecord = before == 0 || game.seconds < before
                store?.min(level.bestKey, maxOf(1, game.seconds)); store?.add("sudoku_won")
                if (level == SudokuLevel.DIFICIL && game.errors == 0) store?.add("sudoku_perfect")
                store?.logActivity()
                save()
                feedback?.win(); confetti++
                delay(900)
                showEnd = true
            } else if (game.lost) {
                save()
                feedback?.lose()
                delay(500)
                showEnd = true
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Quadrante", { save(); onBack() }, onHelp = { showHelp = true })
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ModeSwitch(infinite) { save(); infinite = it; round = 0 }
                Spacer(Modifier.height(8.dp))
                SudokuLevelSwitch(level) { save(); level = it; round = 0; store?.setText("sudoku_level", it.name) }
                if (game == null) return@Column
                Spacer(Modifier.height(10.dp))
                val best = store?.int(level.bestKey) ?: 0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Erros ${game.errors}/${SudokuGame.MAX_ERRORS}", color = if (game.errors > 0) Night.red else Night.muted,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    Text(formatTime(game.seconds), color = Night.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (best > 0) "Recorde ${formatTime(best)}" else "Sem recorde", color = Night.muted, fontSize = 15.sp,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SudokuGrid(game, minOf(maxWidth, maxHeight)) { i -> game.selected = i; feedback?.type() }
                }
                Spacer(Modifier.height(10.dp))
                // Teclado 1–9: número já completo (9 certos na grade) fica apagado.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (d in 1..9) {
                        val done = (0 until 81).count { game.values[it] == d && !game.wrong(it) } == 9
                        Box(
                            Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (done) Night.surface else Night.key)
                                .clickable(enabled = !game.over) {
                                    when (game.input(d)) {
                                        SudokuGame.Input.RIGHT -> feedback?.reveal(2, Mark.CORRECT)
                                        SudokuGame.Input.WRONG -> feedback?.reveal(0, Mark.ABSENT)
                                        SudokuGame.Input.NOTE -> feedback?.type()
                                        SudokuGame.Input.IGNORED -> {}
                                    }
                                    save()
                                }
                                .semantics { contentDescription = "Número $d" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$d", color = if (done) Night.muted.copy(alpha = 0.5f) else Night.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Apagar", Night.surfaceHigh, Night.text, Modifier.weight(1f), enabled = !game.over) { game.erase(); save() }
                    PillButton(
                        if (game.notesMode) "✏️ Anotações: sim" else "✏️ Anotações", if (game.notesMode) Night.present else Night.surfaceHigh,
                        if (game.notesMode) SInk else Night.text, Modifier.weight(1.5f), enabled = !game.over,
                    ) { game.notesMode = !game.notesMode }
                    if (infinite) PillButton("Outro", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { save(); round++ }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showEnd && game != null) {
            BottomSheet({ showEnd = false }) {
                if (game.won) {
                    SheetTitle("Quadrante resolvido! 🎉", "Nível ${level.label}" + if (infinite) " · Infinito" else " · Do dia")
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(formatTime(game.seconds), "Tempo")
                        StatItem("${game.errors}", if (game.errors == 1) "Erro" else "Erros")
                        val bt = store?.int(level.bestKey) ?: 0
                        StatItem(formatTime(if (bt > 0) bt else game.seconds), "Recorde")
                    }
                    if (newRecord) {
                        Spacer(Modifier.height(10.dp))
                        Text("Novo recorde de tempo! 🏅", color = Night.present, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    PillButton(if (infinite) "Outro Quadrante" else "Jogar o Infinito", Night.correct, modifier = Modifier.fillMaxWidth()) {
                        if (infinite) round++ else { infinite = true; round = 1 }
                    }
                } else {
                    SheetTitle("Fim de jogo", "Você chegou a ${SudokuGame.MAX_ERRORS} erros")
                    Spacer(Modifier.height(20.dp))
                    PillButton("Tentar de novo", Night.correct, modifier = Modifier.fillMaxWidth()) { game.reset(); save(); showEnd = false }
                    if (infinite) {
                        Spacer(Modifier.height(10.dp))
                        PillButton("Outro Quadrante", Night.surfaceHigh, Night.text, Modifier.fillMaxWidth()) { round++ }
                    }
                }
            }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Quadrante")
                Spacer(Modifier.height(12.dp))
                Text(SUDOKU_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

@Composable
private fun SudokuLevelSwitch(level: SudokuLevel, onChange: (SudokuLevel) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.surface).padding(4.dp)) {
        SudokuLevel.entries.forEach { l ->
            val on = l == level
            Box(
                Modifier.weight(1f).clip(Shapes.pill).background(if (on) Night.surfaceHigh else Color.Transparent)
                    .clickable { if (!on) onChange(l) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(l.label, color = if (on) Night.text else Night.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

/** Grade 9×9 com blocos 3×3 marcados; destaca linha/coluna/bloco e os números iguais ao da casa escolhida. */
@Composable
private fun SudokuGrid(game: SudokuGame, side: androidx.compose.ui.unit.Dp, onTap: (Int) -> Unit) {
    val sel = game.selected
    val selVal = if (sel >= 0) game.values[sel] else 0
    val peers = if (sel >= 0) SudokuGame.peers(sel) else emptySet()
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.size(side).clip(shape).background(Night.surface).border(2.dp, Night.accent.copy(alpha = 0.7f), shape)
            .drawWithContent {
                drawContent()
                val c = size.width / 9f
                for (k in 1 until 9) {
                    val thick = k % 3 == 0
                    val col = if (thick) Night.accent.copy(alpha = 0.7f) else Night.outline.copy(alpha = 0.55f)
                    val w = if (thick) 2.dp.toPx() else 1.dp.toPx()
                    drawLine(col, Offset(k * c, 0f), Offset(k * c, size.height), w)
                    drawLine(col, Offset(0f, k * c), Offset(size.width, k * c), w)
                }
            },
    ) {
        for (r in 0 until 9) Row(Modifier.weight(1f).fillMaxWidth()) {
            for (c in 0 until 9) {
                val i = r * 9 + c
                val v = game.values[i]
                val wrong = game.wrong(i)
                val bg = when {
                    i == sel -> Night.accent.copy(alpha = 0.55f)
                    v != 0 && v == selVal && !wrong -> Night.accent.copy(alpha = 0.28f)
                    i in peers -> Night.surfaceHigh
                    else -> Color.Transparent
                }
                Box(
                    Modifier.weight(1f).fillMaxSize().background(bg).clickable { onTap(i) }
                        .semantics { contentDescription = "Casa ${r + 1}-${c + 1}" + if (v != 0) " $v" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (v != 0) {
                        Text(
                            "$v", fontSize = (side.value / 9 * 0.55f).sp, textAlign = TextAlign.Center,
                            fontWeight = if (game.given(i)) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = when { wrong -> Night.red; game.given(i) -> Night.text; else -> Night.accent },
                        )
                    } else if (game.notes[i] != 0) {
                        Column(Modifier.fillMaxSize().padding(1.dp)) {
                            for (nr in 0 until 3) Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (nc in 0 until 3) {
                                    val d = nr * 3 + nc + 1
                                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (game.notes[i] and (1 shl d) != 0) Text(
                                            "$d", fontSize = (side.value / 9 * 0.24f).sp, lineHeight = (side.value / 9 * 0.26f).sp,
                                            color = Night.muted, fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
