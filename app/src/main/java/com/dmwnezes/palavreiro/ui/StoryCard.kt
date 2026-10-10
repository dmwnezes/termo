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

/**
 * Cartão dos Stories da partida com amigo (todos os modos).
 * [me]/[opp] = nomes; placar = série (Termo, Dueto, Quarteto, Bomba) ou pontos (Anagrama).
 * [winner]: "me", "opp" ou null (destaca o nome de quem venceu).
 */
data class MatchStory(
    val mode: Char,
    val me: String,
    val opp: String,
    val myScore: Int,
    val oppScore: Int,
    val result: String,
    val detail: String,
    val winner: String? = null,
) {
    val modeLabel: String get() = "${com.dmwnezes.palavreiro.game.Multi.modeName(mode)} ${com.dmwnezes.palavreiro.game.Multi.modeEmoji(mode)}"
    val headline: String get() = "$me  $myScore × $oppScore  $opp"

    companion object {
        /** Meu nome no cartão ("Você" se eu não digitei nenhum). */
        fun myName(name: String?): String = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Você"

        private fun time(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

        private fun roundResult(me: String, opp: String, series: com.dmwnezes.palavreiro.game.Multi.Series, outcome: String?): Pair<String, String?> =
            when (series.winner) {
                "me" -> "🏆 $me levou a série!" to "me"
                "opp" -> "🏆 $opp levou a série!" to "opp"
                else -> when (outcome) {
                    "me" -> "$me venceu a rodada" to "me"
                    "opp" -> "$opp venceu a rodada" to "opp"
                    else -> "Empate" to null
                }
            }

        /** Termo, Dueto e Quarteto: placar da série e as duas linhas de resultado. */
        fun termo(
            mode: Char, myName: String?, opp: String, series: com.dmwnezes.palavreiro.game.Multi.Series, outcome: String?,
            mine: com.dmwnezes.palavreiro.game.Multi.Msg.End?, theirs: com.dmwnezes.palavreiro.game.Multi.Msg.End?,
        ): MatchStory {
            val me = myName(myName)
            fun line(n: String, e: com.dmwnezes.palavreiro.game.Multi.Msg.End?) = when {
                e == null -> "$n: jogando"
                e.won -> "$n: acertou em ${e.tries} · ${time(e.ms)}"
                else -> "$n: errou"
            }
            val (res, w) = roundResult(me, opp, series, outcome)
            return MatchStory(mode, me, opp, series.me, series.opp, res, line(me, mine) + "  |  " + line(opp, theirs), w)
        }

        /** Bomba-Relógio: placar da série e quantas palavras saíram antes de explodir. */
        fun bomb(myName: String?, opp: String, series: com.dmwnezes.palavreiro.game.Multi.Series, outcome: String?, count: Int): MatchStory {
            val me = myName(myName)
            val (res, w) = roundResult(me, opp, series, outcome)
            val words = if (count == 1) "1 palavra" else "$count palavras"
            return MatchStory(com.dmwnezes.palavreiro.game.Multi.BOMB, me, opp, series.me, series.opp, res, "$words antes de explodir 💥", w)
        }

        /** Anagrama: pontos da partida. */
        fun anagram(myName: String?, opp: String, myPts: Int, oppPts: Int): MatchStory {
            val me = myName(myName)
            val (res, w) = when {
                myPts > oppPts -> "🏆 $me venceu!" to "me"
                oppPts > myPts -> "🏆 $opp venceu!" to "opp"
                else -> "Empate" to null
            }
            return MatchStory(
                com.dmwnezes.palavreiro.game.Multi.ANAGRAM, me, opp, myPts, oppPts, res,
                "${com.dmwnezes.palavreiro.game.Anagram.ROUNDS} rodadas · $myPts × $oppPts", w,
            )
        }
    }
}

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

    private val Gold = Color(0xFFF5C84C)

    /** Fundo, brilhos e a marca (cinco quadradinhos + "Palavreiro"), iguais nos dois cartões. */
    private fun base(c: Canvas, bold: Typeface, markTop: Float, nameY: Float) {
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Night.bgTop.toArgb(), Night.bgBottom.toArgb(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.color = Night.accent.copy(alpha = 0.10f).toArgb(); c.drawCircle(W * 0.85f, H * 0.12f, 360f, glow)
        glow.color = Night.correct.copy(alpha = 0.07f).toArgb(); c.drawCircle(W * 0.1f, H * 0.85f, 420f, glow)
        val sq = 46f; val gap = 12f
        var x = (W - (5 * sq + 4 * gap)) / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        Night.brand.forEach { col -> p.color = col.toArgb(); c.drawRoundRect(RectF(x, markTop, x + sq, markTop + sq), 13f, 13f, p); x += sq + gap }
        text(c, "Palavreiro", W / 2f, nameY, 92f, bold, Night.text)
    }

    /** Desenha o cartão 1080×1920 da partida com amigo. */
    fun renderMatch(context: Context, s: MatchStory): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bold = runCatching { ResourcesCompat.getFont(context, R.font.outfit_extrabold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val semi = runCatching { ResourcesCompat.getFont(context, R.font.outfit_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val regular = runCatching { ResourcesCompat.getFont(context, R.font.outfit_regular) }.getOrNull() ?: Typeface.DEFAULT
        base(c, bold, 210f, 370f)

        // Pílula do modo.
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = semi; textSize = 44f; textAlign = Paint.Align.CENTER }
        val pw = pill.measureText(s.modeLabel) + 96f
        val bgPill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Night.surfaceHigh.toArgb() }
        c.drawRoundRect(RectF(W / 2f - pw / 2, 445f, W / 2f + pw / 2, 545f), 50f, 50f, bgPill)
        pill.color = Night.text.toArgb()
        c.drawText(s.modeLabel, W / 2f, 512f, pill)

        // Emoji grande do modo num círculo suave.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        val cy = 820f
        ring.color = (if (s.winner == "me") Gold else Night.accent).copy(alpha = 0.10f).toArgb(); c.drawCircle(W / 2f, cy, 190f, ring)
        ring.color = (if (s.winner == "me") Gold else Night.accent).copy(alpha = 0.12f).toArgb(); c.drawCircle(W / 2f, cy, 130f, ring)
        val big = if (s.winner != null && s.result.startsWith("🏆")) "🏆" else com.dmwnezes.palavreiro.game.Multi.modeEmoji(s.mode)
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 150f; textAlign = Paint.Align.CENTER }
        c.drawText(big, W / 2f, cy - (ep.ascent() + ep.descent()) / 2f, ep)

        // Placar: "NOME  2 × 1  NOME", o placar bem grande no meio.
        val scoreY = 1210f
        val score = "${s.myScore} × ${s.oppScore}"
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 190f; textAlign = Paint.Align.CENTER; color = Night.text.toArgb() }
        val scoreW = sp.measureText(score)
        c.drawText(score, W / 2f, scoreY, sp)
        val side = (W - 2 * 60f - scoreW - 2 * 44f) / 2f
        val nameY = scoreY - 66f
        fun name(n: String, winner: Boolean, left: Boolean) {
            val np = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold; textSize = 72f; color = (if (winner) Gold else Night.text).toArgb()
                textAlign = if (left) Paint.Align.RIGHT else Paint.Align.LEFT
            }
            while (np.measureText(n) > side && np.textSize > 30f) np.textSize -= 2f
            var t = n
            while (np.measureText(t) > side && t.length > 2) t = t.dropLast(2) + "…"
            val x = if (left) W / 2f - scoreW / 2f - 44f else W / 2f + scoreW / 2f + 44f
            c.drawText(t, x, nameY - (np.ascent() + np.descent()) / 2f, np)
        }
        name(s.me, s.winner == "me", left = true)
        name(s.opp, s.winner == "opp", left = false)

        // Resultado e detalhe.
        text(c, s.result, W / 2f, 1360f, 64f, bold, if (s.result.startsWith("🏆")) Gold else Night.text)
        text(c, s.detail, W / 2f, 1450f, 40f, regular, Night.muted)

        // Rodapé.
        text(c, "Jogue comigo: dmwnezes.github.io/termo", W / 2f, H - 230f, 42f, semi, Night.accent)
        text(c, "criado por: @dmwnezes", W / 2f, H - 160f, 38f, semi, Night.text.copy(alpha = 0.85f))
        return bmp
    }

    /** Gera o cartão da partida com amigo e abre o compartilhamento como imagem. */
    fun shareMatch(context: Context, s: MatchStory) = shareBitmap(context, renderMatch(context, s))

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, tf: Typeface, color: Color, spacing: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size; this.color = color.toArgb(); textAlign = Paint.Align.CENTER; letterSpacing = spacing
        }
        // Diminui o texto se não couber na largura.
        while (p.measureText(s) > W - 120f && p.textSize > 20f) p.textSize -= 2f
        c.drawText(s, x, y, p)
    }

    /** Gera a imagem e abre o compartilhamento (Instagram, WhatsApp…). */
    fun share(context: Context, d: StoryData) = shareBitmap(context, render(context, d))

    private fun shareBitmap(context: Context, bmp: Bitmap) {
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
