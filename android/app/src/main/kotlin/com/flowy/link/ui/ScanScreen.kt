package com.flowy.link.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private object ScanCopy {
    const val hint = "把电脑上的配对码放进画面"
    const val paste = "改用粘贴"
    const val denied = "没有相机权限。可以把配对链接粘贴进去。"
}

@Composable
fun ScanScreen(onCode: (String) -> Unit, onClose: (String?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (!allowed) onClose(ScanCopy.denied)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    if (!granted) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(ScanCopy.hint, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { onClose(null) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(ScanCopy.paste) }
        }
        return
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { viewContext ->
            val preview = PreviewView(viewContext)
            val mainExecutor = ContextCompat.getMainExecutor(viewContext)
            val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
            val camera = ProcessCameraProvider.getInstance(viewContext)
            camera.addListener({
                val provider = camera.get()
                val previewUse = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val analysis = ImageAnalysis.Builder().build()
                val scanner = BarcodeScanning.getClient()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val media = imageProxy.image
                    if (media == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { codes ->
                            val value = codes.firstNotNullOfOrNull { code ->
                                code.rawValue?.takeIf { it.startsWith("flowy://pair?") }
                            }
                            if (value != null && delivered.compareAndSet(false, true)) mainExecutor.execute { onCode(value) }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis)
            }, ContextCompat.getMainExecutor(viewContext))
            preview
        }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
            Text(ScanCopy.hint, color = androidx.compose.ui.graphics.Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { onClose(null) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(ScanCopy.paste, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
