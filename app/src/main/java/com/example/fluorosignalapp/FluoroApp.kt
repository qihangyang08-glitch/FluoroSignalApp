package com.example.fluorosignalapp

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Custom Application class for FluoroSignalApp.
 * This is the first component to be instantiated when the app process is created.
 * We use it to perform one-time initializations for globally used components.
 */
class FluoroApp : Application() {

    companion object {
        // Static flag to track OpenCV initialization status
        var isOpenCVInitialized = false
            private set
    }

    /**
     * Called when the application is starting, before any other activity, service,
     * or receiver objects (excluding content providers) have been created.
     */
    override fun onCreate() {
        super.onCreate()

        // Initialize OpenCV library
        // This loads the native OpenCV libraries required for image processing
        try {
            // Try to load OpenCV from the APK (static initialization)
            System.loadLibrary("opencv_java4")
            isOpenCVInitialized = true
            Log.i("FluoroApp", "OpenCV loaded successfully via System.loadLibrary")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("FluoroApp", "System.loadLibrary failed, trying OpenCVLoader.initDebug()")

            // Fallback to OpenCVLoader (requires OpenCV Manager app installed)
            if (OpenCVLoader.initDebug()) {
                isOpenCVInitialized = true
                Log.i("FluoroApp", "OpenCV initialized successfully via OpenCVLoader.initDebug()")
            } else {
                isOpenCVInitialized = false
                Log.e("FluoroApp", "OpenCV initialization FAILED! Image analysis will not work.")
            }
        }

        // Initialize the FileManager singleton.
        // This ensures that the file system is ready before any part of the app
        // (like CameraService or any Activity) tries to access it.
        FileManager.initialize(this)

        Log.i("FluoroApp", "Application initialization complete. OpenCV status: $isOpenCVInitialized")
    }
}
