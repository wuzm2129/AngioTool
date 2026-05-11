# AngioTool 2.0 程序报告

本文档基于对 `D:\CodeProject\AngioTool` 项目的代码阅读整理，目的是为后续修改、维护和新增功能提供整体背景。

## 1. 项目概述

AngioTool 2.0 是一个 Java 桌面图像分析程序，主要用于眼部扫描、血管造影或 OCT 图像中的血管结构分析。程序从输入图像中识别血管区域，生成带有轮廓、骨架、分支点和凸包的叠加图，并导出结构化统计结果到 Excel。

核心用户场景有两个：

- 单张图像分析：打开一张图，调整参数，实时预览结果，保存叠加图或统计表。
- 批量分析：选择一个或多个文件夹，递归处理其中的图像，统一使用当前参数，批量导出 Excel 和可选结果图。

程序可以作为独立桌面程序运行，也可以作为 ImageJ 插件运行。当前代码和 README 都显示，独立模式是主要推荐路径，ImageJ 支持偏实验性。

## 2. 技术栈与工程形态

项目是传统 Java 桌面程序，不使用 Maven、Gradle、Spring 或 JavaFX。

主要技术组成：

- 语言：Java，目标版本 Java 8。
- UI：Swing。
- 图像处理：自研算法为主。
- Excel：自研 `.xlsx` 读写。
- 图片格式：自研 TIFF/NetPBM 读写，其余格式依赖 Java `ImageIO`。
- 构建：Python 脚本 `compile.py`。
- Windows 启动器：`buildexe.py` + `launcher-win32.c`。
- 插件兼容：ImageJ API stub + `IJPlugin.ATPlugin`。

构建产物主要是：

- `AngioTool2.jar`
- `AngioTool2-32.exe`
- `AngioTool2-64.exe`

## 3. 目录与模块职责

源码主体位于 `source/`：

```text
source/
├── AngioTool/   主程序、GUI、参数、分析调度、批处理
├── Algorithms/  血管增强、骨架化、连通域、厚度、lacunarity 等算法
├── Pixels/      图像读取、写入、ARGB 缓冲区、绘图辅助
├── Utils/       BufferPool、向量容器、并行切片执行器、文件/UI 工具
├── Xlsx/        Excel xlsx 读取和写入
├── IJPlugin/    ImageJ 插件入口
├── images/      UI 图标资源
└── manual.html  内置帮助文档
```

项目根目录中还包括：

- `compile.py`：编译 Java、处理 `.tj` 模板、打包 JAR。
- `buildexe.py`：生成 Windows EXE。
- `launcher-win32.c`：原生 Windows 启动器。
- `plugins.config`：ImageJ 插件注册。
- `stubs/`：ImageJ 相关 stub，保证独立编译。
- `deprecated/`：旧代码，不参与当前主构建。

## 4. 程序入口与启动流程

独立程序入口：

```text
source/AngioTool/AngioTool.java
```

启动流程：

1. `main()` 被调用。
2. 预加载 `Algorithms.Lee94`，主要是提前加载 Lee94 骨架化需要的 lookup table。
3. 进入 Swing EventQueue。
4. 初始化图标、资源、参数偏好。
5. 创建主窗口 `AngioToolGui2`。

ImageJ 插件入口：

```text
source/IJPlugin/ATPlugin.java
```

插件模式会从 ImageJ 当前图像复制像素数据，包装为内部 `ArgbBuffer`，然后复用 AngioTool 的 GUI 和分析流程。

## 5. 模块关系图

```mermaid
flowchart TD
    User[用户] --> GUI[AngioToolGui2 主窗口]
    GUI --> Single[ImagingWindow 单图预览]
    GUI --> BatchUI[BatchWindow 批处理窗口]
    GUI --> Help[HelpWindow / manual.html]

    Single --> Analyzer[Analyzer.analyze]
    BatchUI --> Batch[BatchProcessing]
    Batch --> Analyzer

    Analyzer --> Algorithms[Algorithms]
    Analyzer --> Pixels[Pixels]
    Analyzer --> Xlsx[Xlsx]
    Analyzer --> Utils[Utils]

    Algorithms --> Tubeness[Tubeness]
    Algorithms --> Particles[Particles]
    Algorithms --> Skeleton[Zha84 / Lee94]
    Algorithms --> SkelAnalysis[AnalyzeSkeleton2]
    Algorithms --> Lac[Lacunarity2]
    Algorithms --> Thickness[VesselThickness]
    Algorithms --> Hull[ConvexHull]

    Pixels --> ImageIO[ImageFile / TiffReader / TiffWriter]
    Xlsx --> Report[SpreadsheetWriter / XlsxReader]
```

## 6. 核心分析管线

核心分析调度类：

```text
source/AngioTool/Analyzer.java
```

`Analyzer.analyze()` 是整个系统最重要的方法，单图分析和批处理最终都会进入这里。

```mermaid
flowchart TD
    A[输入图像 ArgbBuffer] --> B{Transform Colors?}
    B -->|是| C[PreprocessColor 色彩转灰度]
    B -->|否| D[选择最亮通道]
    C --> E[Tubeness 多尺度血管增强]
    D --> E
    E --> F[阈值二值化]
    F --> G[Filters 形态学过滤]
    G --> H[Particles 连通域和孔洞处理]
    H --> I[统计血管像素面积]
    H --> J[ConvexHull 计算凸包]
    H --> K{Skeletonizer}
    K -->|Fast| L[Zha84 骨架化]
    K -->|Thorough| M[Lee94 骨架化]
    L --> N[AnalyzeSkeleton2 骨架拓扑分析]
    M --> N
    H --> O{Compute Lacunarity?}
    O -->|是| P[Lacunarity2]
    N --> Q{Compute Thickness?}
    Q -->|是| R[VesselThickness]
    I --> S[Analyzer.Stats 汇总]
    J --> S
    N --> S
    P --> S
    R --> S
    S --> T[drawOverlay 绘制叠加图]
    S --> U[SpreadsheetWriter 导出 Excel]
```

分析管线步骤：

1. 输入图像进入 `ArgbBuffer`。
2. 如果启用颜色变换，调用 `PreprocessColor` 将彩色图转换为适合血管识别的灰度强度图。
3. 如果未启用颜色变换，则取输入图像中最亮通道作为分析基础。
4. 调用 `Tubeness` 做多尺度血管增强。
5. 根据用户设置的 low/high intensity 阈值进行二值化。
6. 使用 `Filters` 做形态学过滤，清理噪声。
7. 调用 `Particles` 做连通域分析、小颗粒移除、孔洞填充和 void 处理。
8. 统计血管前景像素面积。
9. 调用 `Lacunarity2` 计算 lacunarity，前提是用户开启该选项。
10. 调用 `ConvexHull` 计算组织/血管区域凸包面积。
11. 根据参数选择骨架化算法：快速 `Zha84` 或彻底 `Lee94`。
12. 调用 `AnalyzeSkeleton2` 分析骨架拓扑，得到分支、端点、junction 等。
13. 如开启厚度计算，调用 `VesselThickness` 估算血管厚度。
14. 汇总为 `Analyzer.Stats`。
15. 可调用 `Analyzer.drawOverlay()` 绘制结果叠加图。

## 7. 关键算法模块

### `Algorithms/Tubeness.java`

用于血管增强。它对输入图像进行多尺度高斯平滑，并基于 Hessian 特征值增强管状结构。用户在 UI 中配置的 `Vessel Diameters list` 会影响这一阶段的 sigma 列表。

### `Algorithms/Zha84.java`

快速骨架化算法，对应 UI 中 `Fast (Zha84)`。优点是速度快，适合批量处理；缺点是结果质量可能不如 Lee94。

### `Algorithms/Lee94.java`

更彻底的骨架化算法，对应 UI 中 `Thorough (Lee94)`。启动时会预加载 lookup table，构建时也会打包 `lee94-simple-points.bin`。算法质量更好，但更慢、资源占用更高。

### `Algorithms/AnalyzeSkeleton2.java`

对骨架结果做拓扑分析，识别：

- end points
- junction voxels
- slab voxels
- branch length
- tree count
- isolated junctions

这些结果会直接进入统计表和 overlay 绘制。

### `Algorithms/Particles.java`

负责二值图中的连通域识别、颗粒过滤、孔洞填充。`Max Hole Level`、`Min Boxness`、`Min Length : Area` 等 UI 参数主要影响这里。

### `Algorithms/Lacunarity2.java`

负责 lacunarity 分析。它基于多尺度 box counting，输出 E/F 两类 lacunarity 的 medial、mean 和 gradient。

### `Algorithms/VesselThickness.java`

基于距离变换估算血管厚度，最终结果进入 `Average Vessel Diameter`。

## 8. UI 架构

主 UI 类：

```text
source/AngioTool/AngioToolGui2.java
```

它负责展示和收集所有分析参数，包括：

- lacunarity 开关
- thickness 开关
- skeletonizer 类型
- max skeleton steps
- resize inputs
- measurement scale
- remove particles
- fill holes
- max hole level
- min boxness
- min length : area
- transform colors
- vessel diameters
- vessel intensity range
- overlay 样式

单图分析窗口：

```text
source/AngioTool/ImagingWindow.java
```

职责包括：

- 显示分析结果图。
- 支持缩放和平移。
- 参数变化时重新调度分析任务。
- 保存结果图。
- 保存单图统计结果到 Excel。

批处理窗口：

```text
source/AngioTool/BatchWindow.java
```

职责包括：

- 选择输入文件夹。
- 选择 Excel 输出文件。
- 设置结果图输出方式。
- 设置结果图格式。
- 设置 worker 数量。
- 展示批处理进度。
- 触发 `BatchProcessing.doBatchAnalysis()`。

帮助窗口：

```text
source/AngioTool/HelpWindow.java
source/manual.html
```

`manual.html` 是较完整的用户手册，也是理解功能语义的重要资料。

## 9. 批处理与并发模型

批处理核心类：

```text
source/AngioTool/BatchProcessing.java
```

工作方式：

1. 从 `BatchParameters.inputImagePaths` 读取一个或多个输入路径。
2. 递归扫描所有子目录。
3. 跳过 `.txt`、`.zip`、`.xls`、`.xlsx` 等非图像文件。
4. 跳过文件名尾部类似 `result`、`tubeness`、`thresholded`、`filtered`、`overlay` 的输出结果图，避免重复处理。
5. 为每张图创建 `AnalysisInput`。
6. 根据 `workerCount` 启动多个 worker。
7. 每个 worker 独立加载图像、执行 `Analyzer.analyze()`、可选保存结果图。
8. 主线程从队列取回结果，写入 Excel。
9. UI 通过 `IProgressToken` 回调更新进度。

并发资源主要依赖：

```text
Analyzer.threadPool
ImagingWindow.threadPool
Utils.ISliceRunner
各类 BufferPool
```

这说明项目比较重视性能，但也意味着后续修改时需要注意线程安全和内存释放。

## 10. 参数与偏好保存

分析参数类：

```text
source/AngioTool/AnalyzerParameters.java
```

批处理参数类：

```text
source/AngioTool/BatchParameters.java
```

偏好保存类：

```text
source/AngioTool/ATPreferences.java
```

偏好文件位置：

```text
%USERPROFILE%\AngioTool2\AT_Prefs.txt
%USERPROFILE%\AngioTool2\AT_BatchPrefs.txt
```

`ATPreferences` 使用反射遍历参数对象字段，并保存为文本。优点是新增字段时较容易自动纳入保存；缺点是字段改名、类型变更时需要注意兼容性。

## 11. 图像 I/O 与数据结构

核心图像类：

```text
source/Pixels/ArgbBuffer.java
source/Pixels/ImageFile.java
```

`ImageFile` 支持：

- TIFF / TIF
- NetPBM，例如 PPM/PGM
- Java ImageIO 支持的格式，如 PNG、JPG、GIF、BMP

内部分析基本围绕 ARGB int 数组、byte mask、float buffer 展开。大量使用 `Utils` 中的 BufferPool，例如 `IntBufferPool`、`FloatBufferPool`、`ByteBufferPool`，以减少大图分析时的内存分配和 GC 压力。

## 12. Excel 导出

Excel 模块：

```text
source/Xlsx/SpreadsheetWriter.java
source/Xlsx/XlsxReader.java
```

程序没有使用 Apache POI，而是自研生成 `.xlsx` ZIP 包和 XML 内容。导出字段包括：

- 图像名称、路径、宽高
- 日期、时间
- 分析参数
- explant area
- vessel area
- vessel percentage area
- junction count
- junction density
- total vessel length
- average vessel length
- endpoint count
- average vessel diameter
- E/F lacunarity 指标

批处理会把设置和结果写入 Excel；单图也可以把当前结果追加到表格中。

## 13. 构建与部署

主构建脚本：

```text
compile.py
```

流程：

1. 检查 `javac`。
2. 检查或生成 `lee94-simple-points.bin`。
3. 处理 `source/` 中的 `.tj` 模板，生成 Java 文件。
4. 收集 Java 源文件和 stub。
5. 使用 `javac --release=8` 编译到 `build/`。
6. 打包资源、class 文件、lookup table 和 `plugins.config` 到 `AngioTool2.jar`。

Windows EXE 构建：

```text
buildexe.py
launcher-win32.c
```

大致流程：

1. 读取 `AngioTool2.jar`。
2. 生成 C 头文件，把 JAR 嵌入为字节数组。
3. 使用 TCC 编译 32 位和 64 位启动器。
4. 启动器负责找到 Java、释放或加载 JAR、调用 JVM。

## 14. 后续修改建议

### 新增分析参数

修改 `AnalyzerParameters`，再修改 `AngioToolGui2.buildAnalyzerParamsFromUi()` 和 UI 控件初始化。如果需要保存，确认 `ATPreferences` 能正确处理该类型。

### 新增分析算法

在 `Algorithms/` 下新增类，然后在 `Analyzer.analyze()` 中插入对应阶段。建议尽量保持输入输出为明确的 buffer，避免直接依赖 Swing UI。

### 新增导出字段

修改 `Analyzer.Stats`，在 `Analyzer.analyze()` 中赋值，再修改：

- `createWriterWithNewSheet()`
- `writeResultToSheet()`
- `createBatchWriterWithNewSheet()`
- `writeBatchResultToSheet()`

### 新增 overlay 绘制

修改 `Analyzer.drawOverlay()`，并在 `AngioToolGui2` 增加颜色、尺寸、开关等 UI 参数。

### 新增批处理选项

修改 `BatchParameters`、`BatchWindow.buildBatchParamsFromUi()`、`BatchProcessing.doBatchAnalysis()`。

### 新增图像格式

优先在 `Pixels/ImageFile.java` 扩展读取/保存逻辑。如果 Java `ImageIO` 已支持，则可能只需要增加文件过滤器或格式处理。

## 15. 风险与注意点

### `Analyzer.analyze()` 是高耦合核心

目前分析管线集中在一个方法中，修改时容易影响多个指标。后续如果改动较大，建议先为每个阶段建立清晰输入输出说明。

### BufferPool 带来性能，也带来生命周期风险

很多数组需要手动 release。新增算法时必须明确谁申请、谁释放，避免内存泄漏或复用错误。

### 并发场景需要谨慎

单图预览、批处理、内部 slice runner 都会使用线程池。新增共享状态时要避免跨线程复用可变对象。

### Excel 写入是自研实现

对复杂 Excel 特性支持有限。如果后续要加入样式、公式、多语言、图表，当前实现可能需要扩展较多。

### 偏好保存依赖字段名和类型

改名或类型变更可能导致旧偏好文件解析失败。新增参数应考虑默认值和兼容策略。

### ImageJ 插件模式不是主路径

修改图像输入输出或 GUI 生命周期时，需要额外验证 ImageJ 插件模式是否仍可用。

### 构建流程非标准

没有 Maven/Gradle，依赖、资源、模板生成都由 `compile.py` 控制。后续引入外部库或自动化测试时，可能需要先整理构建系统。

## 16. 建议的下一步工作

### P0

- 确认当前项目能否在本机完整编译并运行。
- 建立一组固定测试图像，用于验证修改前后结果是否一致。
- 为 `Analyzer.analyze()` 写一份更细的阶段说明或函数级文档。

### P1

- 梳理 `Analyzer.Stats` 与 Excel 字段的对应关系，形成字段字典。
- 为批处理建立小规模回归测试：输入几张图片，检查输出 Excel 和结果图是否生成。
- 明确 `BufferPool` 使用规范，避免后续开发误用。

### P2

- 考虑把核心分析管线拆成更小的阶段函数，降低未来功能插入成本。
- 考虑引入标准构建工具或至少补充构建说明。
- 如果后续要做 UI 大改，再单独梳理 Swing 界面布局和事件流。

## 17. 快速定位索引

后续开发时可以优先查看以下文件：

| 目标 | 文件 |
| --- | --- |
| 程序入口 | `source/AngioTool/AngioTool.java` |
| ImageJ 插件入口 | `source/IJPlugin/ATPlugin.java` |
| 主界面 | `source/AngioTool/AngioToolGui2.java` |
| 单图分析窗口 | `source/AngioTool/ImagingWindow.java` |
| 批处理窗口 | `source/AngioTool/BatchWindow.java` |
| 批处理逻辑 | `source/AngioTool/BatchProcessing.java` |
| 核心分析管线 | `source/AngioTool/Analyzer.java` |
| 分析参数 | `source/AngioTool/AnalyzerParameters.java` |
| 批处理参数 | `source/AngioTool/BatchParameters.java` |
| 偏好保存 | `source/AngioTool/ATPreferences.java` |
| 血管增强 | `source/Algorithms/Tubeness.java` |
| 快速骨架化 | `source/Algorithms/Zha84.java` |
| 高质量骨架化 | `source/Algorithms/Lee94.java` |
| 骨架拓扑分析 | `source/Algorithms/AnalyzeSkeleton2.java` |
| 连通域和孔洞处理 | `source/Algorithms/Particles.java` |
| Lacunarity | `source/Algorithms/Lacunarity2.java` |
| 血管厚度 | `source/Algorithms/VesselThickness.java` |
| 图像 I/O | `source/Pixels/ImageFile.java` |
| Excel 导出 | `source/Xlsx/SpreadsheetWriter.java` |
| 构建 JAR | `compile.py` |
| 构建 EXE | `buildexe.py` |
