package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Minado
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.random.Random

private const val MINADO_HELP = "Abra todas as casas sem mina. O número mostra quantas minas há nas 8 casas vizinhas. Use Bandeira, ou segure a casa, para marcar uma casa suspeita.\n\n" +
    "Tocar num número com todas as bandeiras ao redor abre as vizinhas restantes. A partida já começa com uma área aberta. O desafio do dia é o mesmo para todo mundo em cada nível."

private val NUMBER_COLORS = listOf(Color.Transparent, Color(0xFF6FA8E8), Color(0xFF5FB873), Color(0xFFE5737A), Color(0xFF9B8CFF), Color(0xFFE6C14F), Color(0xFF5FD0C5), Color(0xFFF4F1FF), Color(0xFFA9A2D0))

/** Seletor de nível usado pelo Campo Minado e pelo Caminhos. */
@Composable
internal fun LevelTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    ChipTabs(labels, selected, onSelect)
}

/** Campo Minado: um desafio por nível a cada dia, e infinitos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinadoScreen(store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("mina_mode") == "inf") }
    var level by remember { mutableIntStateOf(store?.int("mina_level")?.coerceIn(0, 2) ?: 0) }
    var round by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    var flagMode by remember(infinite, level, round) { mutableStateOf(false) }
    val lk = Minado.LEVELS[level].key
    val key = if (infinite) "mina_inf_state_$lk" else "mina_state_$lk"
    // Guardado: "marca;semente;abertas;bandeiras;perdeu;venceu;mina;segundos".
    val saved = remember(infinite, level, round) { store?.text(key)?.split(';')?.takeIf { it.size >= 8 && (infinite || it[0] == day) } }
    val game = remember(infinite, level, round) {
        val seed = saved?.get(1)?.toLongOrNull() ?: if (infinite) Random.nextLong(1_000_000_000L) else Words.dayIndex(date) * 3 + level
        Minado(level, seed).also { g -> saved?.let { g.restore(it.drop(1)) } }
    }
    var seconds by remember(infinite, level, round) { mutableIntStateOf(saved?.get(7)?.toIntOrNull() ?: 0) }
    val finished = tick.let { game.won || game.lost }
    LaunchedEffect(game, finished) {
        while (!finished) { delay(1000); seconds++ }
    }
    fun save() { store?.setText(key, "$day;${game.save()};$seconds") }
    fun finish() {
        store?.let { s ->
            s.add("mina_played"); s.logActivity()
            if (game.won) {
                s.add("mina_won")
                val b = s.int("mina_best_$lk")
                if (b <= 0 || seconds < b) s.setInt("mina_best_$lk", seconds.coerceAtLeast(1))
            }
            if (!infinite) { s.setText("mina_done", day); s.setResult("minado", day, game.won) }
        }
        if (game.won) { confetti++; feedback?.win() } else feedback?.lose()
    }
    fun act(c: Int) {
        if (game.won || game.lost) return
        if (flagMode && !game.open[c]) { game.toggleFlag(c); feedback?.type() }
        else {
            game.tap(c)
            if (game.won || game.lost) finish() else feedback?.type()
        }
        tick++; save()
    }
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Campo Minado", onBack, onHelp = { showHelp = true }, trailing = { Text(formatTime(seconds), color = Night.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v -> infinite = v; store?.setText("mina_mode", if (v) "inf" else "day") }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                LevelTabs(Minado.LEVELS.map { it.label }, level) { level = it; store?.setInt("mina_level", it) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                tick.let { _ ->
                    val left = game.mines - game.flags
                    Text(
                        if (game.won) "Campo limpo! 🎉" else if (game.lost) "Boom! Pisou numa mina." else "$left ${if (left == 1) "mina restante" else "minas restantes"}",
                        color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (y in 0 until game.h) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (x in 0 until game.w) {
                                val c = y * game.w + x
                                val isOpen = game.open[c]
                                val show = isOpen || (finished && game.mine[c])
                                val label = if (isOpen) (if (game.count[c] > 0) "${game.count[c]} minas ao redor" else "aberta") else if (game.flag[c]) "com bandeira" else "fechada"
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(7.dp))
                                        .background(if (c == game.boom) Night.red else if (isOpen) Night.surface else Night.key)
                                        .combinedClickable(
                                            onClick = { act(c) },
                                            onLongClick = { if (!finished) { game.toggleFlag(c); feedback?.type(); tick++; save() } },
                                        )
                                        .semantics { contentDescription = "Linha ${y + 1}, coluna ${x + 1}, $label" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        !show && game.flag[c] -> Text("🚩", fontSize = 13.sp)
                                        show && game.mine[c] -> Text("💣", fontSize = 13.sp)
                                        show && game.count[c] > 0 -> Text("${game.count[c]}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = NUMBER_COLORS[game.count[c]])
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (finished) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                                val t = "Campo Minado ${Minado.LEVELS[level].label} ${if (infinite) "" else shortDay(date) + " "}${if (game.won) "💣 limpo em ${formatTime(seconds)}" else "💥"}\nhttps://dmwnezes.com/termo/"
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                            }
                            PillButton(if (infinite) "Nova grade" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                                store?.setText("mina_inf_state_$lk", null)
                                store?.setText("mina_mode", "inf")
                                infinite = true
                                round++
                            }
                        }
                    } else {
                        PillButton(if (flagMode) "🚩 Bandeira ligada" else "🚩 Bandeira", if (flagMode) Night.accent else Night.surfaceHigh, Night.text, Modifier.fillMaxWidth()) { flagMode = !flagMode }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar o Campo Minado")
                Spacer(Modifier.height(12.dp))
                Text(MINADO_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}
