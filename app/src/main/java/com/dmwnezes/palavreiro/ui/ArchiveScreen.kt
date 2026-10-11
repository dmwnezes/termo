package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.data.Store
import java.time.LocalDate
import java.time.YearMonth

/** Jogos com desafio do dia que podem ser jogados pelo Arquivo. */
enum class ArchiveGame(val key: String, val title: String) {
    TERMO("termo", "Termo"), DUETO("dueto", "Dueto"), QUARTETO("quarteto", "Quarteto"),
    CONEXOES("conexoes", "Conexões"), CACA("caca", "Caça"),
}

private val FIRST_DAY: LocalDate = LocalDate.of(2026, 1, 1)
private val MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

/** Arquivo: escolha o jogo e um dia passado para jogar aquele desafio. */
@Composable
fun ArchiveScreen(
    store: Store?,
    onBack: () -> Unit,
    onPlay: (ArchiveGame, LocalDate) -> Unit,
    today: LocalDate = LocalDate.now(),
    initialGame: ArchiveGame = ArchiveGame.TERMO,
) {
    var game by rememberSaveable { mutableStateOf(initialGame) }
    val yesterday = today.minusDays(1)
    var month by rememberSaveable { mutableStateOf(YearMonth.from(yesterday).toString()) }
    val ym = YearMonth.parse(month)
    val results = store?.results().orEmpty()
    val termoDays = store?.termoResults().orEmpty()
    fun resultOf(d: LocalDate): String? = results["${game.key}|$d"] ?: if (game == ArchiveGame.TERMO) termoDays[d.toString()] else null

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Arquivo", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("Jogue os desafios dos dias anteriores. Eles não mexem na sua sequência.", color = Night.muted, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                ChipTabs(ArchiveGame.entries.map { it.title }, game.ordinal) { game = ArchiveGame.entries[it] }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val canPrev = ym > YearMonth.from(FIRST_DAY)
                        val canNext = ym < YearMonth.from(yesterday)
                        RoundStep("‹", canPrev) { month = ym.minusMonths(1).toString() }
                        Text(
                            "${MONTHS[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} de ${ym.year}",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Night.text, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        RoundStep("›", canNext) { month = ym.plusMonths(1).toString() }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                            Text(it, fontSize = 12.sp, color = Night.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                    val offset = ym.atDay(1).dayOfWeek.value % 7
                    val days = ym.lengthOfMonth()
                    for (week in 0 until (offset + days + 6) / 7) {
                        Row(Modifier.fillMaxWidth()) {
                            for (dow in 0 until 7) {
                                val n = week * 7 + dow - offset + 1
                                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                                    if (n in 1..days) {
                                        val date = ym.atDay(n)
                                        val open = !date.isBefore(FIRST_DAY) && !date.isAfter(yesterday)
                                        val r = resultOf(date)
                                        Box(
                                            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        !open -> Night.bgBottom.copy(alpha = 0.4f)
                                                        r == "w" -> Night.correct.copy(alpha = 0.85f)
                                                        r == "l" -> Night.red.copy(alpha = 0.75f)
                                                        else -> Night.surfaceHigh
                                                    }
                                                )
                                                .clickable(enabled = open) { onPlay(game, date) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "$n", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                                color = when { !open -> Night.muted.copy(alpha = 0.4f); r != null -> Night.bgBottom; else -> Night.text },
                                            )
                                            if (r != null) {
                                                Text(
                                                    if (r == "w") "✓" else "✗", fontSize = 9.sp, color = Night.bgBottom, fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val doneInMonth = (1..days).count { resultOf(ym.atDay(it)) != null }
                    Text("$doneInMonth ${if (doneInMonth == 1) "desafio feito" else "desafios feitos"} neste mês", fontSize = 13.sp, color = Night.muted)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(Night.correct, "acertou"); Spacer(Modifier.size(14.dp)); Legend(Night.red, "errou"); Spacer(Modifier.size(14.dp)); Legend(Night.surfaceHigh, "a jogar")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
    Text("  $label", fontSize = 13.sp, color = Night.muted)
}

@Composable
private fun RoundStep(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Night.surfaceHigh.copy(alpha = if (enabled) 1f else 0.35f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, color = Night.text.copy(alpha = if (enabled) 1f else 0.4f), fontWeight = FontWeight.SemiBold) }
}

/** Abas em pílula (usadas no Arquivo e no Perfil). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    if (options.size > 4) {
        // Muitas opções: pílulas soltas que quebram linha, todas visíveis.
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { i, label ->
                Text(
                    label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                    color = if (i == selected) Ink2 else Night.text,
                    modifier = Modifier.clip(Shapes.pill).background(if (i == selected) Night.accent else Night.surfaceHigh)
                        .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.surface).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            Text(
                label,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (options.size > 3) 13.sp else 15.sp,
                maxLines = 1,
                color = if (i == selected) Ink2 else Night.muted,
                modifier = Modifier.weight(1f).clip(Shapes.pill)
                    .background(if (i == selected) Night.accent else Night.surface)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
            )
        }
    }
}

private val Ink2 = androidx.compose.ui.graphics.Color(0xFF14102C)
