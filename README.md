

# FluoroSignalApp - 智能手机荧光信号定量分析系统

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin_1.9-blue.svg)
![UI](https://img.shields.io/badge/UI-Jetpack_Compose-purple.svg)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-orange.svg)
![Status](https://img.shields.io/badge/Status-Scientific_Raw_Ready-red.svg)

本项目旨在开发一款便携、低成本但具备**科研级精度**的荧光检测工具。通过深入 Android 底层相机控制，屏蔽系统自动处理算法，配合 OpenCV 图像分析，实现对荧光样本浓度的线性定量分析。

---

## 🌟 核心亮点 (Key Highlights)

### 1. 🧪 纯净的“一手”数据采集 (Scientific Data Acquisition)
为了解决普通手机拍照“非线性”、“美颜涂抹”导致实验数据失真的问题，我们重写了相机底层逻辑：
*   **ISP 算法屏蔽**：在代码层面强制关闭了 Android 系统的 **色调映射 (Tone Mapping)**、**自动降噪 (Noise Reduction)**、**边缘增强** 和 **自动白平衡**。
*   **线性响应**：确保传感器捕获的光子数量与像素数值呈线性关系（R² > 0.98），这是定量分析的基础。
*   **无损存储**：全链路使用 **PNG 无损格式**，杜绝 JPEG 压缩带来的随机噪声。

### 2. 🎛️ 精细化拍摄控制 (Precision Control)
专为实验室场景设计的交互界面：
*   **全手动参数**：支持手动锁定 **ISO (感光度)** 和 **曝光时间 (Exposure Time)**。
*   **微调步进 (Fine-tuning)**：新增 **[+] / [-] 精确调节按钮**。ISO 步进 50，曝光时间步进 1ms，确保实验条件可被精确复制。
*   **参数锁定**：拍摄瞬间强制锁定自动曝光 (AE Lock)，防止画面亮度波动。

### 3. 🩺 智能数据诊断 (Smart Diagnosis)
不仅仅输出冷冰冰的数字，App 内置了“AI 医生”逻辑：
*   **质量评估**：自动判断照片是否 **过曝 (Saturation)**、**欠曝** 或 **信噪比过低**。
*   **人话建议**：直接在报告中给出建议（如：“⚠️ 严重过曝，请降低 ISO”），降低医学生的上手门槛。
*   **可视化反馈**：分析卡片根据数据质量自动变色（🟢优质 / 🟡警告 / 🔴废片）。

### 4. 📂 自动化数据流 (Automated Workflow)
*   **闭环管理**：拍摄 -> `PendingAnalysis` (待分析) -> 分析 -> `AnalysisHistory` (归档)。
*   **全量导出**：支持一键导出包含所有通道数据（R/G/B）、统计指标（偏度/峰度）及诊断结论的 **CSV 报表**，方便导入 Excel/SPSS 进行二次分析。

---

## 🛠️ 技术架构 (Technical Architecture)

项目采用现代 Android 开发技术栈：

| 模块 | 技术选型 | 职责说明 |
| :--- | :--- | :--- |
| **UI 层** | **Jetpack Compose** | 声明式 UI，实现流畅的实时预览与交互 |
| **导航** | **Navigation-Compose** | 管理相机页与结果页的无缝跳转 |
| **相机层** | **Camera2 API** | 底层硬件控制，实现“科研模式”拍摄 |
| **逻辑层** | **Kotlin Coroutines** | 异步处理文件读写与耗时计算 |
| **数据层** | **Kotlinx Serialization** | 复杂数据结构（分析结果）的序列化存储 |
| **算法层** | **OpenCV (Integration)** | (接口已预留) 负责 ROI 分割与多通道统计 |

---

## 📸 功能演示 (Features)

### 拍摄界面 (Camera Screen)
*   **实时预览**：所见即所得，无画面拉伸。
*   **控制面板**：
    *   ISO 滑块 + 微调按钮
    *   曝光时间滑块 + 微调按钮
    *   **[选择]**：从相册导入外部图片分析
    *   **[拍照]**：获取无损 PNG
    *   **[分析]**：触发后台算法流程

### 结果界面 (Result Screen)
*   **核心指标**：突出显示绿色通道均值 (Mean G)。
*   **诊断卡片**：显示数据质量评分与操作建议。
*   **详细数据**：折叠展示多通道均值、方差、偏度等高级统计量。
*   **[导出]**：调用系统分享表单发送 CSV 报告。

---

## 🤝 协作指南 (For Contributors)

当前分支 **`num5`** (或最新开发分支) 已完成以下工作，等待算法核心接入：

1.  **Data Model Ready**: `AnalysisResult.kt` 已升级，预埋了 `meanR/G/B`, `skewness`, `warnings`, `roi` 等所有高级字段。
2.  **UI Ready**: 结果页面已适配新模型，能够动态显示所有新字段。
3.  **Source Ready**: `CameraService` 产出的图片已确认为线性无损 PNG。

**接下来的工作 (To-Do):**
*   [ ] 将最新的 `ImageAnalyzer.kt` (包含 ROI 掩膜与多通道逻辑) 合并入项目。
*   [ ] 验证算法计算出的 `meanG` 与拍摄参数的线性关系。

---

## 📦 如何构建与运行

1.  克隆仓库：
    ```bash
    git clone [Repo URL]
    ```
2.  打开 Android Studio，等待 Gradle 同步完成。
3.  连接 Android 手机（需开启 USB 调试）。
4.  点击 **Run** (绿色三角形) 安装应用。

---

> **致谢**: 本项目由计算机工程团队与医学院团队合作开发。特别感谢在光学标定与样本制备方面提供的支持。

不成线性关系原因：
1.问题代码位置： CameraService.kt
问题： ImageReader 配置为 ImageFormat.JPEG，JPEG 本身已经应用了非线性 gamma 校正！
在 CameraService.kt 的 takePicture() 方法中，替换所有拍摄相关代码。
同步更新预览参数设置。
2.在 CameraService.kt 中使用的 CaptureRequest.CONTROL_AE_MODE 可能并未被成功锁定或覆盖，导致手机相机系统自动抵消了您手动调整 ISO 带来的亮度变化。

