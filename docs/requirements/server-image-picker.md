# 图生图：从 ComfyUI Input / Output 选择原图

> 当前正式版 **v0.8.25/code54**，用户已授权先测试裁剪再提交/推送/Release。实际构建/体积/限制见 [Release裁剪移交](release-v0.8.25-shrinking.md)。下文版本与仅本地授权声明是各阶段历史记录；发布由主agent独立完成，构建worker不提交/推送。

## 范围与验收
- 本地实施，不提交、推送、打 tag 或发 Release；后续用户明确授权 UI 修复时升至 v0.8.25/code52（见末节）。基线 `e9e25a41afcfc06072e8fc31811b269f6c0446f6`，保留原有 `.agents/skills` 未跟踪目录及 stash。
- 图生图增加「从 Input 选择」「从 Output 历史选择」，保留原系统文件、素材库、图库流程；当前用户修订明确要求复用素材选择弹窗样式（覆盖初版不使用 modal 的要求）。
- 捕获打开时的目标原图槽 1–4，页面滑动不改变目标；显示目标槽、来源范围、加载/下载、空列表、失败与刷新/取消；EN/ZH。
- 使用当前连接的已认证 ComfyUIClient，所有请求只读 GET，URL 参数编码；取消关闭网络 call。最终下载/解码完成到源图与缓存写入前检查 server ID + client 实例 + 槽有效性，切服务器/断开/离开屏幕/切来源丢弃过期任务。
- 成功替换仅对应槽，更新实际编码字节数，清除该槽压缩标记；槽1清除旧蒙版。保留原始尺寸（非静默缩图），超过32 MiB或16,777,216像素拒绝，解码在IO执行。

## 当前批准版本：优先资产索引（2026-11-03）
- 当前版本保持 v0.8.25/code52；仅本地实现、测试与9010 APK分享，无commit/push/tag/Release授权。
- 后文早期“本次不采用资产API/无真实目标”等为历史记录；本节覆盖当前行为。当前目标 `192.168.4.36:8189` 已只读GET确认 ComfyUI 0.43.0、`--enable-assets`、`/api/features.assets=true`；禁止生成或修改服务器。
- 当前已认证client先探测 `/api/features`，再使用 `/api/assets?limit=50&tags_all=input|output&tags_none=missing&sort=...&order=...`。名称用name_contains，游标用after；最新/最旧created_at、名称asc、大小desc。搜索300ms防抖，搜索/排序/来源/连接身份变化取消旧请求并清游标、列表与滚动。仅服务明确禁用（features.assets=false或503 SERVICE_DISABLED）/404不支持才回退；401/403/网络/其他5xx/坏JSON不是空列表亦不触发回退。features路由404仍实际探测assets。
- Input回退LoadImage选项、Output回退已有history50条record分页保持；回退不显示资产服务端搜索/排序，明确旧范围。资产total包括非图片，仅显示“已加载N张图片/匹配M个资产”，不伪称图片总量或完整实时目录。
- 标签可修改，因此要求image/* MIME、来源tag、非missing，且preview_url为当前origin的原生相对 `/api/view` 或 `/view`，唯一type=input/output匹配、filename/subfolder通过安全描述符校验。拒绝外部/绝对URL、userinfo、fragment、重复/未知参数、路径穿越；loader_path可null，不从metadata绝对file_path推导来源。原图始终 `/api/assets/{id}/content`，不把预览当原图。安全重建/view用于预览；专用派生认证client禁重定向，不跟随任意主机。
- 资产cursor独立于旧history offset；图片过滤为空也推进cursor。重复/循环cursor报错，追加失败保留列表和游标。每轮最多自动请求5页，之后显式“加载更多”继续，避免稀疏过滤无界拉取。每响应8MiB、预览4MiB/256px/并发3、内存LRU12MiB/64项、原图32MiB/16,777,216像素及60s超时保持。
- 选中原图时仅暂停请求资格，不改变可见性、不更换session、不清已显示Bitmap/列表/滚动；选中格遮罩+底部不定进度，明确进度不可用，无虚构百分比。可取消回浏览；失败保留列表与重试，关闭/ON_STOP/身份变化停止所有工作。保持同文件/素材图标行、48dp目标、String stableKey及独立实时诊断。
- JVM新增ServerAssetsTest覆盖真实fixture解析、命名空间/MIME/恶意URL、认证/编码搜索排序cursor、资产id原图、仅禁用/不支持回退、不跟随重定向、Input资产cursor/去重/取消保持session、搜索防抖，以及UI请求暂停源契约。既有HTTP/缓存/并发/关闭/迟到响应/1–4槽回归保留。源码契约不是实机手势测试。
- 可选live测试仅由COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36启用，只GET当前client资产列表、四排序、搜索、后续cursor、预览与id原图；最终证据见下方交付段。adb无设备，Android触摸/渲染/Bitmap解码和带真实认证部署仍未实机验收。

## 紧凑弹窗与 beta 打包（当前修订）
- 用户要求减少上下文本：来源+目标槽、图片/资产计数合并为48dp标题栏两行（titleSmall/labelSmall，单行省略）；刷新移到标题图标，关闭/信息/刷新均48dp。长来源说明、非图片资产计数语义、下载不定进度/暂停预览和图片限制移到信息菜单。
- 搜索bodySmall单行占剩余宽度，排序48dp图标同一行，打开菜单展示四种顺序与当前勾选；保留搜索防抖和状态机。移除独立排序/计数/说明行、底部全宽刷新、固定32dp底部留白和独立拖动柄；采用navigationBarsPadding避免系统导航遮挡。
- 三列方形图块、8dp间距/圆角和weight(1f)保持；无空闲footer，末尾状态移入网格可滚动内容。仅下载/错误/自动五页用尽时出现一行小字+48dp取消/重试/更多；仅列表请求中显示2dp不定进度，所选格原有遮罩保持。预览缓存、session、稳定键、实时日志及顶部SourceButtons不改。
- 追加授权beta：从v0.8.25/code52改为 **v0.8.25-beta.1/code53**，保持applicationId和debug签名配置。下次分发beta必须同时递增beta.N和全局versionCode（示例beta.2/code54），正式版code也须高于所有已分发beta；不使用时间戳code或新appId。仅文件名后缀不会改变Android升级比较，升级还须包名/签名匹配；用户手机“重复安装”的确切提示尚未复现，不保证仅beta名称能解决所有安装问题。
- 新增三项UI源码布局契约覆盖紧凑栏、搜索排序同排/48dp、按需footer和原网格尺寸；它们不是截图或触摸测试。adb无设备，真实手机/窄屏/大字体渲染和覆盖安装数据保留仍未实机验收。
- 最终完整构建（启用现有只读live服务回归）`testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=compact-assets-picker`：**BUILD SUCCESSFUL in 2m34s；94 tests /0 failures /0 errors /0 skipped**，含三项新增源码契约。日志 `/root/.hermes/profiles/dev/cache/scratch/comfychair-compact-beta-final-build.log`，报告 `app/build/reports/tests/testDebugUnitTest/index.html`。首轮通过后将新增Sort弃用图标改成AutoMirrored并完整重跑，当前只有既有条件常量/TabRow弃用警告。
- metadata+aapt确认 **sh.hnet.comfychair / v0.8.25-beta.1 / code53**；新旧APK均apksigner verify通过v2，同一Android Debug证书SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`。aapt `/root/.hermes/profiles/dev/cache/scratch/comfychair-compact-beta-aapt.txt`；汇总 `/root/.hermes/profiles/dev/cache/scratch/comfychair-compact-beta-verification.json`。
- APK `app/build/outputs/apk/debug/comfychair-v0.8.25-beta.1-debug-compact-assets-picker-20261103-1425.apk`；分享 `/root/share/comfychair/comfychair-v0.8.25-beta.1-debug-compact-assets-picker-20261103-1425.apk`，**86,933,230 bytes**；SHA256 `352c870dbdc1625341809c3efd2a8d78da699a20909e26af1126afb90528d5e8`。
- 复用9010 PID1433377、仅/root/share/comfychair，完整无代理LAN GET **200 /86,933,230 bytes/同SHA256**：<http://192.168.4.168:9010/comfychair-v0.8.25-beta.1-debug-compact-assets-picker-20261103-1425.apk>。9011诊断服务未动；无commit/push/tag/Release，保留旧stash与无关skills及未提交功能。

## 接口调查与选择依据
本次读取 ComfyUI 上游 master（查询到提交 `6b904b5fafd062effc1acbc46b1d3196922f7055`）：
- `nodes.py` LoadImage.INPUT_TYPES 使用 input 根目录 `os.listdir` + `isfile` + image MIME 过滤，返回 `required.image[0]`。使用 `GET /object_info/LoadImage`，不声称递归目录浏览；若某节点实现暴露子目录选项则原样支持。
- `server.py` `/history` 提供执行记录，`/view` 是按 filename / subfolder / type 读取文件，不是列目录。Output 使用每页最多50条执行记录的真正分页，滚动接近底部自动追加更早记录，无200条上限；排除temp/视频，按执行号倒序并按完整文件描述符去重。支持初始加载、追加加载、保留列表的失败/手动重试与末尾状态。Input仍一次读取LoadImage列表，当前修订在客户端每次显示50个去重选项。
- 已重新核对同提交 `execution.py:PromptQueue.get_history` 与 `server.py:get_history`：offset>=0从最老记录按插入顺序计算；所有负offset均被改写为len(history)-max_items，不能用-50/-100反向翻页！接口不返回total；首载用max_items=1的指数探测+二分确定历史记录数，然后固定非负record offset从尾部向前读50条；最后一页严格收缩max_items，避免跨页重叠。后续游标是上一页start（与图片数无关）；无图片页仍推进。初载存在O(log N)次小响应探测开销，而非拉取全部history。
- 新执行记录只追加时，不影响本轮已固定的历史offset；刷新才纳入新记录。上游没有快照/稳定cursor，分页期间清空、删除、裁剪/重启历史可能造成缺失或重叠；客户端去重、空响应终止、游标严格递减防死循环，用户可刷新恢复，不能承诺并发修改下快照完整性。文件已删时下载会报错。
- 首载和追加共享请求代次/身份/槽校验；关闭、换来源、刷新或连接变化取消请求；选择已加载图片会先取消追加并递增代次，防止不合作回调回填或覆盖下载状态。追加失败保留原列表与游标，自动滚动不无限重试。
- 新版 `app/assets/api/routes.py` 有 feature-gated `GET /api/assets`；`schemas_out.py` 返回 assets/total/has_more/next_cursor，asset的 loader_path/preview_url 可空，并非所有部署均有的 Input/Output 目录列表。为兼容现有ComfyUI、不引入未知资产身份与API版本依赖，本次不采用资产API，不探测或编造 `/files`。这意味着不支持完整output磁盘扫描或input递归浏览；未来可作为独立适配层加入。
- 上游源码：https://github.com/Comfy-Org/ComfyUI/blob/6b904b5fafd062effc1acbc46b1d3196922f7055/nodes.py 、同提交 `server.py`、`app/assets/api/routes.py`、`app/assets/api/schemas_out.py`。
- 原始调查文件位于 dev scratch `picker-upstream-*`，没有访问历史IP或读取远端凭据。

## 代码与测试
- `model/ServerImage.kt`：描述符校验、输入/历史解析、URL编码。
- `ComfyUIClient.kt`：有限大小/超时、可取消的已认证列表与文件读取。
- `viewmodel/ServerImagePicker.kt`：可独立JVM测试的状态机，最终接受前身份检查。
- `viewmodel/ImageToImageViewModel.kt`：实际下载/解码及独立槽发布/缓存。
- `ui/components/ServerImagePickerPanel.kt`、`ui/screens/ImageToImageScreen.kt`：新增就地列表与按钮。
- 两种语言 strings，`ServerImagePickerTest.kt`：真实形状fixture + MockWebServer + 状态机。

## 待验证边界
- 当前没有指定可访问的真实ComfyUI目标服务器，不用历史IP试探；MockWebServer不是ComfyUI实测。
- adb设备列表为空；真实触摸、窄屏/横屏布局、完整生成、实际认证部署及服务重启后行为须实机验证。
- 当前修订已改为可见项异步缩略图网格，资源限制和生命周期见末节。
- 当前修订将源图PNG准备移到IO；最终身份检查后只执行内存发布或已准备文件的原子rename，无二次无守卫协程。

## 初版构建证据（分页前历史记录，下节为本次交付）
- 最终命令：`./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=server-image-picker`。
- 真实结果 `BUILD SUCCESSFUL in 1m 50s`，44 actionable tasks: 12 executed, 32 up-to-date；JUnit XML汇总 **54 tests / 0 failures / 0 errors / 0 skipped**（原40 + 新14）。`git diff --check`与EN/ZH新增键一致性/资源ID无重复检查通过。
- 日志 `/root/.hermes/profiles/dev/cache/scratch/comfychair-server-image-picker-final-build.log`；测试报告 `app/build/reports/tests/testDebugUnitTest/index.html`。
- APK `/root/comfychair/app/build/outputs/apk/debug/comfychair-v0.8.24-debug-server-image-picker-20261103-1030.apk`，86,774,706 bytes；SHA256 `911907470dd9e66e942c9c1004dfccd58336506f4564f8283ebf87ab81ba3277`。
- output-metadata与aapt确认 `sh.hnet.comfychair` / versionCode51 / v0.8.24，apksigner verify exit0。未升版、提交、推送或发布，HEAD仍为基线；无关skills/stash保留。
- 首次构建应用与测试编译成功，但取消测试使用30s MockWebServer body delay，资源关闭触发其5s shutdown等待错误；改用2s delay并断言500ms内取消，最终完整命令通过。既有always-true/false与TabRow deprecated警告仍在，无新编译阻塞。
- 未开启分享HTTP服务；APK为本地真实构建产物，不是新Release。真实设备/ComfyUI业务尚未验收，详见边界一节。

## 本次分页构建与分享交付
- 命令：`./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=server-image-picker-paging`。
- `BUILD SUCCESSFUL in 1m 52s`，44 tasks / 12 executed / 32 up-to-date；JUnit XML：**62 tests / 0 failures / 0 errors / 0 skipped**，包含新增8个分页测试（237历史记录、多图/无图记录、去重、追加重试、合并底部触发、取消、迟到回调及server/client/slot/revision切换、无效cursor、历史清空）。
- 日志：`/root/.hermes/profiles/dev/cache/scratch/comfychair-picker-paging-final-build.log`。报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
- APK：`/root/comfychair/app/build/outputs/apk/debug/comfychair-v0.8.24-debug-server-image-picker-paging-20261103-1101.apk`。分享副本：`/root/share/comfychair/comfychair-v0.8.24-debug-server-image-picker-paging-20261103-1101.apk`，**86,793,130 bytes**。
- SHA256：`d07e6eb52cd7e41a04494beb687cc93891a27ec1b60b757b15758e86adb9e21d`。
- metadata + aapt：`sh.hnet.comfychair` / versionCode **51** / versionName **v0.8.24**；apksigner verify通过，Android Debug证书SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`。
- HTTP：`python3 -m http.server 9010 --bind 0.0.0.0 --directory /root/share/comfychair`，监听PID1433377；只暴露APK目录。实际路由源IP为192.168.4.168，loopback HEAD 200；**无代理完整LAN GET 200 / 86,793,130 bytes / SHA256与本地一致**。
- 下载：<http://192.168.4.168:9010/comfychair-v0.8.24-debug-server-image-picker-paging-20261103-1101.apk>。手机需能访问此LAN地址；本机LAN回读不代表手机路由或防火墙已实测。
- `git diff --check`、EN/ZH 14个picker键一致/无重复通过。HEAD仍为基线，未提交、推送、发布或改版本；原无关skills和stash未动。既有Kotlin条件/TabRow弃用警告，无构建阻塞。
- 未指定真实ComfyUI与设备；没有宣称真实服务/触摸测试已完成。首次探测请求开销及历史并发删除的非快照边界见上。

## UI 修复及升版交付（本次）
- 用户要求：增加版本号；修复不能点击；Input/Output 与现有选图按钮同位置、同 Material 图标按钮设计，去掉独立长文字行。实际版本由 v0.8.24/code51 一次提升至 **v0.8.25/code52**。
- 源码根因：旧行显式 `enabled = connectionStatus == ConnectionStatus.CONNECTED`，但该状态来自 GenerationViewModel 的 WebSocket 状态，Input/Output 列表却是已认证 HTTP GET；WebSocket 重连、失败等状态可禁用 HTTP 入口。没有发现该独立 Row 上额外 pointerInput 或覆盖层拦截；不能在无设备情况下声称复现了用户所有点击失败情形。
- 修复：在顶部现有 AddPhotoAlternate/Collections 之后加入 `ServerImageSourceButtons`，使用同款默认 Material IconButton、CloudUpload/CloudDownload 图标以及中英无障碍说明；入口不再由生成 WebSocket 状态禁用，打开后仍由现有 picker 身份守卫与错误面板处理不可用连接。TopBar 动作条 max240dp 可横向滚动，保持默认48dp触摸区域，避免窄屏及额外蒙版按钮挤出；菜单保持条外。原文件、素材、图库选择以及全部分页/取消/身份校验代码保持不变。
- 本轮直接变更：`app/build.gradle.kts`、`ui/screens/ImageToImageScreen.kt`、新增 `ui/components/ServerImageSourceButtons.kt`、EN/ZH strings、新增 `app/src/test/java/sh/hnet/comfychair/ServerImagePickerUiContractTest.kt`、本文。旧未提交功能文件全部保留。
- 4个新增源码/资源契约回归先运行全部失败（RED日志 `/root/.hermes/profiles/dev/cache/scratch/comfychair-picker-ui-red.log`）；修复后完整 `testDebugUnitTest assembleDebug` **BUILD SUCCESSFUL in 2m20s**，44 tasks /24 executed/20 up-to-date，JUnit XML **66 tests /0 failures/0 errors/0 skipped**。新增测试验证同一TopBar动作、无WebSocket禁用、滚动动作条不缩触摸目标、双语无障碍标签；它们不是Compose触摸测试。
- 最终构建日志：`/root/.hermes/profiles/dev/cache/scratch/comfychair-picker-ui-final-build.log`；报告 `app/build/reports/tests/testDebugUnitTest/index.html`；命令沿用上节资源参数、suffix改为 `-PapkSuffix=server-image-picker-ui`。原有条件常量/TabRow弃用警告无阻塞；`git diff --check`和双语字符串无重复/键一致通过。
- metadata与aapt一致：`sh.hnet.comfychair` / **v0.8.25 /52**；apksigner verify成功，v2签名，仍为原Android Debug证书 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`。
- APK：`/root/comfychair/app/build/outputs/apk/debug/comfychair-v0.8.25-debug-server-image-picker-ui-20261103-1113.apk`；副本 `/root/share/comfychair/comfychair-v0.8.25-debug-server-image-picker-ui-20261103-1113.apk`；**86,793,198 bytes**；SHA256 **`e4cd27bd705f900a525d485a0db24ee7fa1aa77f1c6fa0db19cd75eb5a2276ae`**。
- 复用原PID1433377的9010 HTTP服务，不重启/终止；当前网卡LAN IP实查192.168.4.168；无代理完整LAN GET返回200、字节数及SHA256与本地副本完全一致。
- 下载：<http://192.168.4.168:9010/comfychair-v0.8.25-debug-server-image-picker-ui-20261103-1113.apk>。
- UI验证级别：已检查实际Compose布局、modifier、onClick和状态来源，完成Android编译与源码契约/JVM回归；`adb devices -l`再次为空，**没有实机截图、真实触摸或窄屏渲染验收**；未提供真实ComfyUI目标，业务端到端亦未实测。请在设备上验证入口、横向滚动和1–4槽选择；不要把JVM通过说成触摸已验证。
- HEAD仍为基线，未提交/推送/tag/Release；无关 `.agents/skills` 与原stash不动。

## 用户修订：素材样式弹窗与异步缩略图（最新交付）
- 用户已确认按钮可用；本次保留同位置 Input/Output IconButton 和原48dp触摸区域，不恢复 GenerationViewModel WebSocket enabled 门控。
- 显式采用现有 `MaterialLibraryPickerBottomSheet` 的 ModalBottomSheet、标题/关闭栏、3列正方形Crop图块、8dp间距与圆角、底部操作栏样式；远端数据源与生命周期不同，不将本地素材导入/删除逻辑接到只读远端。文件路径仅作图片无障碍描述，不再显示文件名列表。图片预览失败可点该图块重试；列表、追加、选择失败均保留明确状态/重试路径。
- 可见性由 LazyGrid layoutInfo.visibleItemsInfo 控制，预组合/屏外图块不发请求；滚出可见区取消该请求并释放图块Bitmap引用。近底部自动追加，Input客户端每50项、Output沿用真实历史每50条record与正确oldest-based offset探测/去重；不改历史接口语义。
- 缩略图经相同已认证 ComfyUIClient/transferClient读取 `/view?...&preview=webp;60`，只GET，参数仍正确编码。3个信号量许可限制同时下载/解码；响应最多4 MiB，60s call timeout；先读尺寸，拒绝无效/超过268,435,456源像素，采样解码到最长边不超过256px。服务端preview转换不保证缩小编码尺寸，超过4MiB显示预览失败而不自动拉原图绕过限制。
- 仅内存LRU缓存：12 MiB解码字节、64项双限制；key=serverId+真实已认证client实例+完整filename/subfolder/type，不使用裸路径或跨client全局key；无缩略图磁盘文件、无凭据落盘（磁盘预览缓存上限0）。允许同client重开复用，重连新client不命中；同名文件覆盖可能暂时展示旧预览，原图选择始终重新下载。缓存/在屏幕Bitmap引用不主动recycle，避免Compose使用已回收图。
- Popup session拥有SupervisorJob；每个可见图块任务同时受调用方LaunchedEffect与session控制，许可后再次核对session/身份，防止取消释放许可时队列继续启动。关闭/滑下/返回、刷新/换来源/槽、server/client变更、槽失效、离屏及ON_STOP取消网络/分页/缩略图，迟到结果不能发布。选择先停止分页与预览，原图单次获取最多32MiB、16,777,216像素，IO解码；最终server/client/slot/revision守卫后发布。
- disk-first源图PNG编码在IO完成，缓存目录临时文件在成功rename、失败或取消时清理，最终主线程仅校验身份+原子rename/内存发布。强杀进程发生在清理前可能留下Android cacheDir临时文件（非预览持久缓存），系统可回收；未声称进程强杀恢复测试。
- 新增8个真实JVM状态/MockWebServer/缓存回归，覆盖Input50项与去重、并发上限、关闭主动/排队取消、单图滚出取消、失败重试、server/client/slot/reopen取消、LRU字节/条目/身份隔离、预览认证/编码参数/大小限制、HTTP关闭取消；另新增1个UI源码契约（不是手势测试）。原有分页/选择身份测试全部保留。
- 首轮完整构建74tests/1fail，实际捕获取消时许可释放导致队列继续启动；增加许可后父job活跃/代次校验修复，最终 **75tests /0 failures /0 errors /0 skipped**。
- 最终完整命令：`./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=server-image-grid`；**BUILD SUCCESSFUL in 1m54s**，44 tasks/12 executed/32 up-to-date。日志 `/root/.hermes/profiles/dev/cache/scratch/comfychair-grid-final-build.log`，XML `app/build/test-results/testDebugUnitTest/`，HTML `app/build/reports/tests/testDebugUnitTest/index.html`。
- APK `app/build/outputs/apk/debug/comfychair-v0.8.25-debug-server-image-grid-20261103-1140.apk`，分享副本 `/root/share/comfychair/comfychair-v0.8.25-debug-server-image-grid-20261103-1140.apk`，**86,826,466 bytes**；SHA256 `ad7c8c9f27c7f9210a4cfbe087ba31a272714b812721f26decee3cd0d430390a`。
- metadata+aapt：`sh.hnet.comfychair` / v0.8.25 /52，未升版；apksigner verify通过，v2/原Android Debug证书SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`。
- 复用9010 PID1433377专用 `/root/share/comfychair` server，未重启。完整无代理LAN GET **200 /86,826,466 bytes /SHA256一致**；下载 <http://192.168.4.168:9010/comfychair-v0.8.25-debug-server-image-grid-20261103-1140.apk>。
- `git diff --check`通过，两份strings无重复ID；既有Kotlin条件/TabRow警告无阻塞。版本/原未提交功能与无关skills/stash保留，未commit/push/tag/Release。
- adb当前仍无设备，未提供live ComfyUI；真实手机触摸/底部弹窗尺寸/快速滚动、Android Bitmap解码峰值与disk-first rename、真实认证/慢网/服务端preview支持及手机访问LAN须实机验收。JVM与源码契约不能冒充这些实测。



## 资产版本最终构建/只读实测/交付（2026-11-03）
- 完整命令：`COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36 ./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=server-assets-picker`。
- **BUILD SUCCESSFUL in 2m**，44 tasks/12 executed/32 up-to-date；XML实际汇总 **91 tests/0 failures/0 errors/0 skipped**（原80保留、新11）。日志 `/root/.hermes/profiles/dev/cache/scratch/comfychair-assets-final-build.log`，HTML `app/build/reports/tests/testDebugUnitTest/index.html`。
- 可选live回归在本次真实执行（非跳过）：当前Kotlin ComfyUIClient对两个来源执行assets能力、首50、after后续50、edit搜索、created_at asc/desc、name asc、size desc、预览、id原图GET。INPUT首50图片、totalAssets4636、次50、edit资产468；OUTPUT首46图片、totalAssets2176、次50、edit资产124。计数已排除missing，是当时读数，不沿用先前未排除missing的总量；首50资产只46张合格图片证明total与图片量不可混同。未向服务POST或生成，未调整启动配置。
- APK：`/root/comfychair/app/build/outputs/apk/debug/comfychair-v0.8.25-debug-server-assets-picker-20261103-1359.apk`；分享副本 `/root/share/comfychair/comfychair-v0.8.25-debug-server-assets-picker-20261103-1359.apk`；**86,915,058 bytes**；SHA256 `33485acffbfd3bfeb851bf8131ba04621b6ea6e11c70c0e4949dff361942f404`。
- metadata+aapt确认 `sh.hnet.comfychair` / **v0.8.25/code52**；apksigner verify通过v2，Android Debug证书SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`。aapt证据 `/root/.hermes/profiles/dev/cache/scratch/comfychair-assets-aapt.txt`。
- 复用9010 PID1433377专用分享服务；实际LAN完整无代理GET **200 /86,915,058 bytes/SHA256一致**。下载 <http://192.168.4.168:9010/comfychair-v0.8.25-debug-server-assets-picker-20261103-1359.apk>。
- `git diff --check`通过；EN/ZH新9键齐全、各资源无重复ID。HEAD仍e9e25a41afcfc06072e8fc31811b269f6c0446f6，stash及无关skills未动；没有commit/push/tag/Release/升版，实时诊断实现未改。
- 无ADB设备：真实Compose触摸、布局、源图Android解码/磁盘发布仍需设备验收；live服务为无认证LAN，实际认证部署以MockWebServer验证header而非冒称实测。明确不定进度，没有实现原图下载字节进度。既有条件常量/TabRow弃用警告无阻塞。

## 登录页回退调查与实时诊断（2026-11-03）
- 用户澄清为回到登录页，不能直接判定进程崩溃。检查了 MainContainerActivity guard / connection alert / explicit logout、ConnectionManager、AuthInterceptor、picker生命周期及HTTP调用：picker没有调用logout或导航；普通列表/下载失败只更新picker错误，不会把401自动转登录。未修改连接失败认证语义来掩盖问题。
- **已确认的代码缺陷**：新LazyVerticalGrid使用 `key = { it }`，传入普通 `ServerImage` data class，既非Parcelable亦非Serializable。实查当前Gradle缓存实际Compose foundation/runtime-saveable字节码：LazySaveableStateHolder把key交给SaveableStateHolderImpl，其SaveableStateProvider即时检查canBeSaved，不支持即抛 IllegalArgumentException（“Type of the key ... is not supported. On Android you can only use types which can be stored inside the Bundle.”）。两个来源共用该grid，非空列表都会走此路径。改为长度前缀、无歧义的String stableKey；可见项匹配同步改为String，避免修key后缩略图全不加载。
- **未确认**：没有手机/Logcat/原用户会话堆栈，不能声称此次实际登录跳转一定由该异常造成。Activity重建/进程恢复时guard因内存连接丢失回登录可以解释现象，但仍是待诊断证据，不是已证实用户根因。adb设备列表为空。
- 新增DiagnosticsApplication，从进程启动读取现有debug开关；默认关闭。记录进程/Activity创建、恢复、停止、销毁/finishing、连接及WebSocket状态类型、明确登录回退原因、picker打开及异常类型。只上传专门的value-free诊断事件，不转发任意DebugLogger正文、prompt、图片、workflow、URL/主机、认证或异常message。全局fatal handler保留异常类和最多16个栈帧，不含message，写本地后调用原handler；不会吞异常或在崩溃线程联网。
- 设置 → 调试日志：开启调试，填写独立实时地址 `http://192.168.4.168:9011/events`，再主动打开实时发送；与手动ZIP `http://192.168.4.168:9011/upload` 分开。中英帮助说明HTTP明文，仅可信LAN；地址拒绝userinfo/query/fragment和非/events路径，禁重定向，不复用ComfyUI认证/网络拦截器。关闭debug或实时/更换目标立即取消in-flight且清旧队列；地址为空默认不发送。
- 协议：POST exact `/events`, Content-Type application/json, body `{"app":"ComfyChair","schema":1,"dropped":0,"events":["<epochMillis> picker_open ..."]}`；任意2xx为ack，不解析response body。兼容parent接收器200 JSON。新logger不改变手动multipart file/filename/app协议。receiver /health已实际返回200 `{ok:true}`。
- 队列最多256条、每条1024字符、每次最多32条，后台单线程每秒tick；3秒call/2秒connect timeout，一次只一请求；失败保留事件，2/4/8/16/32/60秒退避，持续失败60秒封顶（非无限加速/反馈日志）；超限丢最旧并累计dropped。仅Activity处于started前台时联网，后台取消当前call；本地记录仍可落盘。无WorkManager或常驻前台服务用于诊断。上传失败不写DebugLogger，避免自激循环。
- 私有 `filesDir/diagnostics/recent.log` 128KiB + `.previous` 128KiB轮转；IO线程每秒写诊断事件，fatal同步补写待落盘事件。故普通强杀可能丢最后约1秒，native crash/系统OOM不保证fatal回调。文件跨进程保留，不自动向新目标回放；手动ZIP新增这两份诊断附件，可在恢复后上传。原debug_log.txt仍是内存日志，不虚称全量持久化。
- JVM新增5测试（stableKey + 4 diagnostics），测试先因stableKey未实现失败，再完整构建通过：**80tests/0failures/0errors/0skipped**。覆盖opt-in、前后台/本地落盘、HTTP认证/Cookie缺省不带、JSON、256队列/drop、32batch、失败退避、换target清队列、journal轮转、fatal不泄露message、目标拒绝凭据。原75项picker/paging/thumbnail测试保留；UI源码契约追加saveable key及可见匹配断言，不冒充Android触摸测试。
- 最终 `testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=picker-realtime-debug`：**BUILD SUCCESSFUL in 2m8s**。既有条件常量与TabRow弃用警告，无新阻塞。
- APK `app/build/outputs/apk/debug/comfychair-v0.8.25-debug-picker-realtime-debug-20261103-1206.apk`，分享 `/root/share/comfychair/comfychair-v0.8.25-debug-picker-realtime-debug-20261103-1206.apk`；**86,861,398 bytes**；SHA256 `fd8b19cdf21a265e311973902463dbfa49216585c5048861328fce87ceb7812c`。
- metadata+aapt确认v0.8.25/code52，apksigner verify成功（v2）。DEX核对含新picker_open与RealtimeDiagnostics代码。复用原9010 server PID1433377，仅 `/root/share/comfychair`；无代理完整LAN HTTP GET200，字节和SHA256与本地完全一致。下载 <http://192.168.4.168:9010/comfychair-v0.8.25-debug-picker-realtime-debug-20261103-1206.apk>。
- 未commit/push/tag/Release/升版，保留所有先前未提交picker/thumbnail/paging改动和无关skills/stash。仍需手机验证：两按钮打开/滚动、实时事件到LAN、实际跳转前的证据与恢复ZIP；无ADB设备，无法替代用户复现。
