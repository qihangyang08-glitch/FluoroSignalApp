package com.example.fluorosignalapp

import android.app.Application

/**
 * Custom Application class for FluoroSignalApp.
 * This is the first component to be instantiated when the app process is created.
 * We use it to perform one-time initializations for globally used components.
 */
class FluoroApp : Application() {

    /**
     * Called when the application is starting, before any other activity, service,
     * or receiver objects (excluding content providers) have been created.
     */
    override fun onCreate() {
        super.onCreate()

        // Initialize the FileManager singleton.
        // This ensures that the file system is ready before any part of the app
        // (like CameraService or any Activity) tries to access it.
        FileManager.initialize(this)
    }
}
