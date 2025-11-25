# FluoroSignalApp - 荧光信号测量与分析App

![App Icon](https://img.shields.io/badge/platform-Android-brightgreen.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6.0-blueviolet.svg)
![OpenCV](https://img.shields.io/badge/OpenCV-4.9.0-orange.svg)
![Status](https://img.shields.io/badge/status-In%20Development-yellow.svg)

本项目旨在开发一款安卓应用，用于对生物医学领域的荧光标记图像进行快速、便捷的定量分析。用户可以通过手机摄像头拍摄荧光样本，或从相册选择图片，应用将自动计算其绿色通道的信噪比（SNR）等关键指标，并生成分析报告。

---

## 🚀 项目概览 (Project Overview)

目前，项目已经完成了**最小可行产品 (MVP)** 的前端框架和后端数据流。核心功能流程已经完全打通，现在需要集成核心的图像分析算法。

### ✅ 已实现功能

*   **📷 实时相机预览**: 基于 `Camera2` API，提供流畅的实时预览。
*   **🎛️ 手动参数调节**: 支持实时调节 **ISO** 和**曝光时间**，预览画面会即时响应。
*   **📸 拍照与选择**:
    *   **拍照**: 将当前预览画面以高分辨率保存。
    *   **选择**: 从手机相册导入图片进行分析。
*   **📂 自动化文件管理**:
    *   所有待分析的图片会自动保存到应用专属的 `PendingAnalysis` 目录。
    *   分析完成后的图片和结果 (`.json` 格式) 会被自动归档到 `AnalysisHistory` 目录。
*   **📊 分析流程** (当前为模拟):
    *   点击“分析”按钮，会自动处理最新的待分析图片。
    *   整个流程包括：获取图片 -> **调用分析模块** -> 归档数据。
*   **📄 结果展示与导航**:
    *   使用 `Navigation-Compose` 实现页面跳转。
    *   分析成功后，自动跳转到设计精美的**分析报告页面** (`ResultScreen`)。
*   **📤 报告导出**: 在结果页面，用户可以将分析报告导出为 `.csv` 文件，并通过系统分享功能发送。

---

## 🛠️ 技术栈 (Tech Stack)

*   **语言**: Kotlin
*   **UI**: Jetpack Compose
*   **导航**: Navigation-Compose
*   **相机**: Camera2 API
*   **异步处理**: Kotlin Coroutines & Flow
*   **数据序列化**: Kotlinx.Serialization
*   **核心分析库**: OpenCV for Android (版本: 4.9.0)

---

## 🎯 协作任务：实现核心图像分析 (Your Task)

你的任务是完成项目中唯一待填充的核心部分：**图像分析算法**。

你需要修改 `ImageAnalyzer.kt` 文件，将其中**模拟的（Mock）分析逻辑**替换为**真实的 OpenCV 计算逻辑**。

### 📝 接口定义

你需要实现的接口位于 `ImageAnalyzer.kt` 文件中：

```kotlin
class ImageAnalyzer {
    /**
     * 分析给定的图像文件，并提取荧光信号指标。
     * @param imageFile 需要分析的图片文件 (e.g., "FLUORO_... .jpg")。
     * @return 一个包含所有计算结果的 AnalysisResult 对象。
     */
    suspend fun analyze(imageFile: File): AnalysisResult {
        // 【你的代码将在这里实现】
    }
}
```

### 📋 实现要求

在 `analyze` 函数中，你需要完成以下步骤：

1.  **加载图片**: 使用 OpenCV 的 `Imgcodecs.imread(imageFile.absolutePath)` 将图片文件加载为一个 `Mat` 对象。
2.  **颜色通道分离**: 使用 `Core.split()` 将 `Mat` 对象分离成 B, G, R 三个颜色通道。
3.  **选取绿色通道**: 我们的目标是分析绿色荧光，所以请选取**绿色通道**的 `Mat` 进行后续计算。
4.  **计算核心指标**: 对绿色通道的 `Mat` 调用 `Core.meanStdDev()` 来计算**均值 (mean)** 和**标准差 (stdDev)**。
5.  **计算派生指标**:
    *   **信噪比 (SNR)**: `mean / stdDev`
    *   **方差 (Variance)**: `stdDev * stdDev`
6.  **（可选）计算补充指标**:
    *   **最小/最大像素值**: 可以通过 `Core.minMaxLoc()` 获取。
7.  **填充并返回结果**: 将所有计算出的值，连同从 `imageFile` 提取的 `imageName` 和当前 `timestamp`，填充到一个 `AnalysisResult` 对象中并返回。
8.  **内存管理**: 确保所有创建的 `Mat` 对象在函数结束前都被 `release()`，以防止内存泄漏。

> **提示**: 当前的模拟实现中已经包含了返回 `AnalysisResult` 对象的框架，你可以直接在其基础上修改。

---

## ⚙️ 如何开始 (Getting Started)

1.  **克隆仓库**:
    ```bash
    git clone 【请在此处粘贴你的仓库HTTPS地址】
    ```

2.  **打开项目**:
    使用最新稳定版的 Android Studio 打开克隆到本地的项目文件夹。

3.  **构建项目**:
    Android Studio 会自动使用 Gradle 同步并下载所有必需的依赖库。等待构建完成。

4.  **运行应用**:
    *   将你的安卓手机连接到电脑（确保已开启USB调试）。
    *   在 Android Studio 的设备列表中选择你的手机。
    *   点击 "Run 'app'" (绿色ثلث按钮) 来安装并启动应用。

5.  **开始编码**:
    *   找到并打开 `ImageAnalyzer.kt` 文件。
    *   按照上面的 “实现要求” 开始编写你的 OpenCV 代码。
    *   你可以随时运行 App 来测试你的分析逻辑是否能与现有流程无缝对接。

---
修改了ImageAnalyzer.kt和AnalysisResult.kt两个其他与master无异
新增存储图像分析过程中产出的所有关键指标，支持多通道和高级统计
进行ROI 分析、多通道统计和异常检测
