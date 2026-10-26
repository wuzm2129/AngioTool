# CLI schema 1 完整参数参考

配置 parameters 使用以下固定字段名；所有 53 个字段都有完整 CLI 选项。布尔值使用 true/false，数组 CLI 值为逗号分隔。enabledBy 是启用条件，always 为始终、binary/tubeness 为输入模式；其他名称指对应开关，条件递归继承。除结构安全条件外，休眠参数不检查业务范围。

倍率不是百分比；图像缩放先于分析，几何阈值使用分析图像像素。radius 参数在现有组件清理算法中转换为面积阈值，不能当作面积直接输入。颜色需六位十六进制。强度为0–255，sigmas激活时非空且全部正数，开启的倍率/半径/线宽/迭代次数为正，剪枝/合并距离和面积长度比非负，fraction范围0–1。颜色变换的权重非负且和为正，目标/背景必须有不同色相，亮度曲线为[0,100]的完整x,y对；激活时至少两个不同x且不重复。

| 配置字段 | CLI 选项 | 默认值 | 单位/类型 | enabledBy |
| --- | --- | --- | --- | --- |
| `shouldUseBinaryMask` | `--use-binary-mask` | `false` | boolean | `always` |
| `binaryRemoveSmallParticles` | `--binary-remove-small-particles` | `false` | boolean | `binary` |
| `binarySmallParticleRadius` | `--binary-small-particle-radius` | `15.0` | analysis-image radius px | `binaryRemoveSmallParticles` |
| `binaryFillHoles` | `--binary-fill-holes` | `false` | boolean | `binary` |
| `binaryHoleRadius` | `--binary-hole-radius` | `3.0` | analysis-image radius px | `binaryFillHoles` |
| `shouldResizeImage` | `--resize-image` | `false` | boolean | `tubeness` |
| `resizingFactor` | `--resizing-factor` | `1.0` | multiplier | `shouldResizeImage` |
| `shouldRemapColors` | `--remap-colors` | `false` | boolean | `tubeness` |
| `hueTransformWeight` | `--hue-transform-weight` | `1.0` | weight | `shouldRemapColors` |
| `brightnessTransformWeight` | `--brightness-transform-weight` | `1.0` | weight | `shouldRemapColors` |
| `targetRemapColor` | `--target-remap-color` | `"#ff0000"` | #RRGGBB | `shouldRemapColors` |
| `voidRemapColor` | `--void-remap-color` | `"#ff9900"` | #RRGGBB | `shouldRemapColors` |
| `saturationFactor` | `--saturation-factor` | `1.0` | fraction [0,1] | `shouldRemapColors` |
| `brightnessLineSegments` | `--brightness-line-segments` | `[0,0,100,100]` | x,y pairs in [0,100] | `shouldRemapColors` |
| `shouldFillBrightShapes` | `--fill-bright-shapes` | `false` | boolean | `tubeness` |
| `brightShapeThresholdFactor` | `--bright-shape-threshold-factor` | `1.5` | multiplier | `shouldFillBrightShapes` |
| `shouldApplyMinBoxness` | `--apply-min-boxness` | `true` | boolean | `tubeness` |
| `minBoxness` | `--min-boxness` | `0.09375` | fraction [0,1] | `shouldApplyMinBoxness` |
| `shouldApplyMinAreaLength` | `--apply-min-area-length` | `true` | boolean | `tubeness` |
| `minAreaLengthRatio` | `--min-area-length-ratio` | `16.0` | ratio | `shouldApplyMinAreaLength` |
| `shouldRemoveSmallParticles` | `--remove-small-particles` | `false` | boolean | `tubeness` |
| `removeSmallParticlesThreshold` | `--remove-small-particles-threshold` | `0.0` | analysis-image radius px | `shouldRemoveSmallParticles` |
| `shouldFillHoles` | `--fill-holes` | `false` | boolean | `tubeness` |
| `fillHolesValue` | `--fill-holes-value` | `0.0` | analysis-image radius px | `shouldFillHoles` |
| `sigmas` | `--sigmas` | `[12.0]` | analysis-image px list | `tubeness` |
| `thresholdHigh` | `--threshold-high` | `255` | intensity [0,255] | `tubeness` |
| `thresholdLow` | `--threshold-low` | `15` | intensity [0,255] | `tubeness` |
| `shouldUseFastSkeletonizer` | `--use-fast-skeletonizer` | `false` | boolean: true=Zha84, false=Lee94 | `always` |
| `shouldCapSkelIterations` | `--cap-skel-iterations` | `false` | boolean | `always` |
| `maxSkelIterations` | `--max-skel-iterations` | `25` | iterations | `shouldCapSkelIterations` |
| `shouldPruneSkeleton` | `--prune-skeleton` | `true` | boolean | `always` |
| `maxTerminalBranchLength` | `--max-terminal-branch-length` | `3.0` | analysis-image px | `shouldPruneSkeleton` |
| `shouldMergeNearbyJunctions` | `--merge-nearby-junctions` | `true` | boolean | `always` |
| `maxJunctionMergeDistance` | `--max-junction-merge-distance` | `3.0` | analysis-image px | `shouldMergeNearbyJunctions` |
| `shouldApplyLinearScale` | `--apply-linear-scale` | `false` | boolean | `always` |
| `linearScalingFactor` | `--linear-scaling-factor` | `1.0` | multiplier | `shouldApplyLinearScale` |
| `shouldNormalizeOutputData` | `--normalize-output-data` | `false` | boolean | `always` |
| `shouldDrawOutline` | `--draw-outline` | `true` | boolean | `always` |
| `outlineColor` | `--outline-color` | `"#ffff00"` | #RRGGBB | `shouldDrawOutline` |
| `outlineSize` | `--outline-size` | `1.0` | analysis-image px | `shouldDrawOutline` |
| `shouldDrawSkeleton` | `--draw-skeleton` | `true` | boolean | `always` |
| `skeletonColor` | `--skeleton-color` | `"#ff0000"` | #RRGGBB | `shouldDrawSkeleton` |
| `skeletonSize` | `--skeleton-size` | `2.0` | analysis-image px | `shouldDrawSkeleton` |
| `shouldDrawBranchPoints` | `--draw-branch-points` | `true` | boolean | `always` |
| `branchingPointsColor` | `--branching-points-color` | `"#0099ff"` | #RRGGBB | `shouldDrawBranchPoints` |
| `branchingPointsSize` | `--branching-points-size` | `4.0` | analysis-image px | `shouldDrawBranchPoints` |
| `shouldDrawConvexHull` | `--draw-convex-hull` | `true` | boolean | `always` |
| `convexHullColor` | `--convex-hull-color` | `"#ccffff"` | #RRGGBB | `shouldDrawConvexHull` |
| `convexHullSize` | `--convex-hull-size` | `1.0` | analysis-image px | `shouldDrawConvexHull` |
| `shouldIsolateBrightestChannelInOutput` | `--isolate-brightest-channel-in-output` | `true` | boolean | `always` |
| `shouldExpandOutputToGrayScale` | `--expand-output-to-gray-scale` | `true` | boolean | `shouldIsolateBrightestChannelInOutput` |
| `shouldComputeLacunarity` | `--compute-lacunarity` | `true` | boolean | `always` |
| `shouldComputeThickness` | `--compute-thickness` | `true` | boolean | `always` |
