package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val x: Float, val delay: Float, val speed: Float, val drift: Float,
    val size: Float, val spin: Float, val color: Int, val round: Boolean,
)

/** Confete de vitória: cai por ~3 segundos e some. [key] muda a cada vitória. */
@Composable
fun Confetti(key: Int, onEnd: () -> Unit) {
    val palette = listOf(Night.correct, Night.present, Night.accent, Night.text)
    val pieces = remember(key) {
        val r = Random(key)
        List(140) {
            Piece(
                x = r.nextFloat(), delay = r.nextFloat() * 0.6f, speed = 0.35f + r.nextFloat() * 0.45f,
                drift = r.nextFloat() * 2f - 1f, size = 6f + r.nextFloat() * 8f, spin = r.nextFloat() * 720f - 360f,
                color = r.nextInt(palette.size), round = r.nextBoolean(),
            )
        }
    }
    val time = remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (time.floatValue < 3.2f) {
            withFrameNanos { now -> time.floatValue = (now - start) / 1_000_000_000f }
        }
        onEnd()
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.floatValue
        for (p in pieces) {
            val life = t - p.delay
            if (life < 0f) continue
            val y = -20f + life * p.speed * size.height
            if (y > size.height + 20f) continue
            val x = p.x * size.width + sin(life * 3f + p.drift * 5f) * 30f * p.drift
            val fade = (1f - ((t - 2.4f) / 0.8f)).coerceIn(0f, 1f)
            rotate(p.spin * life, pivot = Offset(x, y)) {
                val c = palette[p.color].copy(alpha = fade)
                if (p.round) {
                    drawCircle(c, radius = p.size / 2, center = Offset(x, y))
                } else {
                    drawRoundRect(
                        c, topLeft = Offset(x - p.size / 2, y - p.size / 4),
                        size = Size(p.size, p.size / 2), cornerRadius = CornerRadius(p.size / 4),
                    )
                }
            }
        }
    }
}
