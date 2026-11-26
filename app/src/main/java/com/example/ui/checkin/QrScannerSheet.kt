package com.example.ui.checkin

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.FlameOrange
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun QrScannerSheet(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Scan Check-in Code",
    subtitle: String = "Align the front desk QR display in the frame",
    helperText: String = "Hold still facing the check-in screen.\nThe code refreshes automatically every minute."
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasScanned by remember { mutableStateOf(false) }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderInstance?.unbindAll()
            } catch (_: Throwable) {}
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .background(MaterialTheme.colorScheme.surface)
            .testTag("qr_scanner_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_scanner_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Scanner")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            cameraProviderInstance = provider

                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val reader = MultiFormatReader().apply {
                                setHints(
                                    mapOf(
                                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
                                    )
                                )
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (hasScanned) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                try {
                                    val buffer = imageProxy.planes[0].buffer
                                    val data = ByteArray(buffer.remaining())
                                    buffer.get(data)
                                    val width = imageProxy.width
                                    val height = imageProxy.height

                                    val source = PlanarYUVLuminanceSource(
                                        data,
                                        width,
                                        height,
                                        0,
                                        0,
                                        width,
                                        height,
                                        false
                                    )
                                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                    val result = reader.decodeWithState(binaryBitmap)

                                    if (result != null && !hasScanned) {
                                        hasScanned = true
                                        val text = result.text
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            try {
                                                provider.unbindAll()
                                            } catch (_: Throwable) {}
                                            onCodeScanned(text)
                                        }
                                    }
                                } catch (_: NotFoundException) {
                                    // QR not in frame, keep analyzing
                                } catch (_: Throwable) {
                                    // Ignore parse frame errors
                                } finally {
                                    reader.reset()
                                    imageProxy.close()
                                }
                            }

                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                // Viewfinder Target Overlay
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .border(3.dp, FlameOrange, RoundedCornerShape(16.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
