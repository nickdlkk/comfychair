# Upstream PR 工作记录

> 本文档记录 fork（main / customization/v0.8.26）向上游 `legal-hkr/comfychair` 贡献 PR 的实际进展。
> 拆分依据见 `docs/customizations/UPSTREAM-PR-PLAN.md`（记录在 main 分支，本文档也回填到 main）。

## 当前状态总览

| 项目 | 状态 |
|---|---|
| fork 整合 | `main` 已重置为 integration/upstream-20261105（含上游 bfd69ad→825d72a 全量合并），原 main 备份为 `customization/v0.8.26` |
| 分叉点 | v0.8.11 (`791cd95`)，本地定制 56 commits |
| PR 拆分方案 | `UPSTREAM-PR-PLAN.md`（PR-A … PR-K） |
| **PR-B** | **已发 [PR #51](https://github.com/legal-hkr/comfychair/pull/51)（OPEN）** |
| **Login backup restore** | **已发 [PR #50](https://github.com/legal-hkr/comfychair/pull/50)（OPEN）** |
| **PR-C** | **topic 分支已就绪**（feat/upload-reuse，未发 PR） |
| **PR-D** | **topic 分支已就绪**（feat/direct-input-reuse，含 C，未发 PR） |
| PR-A/E…K | 未开始 |

## PR-B — Import saved ComfyUI server workflows（已发 PR #51）

### PR

- **PR #51**：https://github.com/legal-hkr/comfychair/pull/51（OPEN，head=nickdlkk:feat/server-workflow-import → base=main）
- rebase 到 origin/main 6f202a1（上游 FormDialog 改造冲突已适配：warnings 块迁入 FormDialog content slot；prepareImportJson 合并 fileName + filter 感知类型检测）
- FAB 第三项 Import from server 入口（f4f3831 的 rebase 丢弃后重做进主提交）
- 最终提交 589b3a1，13 files +806/−59，55 tests 全绿

### 分支

- **分支名**：`feat/server-workflow-import`
- **基线**：上游 `origin/main` @ `825d72a`（从最新 upstream main 新建，非集成分支）
- **提交**：单提交 `2f89fd2` `feat: import saved ComfyUI server workflows`
- **远程**：已推送至 nick（nickdlkk/comfychair）
- **diff 规模**：13 files，+778 / −55

### 内容

浏览/下载已连接 ComfyUI 服务器的 userdata 保存 workflow 并导入：

- `ComfyUIClient.kt`：`/users` 身份发现、`/userdata` 列表/下载 API，路径校验、8MB 上限、SavedWorkflowResult/SavedWorkflowError
- `util/ServerImportOrigin.kt`：服务器身份门禁（client 实例 + serverId 匹配），连接切换/断开时使导入失效
- `util/WorkflowImportPreparation.kt`：JSON 形状检查 + LiteGraph 转换前置（等 /object_info ready）
- `WorkflowManagementViewModel.kt`：服务器浏览状态机、request generation 防串、prepareImportJson 复用管线
- `WorkflowsSettingsScreen.kt`：ServerWorkflowPanel overlay（适配上游重设计页面）
- `WorkflowDialogs.kt`：WorkflowDetailsDialog 增加 `warnings` 转换提示参数
- 测试：`ServerWorkflowApiTest`（MockWebServer 9 例）、`ServerImportOriginTest`、`WorkflowImportPreparationTest`

### 与 fork 原始提交（89e592b）的差异

按上游重设计页面做了适配，不是直接 cherry-pick：

1. 删 fork 版本号 bump、旧 TopAppBar 按钮排布、fork 版 ImportWorkflowDialog composable、`importDescriptionError` 状态
2. 入口改为 ServerWorkflowPanel overlay 挂在上游新页面的 Box 内
3. 共享对话框加 `warnings` 默认参数（现有调用方不受影响）
4. `org.json` 用上游 version catalog，MockWebServer 新增
5. 保留：服务器 API、身份门禁、prepare/confirm 管线、全部测试

### 验证

- JVM：55 tests / 0 failures / 0 errors（含 PR-B 相关 14 例）
- APK：`comfychair-v0.8.13-prb-serverworkflow-debug-20261105-1847.apk`（86,556,476 bytes，SHA256 `d3cb59cf940e...69453286`），aapt 确认 versionCode 40 / v0.8.13（上游原值）
- LAN 分发：`http://192.168.4.168:9010/comfychair-prb-debug.apk`（GET 200）
- 真机/真实 server 端到端：**未测**（JVM 测试不能替代），发 PR 前需补

### 过程记录

- 冲突文件：build.gradle.kts、ComfyUIClient.kt（仅 import）、WorkflowsSettingsScreen.kt（整页重设计）、WorkflowManagementViewModel.kt（import 块 + onFileSelected 管线）、strings.xml
- 踩坑 1：`git add -A` 把工作区 untracked（.agents skills、__pycache__、log zip）带进了 amend 前提交，已用 `git reset --soft origin/main` + 显式文件列表重新 add
- 踩坑 2：cherry-pick 后 import 块清理失误，ServerImportOrigin/WorkflowImportPreparation import 丢失导致编译失败
- 踩坑 3：冲突解决时误删上游 `versionCode/versionName`（40/v0.8.13），aapt 检查发现空版本号后 amend 修复

### 发 PR 前的门禁（未完成项）

- [ ] **先在上游开 feature proposal / issue**（UPSTREAM-PR-PLAN 建议 PR-B 先讨论再交 diff）
- [ ] GitHub fork legal-hkr/comfychair，从 fork 的该 topic 分支开 PR（当前 token 无 upstream 权限）
- [ ] 真机 + 真实 ComfyUI server 端到端验证（single-user / multiuser 两种形状）
- [ ] PR 描述注明设备验证范围与限制
- [ ] 授权后 push / open PR（目前未向维护者发出任何 PR）

## 后续 PR 候选顺序（参照 UPSTREAM-PR-PLAN）

1. PR-A PNG 导出默认值修复（最小，先确认上游仍可复现）
2. PR-C 内容寻址上传复用（RAM-only）
3. PR-E 只读服务器素材 picker（可拆模型/UI 两步）
4. PR-F 扩展 slots + bypass（先和维护者讨论 slot 模型）
5. 其余按计划文档顺序

## 环境备忘

- 构建：`JAVA_HOME=/root/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2`（系统 JDK17 不可用，Gradle 9.4.1）
- 打包产物命名：`comfychair-{版本}-{特性}-debug-{时间戳}.apk`，固定名 `comfychair-prb-debug.apk` 供覆盖安装
- LAN 分发：9010 端口 python http.server，`--directory /root/share/comfychair`，URL 不带 /comfychair/ 前缀
- remote：`origin` = legal-hkr（上游，只读 fetch）、`nick` = nickdlkk（fork 推送，URL 含 token 明文，待改为 credential helper）

## PR — Login screen backup restore（已发 PR）

### PR

- **PR #50**：https://github.com/legal-hkr/comfychair/pull/50（OPEN，head=nickdlkk:feat/login-restore-backup → base=main）
- 描述含 What/How/Testing/Scope notes；Testing 中真机验证表述留给维护者确认，未夸大验证级别
- 状态：待维护者响应；未做 proposal issue（功能小、diff 97 行，直接 PR 并在 Scope notes 邀请讨论 UI 位置）

### 分支

- **分支名**：`feat/login-restore-backup`
- **基线**：上游 `origin/main` @ `70222e6`（fetch 后新建，包含比 PR-B 更新的上游提交）
- **提交**：单提交 `6562acf` `feat: restore configuration from a backup on the login screen`
- **diff 规模**：3 files，+97
- **远程**：已推送至 nick

### 内容

在登录/连接服务器界面（LoginScreen）的 Add server 按钮下增加「从备份文件恢复」按钮：

- 场景：全新安装/清数据/换机后首次打开，服务器列表为空，无需先进 Settings 即可恢复备份
- 流程：GetContent 选 JSON → 复用 Settings 同款覆盖确认对话框（title/msg/button 均复用现有 strings）→ `BackupManager.restoreBackup` 恢复 → 就地刷新服务器列表与选中项
- `serversChanged` 时调用 `ConnectionManager.invalidateForRestore()`（与 Settings 路径一致）
- 连接中或恢复进行中按钮禁用；恢复用 LONG toast
- 复用 `BackupManager.restoreBackup`，无新恢复逻辑；新 string 仅 1 条 `button_backup_restore_login`（中英）

### 验证

- compileDebugKotlin / assembleDebug 通过，aapt 确认 40/v0.8.13
- JVM 测试 41/0（该基线上游源码树共 9 个测试类）
- APK：`comfychair-v0.8.13-login-restore-backup-debug-20261105-1905.apk`，固定名 `comfychair-loginrestore-debug.apk`，LAN 200
- 真机验证：未做

### 备注

- 类似功能此前未做过（fork 历史无 login 恢复入口；备份功能本身是上游 058f3e1 引入、Settings 页承载）
- 送上游前需门禁同 PR-B；UI 位置（服务器列表下 vs 行菜单/overflow）可能需维护者讨论

## 更新记录

### 2026-11-05（晚）

- PR #50、#51 均已发出，上游无 review/评论（待维护者响应）；早期 PR #25（slot bypass）4 月底被关闭未合并。
- PR-B 最终提交 `589b3a1`：rebase 到 origin/main `6f202a1`，适配上游 FormDialog 对话框体系（warnings 迁入 content slot）、合并 importFileName/filter 类型检测；FAB 第三项 Import from server 入口并入主提交。55 tests 全绿。
- 测试包：beta2 APK（v0.8.13-beta2，`comfychair-prb-debug.apk` 固定名分发），与 beta1 差异 = 上游 editor 重构 + FAB 入口。

## 下一个 PR 点：PR-A — SaveImageAdvanced PNG 默认值修复（推荐下一个）

**已验证上游仍可复现**（2026-11-05 检查 origin/main）：

- `git grep bit_depth|SaveImageAdvanced origin/main -- app/src/main/java` → 无结果，上游完全没有该修复
- fork 侧实现：`customization/v0.8.26` 的 `util/WorkflowPromptFinalizer.kt`（`fillMissingPngSaveInputs`：仅当 `format` 精确为 png 时补 `format.bit_depth=8-bit`、`format.input_color_space=sRGB`，只补 missing/null/blank，不猜其他字段）+ `applyNodeAttributeEdits`（widget 编辑安全过滤：跳过 JSONArray 连接、支持 Long）
- fork 中 `WorkflowManager` 在 3 处 prepare 末尾调用；上游需接入 `WorkflowManager.prepareWorkflow`（单一出口，比 fork 干净）
- 上游 prepareWorkflow 已有 Long seed 处理（`(params.seed.toLongOrNull() ?: 0)`），fork 的 Long 保护可能部分冗余，移植时对照删重

**步骤**：
1. `git branch topic/pr-a-png-export-defaults origin/main`（基于 b97af69 或更新）
2. 移植 `WorkflowPromptFinalizer`（去掉 fork 的 bypass 相关调用点，只保留纯函数对象）
3. 在上游 `prepareWorkflow` 返回前接 `fillMissingPngSaveInputs`
4. 测试：从 fork `util/WorkflowPromptFinalizerTest` 抽裸/nodes/prompt wrapper 负例 + PNG 缺字段正例
5. 描述里附 node schema 依据（SaveImageAdvanced 的 format.bit_depth/input_color_space 定义），说明为何补这两个默认值

**为什么排下一个**：diff 最小（预计 <100 行）、无 UI 依赖、已确认上游缺失、独立可回退——比 PR-C（上传复用，~500+ 行含并发/认证）审查成本低得多。

### 2026-11-05（深夜）PR-C / PR-D topic 分支完成

**PR-C（feat/upload-reuse @ 9680039，基于 origin/main 0c43c47）**
- `model/ServerImage.kt`（精简版：descriptor 校验 + viewUrl，fork 的 picker 富模型不带）
- `connection/ImageUploadReuse.kt`：**RAM-only**（fork 的 PrivateImageDiskCache/namespace/上传 descriptor 磁盘缓存全部剥除，那是 PR-H 的内容）
- ComfyUIClient epoch API：imageReuseSession（冻结认证，serverTrust.configure 派生 client，禁 redirect）、imageUploadIdentity、uploadImageReusing（完成时校验 epoch）；setWorkingProtocol(值变化时)/setCredentials 失效化
- ITIVM 4 个上传点（editing source/ref1/ref2/inpaint source）+ ITV 1 个点全部切到 uploadImageReusing；随机 UUID 命名路径在这些屏上完全移除；错误面保留（401/403→connection alert，其余→error_save_image toast）
- 上游无 stall 概念冲突：reuse 按 payload 字节推进，无进度则 HTTP 层 60s 超时
- 测试：ImageUploadReuseTest 13 例（MockWebServer，含新增 protocolChange epoch 用例）+ WiringTest 4 例源码契约；全量 59/0

**PR-D（feat/direct-input-reuse @ 3c2d966 = C + D 提交）**
- `connection/DirectInputSource.kt` 原样移植（supports() 槽位 token 校验、eligible 对象身份三重校验）
- ComfyUIClient.reuseInputImage（HEAD 存在性，404 才回退编码上传）
- ITIVM：directSources 进程级 provenance map（slot 1）；新入口 useServerImageAsSource(descriptor, bitmap)；useAsSource/onSourceImageChange/clearSourceImage 三处 remove(1)
- MediaViewer：currentItemDescriptor()（非视频 + descriptor 校验），MediaViewerOverlay/Host/MainNavHost 三层把 descriptor 传给 ITIVM（bitmap 必须是 viewer 对同一 descriptor 的自有解码）
- prepareEditingWorkflow：eligible + supports(workflowTemplate, 1) + HEAD 全过才直用原文件，否则回退 uploadImageReusing
- 设计约束：上游尚无 server→source 的其它入口（PR-E 才有 picker），当前唯一 provenance 产生点是 MediaViewer 的 Use as source；上游 MediaViewerItem(filename/subfolder/type) 天然带服务器 descriptor
- 测试：DirectInputSourceTest（eligibility 矩阵 + supports 正负例）+ DirectInputWiringTest（3 处 remove(1) 计数、回退路径、handoff 链）；全量 65/0

**踩坑**：MainNavHost 局部 lambda 引用后置局部函数需声明在前（三次重排才成功）；cherry-pick C 到 D 分支因 patch-id 相同直接快进。
**均未发 PR**；发 PR 前需真机验证 + 确认上游是否要求 feature flag。

### 2026-11-05（末）分支重命名 + PR 关闭

- 本地与 nick 远程分支统一改为 `feat/<功能名>`：feat/server-workflow-import（原 topic/pr-b-server-workflow-import）、feat/login-restore-backup、feat/upload-reuse（原 topic/pr-c-upload-reuse）、feat/direct-input-reuse（原 topic/pr-d-direct-input，C+D 合集）
- PR #50/#51 已关闭，由 **PR #52/#53** 接替（feat/ 分支名，内容不变）：GitHub 跨 fork PR 的 head 分支无法用 rename API 换名——rename 级联删旧 ref 会关闭 PR 且 reopen 失败（head 丢失）。可行流程：保留旧 ref → reopen → 建新 PR（feat/ head）→ 关旧 PR 留指针评论 → 删旧 ref
- PR #52 额外 rebase 到 origin/main 0f5dc25：登录屏对话框适配上游 ConfirmDialog 模式（destructive + SettingsBackupRestore icon）、按钮统一 FilledTonalButton、上游改写的确认文案直接复用
- cd1 测试包（C+D 合集）已 LAN 分发：comfychair-cd-debug.apk

### 2026-11-06 PR-A 完成（独立 worktree，未推送）

**feat/png-export-defaults @ b7130fb**（worktree /root/comfychair-wt/png-export-defaults，基于 origin/main 5de063f，单提交，3 files +193/−37）

**关键更正**：此前 PR-STATUS 记录"已验证上游缺失"是在 main 工作树 grep 的——结果被 fork 自己的 137b38a（integration 期间已把 finalizer 合入 main 工作树但从未推送上游）污染。本次在干净 worktree 对上游 5de063f 重新验证：上游确实无 SaveImageAdvanced/bit_depth 处理，结论仍成立但证据需用干净树。

**实现**：
- `util/WorkflowPromptFinalizer.kt`（fork 原样）：fillMissingPngSaveInputs（仅 format 精确=="png" 补 bit_depth=8-bit/input_color_space=sRGB，missing/null/JSONObject.NULL/blank 才补，显式值/JSONArray/占位符/非PNG/大小写变体/其他 widget 全不动，JSON 异常原样返回）+ applyNodeAttributeEdits（widget-only、连接保护、Long/Float/Double/Boolean 标量、prompt/nodes/裸三 wrapper 容忍、空 edits 短路）
- `WorkflowManager.applyNodeAttributeEdits` 改为委托 finalizer（原实现连接保护/Long 已有，行为不变，新增 wrapper 容忍与空 edits 短路）
- `prepareWorkflow` 返回前接 fillMissingPngSaveInputs（单出口，在 node edits 与 LoRA 注入之后——修好 edits 刚清空的字段）
- schema 依据：save-image-advanced 的 format.bit_depth/input_color_space 是 required widget 无内置默认，旧 workflow/手写 JSON 提交即报错

**验证**：56 tests / 0 failures（新增 WorkflowPromptFinalizerTest 6 例：三 wrapper×三缺失形态、显式值/连接/占位符保留、10 节点负例矩阵、edits 后修复、畸形 JSON）；WorkflowPreparationGoldenTest 通过（内置模板无 SaveImageAdvanced，golden 不受影响）；assembleDebug 成功（40/v0.8.13）；diff --check + secret 扫描干净。

**限制**：Log node 461 的现场只在 fork 实例见过——schema 依据来自节点文档与 fork 行为，未对上游用户实例重现；无真机验证；custom-node 默认值是否该内置由维护者决定（PR 描述需附 schema 依据）。

**已推送并开 PR：[PR #55](https://github.com/legal-hkr/comfychair/pull/55)（OPEN/MERGEABLE，head=nickdlkk:feat/png-export-defaults → base=main，rebase 到 v0.9.0 tag c40aa36，rebase 后 56 tests 仍全绿）。PR 描述含 schema 依据与"单设备验证"如实声明。**

### 2026-11-06 多语言补全

应用声明 6 语言（locales_config.xml：en de es fr pl zh）。审查三个 PR 的 strings：

- **PR #52**：新 string 仅 `button_backup_restore_login`，en+zh 有，**补 de/es/fr/pl**（commit d2ebfa3），MERGEABLE
- **PR #53**：14 个 `server_workflow_*` en+zh 有，**补 de/es/fr/pl**；另发现 en 里混入 10 个 `notification_*` fork 遗留 string（前台服务功能，分支内零引用、非本 PR 范围）→ **删除**（commit 38b45b1→b9074db）；rebase 到 origin/main 2a2f2cd 解决 versionCode 冲突（保上游 41/v0.9.0）与 `_events.emit`→`emitEvent` 重构（上游改了事件发送 API），64 tests 全绿，MERGEABLE
- **PR #55**：零 string 改动，无需补
- 教训：fork 移植 PR 提交前应 `git diff origin/main...HEAD -- app/src/main/res/values/strings.xml` 审一遍，fork 大杂烩提交容易把无关 string 带进来；zh 常在冲突解决时部分丢失（本例 notification_* 只进了 en），双向都要查
