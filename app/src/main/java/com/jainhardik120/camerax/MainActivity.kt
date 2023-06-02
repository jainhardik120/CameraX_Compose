package com.jainhardik120.camerax

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.jainhardik120.camerax.ui.theme.CameraXTheme
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageCapture = ImageCapture.Builder().build()
        val cameraExecutor = Executors.newSingleThreadExecutor()
        setContent {
            CameraXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionsView {
                        Box(modifier = Modifier.fillMaxSize()){
                            SimpleCameraPreview(imageCapture, ImageAnalysis.Builder()
                                .build().also {
                                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer{
                                        luma->
//                                        Log.d("TAG", "onCreate: $luma")
                                    })
                                })
                            Button(onClick = {
                                val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                    if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
                                    }
                                }
                                val outputOptions = ImageCapture.OutputFileOptions
                                    .Builder(contentResolver,
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        contentValues)
                                    .build()
                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(this@MainActivity),
                                    object : ImageCapture.OnImageSavedCallback{
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            val msg = "Photo capture succeeded: ${outputFileResults.savedUri}"
                                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                                            Log.d("TAG", msg)
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            Log.d("TAG", "onError: ${exception.message}")
                                        }
                                    }
                                )
                            }) {
                                Text(text = "Take Photo")
                            }
                        }
                    }
                }
            }
        }
    }

    private class LuminosityAnalyzer(private val listener : LumaListener) : ImageAnalysis.Analyzer{

        private fun ByteBuffer.toByteArray() : ByteArray{
            rewind()
            val data = ByteArray(remaining())
            get(data)
            return data
        }

        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map{
                it.toInt() and 0xFF
            }
            val luma = pixels.average()
            listener(luma)
            image.close()
        }
    }
}

@Composable
fun SimpleCameraPreview(
    imageCapture: ImageCapture,
    imageAnalyzer: ImageAnalysis
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsView(
    content: @Composable () -> Unit
) {
    val permissionState = rememberMultiplePermissionsState(permissions = listOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    ))
    when{
        permissionState.allPermissionsGranted ->{
            content()
        }
        permissionState.shouldShowRationale->{
            Column {
                Text(text = "Permissions were denied, permissions are required to work")
                Button(onClick = {permissionState.launchMultiplePermissionRequest()}) {
                    Text(text = "Request Permissions")
                }
            }
        }
        else ->{
            Column {
                Text(text = "Permissions are required to work")
                Button(onClick = {permissionState.launchMultiplePermissionRequest()}) {
                    Text(text = "Request Permissions")
                }
            }
        }
    }
}