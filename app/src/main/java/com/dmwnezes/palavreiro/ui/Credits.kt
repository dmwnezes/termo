package com.dmwnezes.palavreiro.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.AppGraph
import com.dmwnezes.palavreiro.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

const val CREATOR_HANDLE = "dmwnezes"

/** Abre o perfil do criador no app do Instagram (ou no navegador, se o app não estiver instalado). */
fun openCreatorInstagram(context: Context) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/_u/$CREATOR_HANDLE")).setPackage("com.instagram.android")
    try {
        context.startActivity(app)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$CREATOR_HANDLE/")))
    }
}

/**
 * Foto redonda pequena + "criado por: @dmwnezes".
 * Toque: abre o Instagram. Segurar ~1,5 s: o celular vibra cada vez mais forte e as letras explodem (easter egg);
 * soltar antes cancela. [easterEgg] = false deixa só o toque (abertura).
 */
@Composable
fun CreatorCredit(modifier: Modifier = Modifier, easterEgg: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var level by remember { mutableFloatStateOf(0f) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val press = if (!easterEgg) Modifier.clickable { openCreatorInstagram(context) } else Modifier
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick("Abrir Instagram") { openCreatorInstagram(context); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                var fired = false
                val hold = scope.launch {
                    var held = 0f
                    while (held < CreditsEgg.HOLD_MS) {
                        level = held / CreditsEgg.HOLD_MS
                        AppGraph.feedbackOrNull?.holdPulse(level)
                        delay(75)
                        held += 75f
                    }
                    fired = true
                    level = 0f
                    AppGraph.feedbackOrNull?.burst()
                    CreditsEgg.fire(center)
                }
                val up = waitForUpOrCancellation()
                hold.cancel()
                level = 0f
                if (!fired && up != null && up.uptimeMillis - down.uptimeMillis < CreditsEgg.TAP_MS) {
                    up.consume()
                    openCreatorInstagram(context)
                }
            }
        }
    // Enquanto segura: o selo cresce, clareia e treme cada vez mais.
    val shake = if (level > 0f) sin(level * 90f) * level * 3f else 0f
    Row(
        modifier
            .onGloballyPositioned { c -> center = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f) }
            .graphicsLayer { scaleX = 1f + level * 0.08f; scaleY = 1f + level * 0.08f; translationX = shake * density }
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f + level * 0.14f))
            .then(press)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.criador),
            contentDescription = "Foto de @$CREATOR_HANDLE",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text("criado por: ", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(
            "@$CREATOR_HANDLE",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** Estado do easter egg dos créditos: cada [fire] gera uma explosão nova a partir de [Burst.origin]. */
object CreditsEgg {
    const val HOLD_MS = 1500f
    /** Até quanto tempo segurando ainda conta como toque (abre o Instagram). */
    const val TAP_MS = 400L
    /** Duração total: letras voando, mensagem e o sumiço. */
    const val TOTAL_S = 4.6f

    data class Burst(val id: Int, val origin: Offset)

    var burst by mutableStateOf<Burst?>(null)
        private set
    private var count = 0

    fun fire(origin: Offset) { burst = Burst(++count, origin) }
    fun clear() { burst = null }
}

/**
 * Letras A–Z (verde, amarelo e azul) que saem dos créditos com gravidade e quicam nas bordas e no chão.
 * Simulação em passos fixos (determinística para a mesma semente).
 */
class LetterBurst(seed: Int, val originX: Float, val originY: Float, val width: Float, val height: Float, val tile: Float, count: Int = 44) {
    val letters = CharArray(count) { 'A' + it % 26 }
    val colors = IntArray(count) { it % 3 }
    val x = FloatArray(count) { originX }
    val y = FloatArray(count) { originY }
    val vx = FloatArray(count)
    val vy = FloatArray(count)
    val angle = FloatArray(count)
    val spin = FloatArray(count)
    val scale = FloatArray(count)
    private val gravity = 1.7f * height

    init {
        val r = kotlin.random.Random(seed)
        for (i in 0 until count) {
            // Leque para cima (de -165° a -15°), cada letra com força diferente.
            val a = Math.toRadians(-165.0 + r.nextDouble() * 150.0)
            val speed = height * (0.75f + r.nextFloat() * 1.15f)
            vx[i] = (kotlin.math.cos(a) * speed).toFloat()
            vy[i] = (kotlin.math.sin(a) * speed).toFloat()
            spin[i] = (r.nextFloat() * 2f - 1f) * 540f
            angle[i] = (r.nextFloat() * 2f - 1f) * 30f
            scale[i] = 0.75f + r.nextFloat() * 0.5f
        }
    }

    /** Avança [dt] segundos (em passos de no máximo 1/120 s). */
    fun advance(dt: Float) {
        var left = dt
        while (left > 0f) {
            val h = minOf(left, 1f / 120f)
            step(h)
            left -= h
        }
    }

    private fun step(h: Float) {
        val half = tile / 2
        for (i in x.indices) {
            vy[i] += gravity * h
            x[i] += vx[i] * h
            y[i] += vy[i] * h
            angle[i] += spin[i] * h
            if (x[i] < half) { x[i] = half; vx[i] = -vx[i] * 0.7f }
            if (x[i] > width - half) { x[i] = width - half; vx[i] = -vx[i] * 0.7f }
            if (y[i] < half) { y[i] = half; vy[i] = -vy[i] * 0.5f }
            val floor = height - half
            if (y[i] > floor) {
                y[i] = floor
                if (vy[i] > 0) vy[i] = -vy[i] * 0.5f
                vx[i] *= 0.86f
                spin[i] *= 0.7f
            }
        }
    }
}

/** Camada da tela inteira com a explosão de letras e "Obrigado por jogar! 💚". Some sozinha. */
@Composable
fun CreditsEggOverlay(burst: CreditsEgg.Burst? = CreditsEgg.burst, onEnd: () -> Unit = { CreditsEgg.clear() }) {
    burst ?: return
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val tile = with(density) { 34.dp.toPx() }
        val origin = if (burst.origin == Offset.Zero) Offset(w / 2, h - tile * 2) else burst.origin
        val sim = remember(burst.id, w, h) { LetterBurst(burst.id * 7919, origin.x, origin.y, w, h, tile) }
        var time by remember(burst.id) { mutableFloatStateOf(0f) }
        LaunchedEffect(burst.id) {
            var last = withFrameNanos { it }
            val start = last
            while (time < CreditsEgg.TOTAL_S) {
                withFrameNanos { now ->
                    sim.advance(((now - last) / 1e9f).coerceAtMost(0.05f))
                    last = now
                    time = (now - start) / 1e9f
                }
            }
            onEnd()
        }
        val fade = (1f - (time - 3.7f) / 0.9f).coerceIn(0f, 1f)
        val palette = listOf(Night.correct, Night.present, Night.flagBlue)
        val style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1438))
        Canvas(Modifier.fillMaxSize()) {
            // Escurece o fundo um pouco para a mensagem aparecer.
            drawRect(Color.Black.copy(alpha = 0.35f * fade * (time / 0.3f).coerceIn(0f, 1f)))
            for (i in sim.x.indices) {
                val s = tile * sim.scale[i]
                rotate(sim.angle[i], pivot = Offset(sim.x[i], sim.y[i])) {
                    drawRoundRect(
                        palette[sim.colors[i]].copy(alpha = fade), Offset(sim.x[i] - s / 2, sim.y[i] - s / 2), Size(s, s), CornerRadius(s * 0.28f),
                    )
                    val m = textMeasurer.measure(sim.letters[i].toString(), style.copy(fontSize = 18.sp * sim.scale[i]))
                    drawText(m, topLeft = Offset(sim.x[i] - m.size.width / 2f, sim.y[i] - m.size.height / 2f), alpha = fade)
                }
            }
        }
        // Mensagem no centro: entra crescendo com um pulinho e fica alguns segundos.
        val appear = ((time - 0.35f) / 0.45f).coerceIn(0f, 1f)
        if (appear > 0f) {
            val pop = 1f + sin(appear * PI.toFloat()) * 0.12f
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = appear * fade; scaleX = appear * pop; scaleY = appear * pop }
                    .clip(RoundedCornerShape(32.dp)).background(Night.surface.copy(alpha = 0.94f))
                    .border(1.5.dp, Night.correct.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Obrigado por jogar! 💚", color = Night.text, fontSize = 23.sp, maxLines = 1, fontWeight = FontWeight.ExtraBold, fontFamily = Outfit, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                FiveSquares(square = 12.dp, gap = 4.dp)
            }
        }
    }
}

/** A marca do app: cinco quadradinhos arredondados (verde, verde, amarelo, verde, verde). */
@Composable
fun FiveSquares(square: Dp = 22.dp, gap: Dp = 5.dp, animated: Boolean = false) {
    val colors = Night.brand
    val phase = if (animated) {
        val t = rememberInfiniteTransition(label = "marca")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "f").value
    } else 0f
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        colors.forEachIndexed { i, c ->
            val lift = if (animated) sin((phase - i * 0.12f) * 2f * PI.toFloat()).coerceAtLeast(0f) else 0f
            Box(
                Modifier
                    .graphicsLayer { translationY = -lift * square.toPx() * 0.35f }
                    .size(square)
                    .clip(RoundedCornerShape(square * 0.3f))
                    .background(c)
            )
        }
    }
}

/** Uma célula da bandeira em quadradinhos (7 colunas × 5 linhas). */
private val FLAG_DIAMOND = setOf(0 to 3, 1 to 2, 1 to 3, 1 to 4, 2 to 1, 2 to 2, 2 to 4, 2 to 5, 3 to 2, 3 to 3, 3 to 4, 4 to 3)

/** Células da bandeira que viram os 5 quadradinhos da marca (verde, amarelo, azul, amarelo, verde). */
private val FLAG_KEEPERS = listOf(2 to 0, 2 to 2, 2 to 3, 2 to 4, 2 to 6)

private fun flagColor(r: Int, c: Int): Color = when {
    r == 2 && c == 3 -> Night.flagBlue
    (r to c) in FLAG_DIAMOND -> Night.present
    else -> Night.correct
}

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

/**
 * A bandeira do Brasil feita de quadradinhos tremula e depois se recolhe
 * nos cinco quadradinhos da marca. [time] em segundos desde o início.
 */
@Composable
fun FlagToMark(time: Float, modifier: Modifier = Modifier, square: Dp = 30.dp, gap: Dp = 7.dp) {
    androidx.compose.foundation.Canvas(modifier) {
        val s = square.toPx(); val g = gap.toPx()
        val cx = size.width / 2; val cy = size.height / 2
        val fs = s * 0.82f; val fg = fs * 0.2f
        val flagW = 7 * fs + 6 * fg; val flagH = 5 * fs + 4 * fg
        val rowW = 5 * s + 4 * g
        val hold = 1.0f; val morphLen = 1.0f
        val m = ease((time - hold) / morphLen)              // 0 = bandeira, 1 = fileira
        val waveAmp = 1f - m
        for (r in 0 until 5) for (c in 0 until 7) {
            val keeper = FLAG_KEEPERS.indexOf(r to c)
            val wave = kotlin.math.sin(time * 5f - c * 0.7f) * fs * 0.2f * waveAmp
            val fx = cx - flagW / 2 + c * (fs + fg)
            val fy = cy - flagH / 2 + r * (fs + fg) + wave
            val color = flagColor(r, c)
            if (keeper >= 0) {
                val tx = cx - rowW / 2 + keeper * (s + g)
                // Depois de formar a fileira, os quadradinhos saltitam como na marca animada.
                val hop = if (m >= 1f) kotlin.math.sin(((time - hold - morphLen) / 1.6f - keeper * 0.12f) * 2f * PI.toFloat()).coerceAtLeast(0f) * s * 0.35f else 0f
                val ty = cy - s / 2 - hop
                val size0 = fs + (s - fs) * m
                val x = fx + (tx - fx) * m; val y = fy + (ty - fy) * m
                drawRoundRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(size0, size0), androidx.compose.ui.geometry.CornerRadius(size0 * 0.3f))
            } else if (m < 1f) {
                val k = 1f - m
                val sz = fs * k
                val x = fx + (cx - fx) * m * 0.35f + (fs - sz) / 2
                val y = fy + (cy - fy) * m * 0.35f + (fs - sz) / 2
                drawRoundRect(color.copy(alpha = k), Offset(x, y), androidx.compose.ui.geometry.Size(sz, sz), androidx.compose.ui.geometry.CornerRadius(sz * 0.3f))
            }
        }
    }
}

/**
 * Abertura: a bandeira vira a marca, depois aparecem o nome e os créditos (sem frase).
 * Some sozinha depois de ~3,6 s ou ao tocar fora do link.
 */
@Composable
fun SplashCredits(onDone: () -> Unit) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < 3.6f) {
            withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
        }
        onDone()
    }
    val textAlpha = ((time - 1.7f) / 0.5f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Night.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlagToMark(time, Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Palavreiro", color = Night.text, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Outfit,
                modifier = Modifier.graphicsLayer { alpha = textAlpha; translationY = (1f - textAlpha) * 30f },
            )
        }
        CreatorCredit(
            easterEgg = false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .graphicsLayer { alpha = textAlpha },
        )
    }
}
