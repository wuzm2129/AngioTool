# AngioTool 叠加绘图导出文件说明

> **2026-09-12 新增字段**：JSON 在 `metrics.fractalBoxCount` 中增加最终血管掩膜的盒计数结果，包含维数、R²、独立有效性和实际尺度/计数；不替换既有统计字段。不可计算的浮点值写为 JSON `null`。完整算法与字段说明见 [盒计数分形维数](FRACTAL_BOX_COUNT_CN.md)。

核对日期：2026-10-06。适用于当前本地 2.2.3 的普通/直接二值模式、独立清理设置和三视图界面。

本文档说明 AngioTool 在保存结果图时额外导出的配套文件：`vessel-mask.png`、`overlay-data.json`，以及启用 `Export normalized data` 时生成的 `normalized-data.json`。这些文件用于后续通过 Python、MATLAB、JavaScript 或其他编程工具读取、复核和二次分析血管检测结果。

`normalized-data.json` 使用 `image-relative-v1` 规则：面积除以分析图像面积，长度和直径除以图像面积平方根，拓扑计数保持不变，现有 junction density 乘以分析图像面积。文件记录分析宽高、面积/长度基准、Resize Inputs 因子和 Measurement Scale。凸包面积为零时，依赖凸包的结果写为 `null`。该文件不替换 `overlay-data.json`，也不包含归一化后的点坐标。

批处理不为每张图单独写归一化 JSON，而是在原工作簿同一目录生成 `<原文件名>_nor.xlsx`；原始工作簿保持原字段和数值。

这两个文件应作为同一次分析结果的一组数据共同使用。`vessel-mask.png` 保存逐像素的血管区域，`overlay-data.json` 保存绘图参数和结构化点集数据。

## 1. 导出文件

保存结果图后，程序会在同一位置额外生成两个文件。

示例：如果保存的结果图是：

```text
sample-result.png
```

则会同时生成：

```text
sample-result vessel-mask.png
sample-result overlay-data.json
```

批处理时文件名前缀会跟随输出设置。例如采用输入目录下 `results` 输出方式时，输入 `a.png` 生成：

```text
results/ana_a.png
results/ana_a vessel-mask.png
results/ana_a overlay-data.json
```

## 2. `vessel-mask.png`

`vessel-mask.png` 是血管 mask 图，也就是程序最终识别出的血管区域二值图。

它来自程序内部的 `analysisImage` 数据。导出时会把每个像素转换为黑白两类：

- 白色像素：该像素被识别为血管。
- 黑色像素：该像素被识别为背景或非血管区域。

当前实现中，mask 不是概率图，也不是灰度强度图。它只有二值语义：血管或非血管。普通模式导出增强和清理后的 mask；直接二值模式导出输入前景经过可选去颗粒/填洞后的 mask，两项均关闭时保留输入前景。

界面的 `Original` / `Final mask` / `Skeleton overlay` 只控制预览。无论选中哪种视图，保存仍生成叠加结果图和最终掩膜；JSON 不保存各标签的临时查看模式。

### 2.1 像素值含义

导出 PNG 时使用 RGB 黑白值：

```text
白色: R=255, G=255, B=255
黑色: R=0,   G=0,   B=0
```

读取时可以按任意一个颜色通道判断，也可以先转灰度后判断：

```text
像素值 > 0   表示血管
像素值 == 0  表示背景
```

### 2.2 主要用途

`vessel-mask.png` 适合用于逐像素或区域级分析，例如：

- 统计血管面积或血管像素数量。
- 做连通域分析。
- 做形态学处理，如膨胀、腐蚀、开闭运算。
- 与原图、骨架点、凸包点重新叠加显示。
- 作为其他图像分析流程的输入。

如果后续问题是“某个像素是不是血管”，应读取这个 PNG 文件，而不是读取 JSON。

## 3. `overlay-data.json`

`overlay-data.json` 是结构化数据文件，保存最终叠加绘图相关的元数据、显示参数和点集。

它不包含逐像素血管 mask，也不包含原始图像像素。逐像素血管 mask 单独保存在 `vessel-mask.png` 中。

顶层结构如下：

```json
{
  "image": {},
  "uiParameters": {},
  "analysisParameters": {},
  "overlayParameters": {},
  "metrics": {},
  "convexHull": [],
  "skeleton": [],
  "branchPoints": [],
  "junctionVoxels": [],
  "removedJunctions": [],
  "vesselSegments": []
}
```

## 4. `image`

`image` 保存图像基础信息。

示例：

```json
"image": {
  "name": "sample.png",
  "absolutePath": "D:\\images\\sample.png",
  "exportDate": "5/11/26",
  "exportTime": "7:30 PM",
  "width": 1024,
  "height": 768,
  "brightestChannel": "green"
}
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `name` | string | 原始图像文件名，对应 XLSX 的 `Image Name`。 |
| `absolutePath` | string | 原始图像绝对路径，对应 XLSX 的 `Image Location`。 |
| `exportDate` | string | 导出 JSON 的日期，对应 XLSX 的 `Date`。 |
| `exportTime` | string | 导出 JSON 的时间，对应 XLSX 的 `Time`。 |
| `width` | number | 图像宽度，单位为像素。 |
| `height` | number | 图像高度，单位为像素。 |
| `brightestChannel` | string | 程序判断出的最亮颜色通道。 |

`brightestChannel` 当前可能值为：

- `"red"`
- `"green"`
- `"blue"`
- `"unknown"`

外部工具读取时，可以用 `width` 和 `height` 校验点坐标是否落在图像范围内，也可以用它们创建与 mask 对齐的画布。

## 5. `uiParameters`

**2.2.3 配置导入**：主界面的 `Load parameters...` 可从本文件恢复分析、颜色和叠加设置，加载只填入控件，不更新已打开图片，也不改变自动更新开关。点击 `Apply parameters` 可手动应用。导入优先采用 `uiParameters`，兼容旧参数组，并读取 `skeletonProcessing` 中的剪枝设置；缺项使用默认值，缺失剪枝/合并开关保持关闭。2.2.3 加载成功后仅更新状态栏，来源版本保留在悬停提示中，不再弹出缺项或版本差异提示；加载失败仍显示错误弹窗。`normalized-data.json` 不包含这些完整配置，无法恢复参数。

`uiParameters` 保存主界面上可调整的分析参数、颜色预处理参数和叠加显示参数。这个字段按界面区域分组，便于后续外部工具直接理解“这次分析运行时界面上是怎么设置的”。

该字段不会替代旧的 `analysisParameters` 和 `overlayParameters`。旧字段仍会保留，用于兼容之前的分析脚本；新字段更适合后续按界面语义读取。

顶层结构：

```json
"uiParameters": {
  "analysis": {},
  "colorPreprocessing": {},
  "overlayDisplay": {}
}
```

### 5.1 `uiParameters.analysis`

`analysis` 对应主窗口 `Analysis` 区域。

示例：

```json
"analysis": {
  "useBinaryMask": false,
  "binaryRemoveSmallParticles": false,
  "binarySmallParticleRadius": 15.0,
  "binaryFillHoles": false,
  "binaryHoleRadius": 3.0,
  "computeLacunarity": true,
  "computeThickness": true,
  "skeletonizer": "Fast (Zha84)",
  "capSkeletonSteps": true,
  "maxSkeletonSteps": 25,
  "resizeInputs": false,
  "resizeInputsPercent": 100.0,
  "applyMeasurementScale": false,
  "measurementScalePercent": 100.0,
  "fillHoles": false,
  "fillHolesPixels": 100.0,
  "removeParticles": false,
  "removeParticlesPixels": 100.0,
  "fillBrightShapes": false,
  "maxHoleLevelPercent": 150.0,
  "applyMinBoxness": true,
  "minBoxnessPercent": 9.375,
  "applyMinAreaLengthRatio": true,
  "minAreaLengthRatio": 16.0,
  "vesselDiameters": [12.0],
  "minVesselIntensity": 15,
  "maxVesselIntensity": 255
}
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `computeLacunarity` | boolean | 是否计算 lacunarity 指标。 |
| `computeThickness` | boolean | 是否计算血管厚度/直径。关闭时每段血管直径可能为 `null`。 |
| `skeletonizer` | string | 骨架化算法，当前为 `Fast (Zha84)` 或 `Thorough (Lee94)`。 |
| `capSkeletonSteps` | boolean | 是否限制骨架化最大迭代步数。 |
| `maxSkeletonSteps` | number | 最大骨架化迭代步数。 |
| `mergeNearbyJunctions` | boolean | 是否合并由短骨架段直接连接的 junction 区域。 |
| `maxJunctionMergeDistancePx` | number | junction 合并的最大骨架路径长度，单位为分析像素。 |
| `resizeInputs` | boolean | 是否缩放输入图像。 |
| `resizeInputsPercent` | number | 输入图像缩放百分比。 |
| `applyMeasurementScale` | boolean | 是否启用测量尺度换算。 |
| `measurementScalePercent` | number | 测量尺度百分比。 |
| `fillHoles` | boolean | 是否填充血管区域孔洞。 |
| `fillHolesPixels` | number | 填洞阈值，单位为 px。 |
| `removeParticles` | boolean | 是否移除小颗粒。 |
| `removeParticlesPixels` | number | 小颗粒移除阈值，单位为 px。 |
| `fillBrightShapes` | boolean | 是否启用 Max Hole Level 对应的亮区域/孔洞层级处理。 |
| `maxHoleLevelPercent` | number | Max Hole Level 的界面百分比值。 |
| `applyMinBoxness` | boolean | 是否启用 Min Boxness 过滤。 |
| `minBoxnessPercent` | number | Min Boxness 的界面百分比值。 |
| `applyMinAreaLengthRatio` | boolean | 是否启用 Min Length : Area 过滤。 |
| `minAreaLengthRatio` | number | Min Length : Area 的比例阈值。 |
| `vesselDiameters` | number[] | Vessel Diameters list 输入的数值数组。 |
| `minVesselIntensity` | number | Vessel Intensity range 的最小值。 |
| `maxVesselIntensity` | number | Vessel Intensity range 的最大值。 |

二值模式新增字段及有效性见下方第 6.1 节；普通模式的 `fillHoles`、`removeParticles`、`resizeInputs` 等仍保留原设置，在二值模式下不能据其值推断实际执行了相应操作。

### 5.2 `uiParameters.colorPreprocessing`

`colorPreprocessing` 对应主窗口 `Transform Colors` 区域。

示例：

```json
"colorPreprocessing": {
  "transformColors": false,
  "saturationPercent": 100.0,
  "offColor": "FF9900",
  "targetColor": "FF0000",
  "hueWeight": 1.0,
  "brightnessWeight": 1.0,
  "brightnessGraph": [0, 0, 100, 100]
}
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `transformColors` | boolean | 是否启用颜色变换。 |
| `saturationPercent` | number | 饱和度百分比。 |
| `offColor` | string | Off Color，RGB 十六进制字符串，不带 `#`。 |
| `targetColor` | string | Target Color，RGB 十六进制字符串，不带 `#`。 |
| `hueWeight` | number | Hue 权重。 |
| `brightnessWeight` | number | Brightness 权重。 |
| `brightnessGraph` | number[] | Brightness Graph 的点数组。 |

### 5.3 `uiParameters.overlayDisplay`

`overlayDisplay` 对应主窗口 `Overlay` 区域。

示例：

```json
"overlayDisplay": {
  "outline": {"enabled": true, "color": "FFFF00", "size": 1.0},
  "branches": {"enabled": true, "color": "0099FF", "size": 4.0},
  "skeleton": {"enabled": true, "color": "FF0000", "size": 2.0},
  "convexHull": {"enabled": true, "color": "CCFFFF", "size": 1.0},
  "outputImageMode": "Convert to Grayscale",
  "isolateBrightestChannelInOutput": true,
  "expandOutputToGrayScale": true
}
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `outline` | object | 血管轮廓显示设置，含 `enabled`、`color`、`size`。 |
| `branches` | object | 分支点显示设置，含 `enabled`、`color`、`size`。 |
| `skeleton` | object | 骨架显示设置，含 `enabled`、`color`、`size`。 |
| `convexHull` | object | 凸包显示设置，含 `enabled`、`color`、`size`。 |
| `outputImageMode` | string | 输出背景模式，对应界面单选项。 |
| `isolateBrightestChannelInOutput` | boolean | 是否只保留最亮通道。 |
| `expandOutputToGrayScale` | boolean | 是否扩展为灰度显示。 |

`outputImageMode` 当前可能值为：

- `"Keep Original Colors"`
- `"Isolate Brightest Channel"`
- `"Convert to Grayscale"`

## 6. `analysisParameters`

`analysisParameters` 保存与单图 XLSX 导出中相同的分析参数。它们描述本次血管识别和统计计算所使用的阈值、尺度、颜色变换和骨架化设置。

示例：

```json
"analysisParameters": {
  "lowThreshold": 15,
  "highThreshold": 255,
  "vesselThickness": [12.0],
  "smallParticles": 100.0,
  "fillHoles": 100.0,
  "maxHoleLevel": 1.5,
  "minBoxness": 0.09375,
  "minAreaLengthRatio": 16.0,
  "skeletonizer": "Fast",
  "maxSkeletonSteps": 25,
  "scalingFactor": 1.0,
  "transformedColors": false,
  "hueTransformWeight": 1.0,
  "brightnessWeight": 1.0,
  "targetColor": "FF0000",
  "offColor": "FF9900",
  "saturationFactor": 1.0,
  "brightnessGraph": [0, 0, 100, 100]
}
```

字段与 XLSX 单图列的对应关系：

| JSON 字段 | 对应 XLSX 列 | 含义 |
| --- | --- | --- |
| `lowThreshold` | `Low Threshold` | 低阈值参数。 |
| `highThreshold` | `High Threshold` | 高阈值参数。 |
| `vesselThickness` | `Vessel Thickness` | 血管增强/厚度相关 sigma 参数数组。 |
| `smallParticles` | `Small Particles` | 小颗粒移除阈值。 |
| `fillHoles` | `Fill Holes` | 填孔阈值。 |
| `maxHoleLevel` | `Max Hole Level` | 最大孔洞亮度/层级阈值。 |
| `minBoxness` | `Min Boxness` | 最小 boxness 参数。 |
| `minAreaLengthRatio` | `Min Area Length Ratio` | 最小面积/长度比参数。 |
| `skeletonizer` | `Skeletonizer` | 骨架化算法，当前为 `Fast` 或 `Thorough`。 |
| `maxSkeletonSteps` | `Max Skeleton Steps` | 最大骨架化迭代步数。 |
| `mergeNearbyJunctions` | `Merge Nearby Junctions` | 是否启用相连邻近 junction 合并。 |
| `maxJunctionMergeDistancePx` | `Max Junction Merge Distance (px)` | 允许合并的直接连接骨架段最大长度。 |
| `scalingFactor` | `Scaling Factor` | 线性尺度换算因子。 |
| `transformedColors` | `Transformed Colors` | 是否启用颜色变换。 |
| `hueTransformWeight` | `Hue Transform Weight` | 色相变换权重。 |
| `brightnessWeight` | `Brightness Weight` | 亮度权重。 |
| `targetColor` | `Target Color` | 目标颜色。 |
| `offColor` | `Off Color` | 背景/排除颜色。 |
| `saturationFactor` | `Saturation Factor` | 饱和度因子。 |
| `brightnessGraph` | `Brightness Graph` | 亮度映射曲线点。 |

### 6.1 输入模式与二值清理字段

以下字段位于 `analysisParameters`；其中 `useBinaryMask` 和四个 `binary*` 字段也出现在 `uiParameters.analysis`：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `inputMode` | string | `"Tubeness"` 或 `"Binary mask"` |
| `useBinaryMask` | boolean | 是否直接使用二值掩膜 |
| `effectiveResizeFactor` | number | 实际输入缩放因子；二值模式固定为 1 |
| `binaryRemoveSmallParticles` | boolean | 二值模式是否去小颗粒，默认 false |
| `binarySmallParticleRadius` | number | 小颗粒等效半径，单位 px，默认 15 |
| `binaryFillHoles` | boolean | 二值模式是否填充封闭孔洞，默认 false |
| `binaryHoleRadius` | number | 孔洞等效半径，单位 px，默认 3 |

面积上限约为 `πr²`。两项清理同时启用时先去颗粒，再重新标记连通域并填洞；填洞不修改与图像边界连通的背景。

读取时先判断模式，再选择对应参数组。二值模式下旧增强参数和普通清理值仅是保留的设置；普通模式下 `binary*` 值不参与计算。不要把灰显控件中保留的数字当成本次实际使用的参数。

这些是追加字段；历史 JSON 可能缺失。缺少模式字段时，应标注为旧版/模式未记录，而不是仅凭文件名判定输入模式。旧字段的名称和点集坐标语义保持不变。

### 6.2 XLSX 对应记录

单图结果表在已有列末尾追加下列列；批处理在 Settings 中记录相同项目：

| XLSX 列名 | 参数来源 |
| --- | --- |
| `Input Mode` | `inputMode` |
| `Binary Remove Particles` | `binaryRemoveSmallParticles` |
| `Binary Particle Radius` | `binarySmallParticleRadius` |
| `Binary Fill Holes` | `binaryFillHoles` |
| `Binary Hole Radius` | `binaryHoleRadius` |

外部脚本应按表头定位列，不要假定新增字段存在于历史工作簿，也不要仅依赖固定总列数。

## 7. `overlayParameters`

`overlayParameters` 保存导出时的叠加显示参数。它们主要用于复现 AngioTool 的叠加绘图效果，而不是血管分析结果本身。

示例：

```json
"overlayParameters": {
  "drawOutline": true,
  "outlineColor": "FFFF00",
  "outlineSize": 1,
  "drawSkeleton": true,
  "skeletonColor": "FF0000",
  "skeletonSize": 2,
  "drawBranchPoints": true,
  "branchingPointsColor": "0099FF",
  "branchingPointsSize": 4,
  "drawConvexHull": true,
  "convexHullColor": "CCFFFF",
  "convexHullSize": 1,
  "isolateBrightestChannelInOutput": false,
  "expandOutputToGrayScale": false
}
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `drawOutline` | boolean | 是否在结果图中绘制血管轮廓。 |
| `outlineColor` | string | 血管轮廓颜色，RGB 十六进制字符串。 |
| `outlineSize` | number | 血管轮廓线宽。 |
| `drawSkeleton` | boolean | 是否绘制血管骨架。 |
| `skeletonColor` | string | 骨架颜色，RGB 十六进制字符串。 |
| `skeletonSize` | number | 骨架线宽。 |
| `drawBranchPoints` | boolean | 是否绘制分支点。 |
| `branchingPointsColor` | string | 分支点颜色，RGB 十六进制字符串。 |
| `branchingPointsSize` | number | 分支点圆点大小。 |
| `drawConvexHull` | boolean | 是否绘制凸包。 |
| `convexHullColor` | string | 凸包颜色，RGB 十六进制字符串。 |
| `convexHullSize` | number | 凸包线宽。 |
| `isolateBrightestChannelInOutput` | boolean | 输出图是否只保留最亮通道。 |
| `expandOutputToGrayScale` | boolean | 输出图是否扩展为灰度显示。 |

颜色字符串不带 `#` 前缀。例如 `"FF0000"` 表示红色。外部工具如果需要 CSS 或 HTML 颜色格式，可以自行转换为 `#FF0000`。

## 8. `metrics`

`metrics` 保存整张图层面的统计值，便于外部工具不再从点集重复推算常用指标。

示例：

```json
"metrics": {
  "vesselPixelArea": 12345,
  "vesselArea": 12345.0,
  "convexHullAreaPixels": 23456.0,
  "convexHullArea": 23456.0,
  "explantArea": 23456.0,
  "vesselsArea": 12345.0,
  "vesselsPercentageArea": 52.63,
  "treeCount": 3,
  "segmentCount": 42,
  "junctionCount": 12,
  "junctionDensity": 0.00051,
  "endPointCount": 18,
  "totalLengthPixels": 987.6,
  "totalLength": 987.6,
  "totalVesselsLength": 987.6,
  "averageVesselsLength": 329.2,
  "averageSegmentLength": 23.5,
  "averageVesselDiameter": 8.2,
  "medialELacunarity": 1.2,
  "meanELacunarity": 1.1,
  "eLacunarityGradient": 0.03,
  "medialFLacunarity": 1.3,
  "meanFLacunarity": 1.2,
  "fLacunarityGradient": 0.04,
  "averageSegmentDiameter": 8.0
}
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `vesselPixelArea` | number | 血管 mask 中的血管像素数量。 |
| `vesselArea` | number | 应用线性缩放后的血管面积。未启用缩放时与像素面积等价。 |
| `convexHullAreaPixels` | number | 凸包面积，单位为像素面积。 |
| `convexHullArea` | number | 应用线性缩放后的凸包面积。 |
| `explantArea` | number | 外植体/凸包面积，对应 XLSX 的 `Explant Area`。 |
| `vesselsArea` | number | 血管面积，对应 XLSX 的 `Vessels Area`。 |
| `vesselsPercentageArea` | number | 血管面积百分比，对应 XLSX 的 `Vessels Percentage Area`。 |
| `treeCount` | number | 骨架连通树数量。 |
| `segmentCount` | number | 血管分支段数量，对应 `vesselSegments` 的元素数量。 |
| `junctionCount` | number | 分支点数量。 |
| `mergedJunctionRegionCount` | number | 被吸收到其他 junction 组中的原始 junction 区域数。 |
| `junctionDensity` | number | 分支点密度，对应 XLSX 的 `Junctions Density`。 |
| `endPointCount` | number | 端点数量。 |
| `totalLengthPixels` | number | 总血管长度，单位为像素。 |
| `totalLength` | number | 应用线性缩放后的总血管长度。 |
| `totalVesselsLength` | number | 总血管长度，对应 XLSX 的 `Total Vessels Length`。 |
| `averageVesselsLength` | number | 平均血管长度，对应 XLSX 的 `Average Vessels Length`。 |
| `averageSegmentLength` | number | 平均分支段长度。 |
| `averageVesselDiameter` | number | 程序原有的整图平均血管直径统计值。 |
| `medialELacunarity` | number | 对应 XLSX 的 `Medial E Lacunarity`。 |
| `meanELacunarity` | number | 对应 XLSX 的 `Mean E Lacunarity`。 |
| `eLacunarityGradient` | number | 对应 XLSX 的 `E Lacunarity Gradient`。 |
| `medialFLacunarity` | number | 对应 XLSX 的 `Medial F Lacunarity`。 |
| `meanFLacunarity` | number | 对应 XLSX 的 `Mean F Lacunarity`。 |
| `fLacunarityGradient` | number | 对应 XLSX 的 `F Lacunarity Gradient`。 |
| `averageSegmentDiameter` | number | 按 `vesselSegments` 中每段直径求平均后的直径。 |

如果未在界面中启用线性缩放，`vesselArea`、`convexHullArea`、`totalLength`、`averageSegmentLength` 和 `averageVesselDiameter` 使用像素单位。

## 9. 点集字段的通用约定

JSON 中的点集字段使用整数坐标。

二维点格式：

```json
{ "x": 10, "y": 20 }
```

三维点格式：

```json
{ "x": 10, "y": 20, "z": 0 }
```

坐标约定：

- `x` 表示横向像素坐标。
- `y` 表示纵向像素坐标。
- `z` 表示骨架分析中保留的层号或深度坐标。
- 对普通二维图像，`z` 通常为 `0`。
- 坐标应与 `vessel-mask.png` 的像素网格对齐。

在大多数二维分析中，可以用 `x` 和 `y` 把这些点直接画到 mask 或原始图像上。如果只处理二维图像，通常可以忽略 `z`，但建议在自己的分析脚本中显式说明这一点。

## 10. `convexHull`

`convexHull` 是凸包点数组，用于表示包围血管区域的凸包轮廓。

示例：

```json
"convexHull": [
  { "x": 10, "y": 20 },
  { "x": 30, "y": 25 },
  { "x": 28, "y": 60 }
]
```

每个元素包含：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `x` | number | 横向像素坐标。 |
| `y` | number | 纵向像素坐标。 |

这个字段是二维点集。外部工具可以用这些点重新绘制凸包边界，或计算凸包面积、周长等指标。

## 11. `skeleton`

`skeleton` 是血管骨架点数组，对应程序内部骨架分析结果中的 `slabList`。

示例：

```json
"skeleton": [
  { "x": 100, "y": 80, "z": 0 },
  { "x": 101, "y": 80, "z": 0 }
]
```

每个元素包含：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `x` | number | 横向像素坐标。 |
| `y` | number | 纵向像素坐标。 |
| `z` | number | 骨架分析保留的层号或深度坐标。 |

这个字段适合用于重新绘制骨架，或作为后续拓扑、长度、连通关系分析的基础数据之一。

注意：当前 JSON 导出的是骨架点集合，不直接导出骨架边列表。因此，如果外部工具需要恢复完整图结构，可能需要结合相邻像素规则或自行构建连通关系。

## 12. `branchPoints`

`branchPoints` 是分支点数组，对应程序最终用于绘制分支点的点。

示例：

```json
"branchPoints": [
  { "x": 120, "y": 90, "z": 0 }
]
```

每个元素包含：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `x` | number | 横向像素坐标。 |
| `y` | number | 纵向像素坐标。 |
| `z` | number | 骨架分析保留的层号或深度坐标。 |

该字段由程序内部的分支点索引映射到 `junctionVoxels` 点池后导出。当前实现中，如果遇到非法索引，会跳过对应点。

## 13. `junctionVoxels`

`junctionVoxels` 是骨架分析过程中识别到的 junction voxel 点池。

示例：

```json
"junctionVoxels": [
  { "x": 120, "y": 90, "z": 0 },
  { "x": 121, "y": 90, "z": 0 }
]
```

每个元素包含：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `x` | number | 横向像素坐标。 |
| `y` | number | 纵向像素坐标。 |
| `z` | number | 骨架分析保留的层号或深度坐标。 |

可以把它理解为 junction 相关点的基础点池。`branchPoints` 和 `removedJunctions` 都是从这个点池中按索引取点后导出的结果。

## 14. `removedJunctions`

`removedJunctions` 是骨架分析过程中被移除或合并处理过的 junction 点。

示例：

```json
"removedJunctions": [
  { "x": 130, "y": 95, "z": 0 }
]
```

每个元素包含：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `x` | number | 横向像素坐标。 |
| `y` | number | 纵向像素坐标。 |
| `z` | number | 骨架分析保留的层号或深度坐标。 |

该字段同样由索引映射到 `junctionVoxels` 点池后导出。当前叠加绘图逻辑中，这些点也会作为骨架层的补充点参与显示。

## 15. `vesselSegments`

`vesselSegments` 是按骨架分支段导出的血管数据。这里的“每段血管”定义为骨架图中一条边，也就是端点、分支点或连接点之间的一段骨架。

示例：

```json
"vesselSegments": [
  {
    "id": 0,
    "tree": 0,
    "lengthPixels": 31.4,
    "length": 31.4,
    "angleDegrees": 45.0,
    "diameterPixels": 7.8,
    "diameter": 7.8
  },
  {
    "id": 1,
    "tree": 0,
    "lengthPixels": 5.0,
    "length": 5.0,
    "angleDegrees": null,
    "diameterPixels": null,
    "diameter": null
  }
]
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | number | 分支段编号，从 `0` 开始。 |
| `tree` | number | 该分支段所属的骨架连通树编号。 |
| `lengthPixels` | number | 该分支段长度，单位为像素。 |
| `length` | number | 应用线性缩放后的分支段长度。未启用缩放时等于 `lengthPixels`。 |
| `angleDegrees` | number 或 null | 该分支段从起点指向终点的方向角，单位为度，范围约为 `0` 到 `360`。无法可靠计算时为 `null`。 |
| `diameterPixels` | number 或 null | 该分支段估计直径，单位为像素。未执行厚度计算时为 `null`。 |
| `diameter` | number 或 null | 应用线性缩放后的分支段估计直径。未启用缩放时等于 `diameterPixels`；未执行厚度计算时为 `null`。 |

每段直径的计算方式是：先对骨架点计算其到血管边界的距离，把距离视为局部半径；再在该分支段包含的骨架点上取中位半径，并乘以 `2` 得到该段估计直径。

注意：短分支段或端点坐标无法形成有效方向时，`angleDegrees` 会是 `null`。分析参数中关闭血管厚度计算时，`diameterPixels` 和 `diameter` 会是 `null`。如果厚度计算已执行但某段缺少可用于估计直径的内部骨架点，则直径可能为 `0`。

## 16. 推荐读取流程

后续使用其他编程工具分析时，建议按以下流程处理：

1. 读取 `overlay-data.json`。
2. 读取 `image.width` 和 `image.height`。
3. 读取同名前缀的 `vessel-mask.png`。
4. 校验 PNG 尺寸是否与 JSON 中的 `width`、`height` 一致。
5. 将 mask 作为血管区域的逐像素数据。
6. 将 `metrics` 作为整图统计数据。
7. 将 `vesselSegments` 作为每段血管的长度、角度和直径数据。
8. 将 `convexHull`、`skeleton`、`branchPoints` 等字段作为结构化点集。
9. 如果只是二维分析，对含 `z` 的点明确采用 `x`、`y` 投影。
10. 如果需要复现界面设置，优先读取 `uiParameters`。
11. 如果需要兼容旧脚本，再读取 `analysisParameters` 和 `overlayParameters`。

## 17. Python 读取示例

下面示例仅说明读取思路，具体库可以按自己的环境替换。

```python
import json
from PIL import Image
import numpy as np

base = "sample-result"

with open(base + " overlay-data.json", "r", encoding="utf-8") as f:
    overlay = json.load(f)

mask_img = Image.open(base + " vessel-mask.png").convert("L")
mask = np.array(mask_img) > 0

width = overlay["image"]["width"]
height = overlay["image"]["height"]

assert mask.shape == (height, width)

skeleton_xy = [(p["x"], p["y"]) for p in overlay["skeleton"]]
branch_xy = [(p["x"], p["y"]) for p in overlay["branchPoints"]]
segments = overlay["vesselSegments"]
ui_params = overlay["uiParameters"]

vessel_area_pixels = int(mask.sum())

print("vessel pixels:", vessel_area_pixels)
print("skeleton points:", len(skeleton_xy))
print("branch points:", len(branch_xy))
print("segments:", len(segments))
print("first segment diameter:", segments[0]["diameter"] if segments else None)
print("skeletonizer:", ui_params["analysis"]["skeletonizer"])
```

## 18. 注意事项

- `overlay-data.json` 不包含血管 mask 的逐像素数据。
- `vessel-mask.png` 不包含骨架、凸包、分支点等结构化点集。
- 两个文件需要一起使用，才能同时获得区域数据和结构化绘图数据。
- 点集数组可能为空。数组为空不一定表示导出失败，也可能是当前图像或参数没有检测到对应结构。
- `overlayParameters` 主要是显示配置，不应直接当成定量分析结果。
- `uiParameters` 是按界面分组的参数快照，适合复现实验设置或记录当时用户选择。
- `analysisParameters` 和 `overlayParameters` 是历史兼容字段，会继续保留。
- `skeleton` 当前导出点，不导出边；如果只需要每段血管长度、角度、直径，优先使用 `vesselSegments`。
- `vesselSegments` 导出的是骨架分支段，不一定等同于肉眼理解的一整根连续血管。
- 颜色字段是不带 `#` 的 RGB 十六进制字符串。
- 对二维图像，含 `z` 的点通常可以按 `(x, y)` 使用，但建议在分析代码中明确处理规则。

## 19. 两个文件的分工总结

| 文件 | 数据类型 | 主要用途 |
| --- | --- | --- |
| `vessel-mask.png` | 二值图像，逐像素 mask | 判断像素是否为血管，做面积、连通域、形态学等区域分析。 |
| `overlay-data.json` | JSON 元数据、统计值和点集 | 读取图像尺寸、绘图参数、整图指标、每段血管指标、凸包点、骨架点、分支点和 junction 点。 |

简单来说：如果要分析“血管区域”，优先读 `vessel-mask.png`；如果要分析“叠加绘图结构和关键点”，读 `overlay-data.json`。
