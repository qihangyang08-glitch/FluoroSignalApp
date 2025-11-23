

# FluoroSignalApp - 荧光信号测量与分析App

![App Icon](https://img.shields.io/badge/platform-Android-brightgreen.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6.0-blueviolet.svg)
![OpenCV](https://img.shields.io/badge/OpenCV-4.9.0-orange.svg)
![Status](https://img.shields.io/badge/status-Feature%20Complete-green.svg)

本项目旨在开发一款科研级安卓应用，将智能手机转化为便携式荧光检测仪。应用通过底层相机控制采集高保真图像，利用 OpenCV 进行本地分析，并提供智能化的数据质量诊断。

---

## 🚀 最新更新 (Latest Updates)

### 🌟 1. 智能数据诊断系统 (Smart Diagnosis)
为了解决非专业用户“看不懂参数”的痛点，我们引入了**“AI 医生”诊断模块**：
*   **自动体检**：系统会自动检查过曝 (Overexposure)、欠曝 (Underexposure)、信噪比 (SNR) 低等问题。
*   **人话建议**：不再只显示冷冰冰的数字，而是直接给出建议（如：“⚠️ 严重过曝，请降低 ISO”）。
*   **视觉反馈**：分析报告卡片会自动变色（🟢优质 / 🟡警告 / 🔴废片），一目了然。

### 💎 2. 高精度无损数据流 (Lossless Data Flow)
*   **PNG 格式标准化**：为了消除 JPEG 压缩伪影对线性分析的影响，全链路数据流现已强制使用 **PNG 无损格式**。
*   **底层字节流读取**：`ImageAnalyzer` 现通过 OpenCV 底层字节流直接解码，绕过了安卓系统的自动缩放和色彩转换，确保了分析结果的**高度可重复性**和**线性度**。

---

## 📱 核心功能 (Key Features)

*   **📷 专业采集**:
    *   基于 `Camera2` API，支持手动调节 **ISO** 和 **曝光时间**，并具备**自动曝光锁定**功能。
    *   **所见即所得**：预览画面无拉伸，并在拍摄时保存为无损格式。
*   **🧬 图像分析**:
    *   自动提取绿色荧光通道。
    *   计算均值 (Mean)、标准差 (StdDev)、信噪比 (SNR)。
    *   **[待实现]** 阈值分割与有效区域计算。
*   **🩺 智能报告**:
    *   自动生成包含诊断建议的分析报告。
    *   支持一键导出 **CSV 数据表**，内含详细指标与诊断结论，方便后续科研统计。
*   **📂 数据管理**:
    *   自动归档：`PendingAnalysis` (待分析) -> `AnalysisHistory` (已归档)。
    *   从相册导入：支持将外部图片无缝导入分析流程。

---

## 🛠️ 技术架构 (Architecture)

*   **UI 层**: Jetpack Compose (CameraScreen, ResultScreen)
*   **逻辑层**:
    *   `CameraService`: 负责硬件控制与无损图像捕获。
    *   `DiagnosisService`: **[新增]** 负责将原始计算数据转化为诊断建议（解耦设计）。
    *   `ImageAnalyzer`: 负责 OpenCV 核心算法。
*   **数据层**: `FileManager`, `AnalysisResult` (Serializable).

---

## 🎯 协作任务：完善图像处理算法 (Collaborator's Task)

**你的任务是专注于 `ImageAnalyzer.kt` 中的核心算法实现。**

由于架构升级，你现在的开发环境更加纯净和稳定：

1.  **输入更有保障**：你接收到的 `imageFile` 保证是无损的 PNG 格式，且通过字节流读取，无需担心压缩噪声。
2.  **无需关心业务逻辑**：你**不需要**编写判断“过曝”或“好坏”的代码。你只需要算出准确的数学指标（Mean, Max, SNR），后续的 `DiagnosisService` 会自动处理诊断逻辑。

### 📝 你需要做的是：

在 `ImageAnalyzer.kt` 中，将目前的“全图平均”逻辑升级为**“有效区域分析”**：

1.  **加载图片**: 保持现有的字节流读取方式（已写好）。
2.  **阈值分割 (Thresholding)**: 使用 `Imgproc.threshold` 生成掩膜 (Mask)，剔除黑色背景。
3.  **计算指标**: 使用带 Mask 的 `Core.meanStdDev` 计算仅针对光斑区域的均值和标准差。
4.  **返回结果**: 将计算出的数值填充到 `AnalysisResult` 中返回即可。（`quality` 和 `diagnosis` 字段使用默认值即可，后续服务会填充）。

---

## ⚙️ 如何开始

1.  **拉取最新代码**:
    ```bash
    git fetch origin
    git checkout feature/diagnosis  # 或者 master，取决于合并情况
    ```
2.  **关注文件**:
    *   核心算法：`data/analysis/ImageAnalyzer.kt`
3.  **运行测试**:
    *   使用提供的标定样本图片进行测试，观察高浓度样本的均值是否呈现线性增长。

---

如有疑问，请参考 `DiagnosisService.kt` 了解评分逻辑，或直接联系项目负责人。Happy Coding! 🚀
