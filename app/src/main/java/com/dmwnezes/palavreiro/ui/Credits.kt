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
import com.dmwnezes.palavreiro.R
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

/** Foto redonda pequena + "criado por: @dmwnezes", tocável, que leva ao Instagram (igual ao Sintonia). */
@Composable
fun CreatorCredit(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { openCreatorInstagram(context) }
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
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .graphicsLayer { alpha = textAlpha },
        )
    }
}
