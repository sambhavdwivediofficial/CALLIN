package com.sambhavdwivedi.callin.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sambhavdwivedi.callin.core.di.AppContainer
import com.sambhavdwivedi.callin.ui.components.PulseBarsLoader
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private enum class ScanOutcome { Scanning, Sending, Sent, AlreadyConnected, Failed }

/**
 * A camera-only QR scanner: it shows a live camera preview and reads
 * frames directly through CameraX + ML Kit. There is no gallery
 * picker anywhere on this screen, so a QR code can only be added by
 * pointing the live camera at it — a screenshot cannot be scanned in.
 *
 * [onBack] is the cancel/back action (top-left arrow). [onAdded] is
 * called once a connection request has actually been sent, so the
 * caller can pop back to Home on success without conflating that
 * with the user simply backing out.
 */
@Composable
fun ScanQrScreen(container: AppContainer, onBack: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) permissionPermanentlyDenied = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var outcome by remember { mutableStateOf(ScanOutcome.Scanning) }
    var statusText by remember { mutableStateOf("Point your camera at a CALLIN QR code") }
    val alreadyHandled = remember { AtomicBoolean(false) }

    fun handleScanned(username: String) {
        if (!alreadyHandled.compareAndSet(false, true)) return
        outcome = ScanOutcome.Sending
        statusText = "Sending connection request to @$username…"
        scope.launch {
            container.connectionRepository.sendRequest(username)
                .onSuccess { status ->
                    if (status == "accepted") {
                        outcome = ScanOutcome.AlreadyConnected
                        statusText = "You're now connected with @$username."
                    } else {
                        outcome = ScanOutcome.Sent
                        statusText = "Request sent to @$username."
                    }
                }
                .onFailure { e ->
                    outcome = ScanOutcome.Failed
                    statusText = e.message ?: "Could not send the connection request."
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission && outcome == ScanOutcome.Scanning) {
            CameraPreview(
                onQrDecoded = { rawValue ->
                    val username = usernameFromCallinQr(rawValue)
                    if (username != null) {
                        handleScanned(username)
                    }
                }
            )

            // Scanning frame overlay.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            )
        } else if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera access is needed to scan a CALLIN QR code.",
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.padding(top = 16.dp))
                Button(
                    onClick = {
                        if (permissionPermanentlyDenied) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2478D4))
                ) {
                    Text(if (permissionPermanentlyDenied) "Open Settings" else "Grant Camera Access")
                }
            }
        } else {
            // Sending / result state — camera stopped, simple status card.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (outcome == ScanOutcome.Sending) {
                    PulseBarsLoader(barColor = Color.White)
                }
                Spacer(Modifier.padding(top = 16.dp))
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                if (outcome == ScanOutcome.Sent || outcome == ScanOutcome.AlreadyConnected) {
                    Spacer(Modifier.padding(top = 20.dp))
                    Button(
                        onClick = onAdded,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2478D4))
                    ) {
                        Text("Done")
                    }
                } else if (outcome == ScanOutcome.Failed) {
                    Spacer(Modifier.padding(top = 20.dp))
                    Button(
                        onClick = {
                            alreadyHandled.set(false)
                            outcome = ScanOutcome.Scanning
                            statusText = "Point your camera at a CALLIN QR code"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2478D4))
                    ) {
                        Text("Try Again")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Scan QR Code",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (hasCameraPermission && outcome == ScanOutcome.Scanning) {
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
            )
        }
    }
}

/**
 * Wraps CameraX's PreviewView plus an ML Kit barcode analyzer inside
 * an AndroidView. Binds to the composable's lifecycle so the camera
 * releases cleanly when this screen leaves composition.
 */
@Composable
private fun CameraPreview(onQrDecoded: (String?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase ->
                    analysisUseCase.setAnalyzer(analysisExecutor) { imageProxy ->
                        processFrame(imageProxy, scanner, onQrDecoded)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) {
                // Nothing sensible to do if binding fails — the
                // permission-denied branch already covers the
                // common failure case (no permission granted).
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

@androidx.camera.core.ExperimentalGetImage
private fun processFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onQrDecoded: (String?) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstOrNull { it.rawValue != null }?.rawValue
            if (value != null) {
                onQrDecoded(value)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
