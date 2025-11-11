package com.example.fluorosignalapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A ViewModel shared between composables in the navigation graph.
 * It acts as a lifecycle-aware, robust container for passing complex data
 * between screens, avoiding the pitfalls of static singletons.
 */
class SharedViewModel : ViewModel() {

    // Backing property for the analysis result, kept private.
    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)

    // Public, read-only StateFlow that UI can collect from.
    val analysisResult = _analysisResult.asStateFlow()

    /**
     * Sets the analysis result, to be called by the source screen (CameraScreen).
     */
    fun setAnalysisResult(result: AnalysisResult) {
        _analysisResult.value = result
    }

    /**
     * Clears the result after it has been consumed. 
     * This is crucial to prevent showing stale data on subsequent navigations.
     */
    fun clearAnalysisResult() {
        _analysisResult.value = null
    }
}
