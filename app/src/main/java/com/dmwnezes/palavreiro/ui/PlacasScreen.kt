package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.PlacaQ
import com.dmwnezes.palavreiro.game.Placas
import com.dmwnezes.palavreiro.game.Words
import java.time.LocalDate
import kotlin.random.Random

private const val PLACAS_HELP = "Escolha o significado da placa ou a resposta certa sobre as regras. Depois, veja a explicação.\n\nOs desenhos são simplificados, e o jogo não substitui o material oficial. Do dia: 10 perguntas. Infinito: até errar 3 vezes."
private val SIGN_RED = Color(0xFFC62828)
private val SIGN_YELLOW = Color(0xFFF2C230)
private val SIGN_INK = Color(0xFF17132F)

/** Desenho simplificado da placa; [shape] "regra" não desenha nada. */
@Composable
private fun Sign(shape: String, symbol: String) {
    if (shape == "regra") return
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val k = size.minDimension / 100f
            fun poly(vararg p: Pair<Float, Float>) = Path().apply { p.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * k, y * k) else lineTo(x * k, y * k) }; close() }
            val c = Offset(50 * k, 50 * k)
            when (shape) {
                "pare" -> {
                    val p = poly(30f to 4f, 70f to 4f, 96f to 30f, 96f to 70f, 70f to 96f, 30f to 96f, 4f to 70f, 4f to 30f)
                    drawPath(p, SIGN_RED); drawPath(p, Color.White, style = Stroke(3 * k))
                }
                "triangulo" -> {
                    val p = poly(6f to 10f, 94f to 10f, 50f to 90f)
                    drawPath(p, Color.White); drawPath(p, SIGN_RED, style = Stroke(10 * k, join = StrokeJoin.Round))
                }
                "contramao" -> {
                    drawCircle(SIGN_RED, 45 * k, c)
                    drawRect(Color.White, Offset(22 * k, 40 * k), Size(56 * k, 20 * k))
                }
                "circulo", "proibido" -> {
                    drawCircle(Color.White, 42 * k, c)
                    drawCircle(SIGN_RED, 42 * k, c, style = Stroke(9 * k))
                }
                "losango" -> {
                    val p = poly(50f to 4f, 96f to 50f, 50f to 96f, 4f to 50f)
                    drawPath(p, SIGN_YELLOW); drawPath(p, SIGN_INK, style = Stroke(3 * k, join = StrokeJoin.Round))
                }
            }
        }
        if (symbol.isNotEmpty()) {
            Text(symbol, fontWeight = FontWeight.ExtraBold, fontSize = if (symbol.length > 2) 26.sp else 38.sp, color = if (shape == "pare") Color.White else SIGN_INK)
        }
        if (shape == "proibido") {
            Canvas(Modifier.size(120.dp)) {
                val k = size.minDimension / 100f
                drawLine(SIGN_RED, Offset(20 * k, 20 * k), Offset(80 * k, 80 * k), 8 * k)
            }
        }
    }
}

/** Placas: placas e regras de trânsito. */
@Composable
fun PlacasScreen(list: List<PlacaQ>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now(), seed: Long = System.nanoTime()) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("pl_mode") == "inf") }
    var game by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val deck = remember(infinite, game) { if (infinite) list.indices.shuffled(Random(seed + game)) else Placas.daily(list.size, Words.dayIndex(date)) }
    val marks = remember(infinite, game) {
        mutableStateListOf<Boolean>().apply {
            if (!infinite) store?.text("pl_state")?.split(';')?.takeIf { it.size == 2 && it[0] == day }?.get(1)?.split(' ')?.filter { it.isNotEmpty() }?.take(deck.size)?.forEach { add(it == "1") }
        }
    }
    var shown by remember(infinite, game) { mutableStateOf(false) }
    val hits = marks.count { it }
    val misses = marks.size - hits
    val finished = (infinite && misses >= Placas.LIVES) || marks.size >= deck.size

    fun pick(option: Int) {
        if (shown || finished) return
        val right = option == 0
        marks += right
        shown = true
        if (right) feedback?.win() else feedback?.invalid()
        store?.let { s ->
            val h = marks.count { it }
            if (infinite) { if (h > s.int("pl_best")) s.setInt("pl_best", h) }
            else s.setText("pl_state", "$day;${marks.joinToString(" ") { if (it) "1" else "0" }}")
            if ((infinite && marks.size - h >= Placas.LIVES) || marks.size >= deck.size) {
                s.add("pl_played"); s.logActivity()
                if (!infinite) {
                    if (h > s.int("pl_day_best")) s.setInt("pl_day_best", h)
                    s.setText("pl_done", day); s.setResult("placas", day, h >= 5)
                    if (h == Placas.ROUNDS) confetti++
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Placas", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v -> infinite = v; store?.setText("pl_mode", if (v) "inf" else "day") }
            if (list.isEmpty()) {
                Text("Não foi possível carregar as placas.", color = Night.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (finished && !shown) {
                    Spacer(Modifier.height(18.dp))
                    Text(if (infinite) "$hits" else "$hits/${Placas.ROUNDS}", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                    Text(if (infinite) "${if (hits == 1) "acerto" else "acertos"} · recorde ${store?.int("pl_best") ?: hits}" else "acertos no desafio de hoje", fontSize = 15.sp, color = Night.muted, textAlign = TextAlign.Center)
                    if (!infinite) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            marks.forEach { m -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(if (m) Night.correct else Night.red)) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                            val t = if (infinite) "Placas 🚦 $hits acertos\nhttps://dmwnezes.com/termo/"
                            else "Placas ${shortDay(date)} $hits/${Placas.ROUNDS}\n${marks.joinToString("") { if (it) "🟩" else "🟥" }}\nhttps://dmwnezes.com/termo/"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                        }
                        PillButton(if (infinite) "Jogar de novo" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                            store?.setText("pl_mode", "inf")
                            infinite = true
                            game++
                        }
                    }
                } else {
                    val at = if (shown) marks.size - 1 else marks.size
                    val qi = deck[at]
                    val q = list[qi]
                    val right = marks.getOrNull(at)
                    Text(
                        if (infinite) "Acertos: $hits · ${"❤️".repeat((Placas.LIVES - misses).coerceAtLeast(0))}" else "Pergunta ${at + 1} de ${Placas.ROUNDS} · $hits ${if (hits == 1) "acerto" else "acertos"}",
                        fontSize = 15.sp, color = Night.muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Sign(q.shape, q.symbol)
                    Spacer(Modifier.height(10.dp))
                    Text(q.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    if (shown) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (right == true) Night.correct else Night.red.copy(alpha = 0.65f)).padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(q.options[0], fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Night.text, textAlign = TextAlign.Center) }
                        Spacer(Modifier.height(10.dp))
                        Text(if (right == true) "Acertou!" else "Errou.", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                        Spacer(Modifier.height(4.dp))
                        Text(q.why, fontSize = 15.sp, lineHeight = 21.sp, color = Night.muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        PillButton(if (finished) "Ver resultado" else "Próxima", Night.correct, modifier = Modifier.fillMaxWidth()) { shown = false }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Placas.order(qi).forEach { o ->
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Night.surfaceHigh).clickable { pick(o) }.padding(horizontal = 14.dp, vertical = 13.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(q.options[o], fontSize = 15.sp, color = Night.text, textAlign = TextAlign.Center) }
                            }
                        }
                    }
                }
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Placas")
                Spacer(Modifier.height(12.dp))
                Text(PLACAS_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}
