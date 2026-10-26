# AngioTool 中文完整使用指南

当前 2.3.2 提供完整参数、多输入、选择导出与 dry-run 的服务器 CLI：[命令行使用指南](CLI_CN.md)。无参数启动仍进入桌面，分析算法及下列桌面操作继承 2.2.3；当前运行包为 `AngioTool2.3.2.jar`。

> **2026-09-12：新增分形维数。** 分析完成后，Measurements 自动显示最终血管掩膜的 `Fractal dimension` 与拟合 R²；单图/批量 Excel 追加维数、R²、有效性、原因、盒子尺寸和计数六列。JSON 保存完整方法参数。无需启用 Lacunarity；小图或空图可能显示 N/A。方法、比较条件和解释边界见 [盒计数分形维数说明](FRACTAL_BOX_COUNT_CN.md)。

> **2026-09-07 UI 更新**：程序已改为单窗口工作台，左侧是 Analysis、Color、Overlay 参数页，右侧是图像标签与 Measurements 统计。Open image（原 View）打开新标签，不再弹出独立分析窗口；标签右侧 × 关闭对应图像。各图像共用分析参数，分别保留缩放、平移及导出路径。Reset defaults、Save image / Save XLSX 对应原恢复默认值和保存操作。下文“分析窗口”应理解为主窗口内的图像分析区。完整变更与验证见 [UI_MODERNIZATION_CN.md](UI_MODERNIZATION_CN.md)。

> 适用版本：AngioTool 2.2.3<br>
> 核对日期：2026-10-06<br>
> 适用范围：独立 JAR、批处理和实验性 ImageJ 插件；本轮只交付独立 JAR，EXE 构建脚本尚未适配版本化 JAR

AngioTool 2.0 用于从眼科、血管生成或其他二维血管图像中提取血管区域、骨架和拓扑统计。它可以交互分析单张图像，也可以用同一组参数递归处理一批图像。

当前 2.2.3 继承了 2.2.2 的 junction 修正，避免将 8 邻域转角的单像素内部连接误计为交汇点外部支路。受影响图像应使用 2.2.3 重新计算 junction 数量和 junction density；最新测试结果及边界见 [当前验证记录](VALIDATION_CN.md)。

本指南根据当前程序架构和实际代码路径编写。它说明软件如何操作、每个参数实际影响哪个分析阶段、结果文件如何解释，以及如何保证不同样本之间的结果可比较。

> [!IMPORTANT]
> AngioTool 是研究图像量化工具，不应单独用于临床诊断。任何研究结论都应结合人工质量检查、实验设计和独立统计分析。

## 直接使用二值掩膜（新增）

在 `Analysis → Vessel detection` 勾选 `Use binary mask directly`，再打开黑底白色前景掩膜。支持像素值为 0/255 或 0/1 的灰度掩膜；其他灰度或彩色像素会明确报错，不做隐式阈值处理。建议使用无损 PNG/TIFF，避免 JPEG 或抗锯齿产生中间灰度。

此模式跳过颜色变换、Tubeness、强度阈值、形态学闭运算、Advanced cleanup 和输入缩放。`Fill Holes` 和 `Remove Particles` 可选，默认关闭，与普通模式分别保存设置。两项关闭时直接保留输入前景。启用后按等效半径筛选，面积上限约为 `π × 半径²`；填洞只填封闭背景，不填与图像边界连通的区域。两项同时启用时先去颗粒再填洞，避免恢复已删除碎片。骨架化、测量单位缩放、统计和叠加仍正常执行。厚区域可取消 `Max Skeleton Steps` 上限，检查充分细化后的结果。

图像工具栏提供 `Original`（当前分析分辨率下的原图）、`Final mask`（清理后的最终掩膜）、`Skeleton overlay`（分析叠加图）。各标签独立记住视图；切换不重算、不重置缩放和平移，也不改变保存图像或 ImageJ 提交的叠加图内容。

模式作用于所有打开的图像和批处理，不会自动识别或反转黑色前景。取消勾选可恢复原增强模式与界面中的参数。XLSX 末尾记录 `Input Mode` 和二值清理开关/半径（批处理在 Settings 中记录）。JSON 的 `analysisParameters` 记录模式、实际缩放比例及 `binaryRemoveSmallParticles`、`binarySmallParticleRadius`、`binaryFillHoles`、`binaryHoleRadius`。旧参数保留用于记录，二值模式下被绕过的参数不参与运算。

## 目录

1. [软件能做什么](#1-软件能做什么)
2. [安装与启动](#2-安装与启动)
3. [界面概览](#3-界面概览)
4. [推荐的标准工作流程](#4-推荐的标准工作流程)
5. [单张图像分析](#5-单张图像分析)
6. [批量分析](#6-批量分析)
7. [分析参数完整说明](#7-分析参数完整说明)
8. [叠加显示参数](#8-叠加显示参数)
9. [统计指标解释](#9-统计指标解释)
10. [输出文件说明](#10-输出文件说明)
11. [参数调优方法](#11-参数调优方法)
12. [实验复现与质量控制](#12-实验复现与质量控制)
13. [常见问题与故障排查](#13-常见问题与故障排查)
14. [ImageJ 插件模式](#14-imagej-插件模式)
15. [从源码构建](#15-从源码构建)
16. [配置文件与相关资料](#16-配置文件与相关资料)

## 1. 软件能做什么

### 1.1 主要功能

- `View`：打开一张图像，实时预览分割与叠加结果，并保存结果图和统计表。
- `Batch`：选择一个或多个文件夹，递归分析其中的图像，并把结果集中写入一个 Excel 工作簿。
- `Help`：打开内置英文手册。
- ImageJ 插件：分析 ImageJ 当前图像，并可把叠加结果提交回当前图像。

### 1.2 分析管线

普通模式对每张图像执行以下流程：

```text
读取图像并按需缩放
→ 颜色变换，或提取全图平均最亮的 RGB 通道
→ 多尺度 Gaussian/Hessian Tubeness 血管增强
→ 0–255 强度范围二值化
→ 形态学最大值/最小值过滤
→ 孔洞、细长空隙和小颗粒处理
→ 生成最终二值血管 mask
→ 计算血管面积与前景凸包
→ Zha84 或 Lee94 骨架化
→ 骨架端点、junction、树和分支段分析
→ 可选血管厚度与 lacunarity 计算
→ 绘制叠加图并导出 XLSX、PNG 和 JSON
```

二值模式则使用：`原始尺寸输入 → 校验黑白像素 → 可选去小颗粒 → 可选封闭孔洞填充 → 最终 mask`，之后进入相同的骨架与指标计算阶段。两种清理均关闭时不改变输入前景。

图像分割的最终依据是二值血管 mask。骨架、分支点、长度、直径和 lacunarity 都是在这个 mask 或其骨架上计算，因此应先保证 mask 与真实血管一致，再解释后续统计值。

## 2. 安装与启动

### 2.1 运行要求

- Windows、Linux 或其他支持 Swing 的桌面系统。
- Java 8，或与 Java 8 字节码兼容的较新 Java 运行环境。
- 足够内存。高分辨率图像、多尺度增强和多个并行批处理任务会显著增加内存占用。

先在终端检查 Java：

```powershell
java -version
```

### 2.2 启动独立程序

推荐使用独立模式：

```powershell
java -jar .\AngioTool2.2.3.jar
```

在 Windows 中也可以双击 `AngioTool2.2.3.jar`，或右键选择 Java Runtime 打开。如果双击没有反应，优先用终端命令启动，以便看到错误信息。

### 2.3 支持的图像格式

文件选择器明确提供以下格式：

- JPEG：`.jpg`、`.jpeg`
- PNG：`.png`
- GIF：`.gif`
- BMP：`.bmp`
- TIFF：`.tif`、`.tiff`
- NetPBM：`.ppm`

底层读取器还会按文件签名尝试 TIFF 和其他 NetPBM 变体，但常规使用应优先选择上面的格式。TIFF 和 NetPBM 文件由程序自带读取器优先处理，其他格式通常通过 Java ImageIO 读取。

多页或多图像 TIFF/NetPBM 输入只读取第一张图像。

### 2.4 路径中的引号

当前版本会自动去除复制路径外层的以下引号：

- ASCII 双引号：`"C:\Images\sample.tif"`
- 中文弯引号：`“C:\Images\sample.tif”`
- 全角双引号：`＂C:\Images\sample.tif＂`

如果使用较旧版本并遇到路径无效，请手动移除这些外层引号。

## 3. 界面概览

### 3.1 主窗口

![AngioTool 主窗口](screenshots/ui-2.1/parameters.png)

主窗口包含顶部 Open image、Batch、帮助按钮，中央参数标签页，以及底部 Reset defaults、内存和参数状态。

1. `Analysis`：增强尺度、阈值、mask 清理、骨架、测量与输入缩放。低频清理参数放在 `Advanced cleanup` 中。
2. `Color`：颜色预处理、目标/排除颜色、饱和度、权重和亮度曲线。
3. `Overlay`：轮廓、骨架、分支点、凸包和底图模式。

较小窗口通过滚动访问下方参数。默认参数与 UI 改造前保持一致。

### 3.2 图像分析区

![AngioTool 单图分析窗口](screenshots/ui-2.1/analysis.png)

打开图像后，主窗口右侧的图像标签包含：

- 原图、最终掩膜、骨架叠加三种画布视图。
- 独立的统计侧栏，可通过 `Measurements` 控制显示，不遮挡图像。
- 缩放、平移按钮；也可使用鼠标滚轮缩放、拖动画布平移。
- `Save image`：保存叠加结果图及配套结构化文件，不受当前预览模式影响。
- `Save XLSX`：把当前统计写入 Excel。

缩放、平移、视图切换和 `Measurements` 只影响预览，不改变分析结果，也不会写入保存的结果图。

## 4. 推荐的标准工作流程

对于普通灰度/彩色图像，建议采用以下顺序；已有二值掩膜则使用本节末尾的分支：

1. 选择一张有代表性的图像。
2. 点击 `Reset defaults`，建立明确起点。
3. 在打开图像前确定是否启用 `Resize Inputs`。
4. 先调整颜色预处理，使目标血管在分析输入中突出。
5. 调整 `Vessel Diameters list` 和 `Vessel Intensity range`，使主要血管进入 mask。
6. 再使用孔洞、boxness、长宽形态和小颗粒参数清理 mask。
7. 检查骨架是否沿血管中心分布，junction 是否与肉眼分叉一致。
8. 最后设置测量比例和叠加显示样式。
9. 保存结果图，使程序同时生成 mask 和参数/指标 JSON。
10. 固定参数，用同一配置处理整组样本，不要逐图“美化”参数。

已有二值掩膜时，勾选 `Use binary mask directly`，先关闭两项清理，使用 `Final mask` 检查输入前景是否保留；确有碎片或小孔洞时，再逐项启用清理，并通过 `Original` 与 `Skeleton overlay` 对照。不要为了消除假环而直接调大填洞范围，否则真实网孔也可能被填充。同组数据应固定模式及清理设置。

如果不同实验组使用不同扫描分辨率、裁剪方式或颜色预处理，不能只根据最终数值直接比较；应先统一图像采集和分析条件。

## 5. 单张图像分析

### 5.1 打开图像

1. 在主窗口选择普通或二值模式；普通模式还需确定 `Resize Inputs`。
2. 点击 `Open image`。
3. 选择一张支持的图像。
4. 等待主窗口右侧的新图像标签完成分析。

程序会记住本次选择图像所在的文件夹；下次点击 `Open image` 时会从该文件夹打开。这个位置会保存在用户配置目录中，重启程序后仍然有效。

普通模式的 `Resize Inputs` 在读取图像时应用，修改后会重载图像；切入二值模式会恢复原始分辨率，切回普通模式恢复原缩放设置。插件模式会重新获取 ImageJ 图像副本。

### 5.2 参数更新与 JSON 配置恢复（2.2.3）

2.2.3 将右栏从 520 缩至 350 个缩放前像素，左栏保持 350。Measurements 是独立第三栏，与图片标签页顶部对齐，占满工作区高度，保留完整分组标题；切换图片显示对应图片的指标，取消 Measurements 勾选可收起这一栏。指标单列排列：图像与面积、长度与直径、网络拓扑、骨架质量、分形维数和 lacunarity；标签完整显示，数值加粗右对齐，E/F lacunarity 同行。配色统一为浅灰背景、白色面板和少量灰蓝色重点色。普通缩放 1480×900 内容区及 150% 缩放 2559×1495 内容区离屏预览均无上下滚动条，包含展开后的高级清理。更小窗口保留滚动兜底，可拖动分隔线调整宽度。正式版优先保留指标栏宽度；图像栏较窄时视图按钮分为两行，Measurements 开关单独显示。折叠/展开高级清理仅影响布局，不应用参数。

图像打开后，可以继续修改主窗口中的大多数参数：

- `Auto update` 默认开启，可自行开关。开启时，勾选选项、调整数值和选颜色会更新所有已打开图片；文本框通常在失去焦点或按 Enter 后触发。
- 关闭 `Auto update` 后，修改参数只更新控件；点击 `Apply parameters` 才应用到所有已打开图片。重新开启自动更新也会应用当前参数。
- 分析参数改变后重新分析；仅改变叠加颜色、尺寸、可见性或底图模式时，只重绘已有结果。
- 分析运行期间，分析窗口的保存和导航控件会暂时禁用。
- 分析中再次应用参数时，只保留最新待应用的一套，当前任务完成后接着处理。关闭自动更新不取消已经开始或已经排队的分析。
- 视图切换、缩放和平移不触发重新分析。`Open image` 仍会立即分析新图片，Batch 启动时读取当前界面参数；自动更新开关只控制已打开图片的参数编辑响应。

点击参数页下方的 `Load parameters...`，选择任意位置的 AngioTool `overlay-data.json`，恢复分析、颜色预处理、二值清理、骨架剪枝/合并、测量比例尺和叠加颜色/线宽/可见性等设置。

加载 JSON 本身只填入控件，不重绘或重新分析现有图片，也不改变 `Auto update` 的选择。要导入后继续调整、最后手动应用，请自行关闭自动更新，再点击 `Apply parameters`。保持自动更新开启时，导入后的普通参数编辑仍会更新图片。

导入先解析和校验整份文件，再填入界面。格式或参数错误时原设置保持不变；文件中的图像路径、测量结果和点坐标不会作为配置加载。`normalized-data.json` 没有完整参数，不能用来恢复配置。

旧文件缺失字段时使用代码默认值；缺失剪枝/合并开关时保持关闭。优先读取 `uiParameters`，兼容旧 `analysisParameters` / `overlayParameters`，剪枝读取 `skeletonProcessing`。2.2.3 加载成功后仅更新状态栏，悬停可查看来源版本，不再弹出缺项或版本差异提示；无效文件仍显示错误弹窗。恢复同一套参数不保证跨版本结果完全相同。

`Reset defaults` 直接恢复默认参数，不再弹出确认框。自动更新开启时应用到已打开图像；关闭时等待点击 `Apply parameters`。

尚未应用新参数时，图像和统计仍是原分析结果，保存 JSON/XLSX 也记录生成该结果时实际使用的参数。图像缩放、平移、导出路径以及批处理文件夹设置不随配置恢复。自动更新开关本身不属于结果配置，重启时默认开启。

建议每次只修改一类参数，并观察：

- 黄色轮廓是否贴合真实血管边缘。
- 红色骨架是否位于血管中心。
- 蓝色分支点是否对应真实分叉。
- 浅蓝色凸包是否合理包围前景，而没有被孤立噪声大幅拉伸。

### 5.3 查看结果

| 视图 | 显示内容 |
| --- | --- |
| `Original` | 当前分析尺寸下的输入像素，普通模式中可能已缩放 |
| `Final mask` | 清理后实际用于面积和骨架计算的黑白前景 |
| `Skeleton overlay` | 按 Overlay 设置绘制的骨架、轮廓等叠加结果 |

![二值清理与掩膜对照](screenshots/ui-2.1/binary-cleanup-mask.png)

视图状态属于各自的图像标签，不会因切换标签丢失；仅在当前运行期间保留，不作为分析参数写入 JSON。`Save image` 始终保存叠加图，同时导出最终掩膜 PNG。

- 鼠标滚轮或 `+`、`−` 按钮缩放。
- 拖动画布或使用方向按钮平移。
- `Measurements` 控制独立统计侧栏，适应窗口和 `1:1` 控制图像查看比例。

画布显示的百分比和统计值是快速预览。正式分析应以保存的 XLSX 和 JSON 为准，并结合二值 mask 做质量检查。

### 5.4 保存结果图

1. 如果路径文本框为空，点击 `Save result image` 下方的文件夹按钮会在原图目录中自动创建 `results` 文件夹，并按 `ana_原文件名` 保存。例如原图为 `sample.tif`，结果图为 `results\ana_sample.tif`。
2. 如需指定其他位置或格式，在文本框中输入完整文件名后再点击保存按钮。文件名必须包含扩展名，例如 `sample-result.png`。
3. 推荐科研保存使用 PNG 或 TIFF；JPEG 是有损格式，更适合展示而不是后续像素分析。无扩展名的源图会默认输出为 PNG。

如果保存为：

```text
sample-result.png
```

程序还会在同一目录生成：

```text
sample-result vessel-mask.png
sample-result overlay-data.json
```

### 5.5 保存统计表

1. 点击 `Save stats to spreadsheet` 下方的 Excel 按钮。
2. 选择新文件，或选择已有 `.xlsx` 文件进行追加。
3. 如果没有扩展名，程序会自动补充 `.xlsx`。

首次保存会创建结果表头并写入当前结果；在同一个分析窗口中再次保存会继续向工作表追加结果。

不要在 Excel 正占用目标文件时写入。对重要工作簿，仍建议在批量追加前自行保存一份外部备份。

## 6. 批量分析

### 6.1 开始前

批处理会在点击 `Run` 时读取主窗口的当前分析参数（包括输入模式及对应清理设置），并把同一组参数应用到所有输入图像。建议先用 `Open image` 在代表性图像上验证 mask、骨架和指标，再运行批处理。不要把灰度/彩色图混入直接二值模式的输入目录。

### 6.2 设置批处理

1. 点击主窗口的 `Batch`。
2. 点击 `Select input folders`，选择一个或多个文件夹。
3. 默认勾选 `Create spreadsheet in first input folder`：程序在第一个输入文件夹中自动创建工作簿，默认名为 `result_<输入文件夹末级名称>.xlsx`，可直接编辑文件名。取消勾选后，可点击 `Excel spreadsheet` 选择其他路径或已有 XLSX 文件。多输入文件夹仍汇总到第一输入文件夹中的同一工作簿。
4. 选择结果图模式：
   - `No output`：只写 Excel，不保存结果图、mask 或 JSON。
   - `Create results folders next to inputs`：在各输入图像所在目录创建 `results` 子目录，并使用与单图分析相同的命名规则保存结果。
   - `Save result images to`：把所有结果写入指定输出根目录。
5. 选择 `Save result images to` 时输入结果图格式。可靠选择为 `tif`、`tiff`、`png`、`jpg`、`jpeg`、`gif`、`bmp`、`ppm`；使用 `results` 子目录时沿用原图扩展名，无扩展名源图默认输出为 PNG。
6. 按需启用 `Override job count`。
7. 点击 `Run`。

### 6.3 输入目录扫描规则

程序会递归扫描每个所选文件夹及其子文件夹。需要注意：

- 扫描器是宽松的；除 `.txt`、`.zip`、`.xls` 和 `.xlsx` 外，其他文件（包括无扩展名文件）都可能被尝试作为图像读取。
- 最好让输入目录只包含受支持的图像，避免把 PDF、JSON、数据库或其他非图像文件混在其中。
- 程序会跳过名为 `results` 的子目录；文件名最后一个空格后的词如果是 `result`、`tubeness`、`thresholded`、`filtered`、`overlay`、`vessel-mask` 或 `overlay-data`，也会跳过，防止重复分析标准输出文件。
- 自定义命名的派生图如果没有这些后缀，仍可能被再次分析。

### 6.4 输出目录结构

选择 `Create results folders next to inputs` 时，每张图像的输出写入其所在目录的 `results` 子目录，结果图命名为 `ana_原文件名`。无扩展名源图默认使用 `.png`。

选择单独输出目录时，每个源目录会映射到输出根目录中的唯一子目录。若不同源目录同名，程序会附加父目录名或数字消除冲突。该布局用于避免重名，但不保证完整复制原始多层目录结构。

假设输入为：

```text
sample.tif
```

使用 `results` 子目录模式时，批处理生成：

```text
results\ana_sample.tif
results\ana_sample vessel-mask.png
results\ana_sample overlay-data.json
```

选择指定输出目录时仍使用原批处理命名，例如输出格式为 PNG 时生成 `sample.tif result.png`、`sample.tif vessel-mask.png` 和 `sample.tif overlay-data.json`。

### 6.5 并发任务数

未勾选 `Override job count` 时，程序默认使用可用逻辑处理器数量的约 75%。界面同时显示：

- 等于处理器数量：偏向最高吞吐量。
- 约 75%：在吞吐量和系统响应之间平衡。

不要简单认为任务越多越快。每个任务还会使用内部图像处理线程和多个大缓冲区。出现内存不足、系统卡顿或磁盘争用时，应降低任务数。

### 6.6 运行、取消和错误

- 进度区显示扫描状态、总体进度和当前文件。
- `Cancel` 会请求停止当前批处理；已经开始的个别计算可能需要完成当前阶段后才退出。
- 无法读取的文件可能计为无效图像。
- 严重的读取、分析或写入错误会写入错误行并可能终止本批任务。
- 批处理运行期间仍可打开单图窗口，但两者共享线程和内存资源，不建议在资源紧张时同时执行。

## 7. 分析参数完整说明

### 7.1 当前代码默认值

| 参数 | 当前默认状态/值 |
| --- | --- |
| Lacunarity | 开启 |
| Thickness | 开启 |
| Skeletonizer | Thorough (Lee94) |
| Max Skeleton Steps | 开启，25 |
| Prune terminal branches | 开启，3 px |
| Merge nearby junctions | 开启，3 px |
| Resize Inputs | 关闭，字段值 100% |
| Measurement Scale | 关闭，字段值 100% |
| Export normalized data | 关闭 |
| Fill Holes | 关闭 |
| Remove Particles | 关闭 |
| Use binary mask directly | 关闭 |
| 二值模式 Fill Holes | 关闭，半径 3 px |
| 二值模式 Remove Particles | 关闭，半径 15 px |
| Max Hole Level | 关闭，字段值 150% |
| Min Boxness | 开启，9.375% |
| Min Length : Area | 开启，`1 : 16` |
| Transform Colors | 关闭 |
| Vessel Diameters list | `12` |
| Vessel Intensity range | `15` 到 `255` |
| Outline | 开启，黄色，1 px |
| Skeleton | 开启，红色，2 px |
| Branches | 开启，蓝色，4 px |
| Convex Hull | 开启，浅蓝色，1 px |
| 输出底图 | Convert to Grayscale |

点击 `Reset To Defaults` 会恢复这些代码默认值，不一定与旧截图或旧偏好文件中的值相同。

### 7.2 Lacunarity

用途：衡量血管分布中的空隙性或不均匀程度。

程序报告两组结果：

- E lacunarity：包含空采样框。
- F lacunarity：排除没有血管像素的采样框。

每组包含 medial、mean 和对数回归 gradient。较高值通常表示更强的空间异质性或更多非血管空隙，但应只在相同图像尺寸、分辨率、裁剪和分割参数下比较。

关闭后可减少计算时间，但对应导出值不应再用于解释。

### 7.3 Thickness

用途：估计血管直径。

程序在骨架 slab 点上计算到最近背景的距离，把它视为局部半径，再取中位数并乘以 2。界面和 XLSX 使用名称 `Average Vessel Diameter`，但整图统计实际上是“骨架内部点局部直径的中位数”，不是算术平均。

开启 Thickness 后，JSON 还会为可用的每个骨架分支段导出直径。关闭可降低计算量。

### 7.4 Skeletonizer

| 选项 | 特点 | 适用情况 |
| --- | --- | --- |
| Fast (Zha84) | 较快、资源占用较低，骨架质量可能较粗糙 | 大批量预筛、性能优先 |
| Thorough (Lee94) | 更彻底、速度较慢、资源需求更高 | 最终研究分析、骨架质量优先 |

在同一实验中不要混用两种骨架算法，否则长度、端点和 junction 可能不可直接比较。

### 7.5 Max Skeleton Steps

限制骨架化迭代次数。每次迭代会继续削薄前景边缘。

- 较小：更快，但粗血管可能没有完全缩到中心线。
- 较大或关闭上限：骨架更充分，但耗时增加。

默认 25 是性能和完成度之间的折中。若红色骨架明显偏粗或偏离中心，可提高上限并复核。

### 7.6 邻近 junction 合并

`Merge nearby junctions` 将由短骨架段直接连接的 junction 区域合并为一个统计点，默认阈值为 3 px。阈值按分析分辨率下的骨架路径长度计算，包含对角距离。空间位置接近但骨架不连通的 junction 不会合并；该功能不删除 mask 或骨架像素。建议用 0/关闭、2、3、5 px 对照，并确认真实的相邻分叉没有被过度合并。

### 7.7 Resize Inputs

仅普通模式可用；二值模式禁用，实际缩放因子固定为 1。普通模式在分析前改变图像宽高：

- 50%：宽高各变为一半，总像素约为原来的 25%。
- 200%：宽高各变为两倍，总像素约为原来的 4 倍。

缩放会发生在分割前，因此不仅改变数值单位，还可能改变 Tubeness 响应、颗粒大小、骨架结构和最终检测结果。

程序不会因为 Resize Inputs 自动修正 Measurement Scale。如果原图标定为 `u` 单位/原始像素，缩放比例为 `r`，则分析图像每像素对应的标度通常应按 `u / r` 重新计算。例如原图 1 单位/像素、缩放到 50% 时，新图 1 像素约对应原图 2 单位。

### 7.8 Measurement Scale

该百分比最终转换为线性乘数 `s`：

- 长度和直径乘以 `s`。
- 面积乘以 `s²`。
- 面积百分比不变。

软件不在结果列中强制记录具体物理单位。100% 表示 `s = 1`，不是自动证明“1 像素 = 1 mm”。应根据显微镜、扫描仪或标尺自行标定，并在实验记录中写明单位。

### 7.9 Export normalized data

开启后会在保留原始输出的同时，额外生成图像相对指标。归一化基准是实际参与分析的图像：面积基准为 `width × height`，长度基准为 `sqrt(width × height)`。面积除以面积基准，长度、直径和骨架像素长度除以长度基准；junction、endpoint、segment 等拓扑计数保持原值。已有 junction density 乘以图像面积，使其成为无量纲结果。面积百分比、分形维数和 lacunarity 本身无量纲，保持原值。

单图保存结果图时额外生成 `normalized-data.json`；批处理在原 XLSX 同一目录额外生成文件名带 `_nor` 的工作簿，例如 `results.xlsx` 对应 `results_nor.xlsx`。开关默认关闭；关闭时不会生成这些附加文件。

该功能只归一化输出，不会自动缩放 Tubeness、形态学清理、骨架剪枝或 junction 合并参数，因此“无量纲”不代表不同分辨率的完整分析必然相同。裁剪、加边框、改变宽高比也会改变以整图为基准的结果。

### 7.10 Fill Holes

填充小型黑色背景连通区域。输入值在代码中作为类似半径的尺寸使用，实际面积阈值约为：

```text
最大面积 ≈ π × 输入值²
```

值越大，越大的孔洞会被填充。过大可能把真实无血管区域误认为血管。二值模式使用独立设置，默认关闭，只填不与图像边界连通的背景区域；该保护不能区分“错误孔洞”和真实血管环内部。

### 7.11 Remove Particles

移除小型白色前景连通区域，面积阈值同样约为 `π × 输入值²`。

值越大，删除的孤立前景越多。它适合去除噪声，但也可能删除真实短小血管或断裂后的血管片段。二值模式使用独立设置，默认关闭；与填洞同时开启时，先去颗粒，再重建连通域并填洞。

### 7.12 Max Hole Level

使用原始图像亮度判断黑色空隙是否应被重新填成血管。当前代码在空隙平均亮度达到“已检测白色前景平均亮度 × 指定比例”时填充。

- 降低百分比：更多空隙满足条件，填充更积极。
- 提高百分比：条件更严格，填充更少。

它依赖图像颜色和亮度方向。对暗血管图像，应先正确设置 `Transform Colors`，再调这个参数。

### 7.13 Min Boxness

程序根据空隙占据边界框的比例和边界框长宽比计算 boxness。接近紧凑正方形时值较高，细长或稀疏空隙值较低。

当空隙的 boxness 低于设定阈值时，程序会填充该空隙：

- 提高阈值：更多低 boxness 空隙会被填充。
- 降低阈值：只填充更细长或更稀疏的空隙。

### 7.14 Min Length : Area

代码用连通空隙面积除以其最大跨度，得到近似面积/长度指标。当这个值低于设定阈值时填充空隙。

它主要针对细长裂缝或血管内部狭长断口：

- 提高阈值：会填充更多细长区域。
- 降低阈值：更加保守。

### 7.15 Transform Colors

关闭时，程序计算整张图像平均最亮的 RGB 通道，并只用该通道进入 Tubeness。

开启时，程序根据颜色和亮度把彩色图像重新映射成单通道强度图。适合：

- 目标血管由特定颜色表示。
- 血管比背景更暗。
- 彩色 OCT 或伪彩色图像中，简单最亮通道不能分离目标。

子参数：

| 参数 | 含义 |
| --- | --- |
| Target | 希望增强的目标颜色；必须有明确色相，不能是灰色 |
| Off | 希望抑制的颜色；必须与 Target 色相不同 |
| Saturation | 调整色相差异对映射结果的影响，范围 0–100% |
| Hue : Brightness | 色相得分和亮度映射的相对权重；两者不能同时为 0 |
| Brightness Graph | 由 `(输入%, 输出%)` 点组成的分段映射曲线；各输入 X 坐标必须唯一 |

默认颜色为 Target 红色 `FF0000`、Off 橙色 `FF9900`，饱和度 100%，Hue : Brightness 为 1 : 1，亮度图为 `(0,0), (100,100)`。

### 7.16 Vessel Diameters list

这个名称容易误解。它是 Tubeness 多尺度 Gaussian/Hessian 增强使用的 `sigma`/尺度列表，不是程序测得的血管直径。

- 至少输入一个正数。
- 多个值可用空格或逗号分隔，例如 `4, 7, 12`。
- 每个尺度分别增强，最后按像素保留最强响应。
- 增加尺度数量会扩大可检测结构范围，但增加计算成本，也可能增强更多非目标结构。

当目标包含粗细不同的血管时，可加入多个代表性尺度；不要仅因为结果“更满”就无限增加尺度。

### 7.17 Vessel Intensity range

Tubeness 输出会归一化到 0–255，然后按此范围二值化：

- 范围内像素变为血管前景。
- 范围外像素变为背景。

默认是 15–255。

- 降低最小值：包含更弱响应，灵敏度上升，但噪声也会增加。
- 提高最小值：保留更强响应，结果更保守。
- 最大值通常保持 255；特殊情况下可排除过强响应。

两个值都必须位于 0–255，且最小值不能大于最大值。

## 8. 叠加显示参数

叠加参数改变结果图的显示，不改变二值 mask 和定量统计。

| 参数 | 显示内容 | 当前默认样式 |
| --- | --- | --- |
| Outline | 血管前景边界 | 黄色，1 px |
| Skeleton | 血管中心骨架 | 红色，2 px |
| Branches | 合并后的 junction/分支点 | 蓝色，4 px |
| Convex Hull | 前景凸包外边界 | 浅蓝色，1 px |

每项都可单独启用、调整颜色和尺寸。

底图模式：

- `Keep Original Colors`：在原始彩色图上绘制。
- `Isolate Brightest Channel`：显示自动选择的最亮通道。
- `Convert to Grayscale`：显示颜色变换结果；未启用颜色变换时显示默认灰度表示。

为了人工检查分割，建议使用能让轮廓和骨架清晰可见的底图。用于展示时可以调整颜色，但科研结果比较应以 mask 和数据文件为主。

## 9. 统计指标解释

| 界面/XLSX 指标 | 代码中的实际含义 |
| --- | --- |
| Width / Height | 分析时图像尺寸；启用 Resize Inputs 后是缩放后的尺寸 |
| Explant Area / Allantois Area | 最终血管前景凸包的鞋带公式面积，再乘 `s²` |
| Vessels Area | 最终二值 mask 的前景像素数量，再乘 `s²` |
| Vessels Percentage Area | `Vessels Area ÷ Explant Area × 100%` |
| Total Number of Junctions | 骨架分析后合并得到的孤立 junction 簇数量 |
| Junctions Density | junction 数量除以缩放后的凸包面积 |
| Total Vessels Length | 各骨架连通树中分支长度之和，再乘 `s` |
| Average Vessels Length | 当前实现为总长度除以骨架连通树数量，不是除以分支段数量 |
| Total Number of End Points | 骨架的 8 邻域端点数量 |
| Average Vessel Diameter | slab 点最近背景距离中位数的两倍，再乘 `s` |
| E/F Lacunarity | 二值 mask 的多尺度滑动框 lacunarity 统计 |

### 9.1 分支段与连通树

- 连通树：一整块相互连接的骨架网络。
- 分支段：端点、junction 或连接节点之间的一条骨架边。

XLSX 中的 `Average Vessels Length` 是“每棵连通树平均总长度”。如果需要真正的平均分支段长度，应读取 JSON 的 `metrics.averageSegmentLength` 或对 `vesselSegments[].length` 求平均。

### 9.2 面积和比例的边界情况

如果没有检测到有效前景，凸包面积可能为 0，面积百分比或 junction density 可能出现 0、NaN 或 Infinity。此类结果应视为分割失败或空样本，不能直接纳入统计。

### 9.3 单位

未启用 Measurement Scale 时：

- 长度和直径是像素单位。
- 面积是像素面积。

启用后，单位由用户标定决定。软件不会在 XLSX 列名中自动写出 µm、mm 等单位，因此应在实验方案或数据字典中额外记录。

## 10. 输出文件说明

### 10.1 结果叠加图

结果图是原图或灰度底图加上启用的 Outline、Skeleton、Branches 和 Convex Hull。画布缩放、平移和 `Display Stats` 文本不会进入保存文件。

### 10.2 `vessel-mask.png`

这是与分析尺寸相同的无损二值 PNG：

- 白色 `255,255,255`：被识别为血管。
- 黑色 `0,0,0`：背景或非血管。

它适合逐像素面积、连通域、形态学或与其他软件联用。它不是概率图，也不包含骨架、分支点或凸包元数据。

### 10.3 `overlay-data.json`

JSON 使用 UTF-8，主要包含：

- `image`：文件、尺寸、最亮通道和导出时间。
- `uiParameters`：按界面分组的完整参数快照。
- `analysisParameters`、`overlayParameters`：兼容旧脚本的参数字段。
- `metrics`：整图统计，包括 tree、segment 和平均分支段指标。
- `convexHull`：凸包点。
- `skeleton`：骨架 slab 点。
- `branchPoints`、`junctionVoxels`、`removedJunctions`：junction 相关点集。
- `vesselSegments`：每条骨架边的树编号、长度、方向角和可选直径。

JSON 不包含逐像素 mask；需要区域数据时应同时读取配套 PNG。

### 10.4 XLSX

单图工作表在每一行同时记录分析参数和结果。批处理工作表先写一组 `Settings`，再写所有图像的 `Results`。

主要字段包括：

- 图像名称、路径、宽高、日期和时间。
- 阈值、增强尺度、清理参数、骨架算法、缩放和颜色变换参数。
- 面积、百分比、junction、长度、端点、直径和 lacunarity。

XLSX 由项目自研写入器生成，不支持所有复杂 Excel 特性。向已有工作簿追加前，建议先关闭 Excel，并对重要文件做独立备份。

当前写入器固定使用 UTF-8 生成 XML，跳过 `NaN/Infinity` 非有限数值，并令工作表维度与实际行数一致，以避免 Excel 打开时提示修复。若仍出现修复提示，请保留 Excel 生成的修复日志和原始 XLSX 以便定位具体部件。

开启 `Export normalized data` 时，单图结果图旁会增加 `normalized-data.json`；批处理工作簿旁会增加 `_nor.xlsx`。附加文件只保存归一化指标和必要元数据，原始 JSON/XLSX 不被替换。

### 10.5 选择哪个文件

| 需求 | 首选文件 |
| --- | --- |
| 看人工叠加效果 | 结果图 |
| 判断某个像素是否为血管 | `vessel-mask.png` |
| 获取整图统计 | XLSX 或 JSON `metrics` |
| 获取每段血管长度、角度和直径 | JSON `vesselSegments` |
| 复现实验参数 | JSON `uiParameters` |
| 获取骨架、凸包和 junction 坐标 | JSON 点集字段 |

更完整的 JSON 字段定义见 [OVERLAY_EXPORT_FORMAT_CN.md](OVERLAY_EXPORT_FORMAT_CN.md)。

## 11. 参数调优方法

### 11.1 先看 mask，不要只看统计数字

参数调优的目标不是得到“更漂亮”或“更大”的数字，而是让白色 mask 与人工判断的真实血管区域一致。每次修改后重点检查：

- 细血管是否被漏掉。
- 背景纹理是否被误识别。
- 粗血管内部是否出现空洞。
- 邻近血管是否被错误粘连。
- 噪声是否把凸包拉到远离样本的位置。

### 11.2 推荐调参顺序

1. `Resize Inputs`：先固定分析分辨率。
2. `Transform Colors`：解决颜色方向和目标色问题。
3. `Vessel Diameters list`：选择要增强的结构尺度。
4. `Vessel Intensity range`：确定主要前景。
5. `Max Hole Level`、`Min Boxness`、`Min Length : Area`：修补狭缝和内部断裂。
6. `Fill Holes`、`Remove Particles`：处理剩余小区域。
7. Skeletonizer 和 Max Skeleton Steps：优化拓扑。
8. Measurement Scale：在像素结果稳定后应用物理标定。
9. Overlay：只调整显示。

### 11.3 常见现象与调整方向

| 现象 | 优先检查 |
| --- | --- |
| 大量背景成为血管 | 提高最小强度；减少不合适的尺度；检查颜色变换 |
| 细血管缺失 | 增加较小增强尺度；适当降低最小强度；减少颗粒移除 |
| 粗血管内部破碎 | 增加合适的大尺度；检查 Max Hole Level、Boxness 和 Length : Area |
| 独立小点过多 | 启用 Remove Particles；提高最小强度 |
| 不同颜色区域识别不一致 | 启用 Transform Colors，调整 Target/Off 和 Hue : Brightness |
| 骨架偏粗或断裂 | 提高 Max Skeleton Steps；尝试 Thorough (Lee94) |
| junction 过多 | 先减少 mask 噪声和粘连，再检查骨架算法；对由极短骨架段连接的节点，可启用 Merge nearby junctions |
| 处理过慢 | 缩小输入、减少尺度、使用 Fast、限制骨架步数、关闭可选统计 |

## 12. 实验复现与质量控制

### 12.1 固定参数

同一批实验应固定：

- 图像采集设备、倍率、分辨率和裁剪方式。
- Resize Inputs 和 Measurement Scale。
- 颜色变换及其 Target/Off、饱和度、权重和亮度曲线。
- Vessel Diameters 和 Intensity。
- 所有孔洞/颗粒参数。
- 骨架算法和迭代上限。
- 软件版本。

### 12.2 保存可复现证据

每批至少保留：

- 原始图像，不覆盖。
- 结果图。
- `vessel-mask.png`。
- `overlay-data.json`。
- XLSX 汇总。
- 对失败或人工排除样本的原因记录。

JSON 的 `uiParameters` 是最完整的参数快照；仅保存截图不足以可靠复现数值。

### 12.3 建立人工质量检查

建议从每批随机抽取样本，人工检查：

- mask 与原图的重合。
- 骨架中心性和连续性。
- junction 是否为真实分叉。
- 凸包是否被噪声影响。
- 极端值对应的图像是否异常。

不要只因为批处理成功结束就认为所有结果有效。

### 12.4 比较参数方案

比较不同参数时，应使用同一组代表性图像，并分别输出到不同目录。不要覆盖原结果。可以比较：

- mask 的人工一致性。
- 血管面积、长度、junction 的稳定性。
- 对背景噪声和弱血管的敏感性。
- 运行时间和内存占用。

确定方案后再运行正式数据集。

## 13. 常见问题与故障排查

### 13.1 JAR 无法启动

1. 在终端运行 `java -version`。
2. 使用 `java -jar .\AngioTool2.2.3.jar` 启动并查看报错。
3. 确认下载的是完整 JAR，不是网页或损坏文件。
4. 确认 Java 架构和系统环境正常。

### 13.2 图像无法读取

- 使用 PNG、TIFF、JPEG、BMP、GIF 或 PPM 重新导出测试。
- 检查文件是否损坏、是否有读取权限。
- 多页文件只处理第一页；必要时先拆分。
- 较旧版本遇到带引号路径时，手动移除外层引号。

### 13.3 Batch 显示 `No images were found`

- 确认至少选择了一个真实存在的文件夹。
- 检查访问权限。
- 确认文件名没有被识别为标准输出后缀。
- 先用 `View` 验证其中一张图能否读取。

### 13.4 Batch 尝试读取非图像文件

批处理扫描器会尝试很多带扩展名文件。把输入图像放入单独目录，或移走 PDF、JSON、CSV、数据库和其他非图像文件。

### 13.5 内存不足或系统卡顿

按以下顺序降低资源消耗：

1. 降低批处理 job count。
2. 降低 Resize Inputs 百分比。
3. 减少 Vessel Diameters 的尺度数量。
4. 使用 Fast (Zha84)。
5. 保留 Max Skeleton Steps 上限。
6. 按需关闭 Thickness 或 Lacunarity。
7. 分批处理文件。

### 13.6 分析非常慢

高分辨率、多个大 sigma、Lee94、较高骨架步数、Thickness 和 Lacunarity 都会增加耗时。先在单图上评估速度，再决定批量并发数。

### 13.7 结果图正确，但统计异常

- 检查凸包是否被孤立噪声拉伸。
- 检查 Measurement Scale 和 Resize Inputs 的组合。
- 检查空 mask 导致的除零、NaN 或 Infinity。
- 注意 `Average Vessels Length` 是按连通树平均，不是按分支段平均。
- 注意 `Average Vessel Diameter` 实际为中位局部直径。

### 13.8 无法写入 Excel

- 关闭正在打开目标文件的 Excel。
- 检查目录写权限和磁盘空间。
- 尝试写入新的 `.xlsx` 文件。
- 避免使用依赖复杂公式、宏、图表或外部链接的重要工作簿作为直接追加目标。

### 13.9 恢复默认参数

先点击 `Reset To Defaults`。如果偏好文件损坏，可关闭程序后把以下文件改名备份，再重新启动：

```text
%USERPROFILE%\AngioTool2\AT_Prefs.txt
%USERPROFILE%\AngioTool2\AT_BatchPrefs.txt
```

不要直接覆盖原始偏好文件；改名后仍可恢复。

## 14. ImageJ 插件模式

ImageJ 支持是实验性的，独立 JAR 仍是推荐路径。

### 14.1 安装与打开

1. 把包含 `plugins.config` 的 `AngioTool2.2.3.jar` 放入 ImageJ/Fiji 的插件目录。
2. 重启 ImageJ/Fiji。
3. 打开一张图像。
4. 从插件菜单选择 `AngioTool2`。

### 14.2 插件行为

- 插件复制 ImageJ 当前图像到 AngioTool 内部 ARGB 缓冲区。
- 插件模式主按钮显示 `Submit`，而不是 `View`。
- `Batch` 按钮不会显示。
- 调整参数后，点击 `Submit` 会把当前叠加结果写回 ImageJ 当前图像。

### 14.3 注意事项

- 提交会修改当前 ImageJ 图像像素；操作前先复制原图。
- 8/16/32 位图像会转换到内部显示/分析表示，可能损失动态范围。
- 如果启用了 Resize Inputs，提交回原图时尺寸不一致区域只按可重合范围复制。
- 插件生命周期、批处理和复杂 ImageJ 图像类型没有独立模式成熟。

## 15. 从源码构建

普通用户不需要构建源码；直接运行发行版 JAR 即可。

### 15.1 要求

- Python 3。
- JDK，且 `java`、`javac` 位于 `PATH`。
- Java 编译器支持 `--release=8`。

### 15.2 当前发布产物与版本追踪

当前发布版为 2.2.3。`compile.py` 会生成 `AngioTool2.2.3.jar`；源码和发布 JAR 应按项目约定一起纳入 Git 版本管理。回退使用 Git 历史，不要求为每次编译创建时间戳 JAR 备份。

### 15.3 编译

在项目根目录运行：

```powershell
python .\compile.py
```

脚本会：

1. 检查或生成 Lee94 lookup table。
2. 展开 `source/Utils/*.tj` 模板。
3. 删除并重建 `build/`。
4. 使用 `javac --release=8` 编译非 deprecated 源码和 ImageJ stubs。
5. 打包资源、class、lookup table 和 `plugins.config` 到 `AngioTool2.2.3.jar`。

编译成功后启动：

```powershell
java -jar .\AngioTool2.2.3.jar
```

项目没有 JUnit 自动测试套件。最低验证应包括：

- 编译成功。
- JAR 可以启动。
- 代表性单图分析成功。
- 结果图、mask、JSON 和 XLSX 均能生成。
- 批处理和 ImageJ 相关修改还需分别做针对性验证。

## 16. 配置文件与相关资料

### 16.1 用户配置

程序在用户主目录保存偏好：

```text
%USERPROFILE%\AngioTool2\AT_Prefs.txt
%USERPROFILE%\AngioTool2\AT_BatchPrefs.txt
```

关闭主窗口或启动批处理时会保存相关参数。字段名或类型在新版本中变化时，旧偏好可能无法完全兼容；遇到配置解析错误时，可按前述方式改名备份并重新生成。

### 16.2 项目资料

- [readme.md](readme.md)：项目简介和发行版入口。
- [source/manual.html](source/manual.html)：程序内置英文手册。
- [PROGRAM_REPORT_CN.md](PROGRAM_REPORT_CN.md)：架构、模块和开发维护说明。
- [OVERLAY_EXPORT_FORMAT_CN.md](OVERLAY_EXPORT_FORMAT_CN.md)：mask 和 JSON 完整字段说明。
- [AGENTS.md](AGENTS.md)：仓库开发规范、指标语义和构建备份要求。

### 16.3 关键术语

| 术语 | 含义 |
| --- | --- |
| mask | 每个像素只表示血管或背景的二值图 |
| Tubeness | 根据 Gaussian 平滑和 Hessian 特征增强管状结构 |
| skeleton | 把血管区域削薄得到的中心线 |
| junction | 骨架中的分叉连接区域 |
| endpoint | 只有一个骨架邻居的端点 |
| slab point | 位于分支段内部、通常有两个骨架邻居的点 |
| tree | 一整块相互连通的骨架网络 |
| segment/edge | 端点或 junction 之间的一段骨架 |
| convex hull | 包围所有血管前景点的最小凸多边形 |
| lacunarity | 描述空间空隙和不均匀性的多尺度指标 |

---

使用 AngioTool 得到可信结果的核心原则是：先验证二值 mask，再验证骨架和 junction，最后解释统计值；同一研究中始终固定图像采集、缩放、分割、骨架和标定参数。
## 历史记录：AngioTool 2.1 使用与结果解释说明（2026-09-07）

更新时间：2026-09-07

## 版本状态

以下内容是 2.1 修复阶段的历史记录，不覆盖本指南前文对当前 2.2.3 的说明。原版目录 `D:\CodeProject\AngioTool` 保持不变。

2.1 阶段曾优先修复空结果、异常尺寸、指标命名和结果追踪问题。相关历史背景仍可参考，但正式实验应以当前 2.2.3 程序和前文说明为准。

## 结果有效性

JSON 的 `metrics.valid` 表示程序是否检测到非空且具有正凸包面积的分析结果。

当 `metrics.valid=false` 时，应根据 `metrics.invalidReason` 排除该样本，不要把输出的 0 当成真实生物学零值。

常见原因：

- `empty-segmentation`：没有检测到前景；
- `degenerate-mask-area`：存在前景，但凸包面积无效。

## 面积解释

当前 `Explant Area` 和 `convexHullArea` 使用血管 mask 的凸包面积，而不是独立组织 ROI。JSON 中会记录：

```text
areaDenominator = vessel-mask-convex-hull
```

因此 `Vessels Percentage Area` 应理解为凸包内血管面积比例。若研究需要组织内血管比例，应提供独立组织区域或后续增加 ROI 支持。

## 长度解释

- `Average Vessels Length`：每棵连通树的平均总长度；
- `Average Segment Length`：每个骨架分支段的平均长度。

两者不能混用。实验报告若讨论“平均分支长度”，应优先使用 `Average Segment Length`。

## 直径解释

`Average Vessel Diameter` 是骨架 slab 点局部 mask 直径的中位数，不是严格的横截面平均直径。`Vessel Diameters list` 则是 Tubeness 检测尺度，两者不是同一个参数。

## 当前固定预处理

在阈值后，程序继续执行固定的 3x3 形态学闭运算两次。该步骤可能填补小间隙并连接相邻前景，因此必须在 overlay 中检查最终 mask。

JSON 中记录：

```text
morphology = 3x3-closing-twice
```

## 骨架质量

JSON 会记录：

- `skeletonPixelCount`
- `isolatedSkeletonPixelCount`

孤立骨架点、异常短分支、明显偏离血管中心的骨架都应视为需要复核的信号。当前版本记录这些问题，但不会自动删除所有异常结构。

## 推荐工作流程

1. 固定原始图像分辨率。
2. 调整颜色预处理和 Tubeness 尺度。
3. 检查最终 vessel mask。
4. 检查骨架是否位于血管中心。
5. 检查 junction 是否与真实分叉一致。
6. 排除 `metrics.valid=false` 的样本。
7. 固定 Skeletonizer、最大迭代步数和测量尺度。
8. 在同一实验中使用一致的参数和图像采集条件。

## 历史回归测试记录

```powershell
javac -cp build -d build benchmarks\AngioTool\AnalysisRegressionTest.java
java -cp build benchmarks.AngioTool.AnalysisRegressionTest
```

当时记录的通过标志（不是本轮复测结果）：

```text
AnalysisRegressionTest: PASS
```
