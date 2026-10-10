package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Matriz do QR (sem margem; a margem branca é o padding do quadro). */
fun qrMatrix(text: String): BitMatrix = QRCodeWriter().encode(
    text, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
)

/** QR code em fundo branco (câmeras leem melhor escuro sobre claro, mesmo no tema escuro). */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val m = remember(text) { qrMatrix(text) }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / m.width
            val ink = Color(0xFF14102C)
            for (y in 0 until m.height) for (x in 0 until m.width) {
                // +0.5 px evita frestas entre módulos vizinhos.
                if (m[x, y]) drawRect(ink, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
