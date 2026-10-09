package com.dmwnezes.palavreiro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.Mark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Barra do topo usada por todos os jogos. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, onHelp: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Night.text) }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
        trailing?.invoke()
        if (onHelp != null) IconButton(onClick = onHelp) { Icon(Icons.Rounded.HelpOutline, "Como jogar", tint = Night.muted) }
    }
}

@Composable
fun PillButton(
    text: String,
    color: Color,
    textColor: Color = Color(0xFF14102C),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier.clip(Shapes.pill).background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) textColor else textColor.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

/** Mensagem curta que aparece e some (ex.: "Palavra não aceita"). */
class ToastState(private val scope: CoroutineScope) {
    var text by mutableStateOf<String?>(null)
        private set
    private var id by mutableIntStateOf(0)

    fun show(msg: String, ms: Long = 1600) {
        text = msg
        val mine = ++id
        scope.launch { delay(ms); if (id == mine) text = null }
    }
}

@Composable
fun rememberToast(): ToastState {
    val scope = rememberCoroutineScope()
    return remember { ToastState(scope) }
}

@Composable
fun ToastView(state: ToastState, modifier: Modifier = Modifier) {
    AnimatedVisibility(state.text != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            state.text.orEmpty(),
            color = Color(0xFF1A1438),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).clip(Shapes.pill).background(Night.text).padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/** Janela que sobe de baixo (resultado, ajuda). Tocar fora fecha. */
@Composable
fun BottomSheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.sheet)
                .background(Night.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(Shapes.pill).background(Night.outline))
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, textAlign = TextAlign.Center)
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 16.sp, color = Night.muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatsRow(stats: Stats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem("${stats.played}", "Jogos")
        StatItem("${stats.winPct}%", "Vitórias")
        StatItem("${stats.streak}", "Sequência")
        StatItem("${stats.maxStreak}", "Melhor")
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
        Text(label, fontSize = 12.sp, color = Night.muted)
    }
}

@Composable
fun Distribution(dist: List<Int>, highlight: Int, rows: Int = 6) {
    val shown = dist.take(rows)
    val max = maxOf(1, shown.maxOrNull() ?: 1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        shown.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 13.sp, color = Night.muted, modifier = Modifier.width(16.dp))
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(maxOf(0.08f, v.toFloat() / max))
                            .clip(Shapes.pill)
                            .background(if (i + 1 == highlight) Night.correct else Night.absent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text("$v", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Night.text)
                    }
                }
            }
        }
    }
}

/** Teclado QWERTY. [colorsFor] devolve as cores de cada tecla (uma por tabuleiro, ou vazia). */
@Composable
fun LetterKeyboard(
    colorsFor: (Char) -> List<Mark?> = { emptyList() },
    enterLabel: String = "ENTER",
    onLetter: (Char) -> Unit,
    onEnter: () -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEachIndexed { idx, letters ->
            Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (idx == 1) Spacer(Modifier.weight(0.5f))
                if (idx == 2) KeyButton(enterLabel, Modifier.weight(1.6f), emptyList(), small = true, onClick = onEnter)
                letters.forEach { ch -> KeyButton(ch.toString(), Modifier.weight(1f), colorsFor(ch)) { onLetter(ch) } }
                if (idx == 2) KeyButton("⌫", Modifier.weight(1.6f), emptyList(), icon = true, onClick = onDelete)
                if (idx == 1) Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier,
    marks: List<Mark?>,
    small: Boolean = false,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    val single = marks.size <= 1
    val only = marks.firstOrNull()
    Box(
        modifier.height(56.dp).clip(Shapes.key).background(if (single && only != null) Night.mark(only) else Night.key).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Dueto: metades; Quarteto: quatro cantos, cada um com a cor de um tabuleiro.
        if (!single) {
            val cells = if (marks.size == 2) listOf(listOf(0, 1)) else listOf(listOf(0, 1), listOf(2, 3))
            Column(Modifier.matchParentSizeCompat()) {
                cells.forEach { line ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        line.forEach { b ->
                            val m = marks.getOrNull(b)
                            Box(Modifier.weight(1f).fillMaxSize().background(if (m != null) Night.mark(m) else Night.key))
                        }
                    }
                }
            }
        }
        if (icon) {
            Icon(Icons.AutoMirrored.Rounded.Backspace, "Apagar", tint = Night.text, modifier = Modifier.size(22.dp))
        } else {
            val dark = single && only == Mark.PRESENT
            Text(
                label,
                fontSize = if (small) (if (label.length > 5) 11.sp else 13.sp) else 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (dark) Night.onMark(only) else Night.text,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

/** Quadradinho de letra simples (usado em ajudas e jogos novos). */
@Composable
fun LetterBox(
    ch: Char?,
    size: androidx.compose.ui.unit.Dp,
    bg: Color = Color.Transparent,
    border: Color? = Night.outline,
    textColor: Color = Night.text,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        modifier.size(size).clip(shape).background(bg).then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(ch?.toString().orEmpty(), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}
