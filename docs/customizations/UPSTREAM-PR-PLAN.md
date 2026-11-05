# Upstream PR decomposition / 上游贡献计划

## 不提交整个fork

Target reviewed: `legal-hkr/comfychair` upstream `bfd69ad878e33ccd65355f001e351a4c879864ca`。本地 `integration/upstream-20261105` 供fork保留功能/阶段整合，**不适合直接作为一个巨大PR的head**。尚未push、创建PR或向维护者承诺接受。每个真正PR需从届时最新上游main新建topic branch，移植最小业务diff和测试，重新验证；以下为拆分建议，不是现成已验证的上游分支。

许可证实查根目录`LICENSE`为GNU GPL version3文本；保留原始版权/上游作者归属，新增代码依仓库现有授权发布。本文不推定“or later”授权、不捏造CLA/DCO要求；发PR前读当时CONTRIBUTING/模板，有额外签署再按真实要求处理。外部依赖/图标/样例workflow另查来源与兼容授权，测试fixture使用自造最小JSON和临时媒体，不附用户workflow/图像。

## 建议顺序

1. PR-A：PNG导出默认值/三wrapper finalizer的小修（先确认上游当前仍能复现）。
2. PR-B：从服务器导入保存的workflows（最强的独立可见功能候选）。
3. PR-C：RAM-only immutable upload reuse（含字节进度），再独立PR-D Direct Input复用。
4. PR-E：只读服务器素材发现/picker，先不带磁盘缓存/删除。
5. PR-F：扩展source slots和bypass；PR-G：Editing before/after比较；两者分别讨论。
6. PR-H：持久缓存（需维护者认可隐私默认值）；PR-I素材库、PR-J独立图库为可选UX。
7. PR-K批量；通知服务、LAN物理删除、实时远程日志先保留fork，需专门proposal和安全review。

## PR-A — Preserve PNG export defaults and safe widget edits（小型portable fix）

- **范围**：`util/WorkflowPromptFinalizer.kt` 中SaveImageAdvanced PNG空字段修复及连接/Long保护；接入上游现有`WorkflowManager.prepareWorkflow`，不要搬旧五组prepare方法。先对上游重现缺失 `format.bit_depth`/`format.input_color_space`，已修则仅贡献尚缺的回归测试或关闭候选。
- **依赖**：无fork UI/服务依赖；上游已有Long处理/old-editor保护，重复部分删掉。
- **验收**：仅format精确为png、字段missing/null/blank时补已知8-bit/sRGB；保留显式值、JSONArray连接和未知format。不替用户猜尺寸/模型等字段。
- **测试**：从`util/WorkflowPromptFinalizerTest`抽取裸/nodes/prompt wrapper、Long、连接、PNG负例，并扩展上游goldens。需用上游toolchain跑全套，不能引用fork177通过代替。
- **文档/license**：简短bug/reproducer/行为变化与节点依赖说明，GPL归属，不带release notes/版本号。
- **接受预期**：复现清楚、diff小更利于审查；维护者可能不愿内置custom-node默认值，应提供node schema依据，不强加普遍规则。

## PR-B — Browse/import workflows saved on the connected ComfyUI server（推荐首个功能PR）

- **移植文件**：`ComfyUIClient.kt`中的list/download和路径校验；`util/{ServerImportOrigin,WorkflowImportPreparation,LiteGraphConverter}.kt`；`viewmodel/WorkflowManagementViewModel.kt`；将`WorkflowsSettingsScreen.kt`旧按钮/确认链移到上游新workflow list/detail及共享WorkflowDialogs。
- **不含**：fork版号、私有连接地址、全部旧screen、素材库或日志上传。
- **依赖**：上游流式NodeTypeRegistry/object_info；准备链必须等定义ready再转换widget；最终save前origin+client+server身份门禁，包括editor回传。
- **验收**：`/users`真实single-user/multiuser形状；无用户上下文时multiuser明确阻断。中文/空格/+和目录整个路径作为单一段编码；超限/遍历/非JSON/认证错误失败。警告在保存前确认，取消清来源，连接切换任何阶段不能跨server保存。
- **测试**：`ServerWorkflowApiTest`、`ServerImportOriginTest`、`WorkflowImportPreparationTest`移植；补网络→转换→mapping editor→save端到端状态测试与真实单/多用户只读fixture。不要把只验证JSON形状说成端到端通过。
- **文档/license**：README能力/限制、权限和无写服务器保证；中英strings，其余语言按维护者约定；GPL、纯自造fixture。
- **接受预期**：原生API、无需插件，依赖少；可能要求先补多用户选择或调整入口，先提feature proposal比大diff更稳妥。

## PR-C — Content-addressed immutable upload reuse（不含持久缓存）

- **文件**：`connection/ImageUploadReuse.kt`、`ComfyUIClient.kt`上传epoch API、上游`ImageToImageViewModel`及统一上传函数小范围接线。真实payload-byte progress一起带入。
- **依赖/安全**：用上游ServerTrust构造受信任client；保持动态认证来源和旧epoch完成拒绝，Cookie不退化匿名身份；shutdown/protocol/auth改变撤销epoch。禁止accept-all TLS。RAM-only避免与上游memory-only默认冲突。
- **验收**：按最终编码含mask/JPEG格式hash；`overwrite=false`、采纳返回descriptor；同内容single-flight、等待者取消隔离、leader取消可重试、有界entries/请求/响应。HEAD only404重传，认证/网络错误不POST fallback。无固定文件名覆盖队列输入。
- **测试**：`ImageUploadReuseTest`和WiringTest、MockWebServer；新增上游TLS确认/拒绝/变更证书、Cookie更新、redirect和epoch失效实测。四模式如共用上传逻辑不得引入回归。
- **文档/license**：HEAD兼容要求、不可用时可解释错误、复用命中不夸大进度；GPL/现有OkHttp无新增依赖。
- **接受预期**：明确减少上传但带状态复杂度；维护者可能要求更简单实现或feature flag。用行为测试和byte/network数据解释，不声称已获同意。

## PR-D — Reuse unchanged native LoadImage inputs（依赖C）

- **文件**：`connection/DirectInputSource.kt`、源picker acceptance、上游统一`WorkflowImages`与`prepareWorkflow`的裸路径/单次注解。
- **范围**：只在已有可表达槽位上实现；若需要fork slots2–4则等待PR-F，别暗中依赖未合入字段。
- **验收/测试**：相同epoch/Bitmap identity/generationId、Editing/Input、无mask/压缩、所有token为完整原生LoadImage.image；custom/mixed consumer/reencode/restored provenance拒绝直用。`PersistentServerImageTest`相关纯逻辑和HEAD测试转成不依赖磁盘版本；golden覆盖路径注解和真实节点消费者。
- **文档/license**：清楚区分原文件与Android重编码语义及第三方替换限制；GPL。
- **接受预期**：优化可选，不应把不能证明原文件等价的情况标成“安全无损”。

## PR-E — Read-only server Input/Output image browser/picker（可分两PR）

- **文件**：`model/{ServerImage,ServerAsset}.kt`、客户端发现/分页；`viewmodel/{ServerImagePicker,ServerImagePreviewCache,ServerPickerScrollPolicy}.kt`；适配上游source actions/preview，而非复制旧screen。
- **依赖**：上游共享鉴权/信任、页面取消模型；先RAM only，独立Settings gallery不必在第一稿出现。
- **验收**：assets source/search/sort/cursor，明确unsupported才legacy fallback；同源URL/descriptor校验，取消旧查询不回填；>5 productive页、稀疏/重复/循环cursor可恢复、缩略图失败不阻塞列表、刷新失败保留网格；原图按需、真实byte/取消。
- **测试**：ServerAssets/Key/Paging/Picker/Preview/Scroll/OriginalLoading各suite，补Compose/设备选择正确slot/gesture；live只读用显式可选环境。
- **文档/license**：ComfyUI API版本/legacy限制/采样不代表省网络，localization与GPL。
- **接受预期**：功能范围较大，可先后端模型+tests再UI；维护者可能只接受原生assets版本，不要求兼容一切旧端点。

## PR-F — Additional image source slots and graph bypass（可选高级功能）

- **文件**：扩展上游`WorkflowImages`/GenerationParams、`TemplateKeyRegistry`/FieldMapping、BypassNodeResolver、ImageToImageViewModel和source UI。
- **依赖**：先统一slot标识再接C/D/E；source2–4与reference1/2不是同一种输入。
- **验收**：模板能力驱动槽数、workflow切换clamp、replace返回正确slot、bypass不送空目录、图连接重写、队列任务保留各自源snapshot。不能只用UI mode=4伪装有效API bypass。
- **测试**：四槽/bypass组合goldens、链接/多消费者/custom loader/空源负例；补Editor映射优先级和document picker返回测试。现有fork覆盖不够，必须新增。
- **文档/license**：模板placeholder契约和最小自造示例，GPL。维护者可能偏向任意数量generic slots而非固定4槽；先讨论模型再交UI。

## PR-G — Editing before/after comparison（独立UX）

- **文件**：`EditingComparison`/`EditingViewerComparison`、`EditingImageComparison.kt`，上游viewer overlay和结果关联。
- **依赖**：上游shared result lifecycle；不要求F/E。只给Editing有可信source snapshot的结果显示。
- **验收**：按promptId绑定准备时snapshot，不用当前源替代；无provenance不显示before；两侧同Fit几何、divider边界、换源/批量/取消不串图，明确cache清理生命周期。
- **测试**：现有Comparison suites加共享viewer/process-death/multi-result/取消清理，设备zoom和divider拖动。
- **文档/license**：截图用自有生成样例，说明进程重建限制和本地snapshot保留，GPL。
- **接受预期**：易演示但会占预览/增加存储，需要维护者认可交互和privacy模式，不同时推整套新UI。

## PR-H — Opt-in persistent picker/upload cache（依赖C/E，先讨论隐私）

- **文件**：PrivateImageDiskCache/PersistentImageCache/ServerImageListDiskCache/ImageCachePolicy、settings controls+统计、backup exclusions和epoch namespace。
- **验收**：上游MEMORY_ONLY不得被暗中绕开；产品决定显式opt-in还是跟media policy。Cookie各账号HMAC隔离、无明文持久化；clear保留key、不删素材/workflow/当前选择，旧请求generation门禁；TTL和retention分离，硬cap和统计字节可解释；restore-defaults即时重载policy。
- **测试**：PersistentServerImage/ImageCacheCleanup，再加Cookie、backup/device transfer、SharedPreferences/Keystore真机、reset/restore/进程死亡状态；不能仅靠source契约。
- **文档/license**：存储种类、默认值、清理范围、密钥丢失、非加密HMAC命名不等于加密图片内容，GPL。
- **接受预期**：与上游隐私方向有潜在冲突，可能不接受；未经讨论不捆绑其它PR。

## PR-I/J — Material library / Standalone server gallery（两项可选UX）

- **I文件**：MaterialLibrary、MaterialLibraryViewModel、Screen/Picker与save动作。依赖普通导入/导出，不强依赖H。
- **I验收/tests**：用户保存素材不能被clear cache删除；JPEG语义明确；补持久化/原子写/重启/重名/导入大图/删除确认与selection tests，现无专用suite。
- **J文件**：ServerGalleryViewModel/AnchorPolicy、ServerGalleryScreen、SourceTransfer；依赖E，overlay navigation按上游架构适配。
- **J验收/tests**：独立入口、滚动anchor/返回target、一次性身份ticket、紧凑无重复Settings chrome、无bitmap Intent；移植Layout/Position/OriginalLoading测试并做设备回归。
- **文档/license**：两项各自用户说明、数据保留策略、中英资源、GPL。
- **接受预期**：维护者可能认为与已有gallery重叠；先证明Input素材与history gallery的区别，不把fork外观偏好强加默认UI。

## PR-K — Batch submission（先复用上游队列所有权）

- **文件**：上游Generate menu/VM；仅移植batch count与逐次准备/提交机制，不复制旧GenerationButton。
- **验收/tests**：click/longpress single-fire；batch中seed/source snapshot稳定；中途取消/断网/partial failure有定义；front/clear确认和own-job权限不扩大。需要新增完整batch行为测试，fork现有source契约不够。
- **文档/license**：batch数量与模板batch_size区分；资源占用限制、中英strings、GPL。
- **接受预期**：先问是否更愿在现有菜单加明确“Repeat N”而非隐藏长按。

## 默认fork-only / 暂不送PR

- **物理删除插件和LAN consent（F06）**：管理员破坏性API，不是上游history delete增强。需独立RFC、威胁模型、排他维护/TOCTOU/tombstone限制、安装/版本兼容和安全review；默认disabled，绝不带运行key/IP。Android与Python分两项目/包是否合适由维护者决定，当前不替其作政策决定。
- **Foreground monitoring/notifications**：FGS/boot/worker、电量/Android后台权限跨度大，先proposal、生命周期/ownership设计和device tests，不能顺带放进batch PR。
- **Realtime log upload/receiver**：默认off也不代表无隐私成本；需明确用户同意、敏感字段脱敏、受信任传输、retention，个人接收器不提交。
- **Fork release/CI/signing/version/个人工作记录**：不进上游。系统OpenDocument记忆、source preset、无界LoRA输入可各自小proposal；已有上游等价项不重提。

## 已在上游的修复：从贡献清单剔除

`a3382a6`、`6f03ba6`、`12c35cd`、`f39ce9e`、`f09f616`相关修复只为同步fork，不是我们的新贡献。上游select-all、Long处理、LoRA控件、结果操作、统一参数/尺寸已部分覆盖fork目标；PR必须对最新upstream逐项重现剩余差异，不能靠历史patch-id推断“都缺”。

## 每个真实PR的提交门禁

- 从新fetch的upstream topic branch出发，仅显式stage该PR文件；不直接用本集成分支，不cherry-pick大release提交。
- 单一用户问题/可独立回退；列出依赖PR和行为限制；license/attribution/模板按真实仓库要求。
- tests先重现再修，运行上游要求的unit/goldens+assembleDebug；触及shrinker跑release，涉及服务端跑Python；设备/实际server未测明确写出。
- diff中无keys、私人IP/URL、日志、APK、mapping、个人workflow/图片、根工作历史和`.agents`目录。新增文档重新编写公共示例，不整批带入历史handoff。
- README/strings/隐私说明和测试一起提交；不擅自改版本、签名、发布CI。最终`git diff --check`、扫描secret、检视实际diff。
- 向用户报告topic SHA、base SHA、测试实际计数/工件，不用fork的绿色build冒充上游验证。获得单独授权后才push/open PR；目前没有发出任何PR。
