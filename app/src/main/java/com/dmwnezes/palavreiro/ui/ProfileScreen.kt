package com.dmwnezes.palavreiro.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import com.dmwnezes.palavreiro.system.Reminder
import java.time.LocalDate
import java.time.YearMonth
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Achievements
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.update.Updater

/** Perfil: estatísticas, conquistas e configurações, num só lugar. */
@Composable
fun ProfileScreen(
    store: Store?,
    stats: Map<Mode, Stats>,
    records: Records,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onHelp: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val daily = stats[Mode.DIARIO] ?: Stats()
    val infinite = stats[Mode.INFINITO] ?: Stats()
    val modes = Mode.entries.filter { it != Mode.DESAFIO }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Night.text) }
                Text("Perfil", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, modifier = Modifier.padding(start = 4.dp))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Night.surfaceHigh), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, null, tint = Night.text, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("${stats.values.sumOf { it.won }} partidas vencidas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Night.text)
                        Text("Sequência no Termo: ${daily.streak}", fontSize = 14.sp, color = Night.muted)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Section("Calendário") {
                    StreakCalendar(store?.activity() ?: emptyMap(), store?.termoResults() ?: emptyMap())
                }
                Section("Estatísticas") {
                    Segmented(modes.map { it.title }, tab) { tab = it }
                    Spacer(Modifier.height(16.dp))
                    val s = stats[modes[tab]] ?: Stats()
                    StatsRow(s)
                    Spacer(Modifier.height(16.dp))
                    Text("Distribuição de tentativas", fontSize = 13.sp, color = Night.muted)
                    Spacer(Modifier.height(8.dp))
                    Distribution(s.dist, highlight = -1, rows = modes[tab].maxTries)
                }
                Section("Recordes") {
                    RecordRow("Conexões", "${records.connWon} resolvidos · ${records.connPerfect} sem erro")
                    RecordRow("Caça-Palavras", "${records.wsPlayed} grades · melhor ${if (records.wsBest > 0) formatTime(records.wsBest) else "—"}")
                    RecordRow("Reverso", "app ${records.revApp} × ${records.revUser} você")
                    RecordRow("Qual é a Palavra?", "recorde ${records.defBest} seguidas · ${records.defRight} acertos")
                    RecordRow("Sinônimos", "maior cadeia: ${records.synBest}")
                }
                Section("Conquistas") {
                    val list = Achievements.all(daily, infinite, stats[Mode.DUETO] ?: Stats(), stats[Mode.QUARTETO] ?: Stats(), records)
                    Text("${list.count { it.unlocked }} de ${list.size}", fontSize = 13.sp, color = Night.muted)
                    Spacer(Modifier.height(10.dp))
                    list.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            pair.forEach { a ->
                                Column(
                                    Modifier.weight(1f).clip(Shapes.card).background(Night.surfaceHigh)
                                        .alpha(if (a.unlocked) 1f else 0.45f).padding(14.dp),
                                ) {
                                    Text(if (a.unlocked) a.emoji else "🔒", fontSize = 24.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Night.text)
                                    Text(a.description, fontSize = 12.sp, color = Night.muted)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Section("Configurações") {
                    var sound by remember { mutableStateOf(store?.sound ?: true) }
                    var vib by remember { mutableStateOf(store?.vibration ?: true) }
                    ToggleRow("Sons", sound) { sound = it; store?.sound = it }
                    ToggleRow("Vibração", vib) { vib = it; store?.vibration = it }
                    var hard by remember { mutableStateOf(store?.hard ?: false) }
                    ToggleRow("Modo difícil", hard) { hard = it; store?.hard = it }
                    Text("Vale a partir da próxima partida do Termo e do Infinito.", fontSize = 12.sp, color = Night.muted)
                    Spacer(Modifier.height(6.dp))
                    var remind by remember { mutableStateOf(store?.reminder ?: false) }
                    var hour by remember { mutableIntStateOf(store?.reminderHour ?: 9) }
                    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                        remind = ok; store?.reminder = ok; Reminder.schedule(context)
                    }
                    ToggleRow("Lembrete diário", remind) { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 && !Reminder.canNotify(context)) {
                            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            remind = on; store?.reminder = on; Reminder.schedule(context)
                        }
                    }
                    if (remind) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Horário", fontSize = 15.sp, color = Night.muted, modifier = Modifier.weight(1f))
                            Stepper("−") { hour = (hour + 23) % 24; store?.reminderHour = hour; Reminder.schedule(context) }
                            Text("%02d:00".format(hour), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Night.text, modifier = Modifier.padding(horizontal = 12.dp))
                            Stepper("+") { hour = (hour + 1) % 24; store?.reminderHour = hour; Reminder.schedule(context) }
                        }
                        Text("Avisa só se você ainda não jogou o Termo do dia.", fontSize = 12.sp, color = Night.muted)
                    }
                    ActionRow("Como jogar", onClick = onHelp)
                    ActionRow("Buscar atualização", detail = "Versão ${Updater.currentName}", onClick = onCheckUpdates)
                }
                Spacer(Modifier.height(8.dp))
            }
            CreatorCredit(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(Shapes.card).background(Night.surface).padding(18.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Night.text)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Night.surfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, color = Night.text, fontWeight = FontWeight.SemiBold) }
}

/**
 * Calendário do mês: quanto mais jogos no dia, mais forte a cor.
 * O pontinho mostra o Termo do dia (verde = acertou, vermelho = errou).
 */
@Composable
fun StreakCalendar(activity: Map<String, Int>, termo: Map<String, String>, today: LocalDate = LocalDate.now()) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val names = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Stepper("‹") { month = month.minusMonths(1) }
        Text(
            "${names[month.monthValue - 1].replaceFirstChar { it.uppercase() }} de ${month.year}",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Night.text, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Stepper("›") { if (month < YearMonth.from(today)) month = month.plusMonths(1) }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
            Text(it, fontSize = 12.sp, color = Night.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(4.dp))
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7 // domingo = 0
    val days = month.lengthOfMonth()
    val cells = offset + days
    val maxCount = maxOf(1, activity.values.maxOrNull() ?: 1)
    for (week in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (dow in 0 until 7) {
                val n = week * 7 + dow - offset + 1
                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                    if (n in 1..days) {
                        val date = month.atDay(n)
                        val key = date.toString()
                        val count = activity[key] ?: 0
                        val level = if (count == 0) 0f else 0.3f + 0.7f * (count.toFloat() / maxCount)
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(if (count == 0) Night.bgBottom else Night.accent.copy(alpha = level))
                                .then(if (date == today) Modifier.border(2.dp, Night.text, RoundedCornerShape(10.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$n", fontSize = 12.sp, color = if (count > 0) Night.text else Night.muted, fontWeight = FontWeight.Medium)
                            termo[key]?.let { r ->
                                Box(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(5.dp).clip(CircleShape)
                                        .background(if (r == "w") Night.correct else Night.red)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val inMonth = activity.filterKeys { it.startsWith(month.toString()) }
    Text(
        "${inMonth.size} ${if (inMonth.size == 1) "dia jogado" else "dias jogados"} · ${inMonth.values.sum()} ${if (inMonth.values.sum() == 1) "jogo" else "jogos"} no mês",
        fontSize = 13.sp, color = Night.muted,
    )
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = Night.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = Night.muted, textAlign = TextAlign.End)
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.bgBottom).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            Text(
                label,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (options.size > 2) 13.sp else 15.sp,
                maxLines = 1,
                color = if (i == selected) Night.text else Night.muted,
                modifier = Modifier.weight(1f).clip(Shapes.pill)
                    .background(if (i == selected) Night.surfaceHigh else Night.bgBottom)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.key).clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = Night.text, modifier = Modifier.weight(1f))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Night.correct, checkedThumbColor = Night.text, uncheckedTrackColor = Night.absent),
        )
    }
}

@Composable
private fun ActionRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.key).clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = Night.text, modifier = Modifier.weight(1f))
        detail?.let { Text(it, fontSize = 13.sp, color = Night.muted) }
        Text("  ›", fontSize = 18.sp, color = Night.muted)
    }
}
