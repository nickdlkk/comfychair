# 点击原图 loading — v0.8.26-beta.8 / code62

## 已验证交付
- APK: <http://192.168.4.168:9010/comfychair-v0.8.26-beta.8-release-original-loading-20261105-0934.apk>
- Bytes: `8372317`; SHA256: `ad78bdaaf5c2e3dc003c10a1617888836802f97caeb41fce2d603dd2185af57c`。
- LAN 无代理完整 HTTP GET200，下载大小/哈希与构建和分享文件一致。复用已运行9010文件服务，未改变服务。
- aapt: `sh.hnet.comfychair`, `v0.8.26-beta.8`, versionCode62；由实际61/ beta7只升一次。新旧 apksigner 验证通过，同 Android Debug SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`，不是生产签名。下次分发code必须大于62。

## 根因及实现
- 实际独立图库点击加载由 ServerGalleryScreen 的 `vm.loading` 显示顶部转圈+等待文字+取消文字；共享原图选择器由 `state.downloading` 同时显示选中格转圈和底部转圈/文字。两条路径不是同一个状态。
- 已检查 GenerationProgressBar：它是有真实生成步数的 LinearWavyProgressIndicator，不适合无可靠总量的原图下载。复用先前 MediaViewerScreen.MediaContent 的 `CircularProgressIndicator(color = Color.White)` 样式，提取为共享 `MediaLoadingIndicator()`；MediaViewer本身改调用同一个组件，非另造竞争指示器。
- 新 `MediaLoadingOverlay`：半透明遮罩保留底下网格，居中旧式白色不定进度转圈，右上48dp取消图标沿用EN/ZH `button_cancel` 可访问描述。普通加载没有文字或百分比。
- 独立图库和共享原图选择器都调用该遮罩；删除原先等待文字/取消文字/底部小转圈及选中tile转圈。仅原图加载变化，列表加载/缩略图初始占位仍用原有反馈；真实错误/重试/分页继续文字、删除确认与安全说明保留。
- 遮罩消费底下点击；图库原图加载时暂停共享网格请求/分页和tile动作，但不隐藏缩略图、不重建panel、不改session/grid state。原图选择器继续使用已有下载暂停条件。底部不因加载多出状态行，保留网格位置。
- 无控制器/网络/删除业务改动；取消、失败、重试、返回全屏、锚点删除既有实现保留。图库错误仍显示错误文字，可再次点图重试，未新增业务入口。

## 实际执行
- 新测试先RED：5项/4失败（缺共享组件/旧文字路径），`test-red.log`。随后增加1项真实控制器协程转换测试。
- 最终命令：`./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=original-loading`。
- Java17，SDK `/root/Android/Sdk`；BUILD SUCCESSFUL in6m37s。真实执行clean tests、R8、资源裁剪/优化、签名打包。未在最终构建期间修改源码/测试。
- 实际XML：170 tests，0 failures/errors/skipped，27 suites。新增6项：原有MediaViewer转圈复用、无文字/假进度、可访问取消/遮挡底层、图库/选择器两条真实加载接线、bitmap/session/grid/anchor契约，以及pending/cancel/failure/retry状态下列表同一对象/session/像素锚点保留、取消不接受旧结果。原164项全保留，包括scrollfix/删除/预览返回。
- `adb devices -l` 无设备；无渲染截图/触摸、取消图标可达性、窄屏/大字体、安装或R8后运行时验证。源码接线/JVM通过不等于真机证明。
- 磁盘初270MiB：旧code56 mapping及code57–60 usage/resources用gzip无损归档，逐项解压SHA256验证后去除未压缩副本；详见 `lossless-archives.json`，未删APK/源码/用户数据。完成后约340MiB可用。
- 未访问远端业务API、删除文件、改服务端/密钥、提交/push/tag/Release；原dirty/untracked skills及stash保留。`git diff --check`通过。

## 本次文件清单
- `app/build.gradle.kts`
- NEW `app/src/main/java/sh/hnet/comfychair/ui/components/MediaLoadingOverlay.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/screens/ServerGalleryScreen.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/screens/MediaViewerScreen.kt`
- NEW `app/src/test/java/sh/hnet/comfychair/ServerOriginalLoadingContractTest.kt`
- `app/src/test/java/sh/hnet/comfychair/ServerImagePickerUiContractTest.kt`（新footer契约）
- `app/src/test/java/sh/hnet/comfychair/ServerGalleryPositionTest.kt`（明确loading暂停条件）
- `README.md`, `README.zh-CN.md`, this handoff。

证据 `/root/comfychair-artifacts/code62/`：本次APK、before快照、初始/最终状态与stash、RED/最终构建日志、测试XML、output-metadata、beta7/current aapt/apksigner、verification.json、matching R8 mapping/*.gz、归档校验记录。上次布局交接保留于 `gallery-layout-handoff.md`。
