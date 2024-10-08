package com.example.in_memory_ss_trial

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private var isCaptureActive = true

    private val POST_NOTIFICATIONS_REQUEST_CODE = 101

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("ServiceConnection", "Service connected")
            val binder = binder as ScreenCaptureService.ScreenCaptureBinder
            screenCaptureService = binder.getService()
            isServiceBound = true
            observeCapturedBitmaps()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("ServiceConnection", "Service disconnected")
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        if (!isAccessibilityServiceRunning()) {
            isAccessibilityServiceEnabled()
        }

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        startForegroundService(Intent(this, FloatingButtonService::class.java))

        // Request screen capture permission immediately
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), POST_NOTIFICATIONS_REQUEST_CODE
                )
            } else {
                startMyForegroundService()
            }
        } else {
            startMyForegroundService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == POST_NOTIFICATIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMyForegroundService()
            } else {
                Toast.makeText(this, "Notification permission is required to show notifications", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startMyForegroundService() {
        val serviceIntent = Intent(this, FloatingButtonService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        Toast.makeText(this, "Enable Accessibility Service", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return false
    }

    private fun isAccessibilityServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val runningServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return runningServices.any { it.id.contains("AutoClickService") }
    }

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

            // Send broadcast to FloatingButtonService
            sendBroadcast(Intent(FloatingButtonService.ACTION_SCREEN_CAPTURE_READY))

            // Finish the activity to return to the previous app
            finish()
        }
    }

    /*@Deprecated("Deprecated in Java")
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
    }*/

    private fun observeCapturedBitmaps() {
        screenCaptureService?.let { service ->
            lifecycleScope.launch {
                service.capturedBitmaps.collect { bitmap ->
                    if (isCaptureActive) {
                        val image = InputImage.fromBitmap(bitmap, 0)

                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                recognizedText = visionText.text
                                processRecognizedText(recognizedText)
                                isCaptureActive = false
                            }
                            .addOnFailureListener { e ->
                                Log.e("TextRecognition", "Error processing image", e)
                                isCaptureActive = false
                            }
                    }
                }
            }
        }
    }

    private fun processRecognizedText(recognizedText: String) {
        val cleanText = recognizedText.replace(Regex("\\b\\d{2}:\\d{2}\\b"), "")
        if (cleanText.contains(":") || cleanText.contains("?")) {
            val parts = cleanText.split(Regex("[?:]"), 2)
            if (parts.size == 2) {
                val question = parts[0].trim() + "?"
                val answers = parts[1].trim().split("\n").mapIndexed { index, answer ->
                    "${index + 1}) ${answer.trim()}"
                }

                val formattedText = (
                        "Question: $question\n" +
                                "Answers:\n" + answers.joinToString("\n") +
                                "\nChoose the correct answer!"
                        ).replace("\n", " ")

                callGroqApi(formattedText)
            }
        }
    }

    private fun callGroqApi(formattedText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val apiKey = "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"
            val groqApiClient = GroqApiClient(apiKey)
            groqApiClient.sendPrompt(formattedText) { result ->
                result?.let {
                    processGroqResponse(it)
                } ?: Log.e("Groq API", "Failed to get a response from Groq API")
            }
        }
    }

    private fun processGroqResponse(response: String) {
        val match = Regex("[1-4]").find(response.trim())
        val answerNumber = match?.value?.toIntOrNull()

        if (answerNumber != null && answerNumber in 1..4 ) {
            val (x, y) = calculateClickPosition(answerNumber)

            if (isAccessibilityServiceRunning()) {
                AutoClickService.getInstance()?.performClick(x, y)
            } else {
                Log.e("AutoClickService", "Service is not running")
                isAccessibilityServiceEnabled()
            }
        } else {
            Log.e("Groq API", "Invalid response: $response")
        }
    }

    private fun calculateClickPosition(answerNumber: Int): Pair<Float, Float> {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val startYPercentage = 0.6f
        val startY = screenHeight * startYPercentage

        val yOffsetPercentage = 0.1f
        val yOffset = screenHeight * yOffsetPercentage

        val y = startY + (answerNumber - 1) * yOffset

        val x = displayMetrics.widthPixels.toFloat() / 2

        Log.d("AutoClickerService", "Calculated click position: x=$x, y=$y")
        return Pair(x, y)
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
        const val ACTION_CAPTURE_SCREEN = "ACTION_CAPTURE_SCREEN"
    }
}