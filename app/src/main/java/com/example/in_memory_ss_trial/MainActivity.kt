package com.example.in_memory_ss_trial

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var recognizedText by mutableStateOf("")
    private var isServiceBound = false
    private var screenCaptureService: ScreenCaptureService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("ServiceConnection", "Service connected")
            val binder = binder as ScreenCaptureService.ScreenCaptureBinder
            screenCaptureService = binder.getService()
            isServiceBound = true

            // Start observing captured bitmaps
            observeCapturedBitmaps()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("ServiceConnection", "Service disconnected")
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            MaterialTheme {
                var isCapturing by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            if (isCapturing) {
                                stopCapturing()
                            } else {
                                startCapturing()
                            }
                            isCapturing = !isCapturing
                        }
                    ) {
                        Text(if (isCapturing) "Stop Capturing" else "Start Capturing")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Recognized Text:")
                    Text(recognizedText)
                }
            }
        }
    }

    private fun startCapturing() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    private fun stopCapturing() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(intent)
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            startForegroundService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeCapturedBitmaps() {
        screenCaptureService?.let { service ->
            lifecycleScope.launch {
                Log.d("BitmapCollect", "Starting to collect bitmaps")
                service.capturedBitmaps.collect { bitmap ->
                    Log.d("BitmapCollect", "Collected bitmap: $bitmap")

                    // Ensure the bitmap is not null
                    val image = InputImage.fromBitmap(bitmap, 0)

                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            Log.d("TextRecognition", "Recognized text: ${visionText.text}")
                            recognizedText = visionText.text
                        }
                        .addOnFailureListener { e ->
                            Log.e("TextRecognition", "Error processing image", e)
                        }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1
    }
}
