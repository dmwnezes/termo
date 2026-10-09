package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.ConnGroup
import com.dmwnezes.palavreiro.game.ConnPuzzle
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.ConnectionsGame
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Conexões: separe 16 palavras em 4 grupos de 4.
 * Desafio do dia; o progresso fica salvo e pode ser retomado.
 */
@Composable
fun ConnectionsScreen(
    puzzles: List<ConnPuzzle>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    date: LocalDate = LocalDate.now(),
) {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val day = date.toString()
    val index = ConnectionsData.dailyIndex(date, puzzles.size)
    val game = remember(day) {
        ConnectionsGame(puzzles[index], seed = Words.dayIndex(date)).also { g ->
            // Retoma as tentativas já feitas hoje.
            val saved = store?.text("conn_state")
            if (saved != null && saved.substringBefore('|') == day) {
                saved.substringAfter('|').split(';').filter { it.isNotBlank() }.forEach { t ->
                    g.deselectAll(); t.split(',').forEach(g::toggle); g.submit()
                }
            }
        }
    }
    var showResult by remember { mutableStateOf(game.over) }
    var showHelp by remember { mutableStateOf(store?.int("conn_help") == 0) }
    var confetti by remember { mutableIntStateOf(0) }

    fun persist() {
        store?.setText("conn_state", day + "|" + game.tries.joinToString(";") { it.joinToString(",") })
    }

    fun finish() {
        val st = store ?: return
        if (st.text("conn_done") == day) return
        st.setText("conn_done", day)
        st.add("conn_played")
        st.logActivity(day)
        if (game.won) {
            st.add("conn_won")
            if (game.mistakes == 0) st.add("conn_perfect")
        }
    }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(game.shakeTick) {
        if (game.shakeTick == 0) return@LaunchedEffect
        for (x in listOf(-10f, 10f, -7f, 7f, -3f, 0f)) shake.animateTo(x, tween(45))
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Conexões", onBack, onHelp = { showHelp = true })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Crie 4 grupos de 4 palavras", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                game.solved.forEach { g -> SolvedGroup(g); Spacer(Modifier.height(8.dp)) }
                // Grade das palavras que faltam (4 por linha).
                Column(
                    Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    game.tiles.chunked(4).forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            line.forEach { w ->
                                WordTile(w, selected = w in game.selected, modifier = Modifier.weight(1f)) {
                                    game.toggle(w); feedback?.type()
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Erros restantes: ", color = Night.muted, fontSize = 14.sp)
                    repeat(game.maxMistakes) { i ->
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(12.dp).clip(CircleShape)
                                .background(if (i < game.maxMistakes - game.mistakes) Night.accent else Night.absent)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                ToastView(toast)
            }
            if (!game.over) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton("Misturar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { game.shuffle() }
                    PillButton("Limpar", Night.surfaceHigh, Night.text, Modifier.weight(1f), enabled = game.selected.isNotEmpty()) { game.deselectAll() }
                    PillButton("Enviar", Night.correct, modifier = Modifier.weight(1f), enabled = game.selected.size == 4) {
                        when (val r = game.submit()) {
                            is ConnectionsGame.Result.Correct -> {
                                feedback?.reveal(r.group.level, Mark.CORRECT)
                                toast.show(r.group.name)
                            }
                            ConnectionsGame.Result.OneAway -> { feedback?.invalid(); toast.show("Falta só uma!") }
                            ConnectionsGame.Result.Wrong -> { feedback?.invalid(); toast.show("Não é um grupo") }
                            ConnectionsGame.Result.Repeated -> toast.show("Você já tentou essa")
                            ConnectionsGame.Result.Incomplete -> {}
                        }
                        persist()
                        if (game.over) {
                            finish()
                            if (game.won) { feedback?.win(); confetti++ } else feedback?.lose()
                            scope.launch { delay(1600); showResult = true }
                        }
                    }
                }
            } else {
                PillButton("Ver resultado", Night.correct, modifier = Modifier.fillMaxWidth().padding(16.dp)) { showResult = true }
            }
        }
        if (confetti > 0) Confetti(confetti) { confetti = 0 }
        if (showResult && game.over) ConnectionsResult(game, store) { showResult = false }
        if (showHelp) {
            BottomSheet({ showHelp = false; store?.setInt("conn_help", 1) }) {
                SheetTitle("Como jogar Conexões")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Encontre 4 grupos de 4 palavras que têm algo em comum. Toque em 4 palavras e em Enviar.\n\n" +
                        "Os grupos vão do mais fácil (amarelo) ao mais difícil (roxo). Cuidado com as pegadinhas: " +
                        "algumas palavras parecem caber em mais de um grupo.\n\nVocê pode errar até 4 vezes. Um desafio novo por dia.",
                    color = Night.text, fontSize = 15.sp,
                )
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false; store?.setInt("conn_help", 1) }
            }
        }
    }
}

@Composable
private fun WordTile(word: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Night.accent else Night.surfaceHigh, label = "fundo")
    val len = word.length
    Box(
        modifier.aspectRatio(1.15f).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            word,
            color = if (selected) Color(0xFF1A1438) else Night.text,
            fontWeight = FontWeight.SemiBold,
            // Tamanho pela palavra mais longa, para nunca quebrar no meio.
            fontSize = when { ' ' in word -> 11.sp; len >= 13 -> 8.5.sp; len >= 11 -> 9.5.sp; len >= 9 -> 10.5.sp; len >= 7 -> 12.sp; else -> 14.sp },
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = ' ' in word,
        )
    }
}

@Composable
private fun SolvedGroup(g: ConnGroup) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Night.levels[g.level]).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(g.name.uppercase(), color = Color(0xFF1A1438), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center)
        Text(g.words.joinToString(", "), color = Color(0xFF1A1438), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ConnectionsResult(game: ConnectionsGame, store: Store?, onClose: () -> Unit) {
    val context = LocalContext.current
    BottomSheet(onClose) {
        SheetTitle(
            if (game.won) (if (game.mistakes == 0) "Perfeito!" else "Você conseguiu!") else "Não foi dessa vez",
            if (game.won) "Erros: ${game.mistakes}" else "Volte amanhã para um desafio novo",
        )
        Spacer(Modifier.height(16.dp))
        Text(game.shareGrid(), fontSize = 22.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("${store?.int("conn_played") ?: 0}", "Jogos")
            StatItem("${store?.int("conn_won") ?: 0}", "Vitórias")
            StatItem("${store?.int("conn_perfect") ?: 0}", "Perfeitos")
        }
        Spacer(Modifier.height(20.dp))
        PillButton("Compartilhar", Night.correct, modifier = Modifier.fillMaxWidth()) {
            val text = "Palavreiro · Conexões\n\n${game.shareGrid()}"
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar"))
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Cartão para Stories", Night.accent, modifier = Modifier.fillMaxWidth()) {
            val rows = game.tries.map { t -> t.map { w -> Night.levels[game.puzzle.groups.first { w in it.words }.level] } }
            StoryCard.share(
                context,
                StoryData(
                    game = "Conexões do dia",
                    headline = if (game.won) (if (game.mistakes == 0) "Perfeito!" else "Resolvi!") else "Quase lá!",
                    detail = if (game.won) "${game.mistakes} ${if (game.mistakes == 1) "erro" else "erros"}" else "Faltou pouco",
                    grids = listOf(rows),
                    stats = listOf("${store?.int("conn_won") ?: 0}" to "Resolvidos", "${store?.int("conn_perfect") ?: 0}" to "Perfeitos"),
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Um desafio novo aparece amanhã.", fontSize = 13.sp, color = Night.muted)
    }
}
