package com.example.fluorosignalapp

import android.Manifest
import android.graphics.SurfaceTexture
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.fluorosignalapp.data.diagnosis.DiagnosisService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import com.example.fluorosignalapp.backend.BackendManager

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    if (cameraPermissionState.status.isGranted) {
        CameraUI(navController = navController, sharedViewModel = sharedViewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "请授予相机权限才能使用本应用")
        }
    }
}

@Composable
fun CameraUI(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraService = remember { CameraService(context) }

    // Backend Manager Integration
    val backendManager = remember { BackendManager.getInstance(context) }
    val isAnalyzing by backendManager.isAnalyzing.collectAsState()
    val analysisProgress by backendManager.analysisProgress.collectAsState()
    val analysisError by backendManager.analysisError.collectAsState()

    var iso by remember { mutableStateOf(800) }
    var exposureTimeMs by remember { mutableStateOf(100L) }
    var aspectRatio by remember { mutableStateOf<Float?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val isCameraReady by cameraService.isCameraReady.collectAsState()
    val areControlsEnabled = isCameraReady && !isCapturing && !isAnalyzing

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    Toast.makeText(context, "正在复制图片...", Toast.LENGTH_SHORT).show()
                    val copiedFile = FileManager.copyImageToPending(context, uri)
                    Toast.makeText(
                        context,
                        "图片已添加到待分析队列!\n${copiedFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.i("CameraUI", "Image copied successfully: ${copiedFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("CameraUI", "Failed to copy image from gallery", e)
                    Toast.makeText(
                        context,
                        "复制图片失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Log.d("CameraUI", "User cancelled image selection")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                cameraService.closeCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraService.closeCamera()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio!!) else Modifier),
                factory = { ctx ->
                    android.view.TextureView(ctx).apply {
                        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                coroutineScope.launch {
                                    cameraService.openCamera(android.view.Surface(surface), width, height)
                                    val optimalSize = cameraService.getPreviewSize()
                                    if (optimalSize != null) {
                                        surface.setDefaultBufferSize(optimalSize.width, optimalSize.height)
                                        aspectRatio = optimalSize.width.toFloat() / optimalSize.height.toFloat()
                                    }
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                cameraService.closeCamera()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                },
                update = {}
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isCapturing,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
            }
        }

        AnimatedVisibility(
            visible = isCameraReady,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ParameterSlider(
                    label = "ISO",
                    value = iso.toFloat(),
                    onValueChange = {
                        iso = it.toInt()
                        cameraService.updateParameters(iso, exposureTimeMs)
                    },
                    valueRange = 100f..3200f,
                    displayValue = iso.toString(),
                    enabled = areControlsEnabled,
                    onDecrement = {
                        val newValue = (iso - 1).coerceIn(100, 3200)
                        iso = newValue
                        cameraService.updateParameters(newValue, exposureTimeMs)
                    },
                    onIncrement = {
                        val newValue = (iso + 1).coerceIn(100, 3200)
                        iso = newValue
                        cameraService.updateParameters(newValue, exposureTimeMs)
                    }
                )

                ParameterSlider(
                    label = "曝光(ms)",
                    value = exposureTimeMs.toFloat(),
                    onValueChange = {
                        exposureTimeMs = it.toLong()
                        cameraService.updateParameters(iso, exposureTimeMs)
                    },
                    valueRange = 1f..500f,
                    displayValue = "${exposureTimeMs}ms",
                    enabled = areControlsEnabled,
                    onDecrement = {
                        val newValue = (exposureTimeMs - 1).coerceAtLeast(1L)
                        exposureTimeMs = newValue
                        cameraService.updateParameters(iso, newValue)
                    },
                    onIncrement = {
                        val newValue = (exposureTimeMs + 1).coerceAtMost(500L)
                        exposureTimeMs = newValue
                        cameraService.updateParameters(iso, newValue)
                    }
                )

                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color.White.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = areControlsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3),
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(text = "选择", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isCapturing = true
                                try {
                                    val file = cameraService.takePicture()
                                    Toast.makeText(context, "Saved to: ${file.name}", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Error taking picture", e)
                                    Toast.makeText(context, "拍照失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isCapturing = false
                                }
                            }
                        },
                        enabled = areControlsEnabled,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(text = "拍照", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val imageFile = FileManager.getLatestPendingImage()

                                if (imageFile == null) {
                                    Toast.makeText(context, "没有待分析的图片", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                Log.i("CameraUI", "Starting analysis for: ${imageFile.name}")
                                
                                try {
                                    val finalResult = backendManager.performAnalysis(imageFile)

                                    if (finalResult != null) {
                                        Log.d("APP_DEBUG", "分析成功，结果SNR: ${finalResult.snr}")
                                        Toast.makeText(context, "分析完成！诊断: ${finalResult.diagnosis}", Toast.LENGTH_LONG).show()

                                        sharedViewModel.setAnalysisResult(finalResult)
                                        navController.navigate(Routes.RESULT)
                                    } else {
                                        Toast.makeText(context, "分析未返回结果", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraUI", "Analysis failed", e)
                                    Toast.makeText(context, "分析失败: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = areControlsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800),
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "分析", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            navController.navigate(Routes.HISTORY)
                        },
                        enabled = !isCapturing && !isAnalyzing,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0),
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(text = "历史", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    enabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 参数标签和值
        Text(
            text = "$label: $displayValue",
            color = Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        // 滑块和按钮行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 减按钮
            Button(
                onClick = onDecrement,
                enabled = enabled,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE57373),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("-", fontSize = 18.sp)
            }

            // 滑块
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
            )

            // 加按钮
            Button(
                onClick = onIncrement,
                enabled = enabled,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF81C784),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("+", fontSize = 18.sp)
            }
        }
    }
}
