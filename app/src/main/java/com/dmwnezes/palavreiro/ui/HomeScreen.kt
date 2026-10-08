package com.dmwnezes.palavreiro.ui

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.data.Mode

/** Informação de cada cartão da tela inicial. */
data class GameCardInfo(
    val title: String,
    val subtitle: String,
    val badge: String?,
    val boards: Int,
    val mode: Mode?,
)

@Composable
fun HomeScreen(dailyDone: Boolean, dailyWon: Boolean, onPlay: (Mode) -> Unit, onProfile: () -> Unit) {
    val cards = listOf(
        GameCardInfo("Termo", "Uma palavra nova por dia", if (dailyDone) (if (dailyWon) "Feito hoje ✓" else "Volte amanhã") else "Novo", 1, Mode.DIARIO),
        GameCardInfo("Infinito", "Quantas palavras quiser", null, 1, Mode.INFINITO),
        GameCardInfo("Dueto", "Duas palavras ao mesmo tempo", "Em breve", 2, null),
        GameCardInfo("Quarteto", "Quatro palavras ao mesmo tempo", "Em breve", 4, null),
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
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                cards.forEach { c -> GameCard(c) { c.mode?.let(onPlay) } }
                Spacer(Modifier.height(8.dp))
            }
            CreatorCredit(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun GameCard(info: GameCardInfo, onClick: () -> Unit) {
    val enabled = info.mode != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(Night.surface)
            .border(1.dp, Night.outline.copy(alpha = 0.45f), Shapes.card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(18.dp)
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniBoards(info.boards)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Night.text)
                info.badge?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (it == "Novo") Color(0xFF1A1438) else Night.text,
                        modifier = Modifier
                            .clip(Shapes.pill)
                            .background(if (it == "Novo") Night.present else Night.surfaceHigh)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(info.subtitle, fontSize = 14.sp, color = Night.muted)
        }
    }
}

/** Desenho do cartão: 1, 2 ou 4 mini tabuleiros. */
@Composable
private fun MiniBoards(count: Int) {
    val cell = if (count == 1) 12.dp else 7.dp
    val gap = 2.dp
    @Composable
    fun board(seed: Int) {
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
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        when (count) {
            1 -> board(0)
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { board(0); board(2) }
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { board(0); board(1) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { board(2); board(3) }
            }
        }
    }
}
