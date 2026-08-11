---
name: comfychair-development-workflow
description: ComfyChair 完整需求开发工作流 — 需求分析 → 设计方案 → 开发 → 编译构建 → Git 推送 → 版本号更新 → 打 Tag → GitHub Release
category: android-dev-comfychair
trigger: "开发 comfychair, comfychair 需求, comfychair 新功能, comfychair bug"
---

# ComfyChair 需求开发完整工作流

## 项目基本信息

| 项目 | 值 |
|------|-----|
| 代码仓库 | `/root/comfychair/` |
| GitHub | `nickdlkk/comfychair` |
| 官方原仓 | `legal-hkr/comfychair`（origin remote） |
| Nick fork | `nickdlkk/comfychair`（nick remote） |
| 编译命令 | `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug --no-daemon [-PapkSuffix=<tag>]` |
| APK 输出 | `app/build/outputs/apk/debug/comfychair-<version>-debug[-<apkSuffix>]-<timestamp>.apk` |
| APK 下载 | `http://192.168.4.69:9010/comfychair/<文件名>.apk` |
| ComfyUI 服务器 | `harbor.comfyui` / `192.168.4.69:8189` |
| 开发机 | Nick 的 Windows `D:\PAS-AI\comfychair\` |

---

## 完整工作流（8 步）

### 第 1 步：需求分析

1. 理解 Nick 要什么（中文交流，简洁直接）
2. **重大操作前必须先报告计划并等待批准**，不能自作主张
3. 探索代码：查找相关文件，理解现有架构

**常用探索路径：**
```bash
# 查找关键文件
find /root/comfychair -name "*.kt" | xargs grep -l "关键词"

# 查看 ComfyUI 容器日志
docker logs harbor.comfyui --tail 100 | grep ERROR

# 测量 API 响应时间
curl -s -m 30 'http://192.168.4.69:8189/object_info' -o /dev/null -w "%{time_total}s\n"
```

### 第 2 步：设计方案

输出包含：
- 问题根因分析
- 解决方案
- 文件变更清单（表格形式）
- 预期效果
- **等 Nick 确认后再执行**

**Nick 的 UI 设计偏好（已验证的）：**
- ❌ 不要 Dialog/ModalBottomSheet → ✅ 用 Expandable Panel（展开面板）
- ❌ 不要 Double-fire → ✅ 用 `combinedClickable` + `longPressedJustFired` flag
- ❌ 不要单独压缩 Checkbox → ✅ 压缩按钮放在 Info Overlay 里
- ✅ 用 Material Library 叠加在系统 Picker 上，不替换原有流程
- ✅ 上传进度用实际字节数，不用模糊进度条
- ✅ String 资源要中英文双语

### 第 3 步：代码开发

**跨机器开发注意：**
- Nick 的代码在 Windows `D:\PAS-AI\comfychair\`（无法从 Linux 直接写）
- 本机 `/root/comfychair/` 是备用/编译环境
- 规划文件写本地 `/root/.hermes/plans/<feature>/`

**修改完成后自检清单：**
- [ ] 编译通过（`./gradlew assembleDebug`）
- [ ] 字符串资源中英双语（`values/strings.xml` + `values-zh/strings.xml`）
- [ ] 新字符串 ID 用 `grep -n` 检查是否已存在
- [ ] `import` 语句无遗漏
- [ ] `remember` / `LaunchedEffect` 边界正确
- [ ] ViewModel 方法在所有 Screen 中都已调用

### 第 4 步：编译构建

```bash
cd /root/comfychair

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug --no-daemon
# 如需带功能标识：
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug --no-daemon -PapkSuffix=workflow-save-loading
```

**参数说明：**
- `--no-daemon` 必须加：解决 Gradle daemon TLS 握手失败
- `-PapkSuffix=<tag>` 可选：给 APK 文件名附加功能标识，便于分享区分
- 编译时间约 1-2 分钟
- APK 大小约 88MB（debug build）

**低磁盘空间时：**
```bash
export GRADLE_USER_HOME=/tmp/gradle-home && mkdir -p "$GRADLE_USER_HOME"
./gradlew assembleDebug --no-daemon -PapkSuffix=workflow-save-loading
```

**编译成功后验证：**
```bash
ls -lh /root/comfychair/app/build/outputs/apk/debug/comfychair-*.apk
# 期望：最新 APK 文件名包含 version/buildType/时间戳，可选包含 apkSuffix
```

### 第 5 步：构建产物分享

```bash
# 1. 复制 APK 到分享目录
mkdir -p /root/share/comfychair
cp /root/comfychair/app/build/outputs/apk/debug/comfychair-*.apk \
   /root/share/comfychair/

# 2. 启动 HTTP 服务（如果没在运行）
python3 -m http.server 9010 --directory /root/share &
# 或用 background=true:
python3 -m http.server 9010 --directory /root/share
# (放在后台，不要在 foreground command 里手写 &)

# 3. 验证服务在线
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:9010/comfychair/comfychair-v0.8.18-debug-workflow-save-loading-20260811-1442.apk
# 期望: 200

# 4. 生成分享链接
# http://192.168.4.69:9010/comfychair/comfychair-v0.8.18-debug-workflow-save-loading-20260811-1442.apk
```

**⚠️ 注意事项：**
- HTTP server 必须从 `/root/share` 所在目录启动（或用 `--directory /root/share`）
- 从 `/tmp` 启动会 404

### 第 6 步：Git 推送

**设置 HTTPS + Token 推送（每次 session 可能需要）：**
```bash
cd /root/comfychair

# 从 gh CLI 提取 token
GITHUB_TOKEN=$(sed -n 's/^\s*oauth_token:\s*\([^ ]*\).*/\1/p' ~/.config/gh/hosts.yml | head -1)

# 设置 nick remote 为 HTTPS + token
git remote set-url nick "https://nickdlkk:${GITHUB_TOKEN}@github.com/nickdlkk/comfychair.git"

# 推送（通常推送到当前分支）
git push nick HEAD
```

**推送前确认：**
```bash
git status          # 确认要提交的文件
git log --oneline  # 确认提交历史
```

### 第 7 步：更新版本号

```bash
# 查看当前版本
grep -E "versionName|versionCode" /root/comfychair/app/build.gradle.kts

# 编辑版本文件（通常是 app/build.gradle.kts）
# versionName: "0.8.18" → "0.8.19"
# versionCode: 18 → 19
```

**版本号规范（来自历史发布记录）：**
- `versionName` 格式：`0.8.xx`（递增）
- `versionCode`：整数，递增
- Release tag 格式：`v0.8.xx`

### 第 8 步：打 Tag 并发布 Release

```bash
cd /root/comfychair

# 打 tag（与 versionName 对应）
git tag -a v0.8.xx -m "v0.8.xx: <功能描述>"
git push nick v0.8.xx

# GitHub 会自动触发 release（如果有 Actions workflow）
# 或者手动在 GitHub 网页创建 Release
```

**GitHub Release 创建：**
1. 打开 https://github.com/nickdlkk/comfychair/releases/new
2. 选择刚推送的 tag
3. 填写标题和描述（包含功能列表 + APK 下载链接）
4. 上传 APK 文件
5. 发布

---

## 历史开发经验总结

### Nick 的行为模式

| 行为 | 含义 | 应对 |
|------|------|------|
| 直接说需求，不说"帮我..." | 简洁指令，直接执行即可 | 不要废话，直接做 |
| "push" | 确认方案，同意执行 | 开始执行 |
| 问"为什么" | 需要解释原理 | 给出简洁技术解释 |
| 截图/现象描述 | 等待 root cause 分析 | 先调查再回复 |
| "show me the proof" | 不要只读代码推断 | 用 curl / docker logs / 浏览器验证 |
| 重大配置变更 | 必须先报告计划 | 不自作主张 |

### 常见坑点

1. **strings.xml 重复**：新增字符串 ID 前先 `grep -n "字符串ID" strings.xml`
2. **`--no-daemon` 必须加**：不加报 TLS handshake 错误
3. **HTTP server 工作目录**：必须在 APK 所在目录启动
4. **image_filename_2/3/4 字段扩展**：必须同时改三个映射表（见 `comfychair-templatekeyregistry` skill）
5. **LoraLoader 空值 bypass**：需要同时修 3 个地方（WorkflowManager + ComfyUIClient + 空字符串处理）
6. **combinedClickable double-fire**：必须用 `longPressedJustFired` flag + `LaunchedEffect` delay
7. **OOM（大图导入）**：BitmapFactory 时要先 `inJustDecodeBounds` 获取尺寸，再按比例缩放

### APK 下载分享（简化版）

```bash
# 编译完成后，一行命令搞定复制 + 确认
cp /root/comfychair/app/build/outputs/apk/debug/comfychair-*.apk \
   /root/share/comfychair/

# APK 链接格式
http://192.168.4.69:9010/comfychair/comfychair-v0.8.18-debug-<功能标识>-<时间戳>.apk
```

---

## 关键文件路径速查

| 功能 | 文件 |
|------|------|
| OkHttp 客户端（三客户端架构） | `app/src/main/java/sh/hnet/comfychair/ComfyUIClient.kt` |
| 连接状态管理 | `connection/ConnectionManager.kt` |
| 模型缓存 | `connection/ModelCache.kt` |
| 图生图 ViewModel | `viewmodel/ImageToImageViewModel.kt` |
| 文生图 ViewModel | `viewmodel/TextToImageViewModel.kt` |
| 图生图 Screen | `ui/screens/ImageToImageScreen.kt` |
| 生成按钮 | `ui/components/GenerationButton.kt` |
| Workflow 管理 | `WorkflowManager.kt` |
| Bypass 节点解析 | `util/BypassNodeResolver.kt` |
| LoRA 注入 | `util/LoraInjectionUtils.kt` |
| 模板字段注册 | `workflow/TemplateKeyRegistry.kt` |
| 字段映射分析 | `util/FieldMapping.kt` |
| 应用设置 | `storage/AppSettings.kt` |
| 字符串资源 | `res/values/strings.xml` + `res/values-zh/strings.xml` |
| 版本配置 | `app/build.gradle.kts` |
