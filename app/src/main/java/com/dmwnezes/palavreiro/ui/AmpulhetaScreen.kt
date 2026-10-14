package com.dmwnezes.palavreiro.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.AmpEvent
import com.dmwnezes.palavreiro.game.AmpulhetaGame
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.random.Random

private val AInk = Color(0xFF14102C)

private const val AMP_HELP = "A linha do tempo começa com um acontecimento e o ano dele. A cada rodada aparece uma carta nova, sem o ano: " +
    "toque no espaço da linha onde ela se encaixa (antes, entre dois ou depois).\n\n" +
    "Acertou: a carta entra com o ano. Errou: a carta entra na posição certa mesmo assim, com o ano à mostra.\n\n" +
    "Do dia: 10 cartas, as mesmas para todo mundo — sua nota é o número de acertos. " +
    "Infinito: cartas sem fim até errar 3 vezes (3 vidas)."

/** Ampulheta: encaixe os acontecimentos na linha do tempo. */
@Composable
fun AmpulhetaScreen(
    events: List<AmpEvent>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    date: LocalDate = LocalDate.now(),
    seed: Long = System.nanoTime(),
    startInfinite: Boolean = false,
) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(startInfinite) }
    var round by remember { mutableIntStateOf(0) }
    val game = remember(infinite, round) {
        if (events.size < 2) null
        else if (infinite) AmpulhetaGame.infinite(events, Random(seed + round))
        else AmpulhetaGame.daily(events, Words.dayIndex(date)).also { g ->
            store?.text("amp_state")?.takeIf { it.substringBefore('|') == day }?.substringAfter('|')
                ?.split(',')?.mapNotNull { it.toIntOrNull() }?.forEach { g.place(it) }
        }
    }
    var showEnd by remember(game) { mutableStateOf(game?.over == true) }
    var showHelp by remember { mutableStateOf(false) }

    fun finish(g: AmpulhetaGame) {
        val st = store ?: return
        st.add("amp_played")
        st.logActivity()
        if (infinite) st.max("amp_best", g.right)
        else { st.setText("amp_done", day); st.max("amp_day_best", g.right) }
    }

    LaunchedEffect(game?.results?.size) {
        val g = game ?: return@LaunchedEffect
        if (g.over && g.results.isNotEmpty() && !showEnd) { delay(900); showEnd = true }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Ampulheta", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { infinite = it }
            Spacer(Modifier.height(12.dp))
            if (game == null) {
                Text("Nenhum acontecimento disponível.", color = Night.muted, modifier = Modifier.padding(20.dp))
                return@Column
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.surface).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (infinite) {
                        Text(
                            (0 until AmpulhetaGame.LIVES).joinToString(" ") { if (it < game.lives) "❤️" else "🤍" }, fontSize = 15.sp,
                            modifier = Modifier.semantics { contentDescription = "${game.lives} vidas" },
                        )
                    } else {
                        Text("Carta ${minOf(game.results.size + 1, game.cards)}/${game.cards}", color = Night.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("✓ ${game.right}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Night.correct)
                    if (infinite) {
                        Spacer(Modifier.width(12.dp))
                        Text("Recorde ${maxOf(store?.int("amp_best") ?: 0, game.right)}", fontSize = 14.sp, color = Night.muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // A carta da vez (sem ano) ou o resultado da última.
                val c = game.card
                val lastOk = game.results.lastOrNull()
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(Shapes.card)
                        .background(Night.surfaceHigh).border(2.dp, Night.present.copy(alpha = 0.7f), Shapes.card).padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⏳ Quando foi?", color = Night.present, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(c.text, color = Night.text, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    } else {
                        Text(
                            if (infinite) "Fim! ${game.right} acertos" else "${game.right}/${game.results.size} ⏳",
                            color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    val l = game.last
                    if (l != null && lastOk != null) {
                        Text(
                            if (lastOk) "Certo! ${l.yearLabel}" else "Errou! Foi em ${l.yearLabel}",
                            color = if (lastOk) Night.correct else Night.red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                val playing = game.card != null
                for (i in 0..game.line.size) {
                    if (playing) Gap {
                        val ok = game.place(i)
                        if (!infinite) store?.setText("amp_state", day + "|" + game.placed.joinToString(","))
                        if (ok) feedback?.reveal(2, com.dmwnezes.palavreiro.game.Mark.CORRECT) else feedback?.invalid()
                        if (game.over) finish(game)
                    }
                    game.line.getOrNull(i)?.let { e -> EventRow(e, if (e == game.last) game.results.lastOrNull() else null) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (showEnd && game != null) {
            BottomSheet({ showEnd = false }) {
                SheetTitle(
                    "Ampulheta ${game.right}/${game.results.size} ⏳",
                    if (infinite) "Infinito · ${game.right} acertos" else "Ampulheta do dia",
                )
                Spacer(Modifier.height(12.dp))
                Text(game.results.joinToString("") { if (it) "🟩" else "🟥" }, fontSize = 20.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${store?.int("amp_day_best") ?: 0}", "Melhor do dia")
                    StatItem("${store?.int("amp_best") ?: 0}", "Recorde infinito")
                    StatItem("${store?.int("amp_played") ?: 0}", "Partidas")
                }
                Spacer(Modifier.height(20.dp))
                PillButton("Compartilhar", Night.correct, modifier = Modifier.fillMaxWidth()) { share(context, game.share()) }
                Spacer(Modifier.height(10.dp))
                if (infinite) PillButton("Jogar de novo", Night.accent, modifier = Modifier.fillMaxWidth()) { round++ }
                else PillButton("Jogar o Infinito", Night.accent, modifier = Modifier.fillMaxWidth()) { infinite = true }
            }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Ampulheta")
                Spacer(Modifier.height(12.dp))
                Text(AMP_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

/** Espaço da linha do tempo onde a carta pode entrar. */
@Composable
private fun Gap(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).height(34.dp).clip(Shapes.pill)
            .border(1.5.dp, Night.present.copy(alpha = 0.55f), Shapes.pill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋ Encaixar aqui", color = Night.present, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Acontecimento na linha: ano à esquerda e a frase; a última carta posta fica verde (acerto) ou vermelha (erro). */
@Composable
private fun EventRow(e: AmpEvent, ok: Boolean?) {
    val bg = when (ok) { true -> Night.correct; false -> Night.red; null -> Night.surface }
    val ink = if (ok != null) AInk else Night.text
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.yearLabel, color = if (ok != null) AInk else Night.present, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(78.dp))
        Text(e.text, color = ink, fontSize = 15.sp, lineHeight = 19.sp)
    }
}
