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
                    if (result.isSaturated) {
                        Text(
                            text = "警告: 图像可能过饱和!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("信噪比 (SNR): ${String.format("%.2f", result.snr)}", fontSize = 18.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Green Channel Mean: ${String.format("%.2f", result.mean)}")
                        Text("标准差: ${String.format("%.2f", result.stdDev)}")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val basicData = mapOf(
                    "图片名称" to result.imageName,
                    "时间戳" to result.timestamp.toString(),
                    "质量评估" to result.quality.name
                )

                val statisticalData = mapOf(
                    "绿色均值 (Mean)" to String.format("%.2f", result.mean),
                    "中位数 (Median)" to String.format("%.2f", result.median),
                    "标准差 (StdDev)" to String.format("%.2f", result.stdDev),
                    "方差 (Variance)" to String.format("%.2f", result.variance),
                    "信噪比 (SNR)" to String.format("%.2f", result.snr),
                    "偏度 (Skewness)" to String.format("%.4f", result.skewness),
                    "峰度 (Kurtosis)" to String.format("%.4f", result.kurtosis)
                )

                val pixelData = mapOf(
                    "最小像素" to result.minPixelValue.toString(),
                    "最大像素" to result.maxPixelValue.toString(),
                    "有效像素数" to result.validPixelCount.toString(),
                    "饱和状态" to result.isSaturated.toString()
                )

                val channelData = mapOf(
                    "Red Mean (R)" to if (result.redMean != null) String.format("%.2f", result.redMean!!) else "N/A",
                    "Green Mean (G)" to String.format("%.2f", result.mean),
                    "Blue Mean (B)" to if (result.blueMean != null) String.format("%.2f", result.blueMean!!) else "N/A"
                )

                // 基本信息
                item {
                    Text("【基本信息】", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                items(basicData.entries.toList(), key = { it.key }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.key, fontWeight = FontWeight.SemiBold)
                        Text(entry.value)
                    }
                }

                // 统计指标
                item {
                    Text("【统计指标】", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.paddingFromBaseline(top = 12.dp))
                }
                items(statisticalData.entries.toList(), key = { it.key }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.key, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(entry.value, fontSize = 12.sp)
                    }
                }

                // 像素信息
                item {
                    Text("【像素信息】", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.paddingFromBaseline(top = 12.dp))
                }
                items(pixelData.entries.toList(), key = { it.key }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.key, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(entry.value, fontSize = 12.sp)
                    }
                }

                // 通道数据
                item {
                    Text("【RGB 通道】", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.paddingFromBaseline(top = 12.dp))
                }
                items(channelData.entries.toList(), key = { it.key }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.key, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(entry.value, fontSize = 12.sp)
                    }
                }

                // 警告信息
                if (result.warnings.isNotEmpty()) {
                    item {
                        Text("【警告信息】", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.paddingFromBaseline(top = 12.dp), color = Color(0xFFFFC107))
                    }
                    items(result.warnings, key = { it }) { warning ->
                        Text(warning, fontSize = 12.sp, color = Color(0xFFFFC107))
                    }
                }

                // 分析区域
                if (result.analysisRegion != null) {
                    item {
                        Text("【分析区域 (ROI)】", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.paddingFromBaseline(top = 12.dp))
                    }
                    item {
                        Text(result.analysisRegion!!, fontSize = 12.sp)
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
