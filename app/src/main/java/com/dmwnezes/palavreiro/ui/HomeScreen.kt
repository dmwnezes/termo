package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Destinos da tela inicial. */
enum class Dest { TERMO, INFINITO, DUETO, QUARTETO, CONEXOES, REVERSO, DEFINICAO, SINONIMOS, DESAFIAR, MESTRE, INTRUSO, ORTOGRAFIA, ARQUIVO }

/** Informação de cada cartão da tela inicial. */
private data class GameCardInfo(
    val dest: Dest,
    val title: String,
    val subtitle: String,
    val badge: String?,
)

/**
 * Tela inicial: um cartão por jogo, perfil no canto superior direito e créditos no rodapé.
 * [badges] traz o selo de cada jogo diário ("Novo", "Feito hoje ✓").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    badges: Map<Dest, String?>,
    onOpen: (Dest) -> Unit,
    onProfile: () -> Unit,
    today: com.dmwnezes.palavreiro.system.DailyStatus? = null,
    celebrate: Boolean = false,
) {
    val guess = listOf(
        GameCardInfo(Dest.TERMO, "Termo", "Uma palavra nova por dia", badges[Dest.TERMO]),
        GameCardInfo(Dest.DUETO, "Dueto", "Duas palavras, 7 tentativas", badges[Dest.DUETO]),
        GameCardInfo(Dest.QUARTETO, "Quarteto", "Quatro palavras, 9 tentativas", badges[Dest.QUARTETO]),
    )
    val more = listOf(
        GameCardInfo(Dest.CONEXOES, "Conexões", "Separe 16 palavras em 4 grupos", badges[Dest.CONEXOES]),
        GameCardInfo(Dest.INTRUSO, "Intruso", "Ache a palavra que não pertence ao grupo", null),
        GameCardInfo(Dest.ORTOGRAFIA, "Certo ou Errado", "A palavra está escrita certo?", null),
        GameCardInfo(Dest.MESTRE, "Mestre Mandou", "Obedeça só quando o mestre mandar", null),
        GameCardInfo(Dest.REVERSO, "Reverso", "O app tenta adivinhar a sua palavra", null),
        GameCardInfo(Dest.DEFINICAO, "Qual é a Palavra?", "Descubra a palavra pela definição", null),
        GameCardInfo(Dest.SINONIMOS, "Sinônimo ou Antônimo", "Mesmo sentido ou o contrário? Leia a pergunta!", null),
        GameCardInfo(Dest.DESAFIAR, "Jogar com amigo", "Partida ao vivo: quem acertar primeiro ganha", null),
    )
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Palavreiro", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                    Spacer(Modifier.height(6.dp))
                    FiveSquares(square = 10.dp, gap = 3.dp)
                }
                IconButton(
                    onClick = onProfile,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Night.surfaceHigh),
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = "Perfil", tint = Night.text)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                today?.let { TodayPanel(it, onOpen) }
                SectionLabel("Adivinhe a palavra")
                guess.forEach { c -> GameCard(c) { onOpen(c.dest) } }
                Spacer(Modifier.height(4.dp))
                SectionLabel("Mais jogos")
                more.forEach { c -> GameCard(c) { onOpen(c.dest) } }
                Spacer(Modifier.height(8.dp))
            }
            CreatorCredit(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
        }
        if (celebrate) Confetti(1) {}
    }
}

/** Painel "Hoje": quantos desafios do dia já foram feitos, com atalho para cada um e para o Arquivo. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TodayPanel(status: com.dmwnezes.palavreiro.system.DailyStatus, onOpen: (Dest) -> Unit) {
    val items = listOf(
        Triple(Dest.TERMO, "Termo", status.termo), Triple(Dest.DUETO, "Dueto", status.dueto),
        Triple(Dest.QUARTETO, "Quarteto", status.quarteto), Triple(Dest.CONEXOES, "Conexões", status.conexoes),
    )
    val done = status.doneCount
    Column(
        Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface)
            .border(1.dp, Night.outline.copy(alpha = 0.45f), Shapes.card).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hoje", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Night.text, modifier = Modifier.weight(1f))
            Text("$done de ${items.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (done == items.size) Night.correct else Night.muted)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.pill).background(Night.bgBottom)) {
            Box(Modifier.fillMaxWidth(done / items.size.toFloat()).height(8.dp).clip(Shapes.pill).background(Night.correct))
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEach { (dest, label, ok) ->
                Text(
                    if (ok) "✓ $label" else label,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    color = if (ok) Color(0xFF1A1438) else Night.text,
                    modifier = Modifier.clip(Shapes.pill).background(if (ok) Night.correct else Night.surfaceHigh)
                        .clickable { onOpen(dest) }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        if (done == items.size) {
            Spacer(Modifier.height(10.dp))
            Text("Tudo feito hoje! 🎉", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Night.correct)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "🗂️  Arquivo de desafios  ›", fontSize = 15.sp, color = Night.accent, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(Shapes.pill).clickable { onOpen(Dest.ARQUIVO) }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Night.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun GameCard(info: GameCardInfo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(Night.surface)
            .border(1.dp, Night.outline.copy(alpha = 0.45f), Shapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) { CardIcon(info.dest) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Night.text, maxLines = 1)
                info.badge?.let {
                    Spacer(Modifier.width(8.dp))
                    val fresh = it == "Novo"
                    Text(
                        it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fresh) Color(0xFF1A1438) else Night.text,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(Shapes.pill)
                            .background(if (fresh) Night.present else Night.surfaceHigh)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(info.subtitle, fontSize = 14.sp, color = Night.muted)
        }
    }
}

/** Desenhos pequenos de cada cartão. */
@Composable
private fun CardIcon(dest: Dest) {
    when (dest) {
        Dest.TERMO, Dest.INFINITO -> MiniBoard(12.dp, 0)
        Dest.DUETO -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MiniBoard(7.dp, 0); MiniBoard(7.dp, 2) }
        Dest.QUARTETO -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MiniBoard(7.dp, 0); MiniBoard(7.dp, 1) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MiniBoard(7.dp, 2); MiniBoard(7.dp, 3) }
        }
        Dest.CONEXOES -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Night.levels.forEach { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(4) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)) } }
            }
        }
        Dest.REVERSO -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("?", color = Night.accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(Night.correct, Night.absent, Night.present, Night.absent, Night.correct).forEach { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(it)) }
            }
        }
        Dest.DEFINICAO -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.width(46.dp).height(5.dp).clip(Shapes.pill).background(Night.muted.copy(alpha = 0.6f)))
            Box(Modifier.width(34.dp).height(5.dp).clip(Shapes.pill).background(Night.muted.copy(alpha = 0.6f)))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(null, 'A', null, null).forEach { ch ->
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(if (ch != null) Night.present else Night.surfaceHigh))
                }
            }
        }
        Dest.DESAFIAR -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            // Dois mini tabuleiros lado a lado: a partida com amigo.
            MiniBoard(8.dp, 0); MiniBoard(8.dp, 3)
        }
        Dest.INTRUSO -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(Night.surfaceHigh, Night.surfaceHigh, Night.red, Night.surfaceHigh, Night.surfaceHigh).forEach {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(it))
            }
        }
        Dest.ORTOGRAFIA -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("✓" to Night.correct, "✗" to Night.red).forEach { (t, c) ->
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(c), contentAlignment = Alignment.Center) {
                    Text(t, color = Color(0xFF1A1438), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Dest.ARQUIVO -> Text("🗂️", fontSize = 26.sp)
        Dest.MESTRE -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("👑", fontSize = 20.sp)
            Pill("TOQUE", Night.present)
        }
        Dest.SINONIMOS -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Pill("BELO", Night.surfaceHigh); Text("=", color = Night.muted, fontSize = 10.sp); Pill("LINDO", Night.correct)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Pill("ALTO", Night.surfaceHigh); Text("≠", color = Night.muted, fontSize = 10.sp); Pill("BAIXO", Night.red)
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color) {
    Text(
        text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        color = if (bg == Night.correct || bg == Night.red || bg == Night.present) Color(0xFF1A1438) else Night.text,
        modifier = Modifier.clip(Shapes.pill).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun MiniBoard(cell: Dp, seed: Int) {
    val gap = 2.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(3) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(3) { c ->
                    val v = (r * 3 + c + seed) % 5
                    val col = when {
                        r == 2 -> Night.correct
                        v == 0 -> Night.present
                        v == 3 -> Night.correct
                        else -> Night.absent
                    }
                    Box(Modifier.size(cell).clip(RoundedCornerShape(cell * 0.3f)).background(col))
                }
            }
        }
    }
}
