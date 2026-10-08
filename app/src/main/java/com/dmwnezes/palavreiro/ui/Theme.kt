package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.game.Mark

/** Paleta "Noite suave": azul-escuro e roxo aveludados, com verde e amarelo do Termo. */
object Night {
    val bgTop = Color(0xFF2A2058)
    val bgBottom = Color(0xFF120E2B)
    val surface = Color(0xFF231C48)
    val surfaceHigh = Color(0xFF2F275C)
    val outline = Color(0xFF4A4180)
    val absent = Color(0xFF3A3363)
    val key = Color(0xFF4C437F)
    val text = Color(0xFFF4F1FF)
    val muted = Color(0xFFA9A2D0)
    val accent = Color(0xFF9B8CFF)
    val correct = Color(0xFF5FB873)
    val present = Color(0xFFE6C14F)

    val background: Brush get() = Brush.verticalGradient(listOf(bgTop, bgBottom))

    fun mark(m: Mark?): Color = when (m) {
        Mark.CORRECT -> correct
        Mark.PRESENT -> present
        Mark.ABSENT -> absent
        null -> Color.Transparent
    }

    /** Texto sobre o amarelo fica escuro, para ler bem. */
    fun onMark(m: Mark?): Color = if (m == Mark.PRESENT) Color(0xFF2A2140) else text
}

/** Formas orgânicas: cantos bem arredondados em tudo. */
object Shapes {
    val tile = RoundedCornerShape(14.dp)
    val key = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)
    val sheet = RoundedCornerShape(32.dp)
}

/** Fonte geométrica: Outfit. */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

@Composable
fun PalavreiroTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.o() = copy(fontFamily = Outfit)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Night.accent,
            onPrimary = Color(0xFF1A1438),
            secondary = Night.correct,
            background = Night.bgBottom,
            surface = Night.surface,
            surfaceContainerHigh = Night.surfaceHigh,
            onSurface = Night.text,
            onBackground = Night.text,
            onSurfaceVariant = Night.muted,
            outline = Night.outline,
        ),
        typography = Typography(
            displayLarge = base.displayLarge.o(), displayMedium = base.displayMedium.o(), displaySmall = base.displaySmall.o(),
            headlineLarge = base.headlineLarge.o(), headlineMedium = base.headlineMedium.o(), headlineSmall = base.headlineSmall.o(),
            titleLarge = base.titleLarge.o(), titleMedium = base.titleMedium.o(), titleSmall = base.titleSmall.o(),
            bodyLarge = base.bodyLarge.o(), bodyMedium = base.bodyMedium.o(), bodySmall = base.bodySmall.o(),
            labelLarge = base.labelLarge.o(), labelMedium = base.labelMedium.o(), labelSmall = base.labelSmall.o(),
        ),
        content = content,
    )
}
