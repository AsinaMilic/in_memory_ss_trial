package com.example.in_memory_ss_trial

import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoClickService : AccessibilityService() {
    companion object {
        private var instance: AutoClickService? = null

        fun getInstance(): AutoClickService? {
            return instance
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AutoClickService", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this task
    }

    override fun onInterrupt() {
        // Not needed for this task
    }

    fun performClick(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        val gesture = gestureBuilder.build()

        dispatchGesture(gesture, null, null)
        Log.d("AutoClickService", "Click performed at: x=$x, y=$y")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
