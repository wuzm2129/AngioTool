# 2.3.2 当前发布产物

新增 `AngioTool2.3.2.jar`，用于完整参数CLI、可选导出、多输入与dry-run；SHA-256：`63ba2a779da0fc68a5ff8b872534c0db46ad5ada99712be85cc56631fec96501`。2.3.1作为独立验证的第一阶段版本保留；2.3.0及更早发布包也未修改。以下为历史记录。

# 2.3.1 完整配置发布

新增 `AngioTool2.3.1.jar`；SHA-256：`b0d1e09af87bdb9efdd95b5b0788842998e732bb9de11707b8dc856408ca51bb`。已有版本保持不变，不创建例行备份。以下为历史产物记录。

# 2.3.0 构建产物

2026-10-26 新增 `AngioTool2.3.0.jar`，作为当前 CLI / 桌面版本，SHA-256 为 `7dd6034c51e25e6cf897d47b33a7aeba2afbab8f3af290df54f633eef07be816`。保留已有版本，未执行旧包清理或创建编译备份。2.2.3 JAR 哈希仍为下表记录值。以下为 2026-10-06 的历史清理记录。

# 构建产物保留与清理记录

日期：2026-10-06。2.2.3 正式版已在提交 `be0184f` 中发布；按用户要求，仅整理项目根目录的旧构建 JAR。源码依赖、验证输出和骨架评估计划未清理。

## 当前保留

| 文件 | 用途 | SHA-256 |
| --- | --- | --- |
| `AngioTool2.2.3.jar` | 当前正式版本 | `f096259e7985f18a4d8b4afad25d235e058843d25820429162211b811f145fe7` |
| `AngioTool2.2.2.jar` | 上一正式版本，回退基线 | `cb397b68d7771607f692801b768f196983601e7ac10e516cd60a99965e8487e3` |
| `AngioTool2.2.1.jar` | 较早正式版本，XLSX 兼容性修复 | `087a69d253a57734231dfd4015e94facb45c97364148277d58e430c17fc279a6` |
| `AngioTool2.jar` | 原始基线，沿用项目保护约定 | `1b38d84aa33c4d3182971931a1d40bc50de22dc210c3ed6db04805f610d2502c` |

## 已删除

共删除 19 个 JAR：13 个带时间戳的备份、5 个 2.2.3 开发预览、旧 2.2 正式包。释放 22896195 字节（约 21.84 MiB）。未创建新的构建备份。

| 文件 | 类型 | 已有 Git 历史 |
| --- | --- | --- |
| `AngioTool2-20260511-191137-before-segment-metrics.jar` | 备份 | `e7d1262` |
| `AngioTool2-20260723-104353-before-quoted-path-support.jar` | 备份 | `5dc4617` |
| `AngioTool2-20260907-153058-654-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2-20260912-184140-007-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2-20260913-013033-494-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2-20260914-123557-515-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2-20260915-165112-772-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2.2-20260913-092302-289-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2.2-20260913-092830-856-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2.2-20260913-095102-225-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2.2-20260913-095519-673-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2.2-20260913-095919-126-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2.2-20260913-102352-454-before-compile.jar` | 备份 | 未纳入 Git |
| `AngioTool2.2.3-preview.jar` | 开发预览 | 未纳入 Git |
| `AngioTool2.2.3-preview2.jar` | 开发预览 | 未纳入 Git |
| `AngioTool2.2.3-preview3.jar` | 开发预览 | 未纳入 Git |
| `AngioTool2.2.3-preview4.jar` | 开发预览 | 未纳入 Git |
| `AngioTool2.2.3-preview5.jar` | 开发预览 | `9e6fe5a` |
| `AngioTool2.2.jar` | 旧正式版本 | `793c077` |

已纳入 Git 的删除文件仍可从表中对应历史提交恢复。未纳入 Git 的中间包已按此次授权删除，不保证能够恢复其原始二进制。后续完成开发修改时，提交对应源码、文档、必要测试与版本化 JAR；不创建例行编译备份。

本机完整清单（包含删除文件的大小和 SHA-256）保存在 `verification/release-2.2.3/artifact-cleanup.json`，此目录按现有规则不纳入 Git。保留文件清理前后 SHA-256 一致。
