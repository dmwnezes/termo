package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.dmwnezes.palavreiro.game.MatizColor
import com.dmwnezes.palavreiro.game.MatizGame
import com.dmwnezes.palavreiro.game.Words
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.random.Random

private const val MATIZ_HELP = "Uma cor aparece por 3 segundos e some. Recrie a cor de memória com as barras R (vermelho), " +
    "G (verde) e B (azul), de 0 a 255, olhando a prévia, e toque em Pronto.\n\n" +
    "A nota vai de 0 a 100: quanto mais perto da cor original, maior. São 5 cores por partida (até 500 pontos). " +
    "No modo Do dia as 5 cores são as mesmas para todo mundo; no Infinito, jogue quantas partidas quiser."

private enum class MPhase { SHOW, PICK, RESULT }

/** Matiz: memorize a cor e recrie com R, G e B. */
@Composable
fun MatizScreen(
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    date: LocalDate = LocalDate.now(),
    seed: Long = System.nanoTime(),
    startInfinite: Boolean = false,
    /** Para os testes: começa já nesta fase (sem os 3 s). */
    startPhase: String? = null,
) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(startInfinite) }
    var round by remember { mutableIntStateOf(0) }
    val game = remember(infinite, round) {
        if (infinite) MatizGame(MatizColor.random(Random(seed + round)))
        else MatizGame(MatizColor.daily(Words.dayIndex(date))).also { g ->
            // Retoma as cores já feitas hoje.
            store?.text("matiz_state")?.takeIf { it.substringBefore('|') == day }?.substringAfter('|')
                ?.split(',')?.mapNotNull { it.toIntOrNull() }?.forEach { g.guess(it) }
        }
    }
    var phase by remember(game) {
        mutableStateOf(startPhase?.let { MPhase.valueOf(it) } ?: if (game.over) MPhase.RESULT else MPhase.SHOW)
    }
    var r by remember(game, game.round) { mutableFloatStateOf(128f) }
    var g by remember(game, game.round) { mutableFloatStateOf(128f) }
    var b by remember(game, game.round) { mutableFloatStateOf(128f) }
    var count by remember(game, game.round) { mutableIntStateOf(3) }
    var showEnd by remember(game) { mutableStateOf(game.over && startPhase == null) }
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(game, game.round, phase) {
        if (phase != MPhase.SHOW) return@LaunchedEffect
        while (count > 0) { delay(1000); count-- }
        phase = MPhase.PICK
    }

    fun finish() {
        val st = store ?: return
        st.add("matiz_played")
        st.logActivity()
        if (!infinite) { st.setText("matiz_done", day); st.max("matiz_best", game.total) }
    }

    fun done() {
        val s = game.guess(MatizColor.rgb(r.toInt(), g.toInt(), b.toInt()))
        store?.max("matiz_top", s)
        if (!infinite) store?.setText("matiz_state", day + "|" + game.guesses.joinToString(","))
        if (s >= 90) feedback?.win() else feedback?.type()
        if (game.over) finish()
        phase = MPhase.RESULT
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Matiz", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { infinite = it }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                RoundDots(game)
                Spacer(Modifier.height(14.dp))
                when (phase) {
                    MPhase.SHOW -> {
                        val c = game.target ?: 0
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(36.dp)).background(Color(0xFF000000.toInt() or c))
                                .semantics { contentDescription = "Memorize esta cor" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$count", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 28.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Memorize esta cor", color = Night.muted, fontSize = 16.sp)
                    }
                    MPhase.PICK -> {
                        val c = MatizColor.rgb(r.toInt(), g.toInt(), b.toInt())
                        Box(
                            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(36.dp)).background(Color(0xFF000000.toInt() or c))
                                .border(1.dp, Night.outline, RoundedCornerShape(36.dp)),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Text(MatizColor.hex(c), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Channel("R", r, Color(0xFFE5737A)) { r = it }
                        Channel("G", g, Night.correct) { g = it }
                        Channel("B", b, Night.blue) { b = it }
                        Spacer(Modifier.height(14.dp))
                        PillButton("Pronto", Night.correct, modifier = Modifier.fillMaxWidth()) { done() }
                    }
                    MPhase.RESULT -> {
                        val i = game.round - 1
                        if (i >= 0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Swatch("Original", game.colors[i], Modifier.weight(1f))
                                Swatch("Sua cor", game.guesses[i], Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(18.dp))
                            val s = game.scores[i]
                            Text("$s", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = scoreColor(s))
                            Text("pontos nesta cor", color = Night.muted, fontSize = 14.sp)
                            Spacer(Modifier.height(18.dp))
                            if (game.over) PillButton("Ver resultado", Night.correct, modifier = Modifier.fillMaxWidth()) { showEnd = true }
                            else PillButton("Próxima cor", Night.correct, modifier = Modifier.fillMaxWidth()) { phase = MPhase.SHOW }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (showEnd) {
            BottomSheet({ showEnd = false }) {
                SheetTitle("${game.total}/${game.colors.size * 100} 🎨", if (infinite) "Matiz Infinito" else "Matiz do dia")
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    game.colors.indices.forEach { i ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF000000.toInt() or game.colors[i])))
                            Spacer(Modifier.height(4.dp))
                            Text("${game.scores.getOrNull(i) ?: "—"}", color = Night.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${store?.int("matiz_best") ?: 0}", "Recorde do dia")
                    StatItem("${store?.int("matiz_played") ?: 0}", "Partidas")
                }
                Spacer(Modifier.height(20.dp))
                PillButton("Compartilhar", Night.correct, modifier = Modifier.fillMaxWidth()) { share(context, game.share(!infinite)) }
                Spacer(Modifier.height(10.dp))
                if (infinite) PillButton("Jogar de novo", Night.accent, modifier = Modifier.fillMaxWidth()) { round++ }
                else PillButton("Jogar o Infinito", Night.accent, modifier = Modifier.fillMaxWidth()) { infinite = true }
            }
        }
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Matiz")
                Spacer(Modifier.height(12.dp))
                Text(MATIZ_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

private fun scoreColor(s: Int) = when { s >= 90 -> Night.correct; s >= 70 -> Night.present; else -> Night.red }

/** As 5 rodadas: cheia com a cor original quando feita, contorno na atual. */
@Composable
private fun RoundDots(game: MatizGame) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        game.colors.indices.forEach { i ->
            val done = i < game.round
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (done) Color(0xFF000000.toInt() or game.colors[i]) else Night.surface)
                    .border(1.5.dp, if (i == game.round) Night.text else Night.outline, RoundedCornerShape(7.dp)),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("${game.total} pts", color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Channel(label: String, v: Float, color: Color, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(22.dp))
        Slider(
            v, { onChange(it.toInt().toFloat()) }, Modifier.weight(1f).semantics { contentDescription = "$label ${v.toInt()}" },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Night.surfaceHigh),
        )
        Text("${v.toInt()}", color = Night.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun Swatch(label: String, c: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color(0xFF000000.toInt() or c)).border(1.dp, Night.outline, RoundedCornerShape(28.dp)))
        Spacer(Modifier.height(6.dp))
        Text(MatizColor.hex(c), color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
