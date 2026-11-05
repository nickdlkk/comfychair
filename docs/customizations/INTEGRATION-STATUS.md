# Integration status / 合入进度与交接

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
