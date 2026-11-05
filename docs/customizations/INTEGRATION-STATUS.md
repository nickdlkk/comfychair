## 2026-11-05 ITI 屏幕续接：仍未解决

- 对照完整 stage 2（1368 行）、stage 3（422 行）与工作树，保留现有大块冲突而非任选一侧；局部合并入口为上游 `onUseAsSource` 加 fork 素材库 VM。引入 `activeSourceSlots`，以真实占位符身份生成槽位，修正仅有 slot 4 时错误显示/允许 slot 2；inpainting 仅接受槽 1。屏幕 pager 及服务器 picker 的 slot 检查接入该规则；服务器 picker 重新校验的 key 由槽数量改为模式和占位符，避免槽数量相同但身份变化时保留旧会话。新增 `ActiveSourceSlotsTest` 三项（稀疏槽、模式、无关占位符）。这些屏幕改动仍位于未解决冲突中，不能 stage 或视为可用功能。
- 实跑 `:app:testDebugUnitTest --tests sh.hnet.comfychair.viewmodel.ActiveSourceSlotsTest --offline --no-daemon --console=plain`，在 main Kotlin 编译失败，测试未执行；日志 `/root/.hermes/profiles/dev/cache/scratch/iti-slots-test.log`，屏幕 399 条直接诊断，`ActiveSourceSlots.kt` 无直接诊断。缺少独立 kotlinc，无法单独执行该测试。没有设备验证、没有 stage/push。
- 核心阻塞：屏幕还有 13 个冲突块，不能仅清标记；上游 `rememberJobSubmitter` 接受 String，但 ITI `prepareWorkflow` 返回含 snapshot/unresolved 的 `PreparedImageWorkflow`，若简单改用 JSON 会丢比较关联与确认/front 语义；`GenerationControls` 也缺 fork batch 与真实上传进度。上游新 `LocalMediaViewer` 尚需接通 fork 的 slot/bypass 和 comparison capability。尺寸保存及旧 dimensions 测试仍需迁移。后续须整体完成屏幕并重编译、执行回归，不要把当前 pager 局部改动误认为实现完成。

## 2026-11-05 导航/设置迁移进展（仍未完成）

- 工作树中的 MainContainerActivity 和 MainNavHost 已去掉冲突标记，采用上游 activity-scoped ViewModel、Settings 页与 viewer overlay，并保留前台通知恢复及素材库入口；新增 Settings 入口访问无底栏的独立服务器图库，保留图库原有插件删除密钥配置、LAN consent 和物理删除流程，未替换为 history 删除。图库“用作源图”改为同 Activity 回调接入 ITI，保留 FullImage 来源身份，不再 finish 主 Activity。
- 将 fork 图片缓存策略/统计/专用清理组件接到上游 CacheSettingsPage；工作流列表保留服务器工作流搜索/下载与转换警告，并迁入上游详情页/FAB。共享 WorkflowDetailsDialog 增加可选警告。Manifest 移除已被上游删除的两个旧容器声明，MediaViewerActivity 暂留给通知及未迁移的调用者。
- 上述均是**进行中修改，未 stage 这六个冲突路径**。尤其 ImageToImage/ImageToVideo 屏幕、ViewModel 与 MediaViewerScreen 仍有冲突，无法证实入口签名和 slot/bypass；从图库携带源图离开 Settings 时，如果 reloadPending 触发 activity 重建，需防止该源图丢失后再标记完成。旧 ApplicationSettingsScreen/SettingsNavHost 仍保留未解决：其旧依赖与新设置架构不兼容；迁移时必须核对全部设置功能并移除失效实现。MediaViewerActivity 的旧 initialize 与 screen 参数不兼容上游，通知 deep link 尚需迁移或适配。
- 离线实跑 `./gradlew :app:compileDebugKotlin --offline --no-daemon` 三次均失败；最后一次 3347 条 Kotlin diagnostics，仍有19个 unmerged 路径（含无冲突标记的 modify/delete）。最后日志 `/root/.hermes/profiles/dev/cache/scratch/nav-compile3.log`；已处理的 WorkflowsSettingsScreen、设置页与图库文件无直接 diagnostics，但不代表依赖均正确或功能已验证。没有跑 Android 测试、设备或真实服务器，没有 push/stash/reset。

# Integration status / 合入进度与交接

## 2026-11-05 ITI 续接：VM 无直接诊断，尚未完成

- ITI ViewModel 去除全部冲突标记并以双 `GenerationParams` 作为共用字段来源；保留四槽、源图 picker、内容上传/原文件复用、snapshot 和上游结果状态。两侧初始化 observer 合并；编辑工作流切换/恢复时重载槽数量及 bypass，切换 bypass 只允许 2–4 并保存到对应 workflow。共享 `GenerationParams.toWorkflowValues` 保留已有 bypass，避免任何普通参数保存抹掉它；替换或清除源图时清对应 compressed 标记和过期大小。`EditingDimensions.kt` 改读 editingParams。底部 sheet 源图尺寸预设接到共享 width/height callbacks；Screen 仅合并第一处 import，剩余冲突仍很大。
- 三次离线 main 编译和一次定向测试任务实际执行：首轮 VM 7 条诊断；第二轮及最终 `iti-continuation/compile3.log` 中 VM、EditingDimensions、BottomSheetConfigExtensions 各零直接诊断，但全局编译失败、Screen 仍有 910 条级联诊断；此结果不是独立编译成功。新增 `GenerationParamsTest.sharedSavePreservesSourceBypassIncludingExplicitEmptySet`，定向测试阻于 main 编译，**未运行**。日志在 `/root/.hermes/profiles/dev/cache/scratch/iti-continuation/`，VM 修改前备份 `ImageToImageViewModel.before.kt`。
- **未完成/高风险**：旧 `EditingDimensionsConfigTest` 仍使用被移除的平铺 constructor 字段和 editing callbacks，必须迁到 GenerationParams 才可编译测试；`toWorkflowValues` 的尺寸保存对隐藏/非法字段可能覆盖已有值，必须与旧 `saveEditingDimensions` 的保护语义整合并回归。检查稀疏槽映射（当前 count 逻辑可能遗漏单独 slot 4）、异步来源替换与直接复用身份、详细错误从 dialog 降为 toast、batch/front、picker 生命周期、每工作流 bypass 保存与跨模式切换；不能仅凭零直接诊断 stage。Screen 和其接口尚需整体迁移，其他 unmerged 文件同样未验证。无 commit、push、服务器访问或 stash 操作。

## 2026-11-05 ITI 局部冲突续接（未完成）

对照 stage 2/3 和现有工作树，合并 ITI ViewModel 的部分冲突：导入两侧 flow API；延迟恢复已缓存图片时保留当前界面已选图片，同时恢复槽 2–4；保留主动刷新及结束时清除加载状态；模式切换保留各自 GenerationParams 并撤销旧 comparison；校验要求源图、共享参数有效和 Editing 尺寸有效。未暂存，也未声称运行时正确。上述仅为局部编辑，文件其他冲突及旧字段迁移仍未完成。

实际执行 `./gradlew :app:compileDebugKotlin --offline --no-daemon --console=plain` 失败，日志 `/root/.hermes/profiles/dev/cache/scratch/iti-partial-compile.log`：4888 条 Kotlin 诊断，其中 ITI ViewModel 879、ITI Screen 936；大量是未合并冲突的级联，不能按数量判断功能完成程度。未运行 Android 测试、未生成新 APK、未访问服务器。

明确余项：ITI state 的旧平铺字段与 GenerationParams 重复，workflow 加载/保存、旧更新方法和尺寸 helper 仍需语义迁移；Screen 仍含全部主要冲突，需接通上游 common generation UI 与 fork 的四槽/选图器/上传进度/比较/batch/front；prepareWorkflow、WorkflowManager、客户端及其他 Screen 的接口和资源仍须一起校验；增加真正执行的回归测试并全局重编译。当前文件留在 unmerged，不应 stage。没有提交、推送、重置或覆盖其他三个 ViewModel。

## 2026-11-05 非 ITI Generation ViewModel 续接（待全局验证）

对照 stage 1/2/3 和原工作树，迁移 TextToImage、TextToVideo、ImageToVideo 至共享 GenerationParams / VideoResult 基类：保留 fork 的手动刷新入口与加载指示（直接读取共享 model cache），ITV 的 bitmap 选图入口、上传字节进度与内容命名，同时保留上游结果列表、视频缓存和统一 workflow/seed 参数。三文件无冲突标记，但尚未暂存：依赖的 WorkflowManager、ComfyUIClient、对应 Screen 等仍未合并，无法证明端到端语义和编译；避免将其标记为完成。原文件备份位于 `/root/.hermes/profiles/dev/cache/scratch/noniti-vm-backup/`。

实际执行 `:app:compileDebugKotlin --offline --no-daemon --console=plain` 失败，日志 `noniti-vm-compile.log` 有 5255 条 Kotlin 诊断（此前同命令 7167，主要为其他冲突级联）；这三个 VM 本身未出现在诊断中，不代表编译通过。新增 `NonItiGenerationStateTest` 覆盖 params 快照、刷新状态与上传状态隔离；实际运行该单元测试任务仍在 main Kotlin 编译阶段失败，日志 `noniti-vm-test.log`，测试未运行。尚需解决全局冲突、检查屏幕调用和排队/上传语义并重跑测试。未修改 ITI、未提交或推送。

## 2026-11-05 Generation ViewModel 局部续接（未完成）

`ImageToImageViewModel.kt` 局部处理三个冲突：Editing 通过上游 `prepareWorkflow` 传递 fork 槽 2–4、引用图和 bypass；Inpainting 使用最终含 alpha 蒙版的字节走内容复用；历史结果读取合并上游 outputs 列表和 fork comparison snapshot/异步身份检查，缓存发布移到身份检查之后。扩展 `ImageUploadReuseWiringTest` 检查额外槽及 bypass 已传入 shared API；这是 source contract，尚未运行。文件仍有其他冲突、扁平字段和 GenerationParams 之间尚未整合，不能暂存或当作有效功能。`ImageUploadReuseWiringTest.kt` 也保留工作树未暂存。

三次实跑 `./gradlew :app:compileDebugKotlin --offline --no-daemon --console=plain` 失败，日志 `/root/.hermes/profiles/dev/cache/scratch/generation-integration-compile{,-2,-3}.log`，分别 7208、7194、7167 条 Kotlin 诊断（大量未解决冲突级联）；无 Android 测试/服务器验证。仍为 25 个未解决路径。下步须统一 ITI `editingParams` 与旧状态的读写、dimensions 和 source slots（当前尚不一致），其他三个 VM、WorkflowManager、客户端及 UI 一同完成后重编译。未操作 stash、六个未跟踪 skills、服务器或远端。

## 2026-11-05 ViewModel 冲突续接：25 路径未解决

本轮合并并暂存 GalleryViewModel、MediaViewerViewModel、SettingsViewModel：保留素材库保存及日志上传，同时保留上游整任务删除提示、MediaExport 和拼写检查设置。恢复协程导入；编译日志中这三个文件不再出现诊断，但不等于功能已运行验证。Settings 旧 ApplicationSettingsScreen 仍调用已被上游改造移除的设置 API，该 screen 本身仍为 modify/delete 冲突，必须在设置页面整合时迁移，不应为通过编译盲目恢复旧缓存开关。

实际运行两次 `./gradlew :app:compileDebugKotlin --no-daemon --offline` 均失败；最终日志 `/root/.hermes/profiles/dev/cache/scratch/merge-viewmodels-compile-2.log` 有 7268 条 Kotlin 诊断（包含冲突引发的级联），未运行 Android 测试。当前仍有 25 个未解决路径；四种 generation ViewModel 与页面存在旧扁平状态和上游 params/base 重构的深层架构冲突，不能简单选择一边。没有提交、推送或部署。

## 2026-11-05 续接：28 路径未解决

本轮明确暂存四个文件：`README.md`、`app/src/main/java/sh/hnet/comfychair/viewmodel/WorkflowManagementViewModel.kt`、`app/src/main/java/sh/hnet/comfychair/viewmodel/GenerationViewModel.kt`、`app/src/main/java/sh/hnet/comfychair/ui/components/LoraChainEditor.kt`。README 和 WorkflowManagementViewModel 原工作树遭截断，基于 stage 1/2/3 重建并保留 fork 导入及上游变更；generation 合并保留通知、提交来源绑定和上游输出警告；LoRA 采用上游 strength 控件。以上仅作初步冲突处理，尚须完整架构和运行时复核。

`./gradlew :app:compileDebugKotlin --offline --no-daemon --console=plain` 三次实际执行仍失败，最后日志为 `/root/.hermes/profiles/dev/cache/scratch/comfychair-merge-continue-3.log`；上述三个 Kotlin 文件无单独编译诊断，但整个应用并未编译通过，其他冲突产生大量诊断。companion 测试实际执行 84 passed、1 warning；不证明 Android 功能。四个明确暂存文件 `git diff --cached --check` 通过。未提交、未推送、未操作服务器；stash 和未跟踪目录保留。

关键风险：`WorkflowManager.kt` 和 `ComfyUIClient.kt` 没有冲突标记但仍处于 unmerged，绝不凭编译诊断为零暂存；前者统一 prepareWorkflow 替代 fork 旧方法并缺失部分 snapshot 功能，还需与四种 generation ViewModel、槽 1–4、bypass、golden 一起语义迁移。当前还有 28 个未解决路径，包括已删除/修改冲突以及大量导航与 UI。后续先完成基础 client/workflow/generation，确保三方内容均保留，再解 UI 并跑全量测试及 debug/release。

## 2026-11-05 本轮续接：仍未完成

已将 44 个冲突路径中的 12 个加入 index，余 32 个仍未解决；没有提交或 push。两种 strings.xml 按资源名做三方合并并验证 XML 和名称唯一性；保留中英文 fork 新资源与上游资源。Gradle 保留 fork v0.8.26/code63、签名和命名，并加入上游 JSON catalog 依赖；`./gradlew help --no-daemon --console=plain` 成功。Manifest 合并保留诊断服务和 Browser SSO、禁用云备份；数据迁移排除两种凭据。还处理 .gitignore、LoRA 模型、导航 route、设置和部分无依赖的 callback/state/MainActivity。修复上游自动合并造成的 TemplateKeyRegistry 重复方法及 WorkflowEditorViewModel 重复 import。上述变更尚未经过 Android 全量编译，仍须对资源文案及功能做语义复核。

执行 `./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain`：资源阶段通过，`compileDebugKotlin` 失败，日志 `/root/.hermes/profiles/dev/cache/scratch/comfychair-merge-build.log` 有 7869 条 Kotlin 诊断（大量由未解决冲突导致），测试及 APK 未完成。核心文件仍有截断及架构冲突，包括 ComfyUIClient、WorkflowManager、WorkflowManagementViewModel、generation/viewer/UI 和导航；不能把此状态当成完整合并。构建后修复的重复声明还未重跑构建。保留 stash、未跟踪目录及 MERGE_HEAD，未更改服务器。

## 2026-11-05 磁盘清理后续接：仍未完成

重新 fetch 双远端，`nick/main=c43f2a6bbb39a2333b8f97e7eec8eb9bd3d0702a` 仍为 HEAD 祖先；`origin/main` 仍为已固定的 MERGE_HEAD `bfd69ad878e33ccd65355f001e351a4c879864ca`。当前仍有44个未解决路径，不存在双亲 merge commit。磁盘可用约5.4G。原 stash 和未跟踪目录保留；未 push、部署或更改服务器。

发现多份无冲突标记文件实际截断而非已解决：例如 `app/build.gradle.kts` 仅23行、`README.md` 仅前1830字节、`AndroidManifest.xml` 仅757字节、`WorkflowManagementViewModel.kt` 仅1525字节。已比对 stage 1/2/3 与工作树；对 build.gradle.kts 保留已有前缀和版本，将 stage 2 从已有版本行之后的缺失尾部补回，尚未 stage 或声称完成该文件的语义合并。其他截断文件和冲突保留，不能通过删除标记或整文件任选一侧来宣称完成。

验证：Gradle 9.4.1 `help --offline --no-daemon` 成功；它自动使用缓存的 JetBrains 21 daemon，Android SDK 36存在。`testDebugUnitTest assembleDebug assembleRelease --offline --no-daemon` 首先因 WorkManager 2.10.0 未缓存失败；在线重试成功解析依赖，随后因 `values-zh/strings.xml:258` 和 `values/strings.xml:279` 的冲突标记在资源合并失败，Android 测试和 APK 均未完成。未执行 `clean`，避免在尚未解决且不能通过构建时删除已有工件。Python companion 再次执行：84 passed、1 warning；这不验证 Android 集成。

仍需系统合并 generation/安全/导航和44路径，修复被截断文件并审查所有无标记文件，完成回归和 clean 构建后方可提交。`nick` 远程 URL 含嵌入式凭据，终端曾输出经界面部分遮蔽的 URL；建议用户轮换该凭据并以安全方式迁移 credential helper，本次未修改远程配置。

## 2026-11-05 后续检查：现有全量 merge 尚在进行，未完成

本次接手时工作树已经处于真实 merge 中，与下文阶段A结束时的状态不同：`HEAD=ba658d3d5d153ccfc462c0c835bc331f358068c8`，`MERGE_HEAD=bfd69ad878e33ccd65355f001e351a4c879864ca`，仍有44个未解决路径和118个冲突块；部分未解决路径已没有冲突标记，可能包含已有人工处理，不能 abort、覆盖或把无标记误当已完成。实际并未生成 merge commit。以下阶段A结论仅为历史记录，不是当前工作树状态。

已成功重新 fetch `nick` 和 `origin`：`nick/main=c43f2a6bbb39a2333b8f97e7eec8eb9bd3d0702a` 是当前 HEAD 祖先，`nick/feature/slot-bypass` 也是祖先；`origin/main=bfd69ad878e33ccd65355f001e351a4c879864ca` 尚非 HEAD 祖先。以共同祖先 `791cd95158c8f11fdbae47605bb06aec8dd4a17e` 计算，fork/main 有56个独有提交，上游有103个独有提交。当前集成分支没有配置 tracking；远程没有同名集成分支，未误设为 nick/main，也没有 push。

磁盘 `df -B1` 显示普通可用空间为0（`/dev/sda2`），不得依靠 root 保留空间完成构建。已发现 `/nix/store/18311cxwsdjsc52dhas54wfqqa212q5m-openjdk-21.0.12+8` 可运行的 OpenJDK 21，以及缓存的 Gradle 9.4.1；但上游 daemon 配置要求 `JETBRAINS` vendor，尚未证明该 OpenJDK 满足要求，且未验证 AGP 9.2.1 所需依赖可离线使用。`/dev/shm` 有临时空间，但不是持久构建容量或可替代用户数据的存储。未删除任何缓存、APK、stash 或用户数据。

本次以 `PYTHONDONTWRITEBYTECODE=1 TMPDIR=/dev/shm .../comfychair-pytest-venv/bin/python -m pytest -q -p no:cacheprovider server/tests` 实跑 **84 passed**（1条 warning；另有 pytest-asyncio 配置弃用提示）。这是当前未完成 merge 下的 companion tests，不证明 Android 或整合功能正确。本次没有运行 Android 编译/测试，没有宣称 TLS、SSO、各 UI/工作流特性已整合完成。原 stash `6abb5226ca40307090c798d27be39f5959cc84b6`、未跟踪 skills 和 pycache 均保留。

下一步先提供足够持久空间并确认 JETBRAINS JDK/依赖；保留当前 index 和工作树的已有 resolution，逐项审查44个冲突、自动合并及安全/功能边界，完成后再跑用户要求的 clean tests 和 debug/release 构建，最后才可创建双亲 merge commit。不得根据本段历史测试宣称合并完成。

## 结论：阶段A已落地并构建；全量合入尚未完成

本地分支：`integration/upstream-20261105`。稳定`main`保持`c43f2a6bbb39a2333b8f97e7eec8eb9bd3d0702a`不动；无push/PR/tag/release/版本提升/实际服务器修改。源码已验证提交：`137b38abac68094d3f69a3feb4b28e937cd6c677`（随后仅文档提交）。保留v0.8.26/code63及原签名/构建策略；构建产物是本地验证候选，**不是新稳定发行**。

### 实际执行过的全量尝试

`git fetch origin main`成功，pin为`bfd69ad878e33ccd65355f001e351a4c879864ca`；共同祖先`791cd95158c8f11fdbae47605bb06aec8dd4a17e`。从stable创建本分支，真实执行`git merge --no-commit --no-ff origin/main`，出现45路径冲突（39内容、6modify/delete），与只读预审一致。

没有为消标记全选ours/theirs，也没有创建假“已merge”提交。考虑跨架构移植、安全客户端和当前磁盘/工具链限制，在**尚未做任何手工resolution前**用`git merge --abort`恢复；无resolution成果被丢弃。外部证据`/root/comfychair-artifacts/upstream-review/INTEGRATION-ATTEMPT.md`记录这一决定。最终不留MERGE_HEAD、不留unmerged index。上游最新tip仍不是本分支祖先，patch cherry-pick不改变此事实。

### 已完成的阶段A

| 本地提交 | 上游来源 | 内容与保留点 |
|---|---|---|
| `8d8f3aa` | `a3382a6` | 相同serverId但hostname/port/protocol/credentials变化时重建client，旧client shutdown撤销上传epoch；不改删除key exact-origin策略 |
| `18e3dd1` | `6f03ba6` | 按model wiring识别GGUF等loader，保留普通checkpoint/UNET与非model输出排除 |
| `d0fe73e` | `12c35cd` | Gallery点击时解析当前item index、gesture读取新callback，保留素材库save动作 |
| `137b38a` | 定向适配`f39ce9e`、`f09f616` | 旧editor edit不能覆盖unexpanded template中app-managed输入（包括槽1–4、literal image markers），保留三wrapper/Long/连接/PNG defaults；普通clearCache不再删imported workflows，material和删除凭据不受影响 |

前三项保留上游原作者；第四项说明来源，不把这些上游自己的修复当成新贡献。没有采用上游新UI、SSO、ServerTrust或MEMORY_ONLY改造；既有fork安全局限未被这次局部同步“顺便解决”。

## 真实验证

### Android

- 工具链实查：Java17.0.20.1、Gradle8.13、现有fork AGP/Kotlin与本机SDK；platform android36存在，实际build-tools目录35.0.0（不猜36路径）。
- `./gradlew testDebugUnitTest assembleDebug --offline --no-daemon`：成功，**177 tests / 28 suites，failures/errors/skipped均0**，来自实际JUnit XML求和。
- 新增`util/UpstreamIntegrationTest`6项；扩展`WorkflowPromptFinalizerTest`到7项。GGUF/非model为运行逻辑测试；reconnect、gallery callback与clearCache边界部分为source contract，不是Android运行时/设备证明。
- TDD记录：首次targeted build在缺少template参数API处compile失败（预期）；实现后13项中1项因测试误写旧callback参数名失败，按源码修正fixture，随后全套通过。日志未隐藏。
- `./gradlew assembleRelease --offline --no-daemon`首次240秒timeout，进程已结束；使用足够等待时间重试，**R8/minify/shrink+lintVital+assembleRelease成功**（3m38s）。无编译期间修改source。
- `aapt`实读debug/release均package `sh.hnet.comfychair`、code63、v0.8.26、min33/target36。`apksigner verify --print-certs`成功，release沿用现有Android Debug证书：SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`。这是fork既有本地签名策略，不代表正式上游签名。

本地APK（路径在`app/build/outputs/apk/`下，未发布）：

| 变体/文件 | bytes | SHA256 |
|---|---:|---|
| `debug/comfychair-v0.8.26-debug-20261105-1053.apk` | 87317494 | `959c3a2cb2a5ef3711743f92728ee191d553dcfdbce6e431be1d17b5b0d6c5a3` |
| `release/comfychair-v0.8.26-release-20261105-1100.apk` | 8372309 | `e6e1fa37c857b507d1cb7927e2a408136f29c470ece8f0823f1e44a620195253` |

### Python companion

系统python无pytest，发现并使用既存active-profile scratch下`comfychair-pytest-venv`，没有安装依赖。对`server/tests`真实执行，**84 passed**，XML零fail/error/skip。仅临时目录与aiohttp本地测试服务，未操作真实ComfyUI。存在pytest-asyncio默认loop scope与环境anyio plugin弃用警告，不影响结果，未来单独清理。

### 证据

外部目录`/root/comfychair-artifacts/upstream-review/`包含：`phase-a-red.log`、`phase-a-targeted.log`、`phase-a-build.log`、`phase-a-release.log`（timeout）与`phase-a-release-retry.log`、`phase-a-pytest.xml`、`phase-a-verification.json`、阶段A JUnit XML和对应R8 mapping压缩备份。不要把这些历史工件/日志放进上游PR。

## full migration blockers / 后续必须完成

1. **统一generation架构**：扩展上游WorkflowImages/GenerationParams到独立source2/3/4和bypass；port内容上传复用/DirectInput、actual progress、snapshot、batch/front confirmation与UNET记忆。把fork工作流加入上游goldens，再迁移调用，不能仅删旧prepare函数。
2. **TLS/SSO边界**：上游删SelfSignedCertHelper但fork upload/delete辅助client仍依赖；所有客户端迁移同一ServerTrust，证书确认/变化/降级均覆盖。Cookie进HMAC auth namespace不能落匿名桶，refresh要撤旧epoch，动态headers不跨账号；专用删除key仍fail-closed、不复用普通HTTP consent。
3. **导航/UI/viewer**：上游删旧MediaViewerActivity/SettingsNavHost/AppMenu/DimensionStepper，需整体迁移，不保留一个失效旧类就算resolved。保留独立服务器图库紧凑无Settings chrome、scroll anchor、slot返回身份、素材库、比较和通知生命周期。
4. **删除语义**：上游`/history` delete/undo不等于插件physical delete；不可把Undo宣称可恢复物理文件。保持授权/确认/queue/path/tombstone/per-item结果与cache invalidation。
5. **隐私和reset**：上游MEMORY_ONLY不自动识别fork私有picker缓存和material；需明确默认/opt-in、restore-defaults对key/consent/material/cache的保留政策，重载live cache policy、阻止旧请求回填。不自行扩大删除范围或承诺零落盘。
6. **容量/工具链**：当前filesystem普通available=0，root仍有保留块，旧toolchain缓存足够阶段A构建不代表可承载完整升级。上游wrapper9.4.1/AGP9.2.1/Kotlin2.2.10+daemon显式JETBRAINS21，当前只验证旧wrapper/JDK17。Gradle9本身不必然要求21，但daemon配置实际要求。未下载/安装大工具链、未删缓存/日志/APK/source/用户数据；继续前需要授权提供足够持久空间与匹配JDK/toolchain方案。
7. **验收不足**：全架构Android+Python、release、设备手势、Keystore、通知权限/后台行为、多槽bypass goldens、真实服务器生成/只读导入仍需重新验收。阶段A177/84不证明上述架构迁移通过。

## 继续工作顺序

- 先决定可用空间/toolchain与memory-only/缓存/恢复默认值产品策略；保持当前已验证branch checkpoint。
- 在该branch后续提交上重新merge固定上游（或显式更新pin后再审差异），逐组解决workflow→auth/clients→generation VM→navigation/settings→UI，记录45路径resolution决策及自动合并路径语义回归。禁止 blanket ours/theirs。
- 每组保留自定义验收；全套真实执行后才创建完整merge commit，不为拓扑漂亮记录假完成。
- 贡献方向从干净上游topic branch另抽取小PR，见UPSTREAM-PR-PLAN。优先服务器workflow导入、可复现PNG小修、RAM-only上传复用。它们目前是**有实现来源的候选**，不是已经针对最新上游验证完成的PR。

## 工作树/发布保护

原stash `6abb5226ca40307090c798d27be39f5959cc84b6`和六个untracked `.agents/skills/`目录保留，两个server `__pycache__`目录保留（运行测试会更新缓存内容，未stage/提交）。未改其他Hermes profile、ops配置、远端仓库或服务器。不stage历史不相关文件。`git diff --check`通过；最终仓库不处于merge/cherry-pick中。

## 2026-11-05 续接会话：全量 merge 完成（本地）

本次接手时工作树已处于真实 merge 中（HEAD=`ba658d3`，MERGE_HEAD=`bfd69ad`），剩 20 个未解决路径、6 个文件仍有冲突标记。逐项完成消解后**实际创建了双亲 merge commit**（见 `git log`），未 push、未开 PR、未动远程与实际服务器。

### 冲突消解决策（关键路径）

| 路径 | 决策 |
|---|---|
| `ComfyUIClient.kt`、`WorkflowManager.kt`、4 个 ViewModel、`MainContainerActivity.kt`、`BottomSheetConfigExtensions.kt`、`MainNavHost.kt`、`WorkflowsSettingsScreen.kt` | 前一工作者已解至无标记；本次复核结构完整（括号平衡、语义抽查）后 stage。fork 槽位/bypass/上传复用/进度、上游 ServerTrust/SSO/统一架构均保留。 |
| `TextToImageScreen.kt`、`TextToVideoScreen.kt`、`ImageToVideoScreen.kt` | 采用上游新架构（GenerationTopBar/PromptDock/SourceResultSwitcher）；fork batch/素材库/进度在 ViewModel 层保留，GetContent launcher 的不可持久化 URI 处理一并去掉（fork persistable 逻辑仅适用于 OpenDocument launcher）。 |
| `ImageToImageScreen.kt` | 混合整合：上游 GenerationTopBar/PromptDock/RecipeChips/GenerationOptionsSheet/PromptPresetDialogs + fork 多槽 pager（槽1–4 稀疏映射）、bypass 覆盖层、蒙版/编码 info、对比图、gallery/material picker、批次数 stepper、上传字节进度按钮、unresolved-placeholder 确认对话框。fork 的 ConnectionStatus 自动 fetch 已由上游连接期自动 `fetchServerData` 取代；server picker 入口迁至 GenerationTopBar actions（`ServerImageSourceButtons`）。submit 路径统一走 fork 语义：mask 门禁 → prepare → placeholder 确认 → batch/front 提交。 |
| `MediaViewerScreen.kt` | 上游浮层 viewer 为主体；扩展可选 `replaceSlot/bypassSlot/isSlotBypassed/onReplace/onBypassToggle/onSaveToMaterialLibrary`，fork `MediaViewerActivity`（保留，服务通知深链与槽位返回流程）继续以其签名调用；`onClose: (Int?) -> Unit` 双向兼容。 |
| `GalleryScreen.kt` | 上游顶栏/选择工具栏/媒体原点 tile；fork 的 saveToMaterialLibrary 按钮并入选择工具栏。 |
| `SettingsNavHost.kt`、`ApplicationSettingsScreen.kt` | **接受上游删除**。二者是 fork 旧导航壳，上游以 Settings tab + topic 页面替代；fork 设置能力（缓存策略、媒体隐私、重置等）已由前一工作者迁入 `SettingsRootScreen`/`settings/*Pages`。保留只能得到不可编译的死代码。 |
| `MediaViewerViewModel.kt` | `initialize` 改 `internal`（上游设 private；fork `MediaViewerActivity` 同模块调用）。 |
| `GenerationButton.kt` | 上游签名 + fork 上传进度/批次数参数共存；`ImageToImageScreen` 调用点改用上游队列参数（canCancelOwnJob/ownJobs/onShowQueue），队列清理移入 QueueSheet（上游行为）。 |
| `LoraSelection.kt` | 补 `MIN_STRENGTH/MAX_STRENGTH`（±100，上游 `LoraStrengthControl` 界）；已存超出界的强度值仍可回读，仅在编辑时被钳制。fork 无界文本输入在此处让位于上游统一控件（REVIEW 中已说明两设计重叠）。 |
| `ic_comfychair_foreground.xml` | 恢复 fork drawable（上游图标更换后删除了 fork 占位图引用的该资源）。 |

### 真实验证（本次全部实跑）

- `compileDebugKotlin`（Gradle 9.4.1 + 缓存的 JETBRAINS JBR 21.0.11 daemon，`--offline`）通过。
- `testDebugUnitTest`：**218 tests，0 failures/errors/skipped**（JUnit XML 汇总）。fork 合约测试（server picker/gallery layout/delete wiring）按新架构更新断言，原保证项逐一映射（见各测试注释）。
- `assembleDebug`：成功。APK `app/build/outputs/apk/debug/comfychair-v0.8.26-debug-20261105-1713.apk`（89,111,499 bytes，SHA256 `b59354a6a3765910a3a769ed2e795df986e1e093bd4772fc331e40044f855e6b`）。
- `assembleRelease`（R8/minify/lintVital）：成功（5m43s）。APK `app/build/outputs/apk/release/comfychair-v0.8.26-release-20261105-1714.apk`（8,670,945 bytes，SHA256 `6d57d85d0cf4c475240cbc0b53a86f49004f6e78386d8a7aaae94101727985d2`）。
- Python companion：`pytest server/tests` 实跑 **84 passed**（hermes venv pytest，仅本地 aiohttp 测试服务）。
- 远程祖先复核：`nick/main=c43f2a6` 是 HEAD 祖先；`origin/main=bfd69ad` 是本 merge commit 的第二亲本（构建后即为祖先）。

### 仍未完成 / 剩余风险

- 无 ADB/真机：手势、通知、Keystore、进程重建等设备行为未验证。
- 未在真实 ComfyUI 服务器上验收生成/删除/复用；golden 测试不等于服务器正确。
- fork LoRA 强度无界输入被上游 ±100 控件取代（编辑时钳制，存储值不受影响）——与上游 `94a6a01` 的已知重叠，REVIEW 报告第 4 节既定取舍。
- 原 stash、untracked skills、pycache 保留未动；未 push/PR/tag/release。
