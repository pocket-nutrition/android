// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.core.normalizeToEan13

/**
 * Live barcode scanning: a CameraX preview feeding an [ImageAnalysis] use case whose analyzer runs
 * ML Kit's on-device barcode decoder. Detection is continuous and automatic — there is no shutter
 * button, which is the whole point: the analyzer callback itself is the detection event.
 *
 * Restricted to EAN-13/UPC-A: this app's backend is path-templated `GET /ingredients/barcode/{ean13}`,
 * and [normalizeToEan13] cannot safely turn other symbologies into a 13-digit key.
 */
@Composable
fun BarcodeScannerScreen(onBarcodeDetected: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    // Not androidx.compose.ui.platform.LocalLifecycleOwner: that accessor is deprecated in favour of
    // lifecycle-runtime-compose, an artifact this app does not otherwise depend on. MainActivity is the
    // app's only Activity and, as a ComponentActivity, is already a LifecycleOwner — findActivity()
    // (ui/CameraPermission.kt) gets there without a new dependency.
    val lifecycleOwner = remember(context) {
        checkNotNull(context.findActivity() as? LifecycleOwner) {
            "BarcodeScannerScreen requires an Activity LifecycleOwner"
        }
    }
    var hasFired by remember { mutableStateOf(false) }
    val onDetected by rememberUpdatedState(onBarcodeDetected)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val scannerOptions = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A)
                            .build()
                        val scanner = BarcodeScanning.getClient(scannerOptions)
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            analyzeFrame(imageProxy, scanner, hasFired, { hasFired = true }, onDetected)
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )

        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text(Fr.SCAN_CLOSE, color = PnColor.SurfacePaper, fontSize = 16.sp)
        }

        Text(
            text = Fr.SCAN_BUTTON_A11Y,
            color = PnColor.SurfacePaper,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .background(PnColor.SurfaceInverse.copy(alpha = 0.8f)),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }
}

/**
 * Runs one frame through the ML Kit decoder. `imageProxy.close()` always runs, in `finally` — a leaked
 * [ImageProxy] stalls the whole analysis pipeline after a few frames, since CameraX will not deliver a
 * new one until the previous is closed. [hasFired] guards against a burst of frames all detecting the
 * same code while the first detection's lookup is still in flight from the caller's perspective.
 */
@OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    hasFired: Boolean,
    markFired: () -> Unit,
    onDetected: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (hasFired || mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val ean13 = barcodes.firstNotNullOfOrNull { it.rawValue?.let(::normalizeToEan13) }
            if (ean13 != null && !hasFired) {
                markFired()
                onDetected(ean13)
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}
