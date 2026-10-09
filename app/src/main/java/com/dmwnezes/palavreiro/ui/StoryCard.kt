package com.dmwnezes.palavreiro.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.dmwnezes.palavreiro.R
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Conteúdo do cartão para Stories.
 * [grids]: um ou mais blocos de quadradinhos (cada bloco = linhas de cores).
 */
data class StoryData(
    val game: String,
    val headline: String,
    val detail: String,
    val grids: List<List<List<Color>>>,
    val stats: List<Pair<String, String>> = emptyList(),
)

object StoryCard {
    private const val W = 1080
    private const val H = 1920

    /** Desenha o cartão 1080×1920 (formato dos Stories). */
    fun render(context: Context, d: StoryData): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bold = runCatching { ResourcesCompat.getFont(context, R.font.outfit_extrabold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val semi = runCatching { ResourcesCompat.getFont(context, R.font.outfit_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val regular = runCatching { ResourcesCompat.getFont(context, R.font.outfit_regular) }.getOrNull() ?: Typeface.DEFAULT

        // Fundo Noite suave com dois brilhos suaves.
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Night.bgTop.toArgb(), Night.bgBottom.toArgb(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.color = Night.accent.copy(alpha = 0.10f).toArgb(); c.drawCircle(W * 0.85f, H * 0.12f, 360f, glow)
        glow.color = Night.correct.copy(alpha = 0.07f).toArgb(); c.drawCircle(W * 0.1f, H * 0.85f, 420f, glow)

        // Marca: cinco quadradinhos + nome.
        val sq = 46f; val gap = 12f
        val markW = 5 * sq + 4 * gap
        var x = (W - markW) / 2f
        val markColors = Night.brand
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        markColors.forEach { col -> p.color = col.toArgb(); c.drawRoundRect(RectF(x, 170f, x + sq, 170f + sq), 13f, 13f, p); x += sq + gap }
        text(c, "Palavreiro", W / 2f, 330f, 92f, bold, Night.text)
        text(c, d.game.uppercase(Locale("pt", "BR")), W / 2f, 420f, 40f, semi, Night.muted, spacing = 0.18f)

        // Destaque.
        text(c, d.headline, W / 2f, 560f, 76f, bold, Night.text)
        text(c, d.detail, W / 2f, 640f, 40f, regular, Night.muted)

        // Grades de quadradinhos, centralizadas.
        val top = 720f
        val bottomLimit = H - 520f
        val blocks = d.grids
        if (blocks.isNotEmpty()) {
            val cols = if (blocks.size == 1) 1 else 2
            val rowsOfBlocks = (blocks.size + cols - 1) / cols
            val maxRows = blocks.maxOf { it.size }.coerceAtLeast(1)
            val maxCols = blocks.maxOf { b -> b.maxOfOrNull { it.size } ?: 1 }.coerceAtLeast(1)
            val blockGap = 50f
            val cellGap = 12f
            val availW = W - 160f - blockGap * (cols - 1)
            val availH = bottomLimit - top - blockGap * (rowsOfBlocks - 1)
            val cell = minOf(
                (availW / cols - cellGap * (maxCols - 1)) / maxCols,
                (availH / rowsOfBlocks - cellGap * (maxRows - 1)) / maxRows,
                120f,
            )
            val blockW = maxCols * cell + (maxCols - 1) * cellGap
            val blockH = maxRows * cell + (maxRows - 1) * cellGap
            val totalW = cols * blockW + (cols - 1) * blockGap
            val totalH = rowsOfBlocks * blockH + (rowsOfBlocks - 1) * blockGap
            val startX = (W - totalW) / 2f
            val startY = top + (bottomLimit - top - totalH) / 2f
            blocks.forEachIndexed { bi, block ->
                val bx = startX + (bi % cols) * (blockW + blockGap)
                val by = startY + (bi / cols) * (blockH + blockGap)
                block.forEachIndexed { r, line ->
                    val lineW = line.size * cell + (line.size - 1) * cellGap
                    val lx = bx + (blockW - lineW) / 2f
                    line.forEachIndexed { k, col ->
                        p.color = col.toArgb()
                        val l = lx + k * (cell + cellGap)
                        val t = by + r * (cell + cellGap)
                        c.drawRoundRect(RectF(l, t, l + cell, t + cell), cell * 0.24f, cell * 0.24f, p)
                    }
                }
            }
        }

        // Números.
        if (d.stats.isNotEmpty()) {
            val y = H - 400f
            val step = W / (d.stats.size + 1f)
            d.stats.forEachIndexed { i, (value, label) ->
                text(c, value, step * (i + 1), y, 72f, bold, Night.text)
                text(c, label, step * (i + 1), y + 54f, 34f, regular, Night.muted)
            }
        }

        // Rodapé com data e créditos.
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        text(c, date, W / 2f, H - 210f, 36f, regular, Night.muted)
        text(c, "criado por @dmwnezes", W / 2f, H - 150f, 38f, semi, Night.text.copy(alpha = 0.85f))
        return bmp
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, tf: Typeface, color: Color, spacing: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size; this.color = color.toArgb(); textAlign = Paint.Align.CENTER; letterSpacing = spacing
        }
        // Diminui o texto se não couber na largura.
        while (p.measureText(s) > W - 120f && p.textSize > 20f) p.textSize -= 2f
        c.drawText(s, x, y, p)
    }

    /** Gera a imagem e abre o compartilhamento (Instagram, WhatsApp…). */
    fun share(context: Context, d: StoryData) {
        val bmp = render(context, d)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "palavreiro-story.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Compartilhar nos Stories").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
