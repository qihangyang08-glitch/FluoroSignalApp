package com.example.fluorosignalapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fluorosignalapp.backend.BackendManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the backend manager
        BackendManager.getInstance(this)

        setContent {
            MaterialTheme {
                Surface {
                    // Create the NavController and the SharedViewModel.
                    // The ViewModel is scoped to the NavHost, so it's shared between screens.
                    val navController = rememberNavController()
                    val sharedViewModel: SharedViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = Routes.CAMERA
                    ) {
                        composable(Routes.CAMERA) {
                            CameraScreen(
                                navController = navController,
                                sharedViewModel = sharedViewModel
                            )
                        }
                        composable(Routes.RESULT) {
                            ResultScreen(
                                sharedViewModel = sharedViewModel,
                                onNavigateBack = {
                                    // Use popBackStack without arguments for simple back navigation
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(Routes.HISTORY) {
                            HistoryScreen(
                                sharedViewModel = sharedViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
