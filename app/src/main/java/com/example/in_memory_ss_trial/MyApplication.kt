package com.example.in_memory_ss_trial

import android.app.Application

class MyApplication : Application() {
    private lateinit var screenCaptureService: ScreenCaptureService

    override fun onCreate() {
        super.onCreate()
        screenCaptureService = ScreenCaptureService()
    }
}