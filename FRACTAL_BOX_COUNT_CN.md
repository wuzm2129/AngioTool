# 盒计数分形维数

## 功能与分析对象

自 2026-09-12 起，单图和批处理分析自动计算最终血管二值掩膜的盒计数分形维数。普通模式使用增强、阈值和清理后的掩膜；直接二值模式使用可选去颗粒、填洞后的掩膜。计算不使用原始灰度、骨架或叠加绘图，也不修改掩膜。Lacunarity 和厚度开关不控制此指标。

这是有限图像、有限尺度上的估计量，不是对图案具有严格分形结构的证明。它描述所选尺度范围内的空间占据特征，不能单独解释为血管健康程度、分支数量或血管生成活性。

## 第一版固定规则

1. 采样范围为完整的已处理图像，白色/非零像素为前景；不裁剪到凸包或前景包围盒。
2. 盒子边长以处理后像素为单位，取 `2, 4, 8, ...`，最大值不超过图像较短边的四分之一。
3. 每个尺度使用四个网格起点：`(0,0)`、`(s/2,0)`、`(0,s/2)`、`(s/2,s/2)`。网格向两个方向延伸，图像边界处不完整的盒子仍计数；不会丢弃边缘前景。
4. 分别统计包含至少一个前景像素的盒子数，并取四个计数的最小值 `N(s)`。这只是四个固定位置中的最小值，不是遍历所有平移后的全局最小覆盖。
5. 对全部配置尺度拟合 `ln N(s) = D × ln(1/s) + C`。输出斜率 `D` 和决定系数 `R²`；不通过挑选最高 R² 的子区间优化结果，不把 D 截断到指定范围。
6. 至少需要四个尺度，因此图像较短边至少为 64 像素。空掩膜、尺度不足或计数恒定等不可拟合情形给出独立的有效性标记和原因。

规则是首版实现选择，不是 ImageJ/FracLac 的默认参数承诺。默认不新增参数控件；后续若需要特定论文的尺度范围，可以增加显式配置和方法版本。

## 查看与导出

统计侧栏显示 `Fractal dimension` 和拟合 R²。不可计算时显示 N/A 和原因。

Excel 在既有列末尾追加六列，原列顺序保持不变：

| 列名 | 含义 |
| --- | --- |
| Fractal Dimension (Box-counting) | 盒计数维数估计，无单位 |
| Fractal Fit R squared | 拟合决定系数 |
| Fractal Valid | 是否满足计算条件；不代表生物学或方法学准确性已验证 |
| Fractal Invalid Reason | 不可计算原因 |
| Fractal Box Sizes (px) | 参与采样的盒子边长 |
| Fractal Box Counts | 各尺度对应的四网格最小占据盒数 |

不可计算的 D 和 R² 在 Excel 留空，在 JSON 中为 `null`，不输出 NaN、Infinity 或替代性的 0。分形结果的有效性独立于原有凸包面积有效性，例如一条直线可以进行盒计数，但其凸包面积可能为零。

保存结果图所附带的 JSON 在 `metrics.fractalBoxCount` 中记录方法名、分析对象、尺度策略、网格策略、处理后图像尺寸、有效性和尺度/计数数组。重新绘制或切换 Original / Final mask / Skeleton overlay 不改变这个计算结果。

以下是 128×128 图像中、边界对齐的 64×64 实心正方形示例：

```json
{
  "method": "box-counting",
  "analysisObject": "final-vessel-mask",
  "gridPolicy": "minimum-count-of-four-half-box-offsets",
  "scalePolicy": "powers-of-two-from-2-to-quarter-shorter-image-side",
  "imageWidth": 128,
  "imageHeight": 128,
  "valid": true,
  "invalidReason": "",
  "dimension": 2.0,
  "rSquared": 1.0,
  "boxSizes": [2, 4, 8, 16, 32],
  "boxCounts": [1024, 256, 64, 16, 4]
}
```

## 比较图像时的约定

- 同组图像尽量保持相同视野、像素尺寸、图像尺寸和分割/清理设置；这些因素会影响有限尺度拟合。
- 测量比例尺只是单位换算，不应改变 D 或 R²；普通模式的输入缩放会重新采样并改变掩膜，可能改变结果。
- 四个固定网格降低单一起点的影响，但不保证任意平移或旋转不变。
- 高 R² 仅表示当前区间的线性拟合较好；低 R²、尺度跨度不足和局部尺度变化都需要结合图像判断。第一版不把高 R² 当成科学有效性的门槛。
- 与 ImageJ/FracLac 对照时应统一前景、ROI、尺度、网格位置及多网格聚合方式；不能直接与其默认输出要求逐位一致。

## 实现与验证

- `source/Algorithms/FractalBoxCount.java`：独立的只读掩膜算法，不依赖 ImageJ 或新第三方库。
- `Analyzer`：在最终掩膜生成后计算，将独立结果传给统计和导出，支持重复使用 Data 时刷新结果。
- `StatisticsPanel`、`OverlayDataExporter`：界面和 JSON 展示；单图 XLSX 位于 Analyzer，批量 Results 表位于 BatchProcessing，二者均追加相同六列并复用值格式函数。
- 合成测试覆盖直线、实心方块、Sierpinski carpet、空图、小图、恒定计数、非整倍尺寸及边界盒计数，并用独立计数方法交叉核对。
- 集成验证覆盖处理模式、比例尺、清理后掩膜、结果复用、单图与批量输出以及界面。
- 当前发布产物为 `AngioTool2.2.2.jar`；源码和发布 JAR 按 Git 版本管理。2.2.1 阶段的 XLSX 修复属于历史记录。以下带日期的验证记录属于当时实际执行的历史记录，未执行项目不视为通过。

2.2.2 还修正了 8 邻域转角产生的单像素内部连接被误计为 junction 外部支路的问题；受影响图像需用新版本重新计算 junction 数量和 junction density。最新测试结果及边界见 [当前验证记录](VALIDATION_CN.md)。

### 2026-09-12 实际验证记录

以下检查在本机完成；生成的验证产物和日志保存在本地
`verification/fractal-box-count-20260912/`，不作为源码提交内容。

| 检查 | 结果 |
| --- | --- |
| `python compile.py` | 通过；Java 8 目标编译，仅有目标版本过时及既有 unchecked 警告 |
| `FractalBoxCountTest` | 通过；直线 D=1、实心方块 D=2、空图/小图/边界尺寸、池化缓冲区尾部隔离、独立盒计数交叉检查 |
| Sierpinski carpet（243×243） | D=1.853437，R²=0.999830；相对理论 ln(8)/ln(3) 的绝对误差 0.039352，反映有限像素及二进制尺度拟合 |
| `FractalIntegrationTest` | 通过；普通/二值模式、清理后掩膜、单位换算、空图复用、Data 重启、独立有效性、侧栏及单图/批量导出 |
| `verify_fractal_exports.py` | 通过；严格解析 7 组 JSON/PNG 和 2 个 XLSX，独立重算全部盒计数和回归，确认无效值为空/null |
| ImageJ 1.54q 参考对照 | 通过；实际调用 ImageJ `FractalBoxCounter.count` 和 `CurveFitter`，统一前景、完整图像范围、尺度和四个网格位置，方块/项目示例计数完全一致，D 和 R² 差异小于 1e-10 |
| 原有 `AnalysisRegressionTest`、`BinaryMaskTest` | 通过 |
| `UiSmokeTest` 普通及 150% 缩放 | 均通过，并检查截图；侧栏下方指标可通过滚动查看 |
| 历史备份 JAR 与当时新 JAR 示例图对照 | 全部既有 Stats 字段及最终 mask 哈希一致；mask 哈希为 1407611904 |
| `git diff --check` | 通过 |

项目自带示例图在默认分析参数下得到 D=1.7500246487569238。该示例包含自身图像内容和边缘信息，仅用于实现对照，不作生物学验证数据。

ImageJ 对照没有调用其默认的自动前景裁剪或交互绘图，而是通过测试适配层使用同样的尺度/网格并以双精度回归；因此不宣称与默认插件界面输出逐位一致。参考 JAR 仅保存在验证目录，不是程序运行依赖，来源为 [ImageJ 官方下载](https://wsr.imagej.net/download/jars/ij.jar)，SHA-256：`B61B298AD7540309553E81EB0A769BD26267C77ADBFE732BE5CA51C7FF7257B6`。

仅对盒计数新增部分计时，每种情况预热一次、测量三次取中位数：1024×1024 稀疏分支图 21.93 ms、实心图 65.75 ms；2048×2048 稀疏分支图 94.60 ms、实心图 387.74 ms。耗时依赖图像占据率和机器负载，不代表完整分析耗时。

历史构建前备份（已验证与当时原 JAR 哈希一致）和新 JAR 均是本机验证时的本地产物；它们不替代 Git 版本追踪，也不是当前发布依赖。

本轮未在 Java 8 实际运行时或真实 Fiji 插件宿主内验证，也没有开展标注数据集的科学准确性评估。

### 复现测试

先完成构建，再编译测试。类路径须包括当前 JAR，因为 Lee94 查找表等资源打包在 JAR 内；仅使用 `build` 会缺少资源。

```powershell
$testSources = @(Get-ChildItem benchmarks/AngioTool -Filter '*.java' | ForEach-Object { $_.FullName })
javac --release=8 -cp AngioTool2.2.2.jar -d build $testSources
java '-Djava.awt.headless=true' -cp 'AngioTool2.2.2.jar;build' AngioTool.FractalBoxCountTest --benchmark
java '-Djava.awt.headless=true' -cp 'AngioTool2.2.2.jar;build' AngioTool.FractalIntegrationTest verification/fractal-recheck
python benchmarks/AngioTool/verify_fractal_exports.py verification/fractal-recheck
```

Python 导出复核需要 Pillow，仅作为验证脚本依赖。ImageJ 参考适配层源码在 `benchmarks/imagej-reference/FractalReferenceTest.java`；需单独以参考 ij.jar 编译，不参与应用构建。

## 方法参考

- [FracLac：Box Counting](https://imagej.net/ij/plugins/fraclac/FLHelp/BoxCounting.htm)
- [FracLac：Box Counting Options](https://imagej.net/ij/plugins/fraclac/FLHelp/BoxCountingOptions.htm)
- [ImageJ FractalBoxCounter 源码](https://imagej.net/ij/developer/source/ij/plugin/filter/FractalBoxCounter.java.html)
