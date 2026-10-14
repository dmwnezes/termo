package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Matiz: amostra de cor e as três barras R, G e B. */
@Composable
internal fun MatizIcon() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(width = 18.dp, height = 40.dp).clip(RoundedCornerShape(7.dp)).background(Brush.verticalGradient(listOf(Color(0xFFF07FB0), Night.accent, Night.blue))))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Night.red to 0.7f, Night.correct to 0.35f, Night.blue to 0.85f).forEach { (c, f) ->
                Box(Modifier.size(width = 30.dp, height = 8.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.size(width = 30.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Night.surfaceHigh))
                    Box(Modifier.size(width = (30 * f).dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    Box(Modifier.offset(x = (30 * f - 4).dp).size(8.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}

/** Pódio: os três degraus (2º, 1º, 3º). */
@Composable
internal fun PodioIcon() {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Triple("2", 26, Night.muted), Triple("1", 38, Night.present), Triple("3", 18, Color(0xFFD9925B))).forEach { (n, h, c) ->
            Box(Modifier.size(width = 16.dp, height = h.dp).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(c), contentAlignment = Alignment.TopCenter) {
                Text(n, color = Color(0xFF1A1438), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Ampulheta: o vidro com areia em cima e embaixo. */
@Composable
internal fun AmpulhetaIcon() {
    Canvas(Modifier.size(width = 34.dp, height = 46.dp)) {
        val w = size.width; val h = size.height
        val cap = 3.dp.toPx()
        val m = w * 0.14f
        val glass = Path().apply {
            moveTo(m, cap); lineTo(w - m, cap); lineTo(w / 2 + 2f, h / 2); lineTo(w - m, h - cap); lineTo(m, h - cap); lineTo(w / 2 - 2f, h / 2); close()
        }
        drawPath(glass, Night.surfaceHigh)
        // Areia: um pouco em cima, um monte embaixo e o fiozinho caindo.
        drawPath(Path().apply { moveTo(w * 0.32f, h * 0.30f); lineTo(w * 0.68f, h * 0.30f); lineTo(w / 2, h / 2); close() }, Night.present)
        drawPath(Path().apply { moveTo(m + 2f, h - cap); lineTo(w - m - 2f, h - cap); lineTo(w / 2, h * 0.70f); close() }, Night.present)
        drawLine(Night.present, Offset(w / 2, h / 2), Offset(w / 2, h * 0.72f), strokeWidth = 1.5.dp.toPx())
        drawPath(glass, Night.text.copy(alpha = 0.75f), style = Stroke(1.5.dp.toPx()))
        drawLine(Night.accent, Offset(0f, cap / 2), Offset(w, cap / 2), strokeWidth = cap, cap = StrokeCap.Round)
        drawLine(Night.accent, Offset(0f, h - cap / 2), Offset(w, h - cap / 2), strokeWidth = cap, cap = StrokeCap.Round)
    }
}
