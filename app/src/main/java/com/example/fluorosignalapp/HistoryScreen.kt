package com.example.fluorosignalapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.fluorosignalapp.backend.BackendManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史记录屏幕 - 展示所有分析记录
 * 
 * 功能:
 * - 展示历史分析列表
 * - 按时间排序
 * - 支持按质量筛选
 * - 显示详细统计信息
 * - 支持删除记录
 * 
 * 代码行数: ~250行
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sharedViewModel: SharedViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backendManager = remember { BackendManager.getInstance(context) }

    // 状态变量
    var analysisHistory by remember { mutableStateOf<List<AnalysisResult>>(emptyList()) }
    var databaseStats by remember { mutableStateOf<HistoryStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, GOOD, WARNING, BAD
    var selectedItem by remember { mutableStateOf<AnalysisResult?>(null) }

    // 初始化加载数据
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val history = backendManager.getAnalysisHistory(limit = 100)
                analysisHistory = history
                
                val stats = backendManager.getDatabaseStats()
                databaseStats = HistoryStats(
                    totalRecords = stats.totalRecords,
                    averageSnr = stats.averageSnr,
                    goodQualityCount = stats.goodQualityCount,
                    oldestRecord = stats.oldestRecord,
                    newestRecord = stats.newestRecord
                )
                
                isLoading = false
                Log.i("HistoryScreen", "Loaded ${history.size} records")
            } catch (e: Exception) {
                Log.e("HistoryScreen", "Failed to load history: ${e.message}", e)
                isLoading = false
            }
        }
    }

    // 过滤逻辑
    val filteredHistory = when (selectedFilter) {
        "GOOD" -> analysisHistory.filter { it.quality.name == "GOOD" }
        "WARNING" -> analysisHistory.filter { it.quality.name == "WARNING" }
        "BAD" -> analysisHistory.filter { it.quality.name == "BAD" }
        else -> analysisHistory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("分析历史记录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "共 ${analysisHistory.size} 条记录",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("← 返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            // 加载状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 统计卡片
                if (databaseStats != null) {
                    StatisticsCard(stats = databaseStats!!)
                }

                // 筛选按钮
                FilterButtonRow(
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it }
                )

                // 历史列表
                if (filteredHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无符合条件的记录",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHistory) { result ->
                            HistoryItemCard(
                                result = result,
                                isSelected = selectedItem == result,
                                onSelect = { selectedItem = it },
                                onDelete = {
                                    coroutineScope.launch {
                                        val success = backendManager.deleteAnalysis(result.imageName)
                                        if (success) {
                                            analysisHistory = analysisHistory.filter {
                                                it.imageName != result.imageName
                                            }
                                            Log.i("HistoryScreen", "Deleted: ${result.imageName}")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 统计信息卡片
 */
@Composable
private fun StatisticsCard(stats: HistoryStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "分析统计",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "总数",
                    value = "${stats.totalRecords}",
                    color = Color(0xFF2196F3)
                )
                StatItem(
                    label = "平均SNR",
                    value = String.format("%.2f", stats.averageSnr),
                    color = Color(0xFF4CAF50)
                )
                StatItem(
                    label = "优质图像",
                    value = "${stats.goodQualityCount}",
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

/**
 * 统计项目
 */
@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), shape = androidx.compose.material3.shapes.RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * 筛选按钮行
 */
@Composable
private fun FilterButtonRow(
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilterButton(
            label = "全部",
            isSelected = selectedFilter == "ALL",
            onClick = { onFilterChange("ALL") }
        )
        FilterButton(
            label = "优质",
            isSelected = selectedFilter == "GOOD",
            onClick = { onFilterChange("GOOD") },
            color = Color(0xFF4CAF50)
        )
        FilterButton(
            label = "警告",
            isSelected = selectedFilter == "WARNING",
            onClick = { onFilterChange("WARNING") },
            color = Color(0xFFFFC107)
        )
        FilterButton(
            label = "不良",
            isSelected = selectedFilter == "BAD",
            onClick = { onFilterChange("BAD") },
            color = Color(0xFFF44336)
        )
    }
}

@Composable
private fun FilterButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    color: Color = Color(0xFF2196F3)
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(32.dp)
            .wrapContentWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else Color.LightGray,
            contentColor = if (isSelected) Color.White else Color.DarkGray
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

/**
 * 历史记录项卡片
 */
@Composable
private fun HistoryItemCard(
    result: AnalysisResult,
    isSelected: Boolean,
    onSelect: (AnalysisResult?) -> Unit,
    onDelete: () -> Unit
) {
    val qualityColor = when (result.quality.name) {
        "GOOD" -> Color(0xFF4CAF50)
        "WARNING" -> Color(0xFFFFC107)
        "BAD" -> Color(0xFFF44336)
        else -> Color.Gray
    }

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = dateFormatter.format(Date(result.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(if (isSelected) null else result) },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xF5F5F5) else Color.White
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 顶部行：文件名和质量
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.imageName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = qualityColor,
                    shape = androidx.compose.material3.shapes.RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = result.quality.name,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 详细数据行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SNR: ${String.format("%.2f", result.snr)}",
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = "Mean: ${String.format("%.1f", result.mean)}",
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = "σ: ${String.format("%.1f", result.stdDev)}",
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 时间和删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateString,
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                if (isSelected) {
                    Button(
                        onClick = onDelete,
                        modifier = Modifier
                            .height(28.dp)
                            .wrapContentWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("删除", fontSize = 10.sp)
                    }
                }
            }

            // 诊断信息
            if (isSelected && result.diagnosis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "诊断: ${result.diagnosis}",
                    fontSize = 11.sp,
                    color = qualityColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            qualityColor.copy(alpha = 0.1f),
                            shape = androidx.compose.material3.shapes.RoundedCornerShape(2.dp)
                        )
                        .padding(6.dp)
                )
            }
        }
    }
}

/**
 * 数据类
 */
data class HistoryStats(
    val totalRecords: Int,
    val averageSnr: Double,
    val goodQualityCount: Int,
    val oldestRecord: Long,
    val newestRecord: Long
)
