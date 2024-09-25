package com.example.in_memory_ss_trial

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var recognizedText by mutableStateOf("")
    private var isServiceBound = false
    private var screenCaptureService: ScreenCaptureService? = null
    private var lastApiCallTime: Long = 0
    private var autoClickService: AutoClickService? = null



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
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE), 123)

        //isAccessibilityServiceEnabled()
        //isAccessibilityServiceRunning()

        if (!isAccessibilityServiceRunning()) {
            isAccessibilityServiceEnabled()
        }

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
                    //Spacer(modifier = Modifier.height(16.dp))
                    //Text("Recognized Text:")
                    //Text(recognizedText)
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        Toast.makeText(this, "Please enable Accessibility Service for Auto-clicker", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        val accessibilityServiceName = packageName + "/" + AutoClickService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(accessibilityServiceName) == true
    }

    private fun isAccessibilityServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val runningServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return runningServices.any { it.id.contains("AutoClickService") }
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
                            Log.d("Recognized Text:", recognizedText)

                            // Process recognized text for Groq API call
                            processRecognizedText(recognizedText)
                        }
                        .addOnFailureListener { e ->
                            Log.e("TextRecognition", "Error processing image", e)
                        }
                }
            }
        }
    }

    private fun processRecognizedText(recognizedText: String) {
        val recognizedTextTemp = recognizedText.replace(Regex("\\b\\d{2}:\\d{2}\\b"), "")
        // Check if recognizedText contains ':' or '?'
        if (recognizedTextTemp.contains(":") || recognizedTextTemp.contains("?")) {
            val extractedText = recognizedTextTemp.replace(Regex("^\\d+\\.\\d{2}\\s*"), "")
            val parts = extractedText.split(Regex("[?:]"), 2)

            if (parts.size == 2) {
                val question = parts[0].trim() + "?"
                val answers = parts[1].trim().split("\n")
                val formattedAnswers = answers.mapIndexed { index, answer ->
                    "${index + 1}) ${answer.trim()}"
                }

                val formattedText = ("Kviz ima jedno pitanje i 4 odgovora. Pitanje: " +
                        "$question ${formattedAnswers.joinToString("\n")}     Odgovori samo brojkom koji se nalazi pored tačnog odgovora!")
                    .replace("\n", " ").replace("mts", "").replace("do kraja", "")
                    .replace("Ne odgovaraj odmah, sačekajte da se odgovori uokvire belom bojom odnosno postanu aktivni.", "")

                Log.d("OCR", "Formatted Text: $formattedText")

                callGroqApi(formattedText)
            }
        }
    }

    private fun callGroqApi(formattedText: String) {
        // Proveri vreme poslednjeg poziva API-ja
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastApiCallTime < 5000) { // manje od 5 sekundi
            Log.d("Groq API", "API poziv onemogućen: prošlo je manje od 5 sekundi.")
            return
        }

        // Ažuriraj vreme poslednjeg poziva API-ja
        lastApiCallTime = currentTime

        CoroutineScope(Dispatchers.IO).launch {
            val apiKey = "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"
            val groqApiClient = GroqApiClient(apiKey)
            groqApiClient.sendPrompt(formattedText) { result ->
                result?.let {
                    Log.d("Groq API", "Response: $it")
                    processGroqResponse(it)
                } ?: Log.e("Groq API", "Neuspelo dobivanje odgovora od Groq API-ja")
            }
        }
    }

    private fun processGroqResponse(response: String) {
        Log.d("Groq API", "Processing response: $response")

        val regex = Regex("[1-4]")
        val match = regex.find(response.trim())
        val answerNumber = match?.value?.toIntOrNull()

        if (answerNumber != null) {
            val x = 540f // Calculate this based on answer number
            val y = 1619f // Adjust Y for answer number

            if (isAccessibilityServiceRunning()) {
                AutoClickService.getInstance()?.performClick(x, y)
            } else {
                Log.e("AutoClickService", "Service is not running")
                // Prompt user to enable the accessibility service
                isAccessibilityServiceEnabled()
            }
        } else {
            Log.e("Groq API", "Invalid response: $response")
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
