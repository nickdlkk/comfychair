# ComfyChair Qwen 2.1 工作流 400 校验故障 — 工作移交

**记录日期：** 2026-10-27
**最新状态：** v0.8.24 / code51 功能与40个JVM测试、APK构建已验证；本次获授权更新README并发布GitHub，发布结果以本文最前方「GitHub发布验收」为准。无设备UI或ComfyUI实测；下文未提交/无Release等描述仅作历史。

## GitHub发布验收（2026-10-27）

- README.md 更新英文使用说明；仓库原无中文README，新增 README.zh-CN.md 并互链。明确尺寸映射/精确原图预设、右上Info、文件大小不可用、滑动对比及全屏按住/松开；保留会话缓存及实机未验收限制。
- 发布使用现有 v0.8.24 / code51，不再次升版。本次只改文档，复用已验证APK：源APK与分享APK均为86,687,706 bytes，SHA256 `12f898194707681fbdabc119f3220e9e4a1b94de6584cea1a462460db7713277`；源码及build.gradle时间均不晚于APK。
- 发布前再次执行 testDebugUnitTest 成功（31s，编译与测试任务UP-TO-DATE），XML仍为40 tests / 0 failures / 0 errors / 0 skipped，证明Gradle输入未变；之前完整构建日志见下文。aapt确认 `sh.hnet.comfychair` / code51 / v0.8.24。
- 已fetch个人fork确认 nick/main 与起始HEAD一致；目标tag及Release原不存在。只提交明确列出的功能、测试、版本及文档文件，保留无关 `.agents/skills` 和已有stash。
- 发布URL、最终ref与tag CI结果将在发布后补入本节。


## v0.8.24 结果信息与全屏长按对比 — 最新验收

- 结果预览右上 Info 与主原图 Info 使用相同默认 IconButton / 8dp inset；点击展开 top52/end8 半透明面板，显示实际解码像素尺寸。`fetchImage` 解码完整响应但不保留编码字节；本版明确显示「文件大小不可用」，没有用 bitmap RAM/重新压缩大小冒充原文件大小。对比开关位于信息下面，移除旧底部浮动开关，保留原有拖动/Slider对比。
- 普通结果预览点击进入大图；仅具有本次编辑提交原图关联的结果支持单指静止长按。按住显示生成时槽1快照，松开/取消/移动超slop/多指/页面离开/生命周期暂停恢复结果；不锁存、不触发tap。沿用现有手势循环和同一full viewport graphicsLayer，不因临时换图重置缩放/平移。图库、原图槽、视频、未知历史不增加比较。
- Intent只传随机process-local能力票据和promptId，不传bitmap/byte extras或可任意指定的文件路径。接收端对server/prompt/filename/subfolder/type精确校验；canonical file必须是当前cacheDir直属的`editing-original-*.png`且存在非空。票据最多64个，溢出安全清空；进程死亡/票据淘汰/系统缓存回收后无长按对比。源快照仍沿用session关联与系统cache生命周期，不删除历史文件。IO下采样解码最长边<=2048，request/item/server检查防异步旧解码覆盖。新增4个JVM测试覆盖hold/release/cancel/new gesture、无资格、server/result/file身份及缓存来源校验。
- 源码改动：`MediaViewerActivity.kt`、`ui/screens/{ImageToImageScreen,MediaViewerScreen}.kt`、`ui/components/{ImageViewer,EditingImageComparison}.kt`、`viewmodel/{ImageToImageViewModel,MediaViewerViewModel,EditingViewerComparison}.kt`、`EditingViewerComparisonTest.kt`、中英文strings、`app/build.gradle.kts`。本次版本只从50/v0.8.23升级一次到51/v0.8.24。此前未提交修改与`.agents/skills`未清理。
- 命令：`./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=editing-compare-info`。
- 实际最终结果：`BUILD SUCCESSFUL in 2m 46s`；`44 actionable tasks: 13 executed, 31 up-to-date`；XML汇总40 tests / 0 failures / 0 errors / 0 skipped；`git diff --check`通过。日志：`/root/.hermes/profiles/dev/cache/scratch/comfychair-editing-info-verified-build.log`。既有always-true/false/TabRow deprecated警告仍有。
- 构建时环境已重启，旧`/dev/shm` Gradle cache符号链接失效，首次因此失败；保留三个失效链接到dev scratch、原位置改普通cache目录并重下载依赖。一次foreground超时后确认无存活Gradle再后台重试；修复nullable server编译错误后最终全量命令通过。没有后台构建未完成。旧9010服务也不在线，重新启动仅服务`/root/share/comfychair`的持久server `proc_bf367bcc288c`并handoff父agent。
- APK source：`/root/comfychair/app/build/outputs/apk/debug/comfychair-v0.8.24-debug-editing-compare-info-20261027-1635.apk`。分享：`/root/share/comfychair/comfychair-v0.8.24-debug-editing-compare-info-20261027-1635.apk`。
- 下载：`http://192.168.4.168:9010/comfychair-v0.8.24-debug-editing-compare-info-20261027-1635.apk`。aapt+metadata确认`sh.hnet.comfychair` / versionCode51 / versionName v0.8.24；真实LAN无代理GET200、Content-Length和下载完整字节数均86,687,706，SHA256 `12f898194707681fbdabc119f3220e9e4a1b94de6584cea1a462460db7713277`与本地一致。
- 限制/待实机：adb设备列表为空。未验证真实设备触摸交互/系统手势取消、旋转/后台切换、缩放后长按或ComfyUI生成业务；JVM测试是状态/身份规则，不是Compose触摸instrumentation。需在设备安装新包，生成编辑结果后验证信息开关/面板内拖动对比、长按松手、移动/多指取消、缩放位置保持，以及排队换主图与切服务器不会串原图。

## v0.8.23 编辑尺寸预设与对比 — 历史验收

- 用法：图生图→编辑→配置→分辨率预设→「与原图相同尺寸」。显示主原图（槽1）的实际解码宽高；没有主图时禁用；无宽高映射或其他模式不显示。仅写已映射字段，走 `onEditingWidthChange` / `onEditingHeightChange` 原有校验及每工作流持久化，不按8/64取整。超出现有1..4096限制时仍精确填入并显示校验错误，不暗改数值。
- 生成成功后进入预览→「原图 / 结果对比」。原图在左、结果在右，可在图上左右拖动竖线或使用可访问滑块；分隔位置限制0..1。两图先各自按完整相同视口居中 Fit，再裁剪左右半边，不对半幅单独缩放；宽高比不同时保留黑边、不拉伸。比较时禁用 pager 横滑，不叠加全屏查看器的缩放/平移手势。关闭对比恢复原有点击全屏行为；不向图库/其他屏幕添加对比入口。
- `PreparedImageWorkflow` 同时携带工作流与独立 cache 文件快照（不传 bitmap/大 byte extras，不复制大 bitmap）。准备阶段使用上传所用同一 state.sourceImage。普通生成、批量、队首、占位符确认全部经过统一提交包装，提交回调在注册 job 前按 serverId + promptId 关联快照。切换当前源图不会改变已排队结果对应的原图；未知/进程恢复任务无关联时不显示入口，绝不拿当前源图猜测。占位符列表随准备结果返回，避免旧事件/旧 prepared 对象错配。
- 对比原图在 IO 线程按最长边不超过2048下采样解码，仅额外持有当前比较 bitmap；不手工 recycle Compose 使用中的 bitmap。切换模式、实时预览、清空、非图片结果或找不到原图时清理对比状态；异步 fetch token/server 校验防止过期结果与新预览配对。
- 限制：对比仅当前 ITI 结果预览，不是图库永久编辑历史。关联存在 ViewModel 会话内；进程重启/未知恢复结果不提供比较。cacheDir 快照可能被系统回收（此时无比较入口），并随准备次数占用缓存空间；未添加自动删除已有数据。多图编辑比较的「原图」始终是主图槽1；比较解码可能下采样，但预设用完整主图尺寸。没有设备/服务实测。
- 新增6个 JUnit：3个预设测试覆盖模式/无图/无映射可用性、1237×853精确值、独立映射回调；3个比较测试覆盖分隔钳制、不同宽高比 Fit 几何、排队原图按 prompt 关联且不跟随选图或完成顺序变化。原30个测试全部保留通过。
- 最终命令：`./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=editing-compare`。
- 真实结果：`BUILD SUCCESSFUL in 2m`，`44 actionable tasks: 12 executed, 32 up-to-date`；XML汇总36/0/0/0；`git diff --check`通过。日志：`/root/.hermes/profiles/dev/cache/scratch/comfychair-editing-compare-build.log`。已有 always-true/false、TabRow deprecated 警告未阻断构建。
- 源 APK：`/root/comfychair/app/build/outputs/apk/debug/comfychair-v0.8.23-debug-editing-compare-20261027-1350.apk`。按本次 metadata 选取；aapt确认 applicationId `sh.hnet.comfychair`、versionName `v0.8.23`、code50。
- 分享 APK：`/root/share/comfychair/comfychair-v0.8.23-debug-editing-compare-20261027-1350.apk`，86,654,462 bytes。
- 下载：`http://192.168.4.168:9010/comfychair-v0.8.23-debug-editing-compare-20261027-1350.apk`。复用仅分享 `/root/share/comfychair` 的9010服务；完整无代理 LAN GET HTTP200，Content-Length及实际下载86,654,462 bytes与本地一致。
- SHA256：`1f7e8490f9db16aabfec6c8791930381f01f6db52aad072a1372eec278007bc8`（本地与完整下载一致）。
- 设备验收待做：adb devices空列表；安装新包验证菜单/无图禁用/单映射、配置持久化、排队后换主图/插队/批量、不同宽高比/拖动两端/旋转，以及读取最终prompt和执行ComfyUI。

## v0.8.22 编辑宽高修复 — 最新验收

- 根因：`buildEditingModeParameterConfig` 未构建 width/height，`ImageToImageUiState` 无 editingWidth/Height，提交阶段只读取旧保存值/defaults，映射复选框无法转化成可编辑的生图配置。
- 新增独立 editingWidth/Height 和对应错误、回调；分别由 `caps.hasWidth` / `caps.hasHeight` 控制显示。复用现有 `DimensionStepperRow` 渲染，不改 inpainting 状态。
- `EditingDimensions.kt` 统一恢复优先级：每工作流已保存值 → 导入 defaults → 与图像生成一致的可见 UI 初值 1024。提交仅传已映射的当前有效状态，不在提交阶段硬编码尺寸或猜源图尺寸；没有映射的维度传 null。映射维度空值/非整数/越界阻止准备和生成；临时无效输入不抹掉上次有效保存值，隐藏维度保留原保存值。
- 9 个新增 JVM 回归测试覆盖四种能力组合、独立宽/高、当前值/错误/专用回调、保存 JSON 往返、defaults 优先级、错误重置、无映射不提交、无效值阻止、inpainting 隔离。
- 完整命令：`./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=qwen21-editing-dimensions`。
- 最终输出：`BUILD SUCCESSFUL in 3m 2s`，`44 actionable tasks: 12 executed, 32 up-to-date`，XML 汇总 30 tests / 0 failures / 0 errors / 0 skipped。
- APK：`/root/share/comfychair/comfychair-v0.8.22-debug-qwen21-editing-dimensions-20261027-1258.apk`（86,586,138 bytes）。源产物由 `app/build/outputs/apk/debug/output-metadata.json` 读取；metadata 和 `aapt dump badging` 均确认 `versionName=v0.8.22` / `versionCode=49`，未再次升版。
- 下载：`http://192.168.4.168:9010/comfychair-v0.8.22-debug-qwen21-editing-dimensions-20261027-1258.apk`。
- SHA256：`d904b5c75cd125e57cae78538e69301023a96a0f8ecd61358d1cdcad6bb65b2a`。复用已有 9010 server（document root `/root/share/comfychair`），无代理完整 LAN GET HTTP 200，下载字节数和 SHA256 均与本地一致。
- 构建日志：`/root/.hermes/profiles/dev/cache/scratch/comfychair-editing-dimensions-build.log`。既有 Kotlin always-true/false 与 TabRow deprecated 警告仍存在，不阻塞构建；本次没有构建错误。
- 未发现项目或祖先 AGENTS.md；原有工作树改动及未跟踪 `.agents/skills/*` 全部保留。没有新起后台进程，没有提交/推送/发布。
- 实机待验收：打开图生图→编辑，选择映射 EmptyLatentImage width/height 的工作流，配置中分别改宽高，切换工作流/重开验证恢复，再读取 `last_prepared_workflow.json` / `last_submitted_prompt.json` 核对最终节点数值并由 ComfyUI 实际执行。


## 编译及分享验收

- 已补齐 SDK，`local.properties` 使用 `/root/Android/Sdk`。
- 执行 `./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=qwen21-validation`，返回 `BUILD SUCCESSFUL in 2m 51s`。
- APK：`/root/share/comfychair/comfychair-v0.8.21-debug-qwen21-validation-20261027-1159.apk`，86,569,750 bytes。
- 下载：`http://192.168.4.168:9010/comfychair-v0.8.21-debug-qwen21-validation-20261027-1159.apk`。
- HTTP server 仅分享 `/root/share/comfychair`，后台进程 `proc_704c9155aa75`；localhost/LAN HEAD 均 200，完整 LAN GET 的字节数及 SHA256 与本地一致。
- SHA256：`1c6b6b1e73f0ffecf89d55f7c0ea519b8a4a50c4b2eaccd618965a473dbdebda`。
- 构建日志：`/root/.hermes/profiles/dev/cache/scratch/comfychair-sdk/build.log`。以下构建阻塞描述为历史记录，已解除。

## 本次续接结果（覆盖下文历史草稿描述）

- 已删除宽泛 `fillMissingWorkflowParameters`，不再根据同名输入为所有节点猜数值、采样器或 EXR/AVIF 默认值。
- 新增纯 Kotlin `util/WorkflowPromptFinalizer.kt`：两套节点属性编辑共用实现，支持 raw/`nodes`/`prompt`，保护连接数组并保持 Long 精度。仅在 `SaveImageAdvanced` 的 `format` 明确为 `png` 时补缺失/null/空白的扁平 `format.bit_depth=8-bit`、`format.input_color_space=sRGB`；不覆盖显式值或未解析占位符。
- 修正 ITI editing 对裸 prompt 的提前返回，确保执行后处理、bypass 和快照；正常分支也经过 bypass。
- editing 宽高参数改为 nullable，由 ViewModel 读取已保存宽高，其次工作流 defaults；不再硬编码 1024，不猜源图尺寸。保存 editing 参数时保留已有宽高。无可用尺寸时仍可能存在未绑定占位符，这不是已修复声明。
- 新增 `WorkflowPromptFinalizerTest.kt`，实际使用 Gradle 8.13 内自带 Kotlin 2.0.21 编译器编译真实源码、org.json:20240303 和 JUnit 4.13.2 执行，父 agent 重跑返回 `OK (6 tests)`；`git diff --check` 通过。仅验证该纯 Kotlin utility，不代表 ViewModel/完整 app 编译通过。
- 离线 Gradle 仍因缺少 `com.android.application:8.13.2` 失败；`local.properties` 指定 SDK 目录不存在。需要下载 SDK/依赖并隔离构建产物，不能在紧张根分区盲目构建或自动清理用户数据。
- 已确认导入/更新代码会把未映射 optional placeholders 置空，且 numeric-string defaults 转换与更新保留 defaults 存在缺陷；未取得 Qwen 原始/最终 JSON，尚不确认其为本次实际故障链，未擅自改变所有导入行为。
- `/root/comfychair` 和 `/root/share` 下找到的历史调试包不是本次 Qwen 2.1；仍需要本次 DEBUG 包和服务端 object_info。未确认模型名截断，未推送、未发布 APK。

## 1. 用户问题

ComfyChair 通过 ComfyUI `/prompt` 提交 Qwen 2.1 工作流，服务器返回 HTTP 400 `prompt_outputs_failed_validation`。这属于**提交前 prompt 输入校验失败**，还没有进入 ComfyUI 执行/生成阶段。

错误覆盖这些节点和输入：

| 节点 ID / class_type | 当前报错 | 初步判定 |
|---|---|---|
| `461` / `SaveImageAdvanced` | 缺 `format.input_color_space`、`format.bit_depth` | prompt API 输入键缺失；不是缺少 UI 上的 `widgets_values` 本身。ComfyUI 报错 detail 去掉了 `format.` 前缀，`extra_info.input_name` 给出了完整键。PNG 常规值为 `format=png`、`format.bit_depth=8-bit`、`format.input_color_space=sRGB`。 |
| `518` / `ImageScaleToTotalPixels` | `megapixels=""`，不能转 FLOAT | 实际提交值空，不是仅 UI 显示问题。 |
| `504` / `EmptyLatentImage` | `height="{{height}}"`、`width=""` | 高度占位符未替换，宽度为空；服务器收到的是无效值。 |
| `506` / `KSampler` | `cfg=""`、`sampler_name=""`、`scheduler=""` | 缺少有效采样参数。 |
| `503` / `VAELoader` | `vae_name` 不在可用列表 | 报错显示值被截断；服务器可选项中确有完整文件名 `qwen_image_2.1_vae_bf16.safetensors`，需对比最终 prompt 的未截断实际字符串。 |
| `502` / `CLIPLoader` | `clip_name` 不在可用列表 | 报错值同样截断；需核对最终值是否与服务器列表精确相同，列表中包含 `qwen/qwen3vl_8b_int8_convrot.safetensors`。 |

错误值列表看起来还可能是**多个工作流节点值没有映射/替换**共同导致的，并非纯粹的尺寸 UI 问题。不得仅凭截断后的错误文字断定模型文件名实际被截断。

## 2. 已查源码/配置

- 项目：`/root/comfychair`
- 当前分支：`main`，起始状态 `main...origin/main [领先 51]`。
- 启动时已有多份未跟踪 `.agents/skills/*` 目录；未触碰、未清理。
- Qwen 2.1 工作流没有在应用 `app/src/main` 资源中找到；需要使用用户导入的工作流 JSON或日志包分析。
- 相关执行路径：
  - `app/src/main/java/sh/hnet/comfychair/WorkflowManager.kt`
  - `app/src/main/java/sh/hnet/comfychair/viewmodel/ImageToImageViewModel.kt`
  - 最终提交证据应检查 DEBUG 日志包中的 `last_submitted_prompt.json`、`last_prepared_workflow.json`、`selected_workflow_original.json`、`workflow_values_snapshot.json`、`generation_context.json`。
- 查询 ComfyUI 官方文档/工作流示例，确认 `SaveImageAdvanced` prompt 的扁平键是 `format.bit_depth`、`format.input_color_space`。并非 `bit_depth`、`input_color_space` 两个裸键。
- 用户初问 EmptyLatentImage 是否“无法识别设置宽高”。证据实际指向更具体的问题：提交 prompt 的 width/height 值为空/未替换；须通过最终 JSON 判明是工作流缺映射、参数没有写入，还是后续编辑覆盖。

## 3. 当前源码工作树改动（未验收）

### `WorkflowManager.kt`

1. 新增 `fillMissingWorkflowParameters(...)`：尝试仅对空/未解析的 numeric/sampler 输入补充默认值，并在 `SaveImageAdvanced` 上补默认格式键。
2. 将该 helper 调用接入 TTI 和 ITI editing 处理路径；ITO editing 新增 `width`、`height` 参数，调用 common placeholder replacement，并尝试传 1024×1024。
3. Helper 会保留已有非空格式选择；当前草稿对 EXR/AVIF 只补空值，但默认值/条件组合仍必须依据目标 ComfyUI 版本和完整节点输入验证。

### `ImageToImageViewModel.kt`

- ITI editing 调用增加 `width = 1024`、`height = 1024` 默认值。当前编辑 UI 状态本身没有 width/height 字段；这是临时兜底，不等于查明正确尺寸来源。

### 重要：本次最后源码尚有待修正/复核点

- `WorkflowManager.kt` 中 ITI editing 分支有二阶段图像节点处理：先在前段 try 内计算 `finalJson` 并保存快照；之前修改了后续 `finalJson` 路径而未经构建校验。必须仔细检查同一函数是否有重复变量、旧 `finalJson` 再赋值/覆盖或快照未包含修复后的最终对象；不可直接发布。
- TTI 的参数修复在 `applyNodeAttributeEdits` 之前调用，因此节点属性编辑可能重新写入空值。应将安全补值放在 node edits 之后，并确认 bypass、LoRA 注入等后置变换没有再次改变输入。
- Helper 当前按输入 key 遍历，但数值默认使用 ITI 参数（如 CFG 1.0 / megapixels 2.0）也可能被 TTI 工作流误用。应改为按 `class_type` 精确限定输入和对应工作流参数，而不是对所有节点上的同名字段一视同仁。
- Sampler/scheduler fallback 不能盲用字符串默认，应保证在服务端 `object_info` 允许列表中。示例里 `euler`、`simple` 是候选，但当前应以连接服务器实际列表及工作流意图为准。
- 对用户空的 VAE/CLIP 选择，helper 没有修复模型字段。后续优先读取最终 prompt 和完整 `received_value` 判断字段映射、字符串差异和节点候选列表；不要猜测截断后的文件名。
- 不应把宽高默认为固定 1024×1024 后就宣称修复。最好从 TTI UI state 或 workflow defaults 传入；对于 ITI editing，应明确是否应由图像尺寸/模型节点决定，或只填工作流原有有效值。

## 4. 验证已尝试及实际结果

- `git diff --check`：通过（当时检查的差异无 whitespace error）。
- 用 Python 写了一个**临时独立 fixture 仿真**，验证“空值填默认、已设置 EXR 保留”这一数据转换意图；这不执行 Kotlin helper，也不算单测或构建验收。
- 第一次构建因此前假定的 JDK 路径 `/usr/lib/jvm/java-17-openjdk-amd64` 不存在而失败。实际 `java -version` 为 OpenJDK 17.0.20.1，位于 NixOS `/run/current-system/sw/bin/java`。
- 项目根目录 `local.properties` 指定 `sdk.dir=/dev/shm/comfychair-android-sdk`，该目录不存在；标准 `/usr/lib/android-sdk`、`/opt/android-sdk`、`/root/Android/Sdk` 也不存在。
- 根分区 `/dev/sda2` 为 127G，已用 123G，可用 0，100%。Gradle cache 中有指向 `/dev/shm` 的 symlink；补齐临时目录后，离线构建失败：找不到 `com.android.application:8.13.2` 插件。联网构建未能继续完成。
- 因此：**无 Kotlin compile、无单元测试、无 APK、无安装运行、无最终 prompt 回读、无 ComfyUI API/业务实测。**修复状态为“源码草稿/未验收”。

## 5. 下一步执行清单（按门禁）

1. **保存现场与核对工作区**：检查 `git status`、现有 diff、未跟踪 skills；只纳入本任务文件，不覆盖或清理他人未跟踪内容。
2. **恢复构建前置条件**：确认空间清理方案（不要自动删除用户数据）；取得有效 Android SDK 路径并更新本地配置；让 Gradle 能解析 AGP 8.13.2。先确认磁盘空间可写和 SDK 组件版本。
3. **先修正源码草稿**：逐段读完整 `prepareWorkflowById`、`prepareImageEditingWorkflowById` 函数；排除二阶段 try/catch 作用域及 `finalJson` 变量问题；把补值放在最后一次用户属性编辑之后；按工作流类型/节点类精确限定；width/height 从正确 UI/defaults 传入；给有效值列表留有约束。
4. **增加针对性 Kotlin 单元测试**：输入覆盖 `EmptyLatentImage` 空宽高/残留占位符、`ImageScaleToTotalPixels` 空 megapixels、`KSampler` 空字段、SaveImageAdvanced 缺扁平键/保留 EXR 参数；另覆盖非目标节点同名输入不被误改、node edits 之后的空值修复、正常非空值不覆盖。
5. **构建和测试**：JDK 17 + 实际 SDK + Gradle Wrapper；执行上述测试和 `:app:assembleDebug`。记录真实输出。
6. **回读最终 JSON**：从用户的 DEBUG 日志包或测试输出确认最后提交到 `/prompt` 的节点 461/502/503/504/506/518 输入。所有 placeholder 都应消失；数值为 JSON number，枚举值存在于服务端 `object_info` 列表，SaveImageAdvanced 为格式匹配的扁平字段。
7. **实机/服务验收**：ComfyUI 返回 promptId 才能说明排队接受；再查 `execution_complete` 和生成结果。遇到新错误重新从最终 prompt 及服务器日志分层定位。
8. **文档和发布**：完善中英文 README（若要提交开源修复），版本/提交/远端 ref 另行按用户授权处理；当前没有提交、推送或发布。

## 6. 需要补充的证据

若继续从用户侧定位，请获取 ComfyChair 上传的 DEBUG 日志包，至少包含：

- `last_submitted_prompt.json`（最关键，未截断）
- `last_prepared_workflow.json`
- `selected_workflow_original.json`
- `workflow_values_snapshot.json`
- `generation_context.json`
- 相关 `debug_log.txt` 中提交日志和 HTTP 400 原文

然后逐项比对节点 `461, 502, 503, 504, 506, 518`，确认缺失发生在原始工作流、字段映射/用户值、处理流水线的哪一层。

## 7. 技能文档

已更新 dev profile：`/root/.hermes/profiles/dev/skills/android-dev/comfychair-workflow-debugging/SKILL.md`，补充 400 诊断、EmptyLatentImage/占位符、SaveImageAdvanced 扁平键、模型值精确对比及验收门禁。项目目录的 `.agents/skills` 里有预存未跟踪目录，本次未改写它们。
