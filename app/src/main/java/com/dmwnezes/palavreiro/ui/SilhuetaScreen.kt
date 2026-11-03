package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Silhueta
import com.dmwnezes.palavreiro.game.SilhuetaItem
import com.dmwnezes.palavreiro.game.Words
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

private const val SILHUETA_HELP = "Adivinhe o país ou o estado brasileiro pelo contorno, em até 6 tentativas. Digite e escolha um nome da lista.\n\n" +
    "Cada erro mostra a distância até a resposta, a direção em que ela fica e o quanto você chegou perto. A silhueta do dia é a mesma para todo mundo."

/** Desenha o contorno (caixa 100×100). */
@Composable
fun SilhuetaShape(item: SilhuetaItem, size: Dp, color: Color = Night.accent, description: String = "") {
    val rings = item.rings
    Canvas(Modifier.size(size).semantics { contentDescription = description }) {
        val k = this.size.minDimension / 100f
        val p = Path()
        rings.forEach { r ->
            r.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x * k, y * k) else p.lineTo(x * k, y * k) }
            p.close()
        }
        drawPath(p, color)
    }
}

/** Silhueta: adivinhe o país ou estado pelo contorno. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SilhuetaScreen(list: List<SilhuetaItem>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("sil_mode") == "inf") }
    var round by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val key = if (infinite) "sil_inf_state" else "sil_state"
    // Guardado: "data;índice;chute|chute|…". No dia, vale a data; no Infinito, até pedir outra.
    val idx = remember(infinite, round) {
        if (list.isEmpty()) return@remember -1
        val saved = store?.text(key)?.split(';', limit = 3)?.takeIf { it.size == 3 && (infinite || it[0] == day) }
        saved?.get(1)?.toIntOrNull()?.takeIf { it in list.indices } ?: if (infinite) Random.nextInt(list.size) else Silhueta.dailyIndex(Words.dayIndex(date), list.size)
    }
    val q = list.getOrNull(idx)
    val options = remember(q) { list.filter { it.kind == q?.kind } }
    val guesses = remember(infinite, round) {
        mutableStateListOf<String>().apply {
            val saved = store?.text(key)?.split(';', limit = 3)?.takeIf { it.size == 3 && (infinite || it[0] == day) && it[1].toIntOrNull() == idx }
            saved?.get(2)?.split('|')?.filter { n -> options.any { it.name == n } }?.let(::addAll)
        }
    }
    var text by remember(infinite, round) { mutableStateOf("") }
    val won = q != null && q.name in guesses
    val over = won || guesses.size >= Silhueta.TRIES

    fun guess(name: String) {
        val answer = q ?: return
        if (over || name in guesses) return
        guesses += name
        text = ""
        val nowWon = name == answer.name
        store?.setText(key, "$day;$idx;${guesses.joinToString("|")}")
        if (nowWon || guesses.size >= Silhueta.TRIES) {
            store?.let { s ->
                s.add("sil_played"); s.logActivity()
                if (nowWon) { s.add("sil_won"); if (guesses.size == 1) s.add("sil_first") }
                if (!infinite) { s.setText("sil_done", day); s.setResult("silhueta", day, nowWon) }
            }
            if (nowWon) { confetti++; feedback?.win() } else feedback?.lose()
        } else feedback?.invalid()
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar("Silhueta", onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v -> infinite = v; store?.setText("sil_mode", if (v) "inf" else "day") }
            if (q == null) {
                Text("Não foi possível carregar os mapas.", color = Night.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        won -> "Acertou em ${guesses.size} ${if (guesses.size == 1) "tentativa" else "tentativas"}!"
                        over -> "Era ${q.name}."
                        else -> "${if (q.kind == "e") "Estado do Brasil" else "País"} · tentativa ${guesses.size + 1} de ${Silhueta.TRIES}"
                    },
                    color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                SilhuetaShape(q, 200.dp, description = "Contorno ${if (q.kind == "e") "de um estado" else "de um país"}")
                Spacer(Modifier.height(12.dp))
                if (over) {
                    Text(q.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                            val t = "Silhueta ${if (infinite) "" else shortDay(date) + " "}${if (won) guesses.size else "X"}/${Silhueta.TRIES} 🗺️\nhttps://dmwnezes.com/termo/"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                        }
                        PillButton(if (infinite) "Outra silhueta" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                            store?.setText("sil_inf_state", null)
                            store?.setText("sil_mode", "inf")
                            infinite = true
                            round++
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        placeholder = { Text(if (q.kind == "e") "Digite o estado" else "Digite o país") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Night.text, unfocusedTextColor = Night.text,
                            focusedBorderColor = Night.accent, unfocusedBorderColor = Night.outline, cursorColor = Night.accent,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Silhueta.matches(options, guesses, text).forEach { o ->
                            Text(
                                o.name, fontSize = 14.sp, color = Night.text,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Night.surfaceHigh).clickable { guess(o.name) }.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val nf = remember { NumberFormat.getIntegerInstance(Locale("pt", "BR")) }
                guesses.forEach { n ->
                    val ok = n == q.name
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(if (ok) Night.correct else Night.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(n, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Night.text, modifier = Modifier.weight(1f))
                        if (ok) Text("Acertou! 🎉", fontSize = 14.sp, color = Night.text)
                        else {
                            val h = Silhueta.hint(options.first { it.name == n }, q)
                            Text("${nf.format(h.km)} km", fontSize = 14.sp, color = Night.text)
                            Spacer(Modifier.width(10.dp))
                            Text(Silhueta.ARROWS[h.dir], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night.present)
                            Spacer(Modifier.width(10.dp))
                            Text("${h.pct}%", fontSize = 14.sp, color = Night.text)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar a Silhueta")
                Spacer(Modifier.height(12.dp))
                Text(SILHUETA_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}
