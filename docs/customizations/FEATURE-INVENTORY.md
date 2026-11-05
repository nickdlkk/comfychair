# 定制功能清单与上游差异

## 固定范围与读法

- fork 稳定基线：`c43f2a6bbb39a2333b8f97e7eec8eb9bd3d0702a`（v0.8.26/code63）。
- 已 fetch 的上游：`bfd69ad878e33ccd65355f001e351a4c879864ca`。
- 共同祖先：`791cd95158c8f11fdbae47605bb06aec8dd4a17e`。
- 基线从共同祖先修改 **173 个跟踪路径**；56 个 fork 独有提交包含 merge、修复迭代、文档和版本提交，不等于56个独立功能。精确文件列表见 [BASELINE-CHANGED-PATHS.tsv](BASELINE-CHANGED-PATHS.tsv)，由 `git diff --name-status <base> <stable>` 生成。
- 下列 Kotlin 路径除注明外相对 `app/src/main/java/sh/hnet/comfychair/`；测试简称相对 `app/src/test/java/sh/hnet/comfychair/`。清单描述稳定基线的实现，不表示全部已移植至上游新架构。
- 当前集成分支仅完成阶段A，**不是已经全量合入上游的候选**。状态和真实验收见 [INTEGRATION-STATUS.md](INTEGRATION-STATUS.md)。

## 1. 数据传输与服务器素材

### F01：最终编码内容寻址的上传复用（核心可移植能力）

**文件**：`connection/ImageUploadReuse.kt`、`ComfyUIClient.kt`、`viewmodel/ImageToImageViewModel.kt`。

按实际上传 PNG/JPEG/mask 编码字节 SHA-256 命名，`overwrite=false`；保留服务器实际返回的文件名/subfolder/type，避免覆盖排队任务仍引用的源文件。同内容请求 single-flight，容量有界；失败不缓存、取消可重试，等待者取消不取消主请求。旧 descriptor 用已认证的无 body HEAD 验证；只有404代表不存在，401/403/5xx/不支持HEAD均停止，不偷偷下载原图或重传。进度按 payload 字节而非 multipart 包络。

**依赖/限制**：必须保留 authenticated-client epoch，认证/协议/连接改变要 invalidate。上游的随机UUID上传不等价。移植前辅助 client 必须接入上游 ServerTrust，不能沿用 accept-all TLS。

**已有测试**：`ImageUploadReuseTest`（并发、取消、认证迁移、容量、HEAD错误、实际进度）、`ImageUploadReuseWiringTest`。首个PR可仅RAM复用，磁盘持久化另拆。

### F02：未改变 Input 原文件直接引用（依赖F01）

**文件**：`connection/DirectInputSource.kt`、`ComfyUIClient.kt`、`viewmodel/ImageToImageViewModel.kt`、`WorkflowManager.kt`。

仅 Editing、Input来源、同一client epoch、相同Bitmap对象和generationId、无压缩/无mask，且该槽所有token完整映射到原生 `LoadImage.image` 才直接引用。覆盖槽1–4，裸路径进入模板后只添加一次input注解。混合/custom消费者、变换、重新解码/进程恢复无原始provenance，一律走编码上传。HEAD只证明存在，不证明Android解码和服务器原文件语义等价，也不防第三方替换。

**测试**：`PersistentServerImageTest`、`ImageUploadReuseWiringTest`；需要补上游统一WorkflowImages golden测试。独立服务器图库的“用作源图”是本地Bitmap导入，不能冒充该原文件provenance。

### F03：Input/Output发现、搜索、分页与原图加载

**文件**：`model/ServerImage.kt`、`model/ServerAsset.kt`、`ComfyUIClient.kt`、`viewmodel/ServerImagePicker.kt`、`ServerImagePreviewCache.kt`、`ServerPickerScrollPolicy.kt`、`ui/components/ServerImagePickerPanel.kt`、`ServerImageSourceButtons.kt`、`MediaLoadingOverlay.kt`。

优先原生assets接口（搜索、排序、cursor分页），仅明确unsupported/disabled才退回legacy发现；不把认证/网络失败当成unsupported。限制同源preview和合法descriptor，认证请求、取消旧查询、稳定身份、采样缩略图、原图按需下载/进度/取消。普通productive分页没有累计5页截止；仅连续无新增项的稀疏页有预算和手动继续。

**测试**：`ServerAssetsTest`、`ServerImageKeyTest`、`ServerImagePagingTest`、`ServerImagePickerTest`、`ServerImagePickerUiContractTest`、`ServerImagePreviewTest`、`ServerPickerScrollPolicyTest`、`ServerPickerScrollRegressionTest`、`ServerOriginalLoadingContractTest`。

**限制**：缩略图采样省解码内存不必然省网络字节；legacy不等于服务器全盘搜索；live-readonly测试需要显式环境启用，普通JVM通过不代表访问了真实实例。

### F04：私有持久缓存与可控清理/统计

**文件**：`cache/{PrivateImageDiskCache,PersistentImageCache,ServerImageListDiskCache,ImageCachePolicy}.kt`、`storage/AppSettings.kt`、`ui/components/ImageCacheSettings.kt`、`viewmodel/SettingsViewModel.kt` 和F01/F03客户端。

`noBackupFilesDir/server-image-cache-v1`，安装随机HMAC命名空间隔离结构化服务器/认证身份；不写明文URL、账号、凭据。缓存第一页列表、采样缩略图和上传descriptor，不持久化旧cursor继续翻页。版本/校验和、原子发布、TTL与保留分离、按创建时间清理、用户自动策略和独立硬上限。手动清理保留namespace.key、不删workflow/material，generation门禁阻止清理前旧请求回填。统计仅有效entry，含44字节entry头，不含key/temp/损坏数据。

**测试**：`PersistentServerImageTest`、`ImageCacheCleanupTest`。普通SharedPreferences未做真机instrumentation。

**上游冲突**：上游MEMORY_ONLY默认并不会自动禁用本独立缓存；不能声称整个app零落盘。Cookie新增后必须用HMAC隔离不同账号并使epoch失效。restore-defaults需重新apply活跃策略。此能力的上游PR必须先达成隐私/默认值共识，不能强行启用。

## 2. 浏览、保存与删除

### F05：Settings独立服务器图库、紧凑布局与返回链

**文件**：`ui/screens/ServerGalleryScreen.kt`、`viewmodel/{ServerGalleryViewModel,ServerGalleryAnchorPolicy}.kt`、`connection/ServerGallerySourceTransfer.kt`、`ui/navigation/{SettingsNavHost,SettingsChromePolicy,MainNavHost}.kt`、`MainContainerActivity.kt`。

独立于生成源picker，复用F03/F04；Input/Output切换、搜索排序、网格、刷新、原图viewer、save-to-device/material、用作槽1源。process-local一次性ticket绑定当前媒体身份，不在Intent传Bitmap。远程删除/返回/刷新保留滚动anchor。图库页面不重复显示Settings chrome/底栏以保留空间。

**测试**：`ServerGalleryLayoutContractTest`、`ServerGalleryPositionTest`、`ServerOriginalLoadingContractTest`。contract测试不是设备手势测试。上游改为同Activity viewer overlay和Settings tab，需要重接ticket/返回目标，不是保留旧Activity文件即可。

### F06：可选的物理文件删除扩展（高风险、管理员opt-in）

**Android文件**：`connection/ServerMediaDelete.kt`、`storage/DeletionCredentialStorage.kt`、`ui/components/ServerDeletionControls.kt`、F03/F05选择和cache失效逻辑。

**服务端**：`server/comfychair_media/{__init__,service}.py`、README；`server/tests/{test_media,test_extra,test_lan}.py`。

独立删除key，EncryptedSharedPreferences失败关闭，不明文fallback；按server和exact-origin保存明文LAN consent。无redirect/自动mutation重试，prepare manifest/token、不可逆确认、每文件结果和持久tombstone。服务端默认不满足配置则禁用；LAN需显式模式+独立key，proxy模式需管理员认证/授权边界。仅regular image files，路径/no-follow/hardlink守卫，真实prompt queue互斥下全队列空闲守卫，journal先fsync。

**不可混淆**：上游Gallery/Viewer Delete是`POST /history`删job历史，不是物理unlink；其Undo不能套到本永久删除。

**限制**：HTTP明文key可被窃取；无按用户LAN角色。外部写者不受队列锁约束，stat→unlink仍有竞态；需管理员排他维护。tombstone不是assets数据库reindex，重建同名仍隐藏，其他前端可能显示旧记录。不能宣传通用安全删除或自动安装。当前任务不修改实际服务器。

**测试**：`ServerGalleryDeleteTest`、`ServerMediaLanTest` 和Python84个参数化用例；真机Keystore/设备手势/真实维护窗口另验收。上游是否接受服务端扩展和协议需先讨论，默认fork-only候选。

### F07：本地素材库

**文件**：`materials/MaterialLibrary.kt`、`viewmodel/MaterialLibraryViewModel.kt`、`ui/screens/MaterialLibraryScreen.kt`、`ui/components/MaterialLibraryPickerBottomSheet.kt`，Gallery/Viewers/导航接入。

导入、网格浏览、从结果/图库保存、回选源、多槽使用、二次确认删除；属于用户显式保存的数据，不是清cache可删的内容。现有JPEG保存语义不等于无损保真。

**测试缺口**：没有独立MaterialLibraryTest；现有gallery/layout契约只覆盖部分接线。上游PR需补持久化/重启/导入大图/删除和设备选择回传测试，不随大release提交整体挑入。

## 3. 工作流与生成

### F08：多源槽1–4、动态pager和可选槽bypass

**文件**：`workflow/{TemplateKeyRegistry,FieldMapping}.kt`、`WorkflowManager.kt`、`util/{BypassNodeResolver,FieldMappingAnalyzer,LiteGraphConverter}.kt`、`viewmodel/ImageToImageViewModel.kt`、`ui/screens/ImageToImageScreen.kt`、`GalleryPickerBottomSheet.kt`、`ReferenceImageThumbnail.kt`。

由工作流占位符/映射能力决定额外槽，切换workflow clamp当前页；槽2/3/4独立替换/bypass。不向服务器发送空目录作为图片；图结构bypass resolver处理实际连接，不只放mode=4。Replace打开文件picker，Use as source和gallery回传区分目标slot，不混淆reference1/2与额外源槽。

**测试现状**：DirectInput的slots1–4和finalizer有测试；完整多槽+bypass golden覆盖不足。上游WorkflowImages当前source/reference1/reference2不足，需显式扩展，不把reference冒作slot2/3/4。

### F09：服务器保存的workflow导入

**文件**：`ComfyUIClient.kt`、`util/{ServerImportOrigin,WorkflowImportPreparation,LiteGraphConverter}.kt`、`viewmodel/WorkflowManagementViewModel.kt`、`ui/screens/WorkflowsSettingsScreen.kt`。

读`/users`确定单/多用户形状，单用户列`/userdata`工作流，下载并复用本地转换/映射保存链。整个`workflows/<relative path>`作为一个URL路径段编码，支持中文、空格、+和子目录，拒绝遍历/超限/非JSON。定义未就绪不转换丢widget；警告先确认，最终保存前再次检查origin/client/server身份（包括独立editor Activity回传）。

**测试**：`ServerWorkflowApiTest`、`util/ServerImportOriginTest`、`util/WorkflowImportPreparationTest`。multiuser未配置用户上下文时阻断，不默认冒用某用户。最适合早期上游功能PR，但需要适配新workflow detail/dialog页面。

### F10：Editing尺寸、源分辨率预设与参数记忆

**文件**：`viewmodel/EditingDimensions.kt`、`model/WorkflowValues.kt`、`ui/components/config/SourceDimensionsPreset.kt`、`DimensionStepperRow.kt`、config builders、`ImageToImageViewModel.kt`、`WorkflowManager.kt`。

width/height按能力显示，各自从saved/default/UI fallback加载，输入校验后执行，隐藏/无效临时编辑不覆盖已存值；源图比例/分辨率预设，独立UNET记忆（`d401c37`），分辨率下拉预设。上游后来已有统一尺寸/参数记忆，需仅提仍缺的source preset和专门边界测试，避免贡献旧整套参数模型。

**测试**：`ui/components/config/{EditingDimensionsConfigTest,SourceDimensionsPresetTest}`；新GenerationParams上仍需同等行为测试。

### F11：按prompt绑定的before/after比较

**文件**：`viewmodel/{EditingComparison,EditingViewerComparison}.kt`、`ui/components/EditingImageComparison.kt`、ImageToImage/Viewers/GenerationViewModel接入。

准备时生成cache-owned源snapshot、按真实promptId绑定结果，显示before/after分割线，ContentScale.Fit两侧同几何；结果/来源未知或进程恢复不假造before。不是当前源图直接冒充历史任务before，不向Intent放Bitmap。

**测试**：`viewmodel/{EditingComparisonTest,EditingViewerComparisonTest}`。上游共享overlay移植需定义snapshot生命周期、批量任务绑定和cache回收，补设备zoom/drag/多结果切换。

### F12：批量/长按、置顶确认、前后台生成监测

**文件**：`ui/components/GenerationButton.kt`、`viewmodel/GenerationViewModel.kt`、`ui/screens/*Generation*`对应四模式屏、`storage/AppSettings.kt`、`MainActivity.kt`、`MainContainerActivity.kt`、`notification/*.kt`、Manifest/WorkManager依赖。

长按生成批量数量、保存偏好、正常点击和长按不double-fire，queue/front操作确认；foreground service维持监测、完成通知、后台worker/boot接线；连接指数backoff/后台retry和数据刷新loading。

**测试缺口/上游重叠**：上游已有queue sheet、按screen/job ownership取消/置顶/确认，需保留这些边界，不把fork批量扩成可操作其他客户端任务。当前没有通知instrumentation或完整batch专用suite。FGS权限、Android限制、电量和通知授权需设备验证；建议批量功能与通知服务分别PR，后者先征求维护者意见。

### F13：prompt finalizer与错误/诊断

**文件**：`util/WorkflowPromptFinalizer.kt`、`WorkflowManager.kt`；`DiagnosticsApplication.kt`、`util/RealtimeDiagnostics.kt`、`storage/AppSettings.kt`、Settings/log viewer；`tools/log_receiver.py`。

finalizer支持裸/nodes/prompt三种wrapper，保留Long，不把widget edit覆盖JSONArray连接，仅补SaveImageAdvanced已知PNG空默认值。错误保留exception details和复制能力。可选日志bundle/实时上传及崩溃诊断默认关闭。

**测试**：`util/WorkflowPromptFinalizerTest`、`RealtimeDiagnosticsTest`。日志上传属于网络隐私能力，需独立同意/脱敏/证书审核；个人接收端和历史日志不进上游PR。上游已有64-bit修复/隐私日志和错误格式化，不能重复整块贡献。

### F14：系统文件选择与数值编辑UX

**文件**：`ui/components/shared/RememberingDocumentPicker.kt`、`storage/AppSettings.kt`、ImageToImage/ImageToVideo/WorkflowEditor/Settings调用处；`ui/components/LoraChainEditor.kt`、`model/LoraSelection.kt`、`util/LoraChainManager.kt`。

OpenDocument持久URI/最近位置、多类导入共用，替代PhotoPicker；LoRA无界float文本和步进输入。上游新的有界-100..100控件/选择动作与这些目标有重叠，属于产品取舍而非缺陷修复。需persist permission失败/进程重启测试和非有限float校验，避免强推无界值。

## 4. 不应作为上游功能提交的差异

- fork版本号/code63、APK动态命名、debug证书兼容签名、R8和fork发布workflow：是发行策略，不把上游版本降回或改成fork发行号。
- `.agents/skills/`、`memorise/USER.md`、根目录plan/findings/progress/task_plan、历史requirements/release/验证handoff、本机日志接收器配置：不是产品patch。新PR从上游干净分支选择业务hunks，不把工作历史/私人运维记录混入。
- README分叉下载链接、历史APK/映射/测试工件、个人主机地址和key不进PR。本文和PR计划不包含实际运行密钥或个人服务器地址。
- 现存untracked技能目录和Python缓存不是本次提交对象，原stash不消费。

## 5. 当前阶段已吸收、无需向上游重复提出

阶段A cherry-pick上游 `a3382a6`（编辑连接后换client）、`6f03ba6`（GGUF wiring LoRA）、`12c35cd`（图库当前index/gesture callback）；另外定向适配 `f39ce9e` 的旧editor覆盖保护和 `f09f616` 的clear-cache不删workflow。

这些是上游自己的修复，不包装成我们的新PR。上游已有：select-all、LoRA范围改进、结果source actions、统一参数/尺寸、64-bit部分修复等；仅保留fork额外语义和补充测试。阶段A的旧editor适配继续保留slots1–4、三wrapper、Long/连接/PNG保护，不声称已移植统一架构。

## 验证边界

当前阶段A实际跑过Android JVM177项（28 suites）和Python84项，均零失败；debug构建成功。Reconnect/gallery/cache删除边界中的部分新增断言是source契约，不是运行Android Activity/Compose/SharedPreferences的证明。完整上游新架构、SSO/TLS迁移、设备手势、真实服务器生成/删除均未验收，详见状态文档。
