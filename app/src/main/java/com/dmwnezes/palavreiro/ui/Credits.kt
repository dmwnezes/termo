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
    val colors = listOf(Night.correct, Night.correct, Night.present, Night.correct, Night.correct)
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

/**
 * Abertura: nome do app, os cinco quadradinhos e os créditos (sem frase).
 * Some sozinha depois de ~2,8 s ou ao tocar fora do link.
 */
@Composable
fun SplashCredits(onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(2100)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Night.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 40f },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FiveSquares(square = 30.dp, gap = 7.dp, animated = true)
            Spacer(Modifier.height(26.dp))
            Text("Palavreiro", color = Night.text, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Outfit)
        }
        CreatorCredit(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .graphicsLayer { alpha = appear.value },
        )
    }
}
