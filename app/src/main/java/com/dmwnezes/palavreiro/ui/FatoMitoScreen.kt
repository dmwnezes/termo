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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.FatoMito
import com.dmwnezes.palavreiro.game.FatoQ
import com.dmwnezes.palavreiro.game.Words
import java.time.LocalDate
import kotlin.random.Random

private const val FM_HELP = "Leia a afirmação e toque em Fato ou em Mito. Depois de responder, veja a explicação.\n\nDo dia: 10 afirmações, iguais para todo mundo. Infinito: até errar 3 vezes."

/** Fato ou Mito: a afirmação é verdadeira? */
@Composable
fun FatoMitoScreen(list: List<FatoQ>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now(), seed: Long = System.nanoTime()) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("fm_mode") == "inf") }
    var game by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val deck = remember(infinite, game) { if (infinite) list.indices.shuffled(Random(seed + game)) else FatoMito.daily(list.size, Words.dayIndex(date)) }
    val marks = remember(infinite, game) {
        mutableStateListOf<Boolean>().apply {
            if (!infinite) store?.text("fm_state")?.split(';')?.takeIf { it.size == 2 && it[0] == day }?.get(1)?.split(' ')?.filter { it.isNotEmpty() }?.take(deck.size)?.forEach { add(it == "1") }
        }
    }
    var shown by remember(infinite, game) { mutableStateOf(false) }
    val hits = marks.count { it }
    val misses = marks.size - hits
    val finished = if (infinite) misses >= FatoMito.LIVES || marks.size >= deck.size else marks.size >= deck.size

    fun pick(fact: Boolean) {
        if (shown || finished) return
        val right = list[deck[marks.size]].fact == fact
        marks += right
        shown = true
        if (right) feedback?.win() else feedback?.invalid()
        store?.let { s ->
            val h = marks.count { it }
            if (infinite) { if (h > s.int("fm_best")) s.setInt("fm_best", h) }
            else s.setText("fm_state", "$day;${marks.joinToString(" ") { if (it) "1" else "0" }}")
            val end = if (infinite) marks.size - h >= FatoMito.LIVES || marks.size >= deck.size else marks.size >= deck.size
            if (end) {
                s.add("fm_played"); s.logActivity()
                if (!infinite) {
                    if (h > s.int("fm_day_best")) s.setInt("fm_day_best", h)
                    s.setText("fm_done", day); s.setResult("fatomito", day, h >= 5)
                    if (h == FatoMito.ROUNDS) confetti++
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Fato ou Mito", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v -> infinite = v; store?.setText("fm_mode", if (v) "inf" else "day") }
            if (list.isEmpty()) {
                Text("Não foi possível carregar as afirmações.", color = Night.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (finished && !shown) {
                    Spacer(Modifier.height(18.dp))
                    Text(if (infinite) "$hits" else "$hits/${FatoMito.ROUNDS}", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                    Text(if (infinite) "${if (hits == 1) "acerto" else "acertos"} · recorde ${store?.int("fm_best") ?: hits}" else "acertos no desafio de hoje", fontSize = 15.sp, color = Night.muted, textAlign = TextAlign.Center)
                    if (!infinite) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            marks.forEach { m -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(if (m) Night.correct else Night.red)) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                            val t = if (infinite) "Fato ou Mito 🧐 $hits acertos\nhttps://dmwnezes.com/termo/"
                            else "Fato ou Mito ${shortDay(date)} $hits/${FatoMito.ROUNDS}\n${marks.joinToString("") { if (it) "🟩" else "🟥" }}\nhttps://dmwnezes.com/termo/"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                        }
                        PillButton(if (infinite) "Jogar de novo" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                            store?.setText("fm_mode", "inf")
                            infinite = true
                            game++
                        }
                    }
                } else {
                    val at = if (shown) marks.size - 1 else marks.size
                    val q = list[deck[at]]
                    val right = marks.getOrNull(at)
                    Text(
                        if (infinite) "Acertos: $hits · ${"❤️".repeat((FatoMito.LIVES - misses).coerceAtLeast(0))}" else "Afirmação ${at + 1} de ${FatoMito.ROUNDS} · $hits ${if (hits == 1) "acerto" else "acertos"}",
                        fontSize = 15.sp, color = Night.muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(if (right == null) Night.surface else if (right) Night.correct else Night.red.copy(alpha = 0.65f))
                            .padding(horizontal = 18.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(q.text, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, color = Night.text, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (shown) {
                        Text("${if (right == true) "Acertou!" else "Errou."} É ${if (q.fact) "fato" else "mito"}.", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                        Spacer(Modifier.height(4.dp))
                        Text(q.why, fontSize = 15.sp, lineHeight = 21.sp, color = Night.muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        PillButton(if (finished) "Ver resultado" else "Próxima", Night.correct, modifier = Modifier.fillMaxWidth()) { shown = false }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Mito", Night.present, modifier = Modifier.weight(1f)) { pick(false) }
                            PillButton("Fato", Night.correct, modifier = Modifier.weight(1f)) { pick(true) }
                        }
                    }
                }
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Fato ou Mito")
                Spacer(Modifier.height(12.dp))
                Text(FM_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}
