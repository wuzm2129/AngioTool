# AngioTool 2.2.1 使用与验证说明

本版文件：AngioTool2.2.1.jar。原 AngioTool2.jar 保留，构建脚本默认输出新版本且拒绝以原包名称为输出。

## 新功能

- Skeleton & measurements 增加 Prune terminal branches，默认开启、阈值 3 px。取消勾选可对比未剪枝结果。
- 阈值是分析分辨率下的骨架路径长度，包含对角距离；不随 Measurement Scale 改变。
- 只进行一轮末端到交汇区的短支路剪枝，不级联删除；保留图像边缘端点、独立线段和闭环，不改血管掩膜。
- junction 蓝点按连通交汇区域统一选择代表像素，至少连接三条外部路径才计数。
- `Merge nearby junctions` 默认开启、阈值 3 px。仅当两个 junction 区域由不超过阈值的骨架段直接相连时合并；空间上靠近但骨架不连通的节点不会合并。
- 初始默认关闭 Max Skeleton Steps，运行至收敛；已有用户设置仍可能启用步数上限。未确认收敛时跳过剪枝，统计栏显示状态。
- 新增 Raw skeleton 视图。导出增加 raw-skeleton.png 和 pruned-skeleton.png，JSON/XLSX 记录剪枝参数、状态和删除像素数。
- 关闭剪枝仍使用新版 junction 计数规则；严格旧版对照请使用原 AngioTool2.jar。
- 骨架长度、端点、分段和基于骨架取样的直径可能变化；掩膜面积和分形维数不受剪枝直接影响。

## 明日验证建议

1. 用原包保留一组旧版结果，新版结果输出到另一目录。
2. 新版选择 Binary mask 和 Lee94，取消 Max Skeleton Steps，先关闭剪枝。
3. 再分别启用 2、3、5 px，比较蓝点、Raw skeleton 和 Skeleton overlay，以及删除像素数。
4. 检查真实血管芽、闭环及相邻血管连接是否保留。短芽也可能是真实结构，不能仅以蓝点数量下降判断正确性。

## 当前验证范围

已通过 Java 8 目标编译；已核对原包 SHA-256 未变化。按用户要求，运行时、图像效果、合成拓扑回归和 GUI 验证留待后续，尚未完成。此包应先用于对照验证。
