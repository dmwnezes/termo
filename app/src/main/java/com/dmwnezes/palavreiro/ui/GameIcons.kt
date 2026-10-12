package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.TriviaDuel

/** Cores fixas das categorias da Trivia (Ciência verde, História amarelo, Geografia azul, Esportes laranja, Arte rosa, Entretenimento roxo). */
internal val TriviaOrange = Color(0xFFF0A05A)
internal val TriviaPink = Color(0xFFF08CC0)
internal fun triviaColor(cat: String): Color = when (cat) {
    "Ciência" -> Night.correct; "História" -> Night.present; "Geografia" -> Night.blue
    "Esportes" -> TriviaOrange; "Arte" -> TriviaPink; "Entretenimento" -> Night.purple
    else -> Night.accent
}
internal fun triviaEmoji(cat: String): String = when (cat) {
    "Ciência" -> "🧪"; "História" -> "📜"; "Geografia" -> "🌎"; "Esportes" -> "⚽"; "Arte" -> "🎨"; "Entretenimento" -> "🎬"; else -> "❓"
}

private val InkTile = Color(0xFF1A1438)
private val CheckLight = Color(0xFF8B80C8)
private val CheckDark = Color(0xFF3A3170)

/**
 * Mini ilustração de cada jogo com amigo (cartões da tela de criar e a grade do "Próximo jogo"),
 * no estilo dos ícones da tela inicial: quadradinhos arredondados nas cores do app.
 * Desenhada numa caixa de 48×48 unidades, escalada para [size].
 */
@Composable
internal fun GameIcon(code: Char, size: Dp) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 48f
        when (code) {
            't' -> board(3f * u, 3f * u, 12f * u, 3f * u, 0)
            'd' -> { board(1f * u, 12.5f * u, 6.5f * u, 1.5f * u, 0); board(25f * u, 12.5f * u, 6.5f * u, 1.5f * u, 2) }
            'q' -> {
                val c = 6f * u; val g = 1.5f * u; val s = 3 * c + 2 * g
                listOf(0, 1, 2, 3).forEach { i -> board((2f * u) + (i % 2) * (s + 4f * u), (2f * u) + (i / 2) * (s + 4f * u), c, g, i) }
            }
            Multi.ANAGRAM -> anagram(u, tm)
            Multi.HUNT -> hunt(u, tm)
            Multi.DEFINE -> define(u)
            Multi.FORCA -> gallows(u)
            Multi.ADEDONHA -> adedonha(u, tm)
            Multi.TRIVIA -> wheel(u)
            Multi.TICTAC -> tictac(u)
            Multi.CHECKERS -> checkers(u)
        }
    }
}

private fun DrawScope.tile(x: Float, y: Float, w: Float, h: Float, color: Color, r: Float = w * 0.3f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.letter(tm: TextMeasurer, ch: String, cx: Float, cy: Float, px: Float, color: Color) {
    val style = TextStyle(color = color, fontSize = (px / density / fontScale).sp, fontWeight = FontWeight.ExtraBold, fontFamily = Outfit)
    val l = tm.measure(ch, style)
    drawText(l, topLeft = Offset(cx - l.size.width / 2f, cy - l.size.height / 2f))
}

/** Mini tabuleiro 3×3 do Termo (a última linha toda verde). */
private fun DrawScope.board(x: Float, y: Float, cell: Float, gap: Float, seed: Int) {
    for (r in 0 until 3) for (c in 0 until 3) {
        val v = (r * 3 + c + seed) % 5
        val col = when { r == 2 -> Night.correct; v == 0 -> Night.present; v == 3 -> Night.correct; else -> Night.absent }
        tile(x + c * (cell + gap), y + r * (cell + gap), cell, cell, col)
    }
}

/** Três letras com a do meio fora do lugar e uma seta de troca por cima. */
private fun DrawScope.anagram(u: Float, tm: TextMeasurer) {
    val s = 13f * u; val y = 22f * u
    val xs = listOf(3.5f * u, 17.5f * u, 31.5f * u)
    val letters = listOf("R", "O", "A")
    xs.forEachIndexed { i, x ->
        val dy = if (i == 1) 3f * u else 0f
        rotate(if (i == 1) -10f else 0f, Offset(x + s / 2, y + dy + s / 2)) {
            tile(x, y + dy, s, s, if (i == 1) Night.present else Night.surfaceHigh)
        }
        letter(tm, letters[i], x + s / 2, y + dy + s / 2, 9f * u, if (i == 1) InkTile else Night.text)
    }
    val arc = Path().apply {
        moveTo(10f * u, 18f * u)
        cubicTo(14f * u, 6f * u, 34f * u, 6f * u, 38f * u, 18f * u)
    }
    drawPath(arc, Night.accent, style = Stroke(2.6f * u, cap = StrokeCap.Round))
    val head = Path().apply { moveTo(33.5f * u, 15.5f * u); lineTo(38f * u, 18.5f * u); lineTo(39.5f * u, 13f * u) }
    drawPath(head, Night.accent, style = Stroke(2.6f * u, cap = StrokeCap.Round))
}

/** Grade de letras com a marcação na diagonal. */
private fun DrawScope.hunt(u: Float, tm: TextMeasurer) {
    drawLine(Night.present.copy(alpha = 0.75f), Offset(10f * u, 10f * u), Offset(38f * u, 38f * u), 11f * u, StrokeCap.Round)
    val rows = listOf("CAS", "OLE", "PAJ")
    rows.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch -> letter(tm, ch.toString(), (10f + c * 14f) * u, (10f + r * 14f) * u, 10f * u, Night.text) }
    }
}

/** Duas linhas de definição e a palavra com uma letra de dica. */
private fun DrawScope.define(u: Float) {
    tile(4f * u, 8f * u, 40f * u, 5f * u, Night.muted.copy(alpha = 0.6f), 2.5f * u)
    tile(9f * u, 17f * u, 30f * u, 5f * u, Night.muted.copy(alpha = 0.6f), 2.5f * u)
    val s = 9f * u; val g = 2f * u; val x0 = (48f * u - (4 * s + 3 * g)) / 2
    for (i in 0 until 4) tile(x0 + i * (s + g), 29f * u, s, s, if (i == 1) Night.present else Night.surfaceHigh, 2.6f * u)
}

/** Forca arredondada com o bonequinho e os tracinhos da palavra. */
private fun DrawScope.gallows(u: Float) {
    val w = 3f * u
    val pole = Night.muted
    drawLine(pole, Offset(6f * u, 40f * u), Offset(22f * u, 40f * u), w, StrokeCap.Round)
    drawLine(pole, Offset(12f * u, 40f * u), Offset(12f * u, 6f * u), w, StrokeCap.Round)
    drawLine(pole, Offset(12f * u, 6f * u), Offset(32f * u, 6f * u), w, StrokeCap.Round)
    drawLine(pole, Offset(32f * u, 6f * u), Offset(32f * u, 11f * u), w * 0.8f, StrokeCap.Round)
    val fig = Night.present
    drawCircle(fig, 4.5f * u, Offset(32f * u, 15.5f * u), style = Stroke(w * 0.9f))
    drawLine(fig, Offset(32f * u, 20f * u), Offset(32f * u, 30f * u), w * 0.9f, StrokeCap.Round)
    drawLine(fig, Offset(32f * u, 23f * u), Offset(27f * u, 27f * u), w * 0.9f, StrokeCap.Round)
    drawLine(fig, Offset(32f * u, 23f * u), Offset(37f * u, 27f * u), w * 0.9f, StrokeCap.Round)
    drawLine(fig, Offset(32f * u, 30f * u), Offset(28f * u, 36f * u), w * 0.9f, StrokeCap.Round)
    drawLine(fig, Offset(32f * u, 30f * u), Offset(36f * u, 36f * u), w * 0.9f, StrokeCap.Round)
    for (i in 0 until 3) drawLine(Night.accent, Offset((27f + i * 6.5f) * u, 44f * u), Offset((31f + i * 6.5f) * u, 44f * u), 2.4f * u, StrokeCap.Round)
}

/** A letra sorteada num disco amarelo e a placa de STOP. */
private fun DrawScope.adedonha(u: Float, tm: TextMeasurer) {
    // Raios da letra girando.
    for (i in 0 until 8) {
        rotate(i * 45f, Offset(19f * u, 19f * u)) {
            drawLine(Night.present.copy(alpha = 0.45f), Offset(19f * u, 1.5f * u), Offset(19f * u, 4.5f * u), 2.2f * u, StrokeCap.Round)
        }
    }
    drawCircle(Night.present, 12f * u, Offset(19f * u, 19f * u))
    letter(tm, "A", 19f * u, 19f * u, 15f * u, InkTile)
    // Octógono do STOP.
    val cx = 35f * u; val cy = 35f * u; val r = 11f * u
    val oct = Path().apply {
        for (i in 0 until 8) {
            val a = Math.toRadians(22.5 + i * 45.0)
            val x = cx + r * kotlin.math.cos(a).toFloat(); val y = cy + r * kotlin.math.sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(oct, Night.red)
    drawPath(oct, Night.surface, style = Stroke(1.6f * u))
    tile(29.5f * u, 33.2f * u, 11f * u, 3.6f * u, Night.text, 1.8f * u)
}

/** Roleta das 6 categorias com o ponteiro em cima. */
private fun DrawScope.wheel(u: Float) {
    val c = Offset(24f * u, 26f * u); val r = 19f * u
    TriviaDuel.CATEGORIES.forEachIndexed { i, cat ->
        drawArc(triviaColor(cat), -90f + i * 60f, 60f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
    }
    for (i in 0 until 6) {
        val a = Math.toRadians(-90.0 + i * 60)
        drawLine(Night.surface, c, Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat()), 1.6f * u)
    }
    drawCircle(Night.surface, 6f * u, c)
    drawCircle(Night.text, 2.6f * u, c)
    val p = Path().apply { moveTo(24f * u, 10.5f * u); lineTo(19.5f * u, 2.5f * u); lineTo(28.5f * u, 2.5f * u); close() }
    drawPath(p, Night.text)
}

/** Velha: grade arredondada com X verdes e O amarelo. */
private fun DrawScope.tictac(u: Float) {
    val g = Night.outline
    val w = 2.6f * u
    listOf(17f, 31f).forEach { p ->
        drawLine(g, Offset(p * u, 4f * u), Offset(p * u, 44f * u), w, StrokeCap.Round)
        drawLine(g, Offset(4f * u, p * u), Offset(44f * u, p * u), w, StrokeCap.Round)
    }
    fun x(cx: Float, cy: Float) {
        val d = 4.2f * u
        drawLine(Night.correct, Offset(cx - d, cy - d), Offset(cx + d, cy + d), 3.2f * u, StrokeCap.Round)
        drawLine(Night.correct, Offset(cx + d, cy - d), Offset(cx - d, cy + d), 3.2f * u, StrokeCap.Round)
    }
    x(10f * u, 10f * u); x(24f * u, 24f * u)
    drawCircle(Night.present, 4.6f * u, Offset(38f * u, 10f * u), style = Stroke(3f * u))
    drawCircle(Night.present, 4.6f * u, Offset(10f * u, 38f * u), style = Stroke(3f * u))
}

/** Damas: tabuleiro 3×3 com uma peça branca e uma preta (dama com coroa). */
private fun DrawScope.checkers(u: Float) {
    val s = 13f * u; val x0 = 4.5f * u
    for (r in 0 until 3) for (c in 0 until 3) {
        drawRoundRect(if ((r + c) % 2 == 0) CheckLight else CheckDark, Offset(x0 + c * s, x0 + r * s), Size(s, s), CornerRadius(if (r == 0 && c == 0 || r == 2 && c == 2 || r == 0 && c == 2 || r == 2 && c == 0) 4f * u else 0f))
    }
    fun piece(r: Int, c: Int, white: Boolean, king: Boolean = false) {
        val o = Offset(x0 + (c + 0.5f) * s, x0 + (r + 0.5f) * s)
        drawCircle(if (white) Color(0xFFF4F1FF) else Color(0xFF17122E), 5f * u, o)
        drawCircle(if (white) Color(0xFFC9C2EE) else Color(0xFF6B5FB0), 3.4f * u, o, style = Stroke(0.9f * u))
        if (king) drawCircle(Night.present, 1.8f * u, o)
    }
    piece(0, 1, white = false); piece(1, 0, white = false, king = true); piece(2, 1, white = true); piece(1, 2, white = true)
}
