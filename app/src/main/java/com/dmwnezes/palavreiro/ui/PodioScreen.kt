package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.PodioData
import com.dmwnezes.palavreiro.game.PodioGame
import com.dmwnezes.palavreiro.game.PodioMark
import com.dmwnezes.palavreiro.game.PodioQ
import com.dmwnezes.palavreiro.game.Words
import java.time.LocalDate
import kotlin.random.Random

private val PInk = Color(0xFF14102C)

private const val PODIO_HELP = "Monte o top 5 do ranking: toque numa opção para pô-la na próxima vaga (1º a 5º) " +
    "e toque numa vaga para devolver a opção. Com as 5 vagas cheias, toque em Enviar.\n\n" +
    "Verde: posição certa. Amarelo: está no top 5, mas em outra posição. Cinza: não está no top 5 (a opção fica riscada).\n\n" +
    "Você tem 5 tentativas. O Pódio do dia é o mesmo para todo mundo; no Infinito, os rankings são sorteados."

private fun PodioMark.color() = when (this) { PodioMark.GREEN -> Night.correct; PodioMark.YELLOW -> Night.present; PodioMark.GRAY -> Night.absent }

/** Pódio: ordene o top 5 de um ranking em 5 tentativas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodioScreen(
    list: List<PodioQ>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    date: LocalDate = LocalDate.now(),
    seed: Long = System.nanoTime(),
    startInfinite: Boolean = false,
) {
    val context = LocalContext.current
    val day = date.toString()
    val dayIdx = Words.dayIndex(date)
    var infinite by remember { mutableStateOf(startInfinite) }
    var round by remember { mutableIntStateOf(0) }
    // Infinito: sorteia sem repetir até passar por todos.
    val seen = remember { mutableListOf<Int>() }
    val game = remember(infinite, round) {
        if (list.isEmpty()) return@remember null
        if (infinite) {
            val rnd = Random(seed + round)
            if (seen.size >= list.size) seen.clear()
            val i = list.indices.filter { it !in seen }.random(rnd).also { seen += it }
            PodioGame(list[i], rnd)
        } else {
            val q = list[PodioData.dailyIndex(dayIdx, list.size)]
            PodioGame(q, PodioData.dailyOptions(q, dayIdx)).also { g ->
                store?.text("podio_state")?.takeIf { it.substringBefore('|') == day }?.substringAfter('|')
                    ?.split(';')?.filter { it.isNotBlank() }?.forEach { t ->
                        g.slots.indices.forEach { g.take(it) }
                        t.split(',').mapNotNull { it.toIntOrNull()?.let(q.all::getOrNull) }.forEach { g.put(it) }
                        g.submit()
                    }
            }
        }
    }
    var showEnd by remember(game) { mutableStateOf(game?.over == true) }
    var showHelp by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }

    fun finish(g: PodioGame) {
        val st = store ?: return
        st.add("podio_played")
        st.logActivity()
        if (g.won) { st.add("podio_won"); if (g.tries.size == 1) st.add("podio_first") }
        if (!infinite) {
            st.setText("podio_done", day)
            val yesterday = date.minusDays(1).toString()
            val cur = if (!g.won) 0 else if (st.text("podio_last") == yesterday) st.int("podio_cur") + 1 else 1
            st.setInt("podio_cur", cur)
            if (g.won) st.setText("podio_last", day)
            st.max("podio_streak", cur)
        }
    }

    LaunchedEffect(game?.tries?.size) {
        val g = game ?: return@LaunchedEffect
        if (g.over && g.tries.isNotEmpty() && !showEnd) {
            kotlinx.coroutines.delay(700)
            showEnd = true
        }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Pódio", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { infinite = it }
            Spacer(Modifier.height(12.dp))
            if (game == null) {
                Text("Nenhum ranking disponível.", color = Night.muted, modifier = Modifier.padding(20.dp))
                return@Column
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    game.q.title, color = Night.text, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).border(1.dp, Night.outline.copy(alpha = 0.45f), Shapes.card)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("Tentativa ${minOf(game.tries.size + 1, PodioGame.MAX)} de ${PodioGame.MAX}", color = Night.muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                val lastMarks = game.tries.lastOrNull()?.let(game::marks)
                game.slots.forEachIndexed { i, o ->
                    val green = o != null && lastMarks?.get(i) == PodioMark.GREEN && game.tries.last()[i] == o
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 46.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (green) Night.correct else if (o != null) Night.surfaceHigh else Night.surface)
                            .border(1.dp, if (o == null) Night.outline.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickable(enabled = o != null && !game.over) { game.take(i); feedback?.type() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}º", color = if (green) PInk else Night.muted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(34.dp))
                        Text(o ?: "", color = if (green) PInk else Night.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (!game.over) {
                    val gray = game.gray
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        game.options.forEach { o ->
                            val used = o in game.slots
                            val out = o in gray
                            Text(
                                o, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                color = if (used || out) Night.muted.copy(alpha = 0.55f) else Night.text,
                                textDecoration = if (out) TextDecoration.LineThrough else null,
                                modifier = Modifier.clip(Shapes.pill).background(if (used) Night.surface else Night.surfaceHigh)
                                    .clickable(enabled = !used) { if (game.put(o)) feedback?.type() }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PillButton("Enviar", Night.correct, modifier = Modifier.fillMaxWidth(), enabled = game.full) {
                        if (game.submit()) {
                            if (!infinite) store?.setText("podio_state", day + "|" + game.tries.joinToString(";") { t -> t.joinToString(",") { game.q.all.indexOf(it).toString() } })
                            if (game.won) { feedback?.win(); confetti++ } else if (game.over) feedback?.lose() else feedback?.invalid()
                            if (game.over) finish(game)
                        }
                    }
                } else {
                    PillButton("Ver resultado", Night.correct, modifier = Modifier.fillMaxWidth()) { showEnd = true }
                }
                if (game.tries.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("TENTATIVAS", color = Night.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.Start).padding(start = 6.dp))
                    Spacer(Modifier.height(6.dp))
                    game.tries.forEach { t -> TryRow(t, game.marks(t)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showEnd && game != null) {
            BottomSheet({ showEnd = false }) {
                SheetTitle(if (game.won) "Pódio completo! 🏆" else "Não foi desta vez", if (game.won) "Em ${game.tries.size} de ${PodioGame.MAX} tentativas" else "O top 5 certo:")
                Spacer(Modifier.height(12.dp))
                game.q.top.forEachIndexed { i, o ->
                    Text("${i + 1}º  $o", color = Night.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${store?.int("podio_played") ?: 0}", "Jogos")
                    StatItem("${store?.int("podio_won") ?: 0}", "Vitórias")
                    StatItem("${store?.int("podio_cur") ?: 0}", "Sequência")
                }
                Spacer(Modifier.height(20.dp))
                PillButton("Compartilhar", Night.correct, modifier = Modifier.fillMaxWidth()) { share(context, game.share(!infinite)) }
                Spacer(Modifier.height(10.dp))
                if (infinite) PillButton("Próximo ranking", Night.accent, modifier = Modifier.fillMaxWidth()) { round++ }
                else PillButton("Jogar o Infinito", Night.accent, modifier = Modifier.fillMaxWidth()) { infinite = true }
            }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Pódio")
                Spacer(Modifier.height(12.dp))
                Text(PODIO_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

/** Uma tentativa: as 5 opções com a cor de cada uma. */
@Composable
private fun TryRow(t: List<String>, marks: List<PodioMark>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        t.forEachIndexed { i, o ->
            val c = marks[i].color()
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(c)
                    .semantics { contentDescription = "${i + 1}º $o: ${when (marks[i]) { PodioMark.GREEN -> "certo"; PodioMark.YELLOW -> "outra posição"; PodioMark.GRAY -> "fora do top 5" }}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                    color = if (marks[i] == PodioMark.GRAY) Night.muted else PInk, modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
