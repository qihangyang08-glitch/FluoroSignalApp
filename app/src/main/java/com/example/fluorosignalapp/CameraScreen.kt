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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.fluorosignalapp.backend.BackendManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraUI(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraService = remember { CameraService(context) }

    val backendManager = remember { BackendManager.getInstance(context) }
    val isAnalyzing by backendManager.isAnalyzing.collectAsState()

    var iso by remember { mutableIntStateOf(800) }
    var exposureTimeMs by remember { mutableLongStateOf(100L) }
    var aspectRatio by remember { mutableStateOf<Float?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }

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
                } catch (e: Exception) {
                    Log.e("CameraUI", "Failed to copy image from gallery", e)
                    Toast.makeText(context, "复制图片失败: ${e.message}", Toast.LENGTH_LONG).show()
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    Row(modifier = Modifier.padding(end = 8.dp)) {
                        IconButton(
                            onClick = { showGrid = !showGrid },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                                contentDescription = "Toggle Grid",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { navController.navigate(Routes.HISTORY) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "History",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isCameraReady,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CompactParameterControl(
                            label = "ISO",
                            value = iso.toFloat(),
                            displayValue = iso.toString(),
                            onValueChange = {
                                iso = it.toInt()
                                cameraService.updateParameters(iso, exposureTimeMs)
                            },
                            valueRange = 100f..3200f,
                            modifier = Modifier.weight(1f),
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

                        Spacer(modifier = Modifier.width(16.dp))

                        CompactParameterControl(
                            label = "Exp(ms)",
                            value = exposureTimeMs.toFloat(),
                            displayValue = "$exposureTimeMs",
                            onValueChange = {
                                exposureTimeMs = it.toLong()
                                cameraService.updateParameters(iso, exposureTimeMs)
                            },
                            valueRange = 1f..500f,
                            modifier = Modifier.weight(1f),
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
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                pickMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = areControlsEnabled,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCapturing = true
                                    try {
                                        val file = withTimeout(5000) {
                                            cameraService.takePicture()
                                        }
                                        Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("CameraScreen", "Error taking picture", e)
                                        val msg = if (e is TimeoutCancellationException) "拍照超时" else e.message
                                        Toast.makeText(context, "拍照失败: $msg", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isCapturing = false
                                    }
                                }
                            },
                            enabled = areControlsEnabled,
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.Gray
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .border(2.dp, Color.Black, CircleShape)
                                    .padding(4.dp)
                                    .background(Color.Red, CircleShape)
                            )
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val imageFile = FileManager.getLatestPendingImage()
                                    if (imageFile == null) {
                                        Toast.makeText(context, "没有待分析的图片", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    try {
                                        val finalResult = backendManager.performAnalysis(imageFile)
                                        if (finalResult != null) {
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
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Analytics,
                                    contentDescription = "Analyze",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
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
                                    try {
                                        cameraService.openCamera(android.view.Surface(surface), width, height)
                                        val optimalSize = cameraService.getPreviewSize()
                                        if (optimalSize != null) {
                                            surface.setDefaultBufferSize(optimalSize.width, optimalSize.height)
                                            aspectRatio = optimalSize.width.toFloat() / optimalSize.height.toFloat()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CameraScreen", "Failed to open camera", e)
                                        Toast.makeText(ctx, "无法打开相机: ${e.message}", Toast.LENGTH_LONG).show()
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

            if (showGrid) {
                GridOverlay()
            }

            AnimatedVisibility(
                visible = isCapturing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp), color = Color.White)
            }
        }
    }
}

@Composable
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val thirdWidth = width / 3
        val thirdHeight = height / 3

        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(thirdWidth, 0f),
            end = Offset(thirdWidth, height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(thirdWidth * 2, 0f),
            end = Offset(thirdWidth * 2, height),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(0f, thirdHeight),
            end = Offset(width, thirdHeight),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(0f, thirdHeight * 2),
            end = Offset(width, thirdHeight * 2),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
fun CompactParameterControl(
    label: String,
    value: Float,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(text = displayValue, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrement", tint = Color.White)
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF2196F3),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            IconButton(onClick = onIncrement, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Increment", tint = Color.White)
            }
        }
    }
}
