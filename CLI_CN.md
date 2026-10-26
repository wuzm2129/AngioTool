# AngioTool 2.3.2 命令行使用指南

CLI 与桌面共用分析算法、参数和导出实现；全部53个分析/显示参数都可配置。无参数启动仍进入桌面；CLI 强制无界面，不加载 GUI 用户偏好。服务器不需要显示器。构建及测试需要支持 `--release=8` 的 JDK（本次使用 ARM64 OpenJDK21）。

## 快速开始

```bash
java -jar AngioTool2.3.2.jar --help
java -jar AngioTool2.3.2.jar --version
java -jar AngioTool2.3.2.jar analyze --input image.png --output output/
java -jar AngioTool2.3.2.jar analyze --input images/ --recursive --output output/
java -jar AngioTool2.3.2.jar analyze --input mask.png --output mask-output/ --mode binary
```

单文件显式输入直接尝试解码；目录扫描识别PNG、JPG/JPEG、BMP、GIF、TIF/TIFF、PPM/PGM/PBM扩展名，实际可解码格式由现有读取器决定。首版及当前版都不新增逐帧图像分析。默认仅扫描当前目录，`--recursive` 扫描子目录；枚举不跟随符号链接，显式指定的输入根或文件链接会解析为实际路径。排除本次输出目录，其他历史结果目录应移出输入树。

图像及内部切片顺序执行，性能不同于GUI多图并行批处理。错误不弹窗，分析线程池会在退出时关闭。

## 完整配置管理

```bash
java -jar AngioTool2.3.2.jar config defaults --output parameters.json
java -jar AngioTool2.3.2.jar config validate --parameters parameters.json
java -jar AngioTool2.3.2.jar config resolve --parameters parameters.json \
  --threshold-low 20 --sigmas 5,8,12 --output resolved.json
java -jar AngioTool2.3.2.jar analyze --input image.png --output output/ \
  --parameters resolved.json
```

独立配置格式：

```json
{
  "schemaVersion": 1,
  "programVersion": "来源信息，可省略",
  "parameters": {
    "shouldUseBinaryMask": false,
    "sigmas": [5, 8, 12],
    "thresholdLow": 20
  }
}
```

`parameters` 字段名作为schema1接口固定，省略字段补程序默认值；defaults/resolve导出完整可重载配置。出现schemaVersion即按新格式解析，未知版本或字段不回退为旧快照。重复键、错误类型、非有限数被拒绝。CLI也继续接受旧 `overlay-data.json`；归一化结果JSON不能当作参数配置。

优先级为：默认值 → 文件 → CLI显式选项，与书写顺序无关。新配置先校验格式/类型，覆盖后检查业务范围，因此CLI可修复范围错误，不能修复类型错误。旧快照保留先校验再导入的历史规则。validate不接受参数覆盖，返回 `{valid,errors}` 并聚合字段路径错误。

全部名称、默认值、单位和启用条件见 [完整参数参考](CLI_PARAMETERS_CN.md)。布尔值显式使用true/false；数值与开关独立，例如：

```bash
java -jar AngioTool2.3.2.jar config resolve \
  --resize-image true --resizing-factor 0.5 \
  --apply-linear-scale true --linear-scaling-factor 2 \
  --remove-small-particles true --remove-small-particles-threshold 10 \
  --draw-outline false --skeleton-color '#00FF00' --skeleton-size 1.5
```

数组在JSON中是数组，在CLI中用逗号分隔；颜色用六位十六进制；亮度曲线用x,y,x,y坐标，坐标范围0–100。倍率不是百分比。业务范围仅校验激活参数，休眠值仍保留，配置命令在stderr列出休眠字段；曲线配对/坐标安全和有限百分比表示等初始化所需条件始终检查。

兼容简写如下。它们会同时设置数值与开关，与完整选项重复赋值同一字段时直接报冲突：

| 简写 | 含义 |
| --- | --- |
| `--mode tubeness\|binary` | 输入模式 |
| `--skeleton zha84\|lee94` | 骨架方法 |
| `--scale FACTOR` | 正测量倍率；1关闭测量缩放 |
| `--resize FACTOR` | 正输入倍率；1关闭输入缩放；二值模式忽略缩放 |
| `--prune PX\|off` | 非负末端长度阈值或关闭 |
| `--merge PX\|off` | 非负交汇点距离阈值或关闭 |
| `--normalize true\|false` | 归一化输出 |

无配置文件时，默认剪枝和合并开启，阈值均3px，与桌面一致。defaults/resolve默认写stdout；`--output FILE`写文件，默认拒绝覆盖，`--overwrite`授权替换普通文件，采用临时文件替换；不读取/写入GUI偏好。

## 多输入、清单与筛选

```bash
java -jar AngioTool2.3.2.jar analyze \
  --input dataset-a/ --input dataset-b/ --input standalone.png \
  --recursive --include '**/*.png' --exclude '**/temporary/**' \
  --output results/
java -jar AngioTool2.3.2.jar analyze --input-list inputs.txt \
  --recursive --output results/
```

`--input`、`--input-list`、`--include`、`--exclude`可以重复。清单为UTF-8，一行一路径，相对路径以清单所在目录为基准；忽略空行、允许开头BOM，不解析注释或每图参数。所有输入共享一份配置。

根和文件按规范化真实路径去重并稳定排序。重叠根中的同一文件归属最具体目录；显式文件优先于目录根。先确定归属，再按所属根的相对路径筛选，显式文件使用文件名。include为OR，exclude为OR且优先；无include则全部纳入。glob中 `/` 是分隔符，`*`不跨目录，`**`可跨目录，`?`匹配单个非分隔符字符，其余字符按字面匹配，不支持字符组。`**/*.png`也匹配根目录的 `a.png`。应给glob加引号以免shell提前展开。

例如同时输入 `data/` 和 `data/nested/`，`nested/b.png`归属后者，相对路径为 `b.png`；`--include 'b.png'`会选中，`--include 'nested/b.png'`不会。空输入或筛选后无图像返回2。

## 执行计划和dry-run

```bash
java -jar AngioTool2.3.2.jar analyze --input images/ --recursive \
  --exports json,mask --output output/ --dry-run > plan.json
```

与正式执行共用规划函数，检查配置、输入枚举、输出映射及当前冲突。dry-run不创建目录、临时文件或报告，不解码图像；仅stdout输出JSON计划。计划列出写入、可能删除、明确保留的产物，骨架图标记为条件生成。它不保证解码/分析/实际写入成功，也不能验证全部权限/磁盘容量或消除运行时文件系统变化。正式运行会再次检查必要路径。

## 导出选择与覆盖

省略 `--exports` 保持2.3.0固定结果包。显式使用逗号分隔的非空集合：

| 家族 | 产物 |
| --- | --- |
| `overlay` | result.png叠加图 |
| `mask` | result vessel-mask.png |
| `raw-skeleton` | result raw-skeleton.png（分析生成时） |
| `pruned-skeleton` | result pruned-skeleton.png（分析生成时） |
| `json` | result overlay-data.json；归一化开启时另有result normalized-data.json |
| `xlsx` | 批次results.xlsx；归一化开启时另有normalized-results.xlsx |

run-report.json始终生成并受覆盖保护；不支持完全无输出。示例：

```bash
java -jar AngioTool2.3.2.jar analyze --input images/ --recursive \
  --exports json,mask --normalize true --output output/
```

单根保持原有结构：`output/images/<相对输入路径，保留扩展名>/result*`。同名异扩展名使用独立目录。多个去重后的输入根使用 `output/images/<根名>-<绝对规范化路径SHA256前12位>/<相对输入路径>/result*`；输入顺序不影响命名，移动数据根会改变命名空间，检测映射冲突会报错。工作簿及报告位于output根目录。

输出不能是任何输入的祖先或等于输入，可以在输入目录内，此时自身被排除。选中输出的子路径与父目录不接受符号链接或普通文件占位。

默认在执行前检查全部获准写入/删除目标，冲突返回2且不修改结果。`--overwrite`授权替换本次生成的普通文件，XLSX始终重新创建，不追加；不递归清理用户文件。

- 默认结果包模式：成功重算会清理已不生成的可选产物，关闭归一化会删除旧归一化文件/工作簿，保持2.3.0语义。
- 显式exports模式：未选文件不参与覆盖检查，也不删除；不再生成的可选文件同样保留。关闭归一化也不会顺便删除旧归一化产物。报告以notUpdated标识已存在但本轮未更新的已知产物。未知用户文件始终保留，不逐一列举。
- 失败/未执行图像可能保留上轮产物。以本轮报告、退出码和实际outputs列表为准，不能根据文件存在就认定本轮成功。

单图先在临时目录编码，再发布选中文件；整批及多文件发布不是事务，错误可能留下部分结果。报告通过临时文件替换；不支持多个进程同时写同一输出目录，不应在运行中修改输入、配置及输出路径。

## 反馈与退出码

进度/错误写stderr，stdout输出JSON运行报告，可重定向：

```bash
java -jar AngioTool2.3.2.jar analyze --input images/ --output output/ \
  > report.json 2> progress.log
```

报告保留成功/失败/未执行计数、指标有效性、原因及耗时，新增完整schema1生效配置、配置来源版本/路径、CLI覆盖选项，以及每图输出目录/实际写入路径/notUpdated。共享outputs列出已完成写入的工作簿和报告，条件骨架只有实际成功写入才列出。配置错误没有分析报告。报告无法保存时stdout可能为空，必须同时检查退出码和stderr。

| 退出码 | 含义 |
| --- | --- |
| 0 | 全部分析/导出完成；空掩膜等可能使metricsValid=false；配置验证或dry-run成功 |
| 1 | 单图解码/分析/导出失败，或共享输出失败 |
| 2 | 用法、参数、枚举或路径冲突错误，包括空输入 |

单图错误默认继续；`--fail-fast`在首个单图失败后停止，剩余图像记not-run。共享工作簿或报告写入失败直接结束任务；OOM等致命错误不作为普通坏图吞掉。

## 验证

```bash
python3 compile.py
python3 benchmarks/run_regression.py --jar AngioTool2.3.2.jar
python3 benchmarks/run_cli_tests.py --jar AngioTool2.3.2.jar
python3 benchmarks/run_config_tests.py --jar AngioTool2.3.2.jar
python3 benchmarks/run_plan_tests.py --jar AngioTool2.3.2.jar
```

集成脚本在Linux上使用Python标准库和JDK运行，无显示、隔离user.home、设置超时。原CLI脚本用resource测试输出失败，对照需要保留2.2.3 JAR。日志在verification/对应独立目录；实际结果和GUI验证边界见 [验证记录](VALIDATION_CN.md)。

暂不支持多图并行、断点恢复、每图独立配置、HTTP服务和多帧分析。
