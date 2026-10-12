package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.Records

/** Um cartão de recorde: jogo, número principal (com unidade) e uma linha de apoio. */
data class RecordCardInfo(val dest: Dest, val title: String, val value: String?, val unit: String, val detail: String)

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** Todos os recordes, na ordem da grade (mesmos dados de antes, agora em cartões). */
fun recordCards(infinite: Stats, r: Records): List<RecordCardInfo> {
    fun v(n: Int) = n.takeIf { it > 0 }?.toString()
    val chain = maxOf(r["sa_best"], r.synBest, r["ant_best"])
    val tricks = maxOf(r["ant_best"], r["sa_tricks"])
    return listOf(
        RecordCardInfo(Dest.INFINITO, "Termo Infinito", v(infinite.won), "acertos", "maior sequência ${infinite.maxStreak}"),
        RecordCardInfo(Dest.CONEXOES, "Conexões", v(r.connWon), "resolvidos", "${r.connPerfect} sem erro"),
        RecordCardInfo(Dest.CACA, "Caça-Palavras", r.wsBest.takeIf { it > 0 }?.let(::formatTime), "melhor tempo", plural(r.wsPlayed, "grade", "grades")),
        RecordCardInfo(Dest.DEFINICAO, "Qual é a Palavra?", v(r.defBest), "seguidas", plural(r.defRight, "acerto", "acertos")),
        RecordCardInfo(Dest.SINONIMOS, "Sinônimo ou Antônimo", v(chain), "na cadeia", plural(tricks, "pegadinha", "pegadinhas")),
        RecordCardInfo(Dest.INTRUSO, "Intruso", v(r["intr_best"]), "pontos", plural(r["intr_right"], "acerto", "acertos")),
        RecordCardInfo(Dest.ORTOGRAFIA, "Certo ou Errado", v(r["ort_best"]), "pontos", plural(r["ort_right"], "acerto", "acertos")),
        RecordCardInfo(Dest.MESTRE, "Mestre Mandou", v(r["mestre_best"]), "pontos", plural(r["mestre_right"], "acerto", "acertos")),
        RecordCardInfo(Dest.TRIVIA, "Trivia", v(r["trivia_best"]), "seguidas", "${plural(r["trivia_won"], "vitória", "vitórias")} · ${r["trivia_right"]} ✓"),
        memoryCard(r),
        RecordCardInfo(Dest.ARQUIVO, "Arquivo", v(r["arch_played"]), if (r["arch_played"] == 1) "desafio" else "desafios", plural(r["arch_won"], "vencido", "vencidos")),
        RecordCardInfo(Dest.DESAFIAR, "Com amigos", v(r["mp_won"]), if (r["mp_won"] == 1) "vitória" else "vitórias", "em ${plural(r["mp_played"], "partida", "partidas")}"),
    )
}

/** Memória: melhor tempo do nível mais difícil já terminado. */
private fun memoryCard(r: Records): RecordCardInfo {
    val lvl = com.dmwnezes.palavreiro.game.MemLevel.entries.lastOrNull { r[it.timeKey] > 0 }
        ?: return RecordCardInfo(Dest.MEMORIA, "Jogo da Memória", null, "melhor tempo", "nenhum nível ainda")
    return RecordCardInfo(Dest.MEMORIA, "Jogo da Memória", formatTime(r[lvl.timeKey]), "no ${lvl.label}", "menos jogadas: ${r[lvl.movesKey]}")
}

/** Seção "Recordes": destaque da maior sequência do Termo e uma grade de cartões por jogo. */
@Composable
fun RecordsSection(daily: Stats, infinite: Stats, records: Records) {
    HighlightCard(daily)
    Spacer(Modifier.height(12.dp))
    recordCards(infinite, records).chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { RecordCard(it, Modifier.weight(1f).fillMaxHeight()) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HighlightCard(daily: Stats) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(Night.surfaceHigh, Night.bgTop)))
            .border(1.dp, Night.present.copy(alpha = 0.45f), shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(Night.present.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("🔥", fontSize = 30.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("MELHOR SEQUÊNCIA", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Night.present, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${daily.maxStreak}", fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                Spacer(Modifier.width(6.dp))
                Text(if (daily.maxStreak == 1) "dia no Termo" else "dias no Termo", fontSize = 16.sp, color = Night.muted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                "atual ${daily.streak} · ${daily.winPct}% de vitórias",
                fontSize = 12.sp, color = Night.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecordCard(c: RecordCardInfo, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(Night.bgBottom.copy(alpha = 0.55f))
            .border(1.dp, Night.outline.copy(alpha = 0.35f), shape)
            .padding(14.dp),
    ) {
        // Mesmo desenho do cartão da tela inicial, em miniatura, e o nome do jogo embaixo (até 2 linhas).
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Night.surface), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(58.dp).scale(0.48f), contentAlignment = Alignment.Center) { CardIcon(c.dest) }
        }
        Spacer(Modifier.height(10.dp))
        Text(c.title, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = Night.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.weight(1f))
        Text(
            c.value ?: "—", fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold,
            color = if (c.value != null) Night.text else Night.muted.copy(alpha = 0.6f), maxLines = 1,
        )
        Text(c.unit, fontSize = 12.sp, color = Night.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(c.detail, fontSize = 12.sp, color = Night.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
