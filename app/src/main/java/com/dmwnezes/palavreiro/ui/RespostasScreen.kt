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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.RespQ
import com.dmwnezes.palavreiro.game.Respostas
import com.dmwnezes.palavreiro.game.Words
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

private const val PESQUISA_HELP = "Adivinhe as respostas mais prováveis para a pergunta. Cada resposta vale pontos, e as mais óbvias valem mais. Você pode errar 3 vezes.\n\nAs respostas foram escolhidas para o jogo, não vêm de uma pesquisa real."
private const val TOP10_HELP = "Escreva os 10 itens da lista, em qualquer ordem. Você pode errar 3 vezes. Acentos e maiúsculas não importam."

@Composable
fun PesquisaScreen(list: List<RespQ>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now()) =
    RespostasGame("Pesquisa", "pq", "pesquisa", PESQUISA_HELP, true, "Respostas escolhidas para o jogo, sem pesquisa real.", list, store, feedback, onBack, date)

@Composable
fun Top10Screen(list: List<RespQ>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate = LocalDate.now()) =
    RespostasGame("Top 10", "t10", "top10", TOP10_HELP, false, null, list, store, feedback, onBack, date)

@Composable
private fun RespostasGame(
    name: String, key: String, game: String, help: String, points: Boolean, note: String?,
    list: List<RespQ>, store: Store?, feedback: Feedback?, onBack: () -> Unit, date: LocalDate,
) {
    val context = LocalContext.current
    val day = date.toString()
    var infinite by remember { mutableStateOf(store?.text("${key}_mode") == "inf") }
    var round by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val stateKey = "${key}_${if (infinite) "inf_" else ""}state"
    val saved = remember(infinite, round) {
        runCatching { JSONObject(store?.text(stateKey) ?: "") }.getOrNull()?.takeIf { infinite || it.optString("day") == day }
    }
    val idx = remember(infinite, round) {
        if (list.isEmpty()) return@remember -1
        saved?.optInt("idx", -1)?.takeIf { it in list.indices } ?: if (infinite) Random.nextInt(list.size) else Respostas.dailyIndex(Words.dayIndex(date), list.size)
    }
    val q = list.getOrNull(idx)
    val got = remember(infinite, round) {
        mutableStateListOf<Int>().apply { saved?.optJSONArray("got")?.let { a -> for (i in 0 until a.length()) add(a.getInt(i)) } }
    }
    val errors = remember(infinite, round) {
        mutableStateListOf<String>().apply { saved?.optJSONArray("errors")?.let { a -> for (i in 0 until a.length()) add(a.getString(i)) } }
    }
    var text by remember(infinite, round) { mutableStateOf("") }
    val toast = rememberToast()
    val total = if (q == null) 0 else got.sumOf { q.items[it].pts }
    val full = q != null && got.size == q.items.size
    val finished = full || errors.size >= Respostas.ERRORS

    fun send() {
        val answer = q ?: return
        if (finished || Respostas.clean(text).isEmpty()) return
        val i = Respostas.match(answer.items, text)
        when {
            i >= 0 && i in got -> { toast.show("Essa já foi!"); return }
            i < 0 && errors.any { Respostas.clean(it) == Respostas.clean(text) } -> { toast.show("Você já tentou essa"); return }
            i >= 0 -> { got += i; feedback?.type() }
            else -> { errors += text.trim().take(40); feedback?.invalid() }
        }
        text = ""
        store?.setText(stateKey, JSONObject().put("day", day).put("idx", idx).put("got", JSONArray(got.toList())).put("errors", JSONArray(errors.toList())).toString())
        val nowFull = got.size == answer.items.size
        if (nowFull || errors.size >= Respostas.ERRORS) {
            store?.let { s ->
                s.add("${key}_played"); s.logActivity()
                val pts = got.sumOf { answer.items[it].pts }
                if (pts > s.int("${key}_best")) s.setInt("${key}_best", pts)
                if (nowFull) s.add("${key}_full")
                if (!infinite) { s.setText("${key}_done", day); s.setResult(game, day, nowFull) }
            }
            if (nowFull) { confetti++; feedback?.win() } else feedback?.lose()
        }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(name, onBack, onHelp = { showHelp = true })
            ModeSwitch(infinite, Modifier.padding(horizontal = 16.dp)) { v -> infinite = v; store?.setText("${key}_mode", if (v) "inf" else "day") }
            if (q == null) {
                Text("Não foi possível carregar o desafio.", color = Night.muted, modifier = Modifier.padding(24.dp))
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(10.dp))
                val left = Respostas.ERRORS - errors.size
                Text(
                    "${if (finished) (if (full) "Completou tudo! 🎉" else "Fim de jogo.") else "$left ${if (left == 1) "erro restante" else "erros restantes"}"} · ${if (points) "$total pontos" else "${got.size} de 10"}",
                    color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(q.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                if (finished) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                            val t = "$name ${if (infinite) "" else shortDay(date) + " "}${if (points) "$total pontos" else "${got.size}/10"}\nhttps://dmwnezes.com/termo/"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                        }
                        PillButton(if (infinite) "Outro desafio" else "Jogar o Infinito", Night.correct, modifier = Modifier.weight(1f)) {
                            store?.setText("${key}_inf_state", null)
                            store?.setText("${key}_mode", "inf")
                            infinite = true
                            round++
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it }, singleLine = true,
                            placeholder = { Text("Digite sua resposta") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { send() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Night.text, unfocusedTextColor = Night.text,
                                focusedBorderColor = Night.accent, unfocusedBorderColor = Night.outline, cursorColor = Night.accent,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        PillButton("OK", Night.correct) { send() }
                    }
                }
                Spacer(Modifier.height(10.dp))
                q.items.forEachIndexed { i, it ->
                    val has = i in got
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (has) Night.correct else if (finished) Night.surfaceHigh else Night.surface).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (has || finished) it.names[0] else if (points) "•••" else "${i + 1}",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (has || finished) Night.text else Night.muted, modifier = Modifier.weight(1f),
                        )
                        if (points && (has || finished)) Text("${it.pts}", fontSize = 15.sp, color = Night.text)
                    }
                }
                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Erros: ${errors.joinToString(", ")}", fontSize = 14.sp, color = Night.red, textAlign = TextAlign.Center)
                }
                note?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = Night.muted, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp)) { ToastView(toast) }
        if (confetti > 0) Confetti(confetti) {}
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar $name")
                Spacer(Modifier.height(12.dp))
                Text(help, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}
