# <img src="metadata/en-US/icon.png" width="42"/> ComfyChair

非官方原生 Android [ComfyUI](https://github.com/comfyanonymous/ComfyUI) 客户端。

**当前版本**：[v0.8.25](https://github.com/nickdlkk/comfychair/releases/tag/v0.8.25) · [English](README.md)

本分支 APK 请从 [nickdlkk/comfychair Releases](https://github.com/nickdlkk/comfychair/releases) 下载。英文首页的上游 GitHub/F-Droid 徽章不代表包含本分支功能。

## 概览

通过 ComfyUI API，在手机上生成和管理图片、视频，编辑节点工作流。支持多服务器、HTTP Basic/Bearer 认证、队列管理、实时预览、LoRA 链、提示词库、中英等多语言和每工作流配置持久化。基于 Kotlin、Jetpack Compose 与 Material Design 3，非 ComfyUI 官方项目。

## 功能

- **生成模式**：文生图；图生图编辑和蒙版重绘；文生视频、图生视频。
- **媒体**：图片/视频全屏浏览、缩放、画廊、批量保存/分享/删除、生成参数查看。
- **工作流**：节点和连线可视化编辑、属性编辑、旁路、分组、字段映射；导入 LiteGraph、ComfyUI API JSON、ComfyChair JSON；后两种支持导出。
- **配置**：多服务器和资源信息、缓存管理、配置备份/恢复、每工作流保存参数。

### 服务器 Input / Output 原图选择（v0.8.25）

- **图生图**顶部文件/素材按钮旁的云上传/下载图标，可为原图槽1–4选择服务器 Input / Output 图片；打开时固定目标槽，滑动原图页不会改目标。原本地文件、素材库和图库流程保留。
- 紧凑素材样式弹窗：三列方图、来源/目标槽/计数合并标题、刷新/关闭/信息按钮；搜索和排序同排。资产服务支持300ms防抖名称搜索、cursor分页、最新/最旧/名称/大小四种排序。计数区分已加载图片与匹配资产（包含非图片）；稀疏过滤每轮最多自动五页，之后手动加载更多。
- 复用当前已认证连接探测 `/api/features`、读取 `/api/assets`；仅禁用/不支持资产API时回退：Input读取LoadImage选项，Output分页读取执行历史，**不是扫描output磁盘目录**。旧模式无资产服务端搜索/排序；索引/历史都不保证实时完整目录，删除文件或并发清历史可能需刷新。认证、网络和服务器错误明确显示，不假装空列表或自动回退。
- 仅可见项请求缩略图，并发最多3、每响应4 MiB、采样最长边256px；按server/client身份隔离的内存LRU上限12 MiB /64项，无缩略图磁盘缓存。选择原图暂停新预览，不清已有缩略图/列表/滚动；所选遮罩和可取消不定进度，无虚构百分比。失败可保留列表重试。
- 原图单独下载（资产用ID content），上限32 MiB /16,777,216像素，不静默缩图；仅替换打开时目标槽。server/client/slot/revision守卫拒绝迟到结果；关闭、后台、切来源或连接取消任务。网格使用可保存的String稳定键。

### 可选实时诊断

**设置 → 调试日志**保留手动ZIP导出/上传，新增独立开关的实时事件。调试采集与实时发送默认关闭；填写自己的可信 `/events` 地址（ZIP另用 `/upload`）。只发送无业务值的生命周期/连接/picker事件与去message的fatal栈，不发送prompt、图片、工作流、服务器地址或ComfyUI凭据。HTTP明文只适用于可信LAN，或使用HTTPS。仅Activity started前台联网，队列/批次/超时/退避有界；关闭或换目标取消并清旧队列。私有有界本地journal会加入手动ZIP，但不是完整持久化debug日志。此功能用于调查回登录页，不能证明每次回退都是崩溃。

### 正式版与 beta

正式 **v0.8.25 /versionCode54** 高于已分发 beta.1 /53。Release开启R8代码优化/混淆及资源裁剪，debug仍不裁剪；精确保留ViewModel/worker反射构造和诊断，依赖consumer rules处理框架/JNI。**不剥离调试日志与实时诊断功能**。本地正式APK保留原applicationId及现有 **Android Debug签名证书** 以兼容升级，并非新生产签名；CI独立构建可能使用不同debug证书，不能视为可覆盖安装的同包。

实测beta.1 debug **86,933,230 → 8,266,557 bytes（减少90.49%）**，DEX **18 → 1**；最终 **94项JVM测试通过**，含显式启用的真实资产只读GET。这是debug与优化Release比较，不是只开关裁剪的独立A/B。实际裁剪体积及验证见[Release裁剪移交](docs/requirements/release-v0.8.25-shrinking.md)。JVM与真实HTTP回归不运行裁剪后的Android代码；无连接设备，安装、触摸/渲染、Android解码/磁盘发布及Release运行时尚未实机验证。

### 编辑尺寸与原图对比（v0.8.24）

1. 打开 **图生图 → 编辑**，选择工作流，进入配置。只有已映射的宽、高字段才分别显示输入控件；有效值按工作流保存并用于本次生成，未映射的尺寸由工作流自己决定。恢复优先级为已保存值 → 导入默认值 → 可见的 1024 UI 初值。当前校验范围为 1–4096，已映射尺寸无效时阻止生成。
2. 在 **分辨率预设** 中选择 **与原图相同尺寸**，将主原图（槽 1）的实际解码宽高精确填入已映射字段，不按 8/64 取整。没有主图时禁用；非编辑模式或没有宽高映射时隐藏。原图超范围会显示校验错误，不会偷偷缩小。
3. 生成后，点击结果预览 **右上角 Info 信息按钮**。面板显示结果实际解码像素尺寸；文件大小明确显示 **文件大小不可用**，因为当前未保留原始编码响应，不能拿 bitmap 内存大小代替文件大小。**原图 / 结果对比** 开关位于信息下方。
4. 开启对比后，在图片上左右拖动竖直分隔线，或使用下方滑块：左边原图，右边结果。两张图先在同一完整视口居中适配再裁剪；不同宽高比保留黑边，不拉伸。对比时暂停预览分页横滑；关闭对比后可正常点击结果进入全屏。
5. 全屏中，**单指静止长按** 显示原图，**松手或取消** 恢复结果；移动超过触摸阈值、多指、离开页面或应用暂停也会结束对比。临时切换原图沿用当前缩放和平移位置，不锁定原图。

**可用范围：** 对比使用准备该次生成时捕获的主原图快照，并按服务器及 prompt 关联，不使用现在重新选择的图片。仅适用于具有原图关联的编辑结果；图库、原图槽、视频、未知或恢复的历史记录没有此入口。关联依赖当前会话，快照保存在系统缓存；进程重启、缓存被回收或全屏能力票据失效后不提供对比。这不是永久编辑历史。对比解码可能下采样（最长边不超过 2048），尺寸预设仍使用完整主原图解码尺寸。

### 工作流处理修复

- 节点属性编辑共用实现，支持裸 prompt、`nodes`、`prompt` 包装，保护连接数组并保留 Long 精度。
- 编辑模式裸 prompt 不再提前返回，继续进行最终处理、旁路和调试快照。
- 仅当 `SaveImageAdvanced` 明确为 `format=png` 时，补全缺失/null/空白的 `format.bit_depth=8-bit`、`format.input_color_space=sRGB`；不覆盖显式值或未解析占位符，不猜其他模型/采样器参数。
- **不宣称已解决全部 Qwen 2.1 服务端校验错误。** 仍需最终提交 JSON 和真实 ComfyUI 执行证据。

## 验证与限制

v0.8.24 / versionCode 51 调试 APK 已成功构建；40 个 JVM 测试通过，失败、错误、跳过均为 0。没有连接设备，尚未实测触摸手势、旋转/后台生命周期或真实 ComfyUI 生成；JVM 状态/身份校验测试不是 Compose 触摸测试。详细证据及实机待办见 [验收移交记录](docs/comfychair-qwen21-validation-handoff-2026-10-27.md)。

## 使用要求

- Android 13（API 33）或更高版本。
- 可访问的 ComfyUI 服务端及网络连接；在应用中配置实际服务器 URL。

## 开发与测试

准备 JDK 17、Android SDK API 36，并在 `local.properties` 中设置 SDK 路径。

```bash
git clone https://github.com/nickdlkk/comfychair.git
cd comfychair
./gradlew testDebugUnitTest assembleDebug --no-daemon
# 需要已连接设备或模拟器：
./gradlew installDebug
./gradlew connectedAndroidTest
```

APK 实际文件名见 `app/build/outputs/apk/debug/output-metadata.json`。完整功能列表、截图、技术栈及贡献说明见 [英文 README](README.md)。

## 许可证与致谢

[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)。感谢上游 [legal-hkr/comfychair](https://github.com/legal-hkr/comfychair)、[ComfyUI](https://github.com/comfyanonymous/ComfyUI) 及贡献者。
