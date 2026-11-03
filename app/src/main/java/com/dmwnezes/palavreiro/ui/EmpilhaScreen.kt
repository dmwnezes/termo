package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Empilha

private const val EMPILHA_HELP = "Toque para soltar o bloco que se move. Só a parte que ficar sobre a pilha continua, então os blocos vão diminuindo. Blocos quase alinhados encaixam certinho.\n\nA velocidade aumenta. A partida acaba quando você erra a pilha."
private val BLOCK_COLORS = listOf(Color(0xFF5FB873), Color(0xFFE6C14F), Color(0xFF6FA8E8), Color(0xFFE5737A), Color(0xFF9B8CFF), Color(0xFFFF9F5A), Color(0xFF5FD0C5))

/** Empilha: solte os blocos e suba a torre. */
@Composable
fun EmpilhaScreen(store: Store?, feedback: Feedback?, onBack: () -> Unit) {
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { Empilha() }
    var frame by remember { mutableLongStateOf(0L) }
    var score by remember(round) { mutableIntStateOf(0) }
    var over by remember(round) { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(store?.int("emp_best") ?: 0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    LaunchedEffect(game) {
        var last = 0L
        while (!game.over) {
            withFrameNanos { t ->
                if (last != 0L) game.tick(minOf(0.05, (t - last) / 1e9))
                last = t
                frame = t
            }
        }
    }
    fun drop() {
        if (game.over) return
        game.drop()
        score = game.score
        if (game.over) {
            over = true
            store?.let { s ->
                s.add("emp_played"); s.logActivity()
                if (game.score > s.int("emp_best")) {
                    s.setInt("emp_best", game.score)
                    if (game.score >= 10) confetti++
                }
                best = s.int("emp_best")
            }
            feedback?.lose()
        } else if (game.perfect) feedback?.win() else feedback?.type()
    }
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar("Empilha", onBack, onHelp = { showHelp = true })
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Altura $score", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                Text("Recorde $best", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.widthIn(max = 400.dp).fillMaxWidth().aspectRatio(400f / 560f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(22.dp)).background(Night.surface).testTag("empilha")
                        .clickable(remember { MutableInteractionSource() }, null) { drop() },
                ) {
                    frame // redesenha a cada quadro
                    val unit = size.width / Empilha.WIDTH.toFloat()
                    val h = size.height / 16.5f
                    val n = game.tower.size
                    val base = maxOf(0, n - 12)
                    fun y(i: Int) = size.height - (i - base + 1) * h
                    fun block(x: Double, w: Double, i: Int) = drawRoundRect(
                        BLOCK_COLORS[i % BLOCK_COLORS.size], Offset(x.toFloat() * unit, y(i)), Size(w.toFloat() * unit, h - 3f), CornerRadius(h * 0.24f),
                    )
                    for (i in base until n) block(game.tower[i].x, game.tower[i].w, i)
                    if (!game.over) block(game.x, game.w, n)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (over) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                        val t = "Empilha 🧱 altura $score\nhttps://dmwnezes.com/termo/"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                    }
                    PillButton("Jogar de novo", Night.correct, modifier = Modifier.weight(1f)) { round++ }
                }
            } else {
                PillButton("Soltar", Night.correct, modifier = Modifier.fillMaxWidth()) { drop() }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Empilha")
                Spacer(Modifier.height(12.dp))
                Text(EMPILHA_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}
