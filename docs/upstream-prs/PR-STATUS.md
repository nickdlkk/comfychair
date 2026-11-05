# Upstream PR 工作记录

> 本文档记录 fork（main / customization/v0.8.26）向上游 `legal-hkr/comfychair` 贡献 PR 的实际进展。
> 拆分依据见 `docs/customizations/UPSTREAM-PR-PLAN.md`（记录在 main 分支，本文档也回填到 main）。

## 当前状态总览

| 项目 | 状态 |
|---|---|
| fork 整合 | `main` 已重置为 integration/upstream-20261105（含上游 bfd69ad→825d72a 全量合并），原 main 备份为 `customization/v0.8.26` |
| 分叉点 | v0.8.11 (`791cd95`)，本地定制 56 commits |
| PR 拆分方案 | `UPSTREAM-PR-PLAN.md`（PR-A … PR-K） |
| **PR-B** | **topic 分支已就绪，未发 PR**（见下） |
| PR-A/C…K | 未开始 |

## PR-B — Import saved ComfyUI server workflows（已完成移植）

### 分支

- **分支名**：`topic/pr-b-server-workflow-import`
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
