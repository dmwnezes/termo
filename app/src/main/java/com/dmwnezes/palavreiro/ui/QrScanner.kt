package com.dmwnezes.palavreiro.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** Lê um QR code do quadro da câmera (plano Y do YUV); null se não achou. */
internal object QrDecode {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    fun luminance(data: ByteArray, width: Int, height: Int): String? = runCatching {
        val src = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
    }.getOrNull().also { reader.reset() }

    fun frame(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buf = plane.buffer
        val w = image.width; val h = image.height; val stride = plane.rowStride
        val data = ByteArray(w * h)
        if (stride == w) buf.get(data, 0, minOf(data.size, buf.remaining()))
        else for (r in 0 until h) { buf.position(r * stride); buf.get(data, r * w, w) }
        return luminance(data, w, h)
    }
}

private val isRobolectric: Boolean get() = Build.FINGERPRINT == "robolectric"

/**
 * Tela da câmera para entrar numa partida pelo QR code do amigo.
 * [onResult] recebe o texto do primeiro QR lido que [accept] aprovar (um link de partida válido).
 * [granted] (testes) força a permissão; [camera] = false mostra só o quadro de mira.
 */
@Composable
fun QrScanner(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    accept: (String) -> Boolean = { true },
    granted: Boolean? = null,
    camera: Boolean = !isRobolectric,
) {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(granted ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
    }
    var denied by remember { mutableStateOf(granted == false) }
    var wrong by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> allowed = ok; denied = !ok }
    LaunchedEffect(Unit) { if (!allowed && granted == null) runCatching { launcher.launch(Manifest.permission.CAMERA) }.onFailure { denied = true } }
    val result by rememberUpdatedState(onResult)
    val ok by rememberUpdatedState(accept)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (allowed && camera) {
            CameraPreview { text ->
                if (ok(text)) result(text) else wrong = true
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Ler QR code", onClose)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (denied) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 56.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Sem acesso à câmera", color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("Libere a câmera nas configurações do celular, ou cole o link da partida.", color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        PillButton("Voltar", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = onClose)
                    }
                } else {
                    // Mira: um quadrado arredondado no meio da imagem.
                    Box(Modifier.size(250.dp).clip(RoundedCornerShape(28.dp)).border(4.dp, Night.accent, RoundedCornerShape(28.dp)))
                }
            }
            if (!denied) {
                Text(
                    if (wrong) "Esse QR code não é de uma partida do Palavreiro." else "Aponte para o QR code da sala do seu amigo",
                    color = if (wrong) Night.red else Night.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(14.dp))
                PillButton("Fechar", Night.surfaceHigh, Night.text, Modifier.fillMaxWidth().padding(horizontal = 24.dp), onClick = onClose)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** Prévia da câmera traseira com análise de quadros (CameraX); chama [onCode] a cada QR lido. */
@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val code by rememberUpdatedState(onCode)
    var done by remember { mutableStateOf(false) }
    val main = remember { ContextCompat.getMainExecutor(context) }
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get(); provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { img ->
                    val text = runCatching { QrDecode.frame(img) }.getOrNull()
                    img.close()
                    if (text != null && !done) main.execute { if (!done) code(text) }
                }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, main)
        onDispose {
            done = true
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}
