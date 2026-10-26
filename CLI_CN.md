# AngioTool 2.3.1 命令行使用指南

CLI 与桌面共用分析算法、参数快照和导出实现。无参数启动仍进入桌面；CLI 强制无界面，不加载 GUI 用户偏好。服务器只需兼容的 Java 运行时，无需显示器。源码构建及测试需要支持 `--release=8` 的 JDK（本次使用 ARM64 OpenJDK 21）。

## 2.3.1 完整参数与配置

全部 53 个参数均可通过 CLI 设置，完整名称、类型、单位和默认值见 [参数参考](CLI_PARAMETERS_CN.md)。开关显式使用 true/false，数值选项不隐式启用功能；2.3.0 简写仍保留其自动启用行为。简写和完整选项同时赋值同一字段会报错，与命令行顺序无关。

```bash
java -jar AngioTool2.3.1.jar config defaults --output parameters.json
java -jar AngioTool2.3.1.jar config validate --parameters parameters.json
java -jar AngioTool2.3.1.jar config resolve --parameters parameters.json \
  --threshold-low 20 --sigmas 5,8,12 --output resolved.json
java -jar AngioTool2.3.1.jar analyze --input image.png --output output/ \
  --parameters resolved.json
```

独立配置格式为 `{ "schemaVersion": 1, "parameters": { ... }, "programVersion": "来源版本，可选" }`。参数名是稳定的 camelCase 字段名，省略字段补程序默认值；defaults/resolve 导出完整可重载配置。出现 schemaVersion 即按新格式解析，未知版本或字段不回退为旧快照。重复键、错误类型、非有限值被拒绝。颜色为六位十六进制，数组在 JSON 中为数组，在 CLI 中使用逗号分隔。

新配置的格式/类型错误不可由 CLI 修复；业务范围错误可在 resolve/analyze 合并 CLI 后修复。validate 不接受参数覆盖，返回 `{valid, errors}` 并聚合字段路径错误。旧 overlay-data.json 保留原导入器先验证的规则，范围错误不支持覆盖修复。业务范围只检查激活参数；休眠参数保留并在配置命令 stderr 列出。亮度曲线配对、坐标 [0,100] 与有限百分比表示等结构安全条件始终检查，因为分析初始化也会读取曲线。

defaults/resolve 默认写 stdout；使用 --output 时默认不覆盖，--overwrite 才替换普通文件。输出路径拒绝符号链接，不修改 GUI 偏好。

## 基本使用

```bash
java -jar AngioTool2.3.1.jar --help
java -jar AngioTool2.3.1.jar --version
java -jar AngioTool2.3.1.jar analyze --input image.png --output output/
java -jar AngioTool2.3.1.jar analyze --input images/ --recursive --output output/
java -jar AngioTool2.3.1.jar analyze --input mask.png --output mask-output/ --mode binary
java -jar AngioTool2.3.1.jar analyze --input image.png --output restored/ \
  --parameters 'previous/result overlay-data.json' --prune off --merge off
```

单文件输入直接尝试解码。目录扫描识别 PNG、JPG/JPEG、BMP、GIF、TIF/TIFF、PPM/PGM/PBM 扩展名，按路径排序；实际可解码格式由现有图像读取器决定。多帧图像不新增逐帧分析支持。默认仅扫描当前目录，`--recursive` 扫描子目录但不跟随目录或文件符号链接。排除本次输出目录；其他历史结果目录应移出输入树。输入文件显式使用符号链接时解析为实际文件。

首版一次接受一个输入文件或目录，逐图顺序执行，内部切片也使用顺序执行器。性能与 GUI 多图并行批处理不同。CLI 初始化不调用桌面 Lee94 弹窗路径；所选算法所需资源异常通过进程失败反馈。

## 参数

优先级固定为：程序默认值 → `--parameters` 文件 → CLI 显式选项，与选项书写顺序无关。复用现有导入器的旧版本兼容与验证规则；归一化 JSON 不能当作参数快照。每张成功导出的 JSON 以及运行报告记录生效参数。未提供参数文件时，默认剪枝和合并均开启，阈值为 3 px，与当前桌面默认值一致。

| 选项 | 含义 |
| --- | --- |
| `--mode tubeness\|binary` | 图像分割或直接二值掩膜模式 |
| `--skeleton zha84\|lee94` | 骨架算法 |
| `--scale FACTOR` | 测量倍率，有限正数；1 关闭测量缩放 |
| `--resize FACTOR` | 输入尺寸倍率，有限正数；1 关闭缩放；二值模式忽略输入缩放 |
| `--prune PX\|off` | 末端剪枝阈值，分析图像像素，有限非负数；off 显式关闭 |
| `--merge PX\|off` | 交汇点合并距离，分析图像像素，有限非负数；off 显式关闭 |
| `--normalize true\|false` | 开启或关闭额外归一化 JSON/XLSX |

其他完整参数可通过新配置、旧快照或完整 CLI 选项提供。无效选项、重复选项、缺失值及配置范围错误会在分析前拒绝。

## 输出与覆盖

```text
output/
  run-report.json
  results.xlsx
  normalized-results.xlsx                 # 启用归一化时
  images/
    <相对输入路径，保留扩展名>/
      result.png                           # 叠加图
      result vessel-mask.png
      result raw-skeleton.png              # 分析生成时
      result pruned-skeleton.png           # 分析生成时
      result overlay-data.json
      result normalized-data.json          # 启用归一化时
```

单文件以其文件名作为相对路径。例如 `sample.png` 和 `sample.tif` 分别写入不同目录。输出必须不包含输入路径，可以位于输入目录内；自身会被扫描排除。输出子路径不接受符号链接。

默认在执行前检查所有保留输出名，任一冲突返回 2，不修改已有结果。`--overwrite` 允许替换这些普通生成文件，工作簿始终重新创建，不追加旧数据；不会递归删除结果目录中的其他文件。成功重算会删除该图已不再生成的可选产物，关闭归一化后也删除旧的归一化工作簿。失败或未执行图像可能保留上一轮产物，**以本轮报告的状态为准**。

单图先在临时目录完成编码再发布文件；多文件发布和整批输出不是事务，发布中途失败可能留下部分结果。运行报告通过临时文件替换，写入失败反馈到 stderr。不要让多个进程同时使用同一输出目录，也不要在运行中改变输入和输出路径。

## 进度、报告和退出码

进度和错误写入 stderr，stdout 输出一个 JSON 运行报告，可重定向使用：

```bash
java -jar AngioTool2.3.1.jar analyze --input images/ --output output/ \
  > report.json 2> progress.log
```

报告包括软件版本、生效参数、耗时、成功/失败/未执行数量，以及每张图的输入路径、状态、指标有效性和原因。配置错误没有运行报告。共享工作簿或报告写入失败时，进程返回 1；若报告自身无法保存，stdout 也可能为空，应同时检查退出码和 stderr。

| 退出码 | 含义 |
| --- | --- |
| 0 | 全部图像执行和导出成功；空掩膜等可能导致 `metricsValid=false`，需单独检查 |
| 1 | 单图解码、分析或导出失败，或共享输出失败 |
| 2 | 用法、参数、路径冲突或输入枚举错误，包括空输入目录 |

单图错误默认记录后继续；`--fail-fast` 在首个单图错误后停止，剩余图像记为 `not-run`。共享工作簿写入失败直接结束任务，OOM 等致命错误不作为普通坏图吞掉。

## 验证

```bash
python3 compile.py
python3 benchmarks/run_regression.py --jar AngioTool2.3.1.jar
python3 benchmarks/run_cli_tests.py --jar AngioTool2.3.1.jar
```

CLI 集成脚本在 Linux 上运行，使用 Python 标准库（含 `resource`）和 JDK；清除显示环境、隔离用户目录，每次子进程设置超时，验证进程正常退出。结果写入 `verification/cli-*`。对照测试需要保留 `AngioTool2.2.3.jar`。实际测试结果与 GUI 验证边界见 `VALIDATION_CN.md`。
