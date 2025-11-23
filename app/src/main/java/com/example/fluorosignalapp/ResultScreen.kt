package com.example.fluorosignalapp

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    sharedViewModel: SharedViewModel,
    onNavigateBack: () -> Unit
) {
    val analysisResult by sharedViewModel.analysisResult.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            sharedViewModel.clearAnalysisResult()
        }
    }

    val result = analysisResult
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No result data. Navigating back...")
        }
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val qualityColor = when (result.quality) {
        ImageQuality.GOOD -> Color(0xFF4CAF50) // Green
        ImageQuality.WARNING -> Color(0xFFFFC107) // Amber
        ImageQuality.BAD -> Color(0xFFF44336) // Red
        ImageQuality.UNKNOWN -> Color.Gray
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("分析报告") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(qualityColor.copy(alpha = 0.1f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.diagnosis,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = qualityColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("信噪比 (SNR): ${String.format("%.2f", result.snr)}", fontSize = 18.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("均值: ${String.format("%.2f", result.mean)}")
                        Text("标准差: ${String.format("%.2f", result.stdDev)}")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val data = mapOf(
                    "图片名称" to result.imageName,
                    "时间戳" to result.timestamp.toString(),
                    "质量评估" to result.quality.name,
                    "方差" to String.format("%.2f", result.variance),
                    "最小像素" to result.minPixelValue.toString(),
                    "最大像素" to result.maxPixelValue.toString()
                )

                items(data.entries.toList(), key = { it.key }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.key, fontWeight = FontWeight.SemiBold)
                        Text(entry.value)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    coroutineScope.launch {
                        try {
                            val csvUri = ReportGenerator.exportResultAsCsv(context, result)
                            Toast.makeText(context, "报告已导出到下载文件夹", Toast.LENGTH_SHORT).show()

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, csvUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享CSV报告"))

                        } catch (e: Exception) {
                            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text("导出报告")
                }
                Button(onClick = onNavigateBack) {
                    Text("返回相机")
                }
            }
        }
    }
}
