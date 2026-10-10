package com.dmwnezes.palavreiro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.game.Multi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reações rápidas da partida com amigo (todos os modos): o botão "😊" no topo, a fileira de emojis
 * e os balões que sobem e somem. O botão fica num espaço reservado na barra do topo ([ReactAnchor])
 * e é desenhado por cima de tudo pela [ReactionOverlay] (assim funciona também na contagem e na folha de fim).
 */
class ReactionState(private val scope: CoroutineScope) {
    data class Bubble(val key: Int, val emoji: String, val name: String, val mine: Boolean, val slot: Int)

    val bubbles = mutableStateListOf<Bubble>()
    var open by mutableStateOf(false)
    /** Intervalo de 1,5 s depois de mandar uma reação (botão apagado). */
    var cooling by mutableStateOf(false)
        private set
    /** Onde fica o botão na tela (coordenadas da raiz). */
    var anchor by mutableStateOf<Rect?>(null)
    /** Manda a reação para o amigo. */
    var onSend: (String) -> Unit = {}
    private var seq = 0

    /** Mostra um balão ([mine] = a minha, menor e com "Você"). */
    fun show(emoji: String, name: String, mine: Boolean) {
        bubbles += Bubble(seq, emoji, name, mine, seq % SLOTS.size)
        seq++
    }

    internal fun remove(key: Int) { bubbles.removeAll { it.key == key } }

    /** Toque num emoji da fileira: manda, fecha e mostra o meu balão. */
    fun send(emoji: String) {
        open = false
        if (cooling || emoji !in Multi.REACTIONS) return
        cooling = true
        onSend(emoji)
        show(emoji, "Você", mine = true)
        scope.launch { delay(Multi.REACT_GAP_MS); cooling = false }
    }

    companion object {
        /** Deslocamento horizontal dos balões seguidos (dp). */
        val SLOTS = listOf(0, -64, 64, -128, 128)
        const val BUBBLE_MS = 2500
    }
}

@Composable
fun rememberReactionState(): ReactionState {
    val scope = rememberCoroutineScope()
    return remember { ReactionState(scope) }
}

/** Espaço do botão "😊" na barra do topo (à direita do título, antes do placar). */
@Composable
fun ReactAnchor(state: ReactionState?) {
    if (state == null) return
    Box(
        Modifier.padding(start = 4.dp, end = 4.dp).size(30.dp).onGloballyPositioned {
            val p = it.positionInRoot()
            state.anchor = Rect(p, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }
    )
}

/**
 * Camada das reações, por cima da tela da partida (inclusive da contagem e da folha de fim).
 * [bubbleGap] = distância entre a barra do topo e os balões (abaixo da faixa do adversário).
 */
@Composable
fun ReactionOverlay(state: ReactionState, bubbleGap: Dp) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val a = state.anchor ?: return@Box
        val ax = (a.left - origin.x).toInt()
        val ay = (a.top - origin.y).toInt()
        val below = ay + a.height.toInt()
        // Balões.
        val bubbleTop = below + with(density) { bubbleGap.roundToPx() }
        for (b in state.bubbles) key(b.key) { ReactionBubble(state, b, bubbleTop) }
        // Fileira de emojis: toque fora fecha.
        if (state.open) {
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.open = false }
            )
            Row(
                Modifier.align(Alignment.TopCenter).offset { IntOffset(0, below + with(density) { 8.dp.roundToPx() }) }
                    .shadow(12.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(Night.surfaceHigh)
                    .border(1.dp, Night.outline.copy(alpha = 0.6f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (e in Multi.REACTIONS) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).clickable(role = Role.Button) { state.send(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 32.sp) }
                }
            }
        }
        // O botão, no lugar reservado na barra do topo.
        Box(
            Modifier.offset { IntOffset(ax, ay) }.size(with(density) { a.width.toDp() }, with(density) { a.height.toDp() })
                .clip(CircleShape).background(if (state.open) Night.accent.copy(alpha = 0.35f) else Night.surfaceHigh)
                .alpha(if (state.cooling) 0.4f else 1f)
                .semantics(mergeDescendants = true) { contentDescription = "Reagir" }
                .clickable(enabled = !state.cooling, role = Role.Button) { state.open = !state.open },
            contentAlignment = Alignment.Center,
        ) { Text("😊", fontSize = 16.sp) }
    }
}

@Composable
private fun BoxScope.ReactionBubble(state: ReactionState, b: ReactionState.Bubble, top: Int) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(ReactionState.BUBBLE_MS, easing = LinearEasing))
        state.remove(b.key)
    }
    val density = LocalDensity.current
    val rise = with(density) { 60.dp.toPx() }
    val dx = with(density) { ReactionState.SLOTS[b.slot].dp.roundToPx() }
    val v = p.value
    Column(
        Modifier.align(Alignment.TopCenter).offset { IntOffset(dx, top) }
            .graphicsLayer {
                translationY = -rise * v
                alpha = if (v < 0.55f) 1f else (1f - (v - 0.55f) / 0.45f).coerceIn(0f, 1f)
                val pop = (0.5f + v * 6f).coerceAtMost(1f)
                scaleX = pop; scaleY = pop
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(b.emoji, fontSize = if (b.mine) 38.sp else 56.sp)
        Text(
            b.name, color = Night.text, fontSize = if (b.mine) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Night.surface.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
