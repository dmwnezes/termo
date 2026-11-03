package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.MaisMenos
import com.dmwnezes.palavreiro.game.MmCat
import com.dmwnezes.palavreiro.game.MmRound
import com.dmwnezes.palavreiro.game.Words
import java.time.LocalDate
import kotlin.random.Random

private const val MM_HELP = "Você vê o valor do primeiro item. Toque em Mais se o segundo tiver um valor maior, ou em Menos se tiver um valor menor.\n\n" +
    "Em temas de ano, Mais quer dizer um ano mais recente. Os valores são aproximados. Do dia: 10 rodadas, iguais para todo mundo. Infinito: até errar."

/** Mais ou Menos: compare números de temas gerais. */
@Composable
fun MaisMenosScreen(cats: List<MmCat>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now(), seed: Long = System.nanoTime()) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("mm_mode") == "inf") }
    var game by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val rnd = remember(game, infinite) { Random(seed + game) }
    fun nextRnd(): Long = rnd.nextLong(1, 2147483647L)
    val rounds = remember(infinite, game) {
        mutableStateListOf<MmRound>().apply {
            if (cats.isEmpty()) return@apply
            if (infinite) add(MaisMenos.round(cats, ::nextRnd, emptyList())) else addAll(MaisMenos.daily(cats, Words.dayIndex(date)))
        }
    }
    // Guardado no dia: "data;1 0 1…" (acertos e erros).
    val marks = remember(infinite, game) {
        mutableStateListOf<Boolean>().apply {
            if (!infinite) store?.text("mm_state")?.split(';')?.takeIf { it.size == 2 && it[0] == day }?.get(1)?.split(' ')?.filter { it.isNotEmpty() }?.take(MaisMenos.ROUNDS)?.forEach { add(it == "1") }
        }
    }
    var answered by remember(infinite, game) { mutableStateOf(false) }
    val hits = marks.count { it }
    val finished = if (infinite) false in marks else marks.size >= MaisMenos.ROUNDS

    fun pick(more: Boolean) {
        if (answered || finished) return
        val r = rounds[marks.size]
        val c = cats[r.cat]
        val right = (c.items[r.b].value > c.items[r.a].value) == more
        answered = true
        marks += right
        if (right) feedback?.win() else feedback?.invalid()
        store?.let { s ->
            val h = marks.count { it }
            if (infinite) { if (h > s.int("mm_best")) s.setInt("mm_best", h) }
            else s.setText("mm_state", "$day;${marks.joinToString(" ") { if (it) "1" else "0" }}")
            val end = if (infinite) !right else marks.size >= MaisMenos.ROUNDS
            if (end) {
                s.add("mm_played"); s.logActivity()
                if (!infinite) {
                    if (h > s.int("mm_day_best")) s.setInt("mm_day_best", h)
                    s.setText("mm_done", day); s.setResult("maismenos", day, h >= 5)
                    if (h == MaisMenos.ROUNDS) confetti++
                }
            }
        }
    }
    fun next() {
        answered = false
        if (infinite && false !in marks) rounds += MaisMenos.round(cats, ::nextRnd, rounds.takeLast(minOf(8, cats.size - 1)).map { it.cat })
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Mais ou Menos", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v -> infinite = v; store?.setText("mm_mode", if (v) "inf" else "day") }
            if (cats.isEmpty()) {
                Text("Não foi possível carregar os temas.", color = Night.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (finished && !answered) {
                    Spacer(Modifier.height(18.dp))
                    Text(if (infinite) "$hits" else "$hits/${MaisMenos.ROUNDS}", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                    Text(
                        if (infinite) "${if (hits == 1) "acerto seguido" else "acertos seguidos"} · recorde ${store?.int("mm_best") ?: hits}" else "acertos no desafio de hoje",
                        fontSize = 15.sp, color = Night.muted, textAlign = TextAlign.Center,
                    )
                    if (!infinite) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            marks.forEach { m -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(if (m) Night.correct else Night.red)) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                            val t = if (infinite) "Mais ou Menos 🔥 $hits seguidos\nhttps://dmwnezes.com/termo/"
                            else "Mais ou Menos ${shortDay(date)} $hits/${MaisMenos.ROUNDS}\n${marks.joinToString("") { if (it) "🟩" else "🟥" }}\nhttps://dmwnezes.com/termo/"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                        }
                        PillButton(if (infinite) "Jogar de novo" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                            store?.setText("mm_mode", "inf")
                            infinite = true
                            game++
                        }
                    }
                } else {
                    val at = if (answered) marks.size - 1 else marks.size
                    val r = rounds[at]
                    val c = cats[r.cat]
                    val a = c.items[r.a]
                    val b = c.items[r.b]
                    Text(
                        if (infinite) "Acertos seguidos: $hits" else "Rodada ${at + 1} de ${MaisMenos.ROUNDS} · $hits ${if (hits == 1) "acerto" else "acertos"}",
                        fontSize = 15.sp, color = Night.muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(c.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    MmCard(a.name, c.label(a.value), Night.surface)
                    Spacer(Modifier.height(10.dp))
                    val last = marks.lastOrNull()
                    MmCard(b.name, if (answered) c.label(b.value) else "?", if (!answered) Night.surfaceHigh else if (last == true) Night.correct else Night.red.copy(alpha = 0.65f))
                    Spacer(Modifier.height(12.dp))
                    if (answered) {
                        Text(if (last == true) "Acertou! ✓" else "Errou.", fontSize = 15.sp, color = Night.muted)
                        Spacer(Modifier.height(10.dp))
                        PillButton(if (finished) "Ver resultado" else "Próxima", Night.correct, modifier = Modifier.fillMaxWidth()) { next() }
                    } else {
                        Text(if (c.year) "É de um ano mais recente ou mais antigo?" else "Tem mais ou menos?", fontSize = 15.sp, color = Night.muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(if (c.year) "Mais antigo" else "Menos", Night.present, modifier = Modifier.weight(1f)) { pick(false) }
                            PillButton(if (c.year) "Mais recente" else "Mais", Night.correct, modifier = Modifier.weight(1f)) { pick(true) }
                        }
                    }
                }
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar o Mais ou Menos")
                Spacer(Modifier.height(12.dp))
                Text(MM_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

@Composable
private fun MmCard(name: String, value: String, color: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(color).padding(vertical = 18.dp, horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Night.text, textAlign = TextAlign.Center)
        Text(value, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
    }
}
