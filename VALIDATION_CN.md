# AngioTool 2.2.2 验证记录

日期：2026-09-16。验证对象为本目录发布产物 `AngioTool2.2.2.jar`；运行环境为 Windows、Microsoft OpenJDK 21.0.8 和 Python 3.13.3，编译目标为 Java 8。

## 本轮修复

新增测试发现：在 8 邻域规则下，L 形转角会形成包含相邻 junction 候选像素的区域。转角的另一个普通骨架像素同时接触该区域的两个像素，旧实现仍给该区域增加一条“外部路径”，从而将两路转弯误报成三路交汇。

2.2.2 排除这种区域内部的单像素连接。真实分叉和具有独立返回路径的闭环保留其外部连接计数。该修改只影响 junction 代表点、数量及密度，不改变血管掩膜、剪枝后的骨架像素或底层分段图。

## 可重复执行

在项目根目录运行：

```powershell
# 从当前源码构建到独立验证目录，然后执行无界面测试
python benchmarks/run_regression.py

# 或直接验证已有发布包
python benchmarks/run_regression.py --jar AngioTool2.2.2.jar
```

脚本只依赖 Python 标准库和 JDK，逐项失败即停止，给每个命令设置超时。测试类编译为 Java 8 字节码，运行类路径为独立测试目录及被测 JAR，避免误用旧 `build` 类或缺失 Lee94 查找表资源。每次产生独立 `verification/regression-*` 目录，包含命令日志、合成图像导出和 `summary.json`；同时核对项目根目录已有 JAR 的 SHA-256 未变。

默认构建会按 `compile.py` 的正常流程重建 `build/` 和模板生成文件，但输出 JAR 位于验证目录，不替换已发布 JAR。`--jar` 模式不重新编译生产源码。

## 已执行结果

发布包检查：`python compile.py` 成功，只有 Java 8 目标过时和既有 unchecked 提示。`python benchmarks/run_regression.py --jar AngioTool2.2.2.jar` 全部通过。

| 测试 | 覆盖及结果 |
| --- | --- |
| `SkeletonPruningTest` | 关闭/2/3/5 px 阈值；对角路径欧氏长度及阈值等号；旋转/镜像；独立线段、孤立点、闭环、边界端点；同时删除多条短支路；空骨架及非法参数。通过。 |
| `SkeletonJunctionMergeTest` | 连接距离阈值内外；不合并断开的节点；合并不改变骨架和底层图长度；四个方向的 L 形转角；方形闭环无伪交汇点；闭环连接一条真实支路时保留交汇点；空图和非法参数。通过。 |
| `SkeletonPipelineTest` | Zha84/Lee94 各自运行关闭/2/3/5 px 剪枝；合成 Y 形的端点、junction 和长度；掩膜/面积/盒计数不变；比例尺不改变像素剪枝；厚条带 1 步上限未收敛并跳过剪枝，无上限可收敛；复用状态清理；JSON、PNG、XLSX 状态和像素核对。通过。 |
| `AnalysisRegressionTest` | 空/小图 lacunarity、1×1 Tubeness、直线及分叉骨架。通过。 |
| `BinaryMaskTest` | 二值输入保真、两种骨架方法、批量输出、参数存取、非法输入、独立去颗粒和填洞。通过。 |
| `BatchOutputPathTest` | 输出路径规则与仓库示例图批量分析。通过。 |
| `FractalBoxCountTest` | 直线、方块、Sierpinski carpet、无效数据、边界及独立计数对照。通过。 |
| `FractalIntegrationTest` | 掩膜、尺度、数据复用、单图/批量/归一化导出，以及统计面板离屏渲染。通过。 |
| `NormalizedMetricsTest` | 图像相对归一化、测量尺度、退化值与 JSON/XLSX 输出。通过。 |
| `SpreadsheetWriterCompatibilityTest` | UTF-8、XML 转义、工作表范围、非有限值及 UTC 整秒元数据。通过。 |

另用 Python 标准库严格解析骨架测试生成的 JSON，并解析 XLSX 内的 XML，均通过。此项检查格式语法，不等于 Microsoft Excel 原生兼容性验证。

最终发布包的无界面完整日志和合成输出位于 `verification/regression-lf_d1dg3/`。该目录按现有 `.gitignore` 留在本机；测试源码、可复现命令及本记录纳入 Git。

### 界面烟测

对最终发布包运行 `UiSmokeTest`，默认缩放和 `-Dflatlaf.uiScale=1.5` 均通过。测试隔离 `user.home`，验证参数映射、多标签切换、分析与视图切换、导出和关闭流程，不改写真实用户配置。已查看两种缩放下的分析窗口截图；版本标题为 2.2.2，示例图交汇点为 220，收敛及剪枝状态正常显示。

在上述无界面测试完成后，可以使用它产生的测试类复现（将路径替换为该次输出目录）：

```powershell
$testClasses = 'verification/regression-lf_d1dg3/test-classes'
java -cp "$testClasses;AngioTool2.2.2.jar" AngioTool.UiSmokeTest verification/ui-recheck
java '-Dflatlaf.uiScale=1.5' -cp "$testClasses;AngioTool2.2.2.jar" AngioTool.UiSmokeTest verification/ui-recheck-150
```

本次日志为 `verification/regression-lf_d1dg3/ui.log`、`ui-150.log`，截图在同目录的 `ui/` 和 `ui-150/`。这些是自动化 Swing 烟测及截图检查，不等于完整人工操作验收。

### 仓库示例图的新旧版本对照

对 `source/deprecated/images/ImageExample.png`，使用默认参数分别运行 2.2.1 和 2.2.2：

| 指标 | 2.2.1 | 2.2.2 |
| --- | ---: | ---: |
| Junction 数量 | 223 | 220 |
| Junction / area | 1.618064272705505E-4 | 1.5962965919067763E-4 |
| 血管面积 | 499335 | 499335 |
| 总骨架长度 | 27482.02134093198 | 27482.02134093198 |
| 端点 / 分段数 | 129 / 405 | 129 / 405 |
| 最终 mask 的 `Arrays.hashCode` | 1407611904 | 1407611904 |

输出的其他标量指标也相同。数量减少符合本次排除伪交汇点的实现变化；这张未标注示例不能证明整个算法具有更高的生物学准确性。比较原始输出保存在 `verification/regression-u2lguyoq/example-2.2.1.txt` 和 `example-2.2.2.txt`；对照后只更新了打包帮助文档，生产算法没有再改动。同目录的 `expected-failure-2.2.1.log` 记录新增转角回归测试在旧发布包上按预期失败。

### 产物标识

- 2.2.2 JAR SHA-256：`CB397B68D7771607F692801B768F196983601E7AC10E516CD60A99965E8487E3`。
- 原 `AngioTool2.jar` SHA-256 保持 `1B38D84AA33C4D3182971931A1D40BC50DE22DC210C3ED6DB04805F610D2502C`。
- 2.2.1 JAR SHA-256 保持 `087A69D253A57734231DFD4015E94FACB45C97364148277D58E430C17FC279A6`。

## 尚未覆盖的边界

- 没有标注数据集上的 Dice、长度误差、junction 误差或真实血管芽保留率；尚不能确认默认剪枝/合并阈值适用于研究样本。
- 两种骨架算法在上述合成用例满足相同约束，不表示所有图像的结果一致。
- 本轮未使用 Java 8 实际运行时、真实 ImageJ/Fiji 插件宿主或 Microsoft Excel 原生应用验证。
- EXE 构建脚本仍读取旧 `AngioTool2.jar`，未适配版本化 JAR；本次只交付独立 JAR。
- 厚度基准、组织 ROI 与凸包分离、多尺度 Tubeness 校准和更广泛噪声/分辨率数据集仍属于后续工作。
