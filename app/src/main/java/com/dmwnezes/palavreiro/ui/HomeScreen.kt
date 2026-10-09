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
enum class Dest { TERMO, INFINITO, DUETO, QUARTETO, CONEXOES, CACA, REVERSO, DEFINICAO, SINONIMOS }

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
@Composable
fun HomeScreen(badges: Map<Dest, String?>, onOpen: (Dest) -> Unit, onProfile: () -> Unit) {
    val guess = listOf(
        GameCardInfo(Dest.TERMO, "Termo", "Uma palavra nova por dia", badges[Dest.TERMO]),
        GameCardInfo(Dest.INFINITO, "Infinito", "Quantas palavras quiser", null),
        GameCardInfo(Dest.DUETO, "Dueto", "Duas palavras, 7 tentativas", badges[Dest.DUETO]),
        GameCardInfo(Dest.QUARTETO, "Quarteto", "Quatro palavras, 9 tentativas", badges[Dest.QUARTETO]),
    )
    val more = listOf(
        GameCardInfo(Dest.CONEXOES, "Conexões", "Separe 16 palavras em 4 grupos", badges[Dest.CONEXOES]),
        GameCardInfo(Dest.CACA, "Caça-Palavras", "Ache as palavras do tema do dia", badges[Dest.CACA]),
        GameCardInfo(Dest.REVERSO, "Reverso", "O app tenta adivinhar a sua palavra", null),
        GameCardInfo(Dest.DEFINICAO, "Qual é a Palavra?", "Descubra a palavra pela definição", null),
        GameCardInfo(Dest.SINONIMOS, "Sinônimos", "Corrente de sinônimos contra o tempo", null),
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
                SectionLabel("Adivinhe a palavra")
                guess.forEach { c -> GameCard(c) { onOpen(c.dest) } }
                Spacer(Modifier.height(4.dp))
                SectionLabel("Mais jogos")
                more.forEach { c -> GameCard(c) { onOpen(c.dest) } }
                Spacer(Modifier.height(8.dp))
            }
            CreatorCredit(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
        }
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
        Dest.CACA -> Box(Modifier.size(52.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Night.present.copy(alpha = 0.7f), Offset(size.width * 0.15f, size.height * 0.2f), Offset(size.width * 0.85f, size.height * 0.8f), strokeWidth = size.width * 0.2f, cap = StrokeCap.Round)
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                listOf("CAS", "OLE", "PAJ").forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        line.forEach { Text("$it", color = Night.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
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
        Dest.SINONIMOS -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Pill("BELO", Night.surfaceHigh)
            Text("=", color = Night.muted, fontSize = 12.sp)
            Pill("LINDO", Night.correct)
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color) {
    Text(
        text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        color = if (bg == Night.correct) Color(0xFF1A1438) else Night.text,
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
