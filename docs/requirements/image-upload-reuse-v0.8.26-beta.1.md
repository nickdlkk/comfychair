# 图生图上传复用 — v0.8.26-beta.1 / code55

## 范围与验收

本地实施及升版；不提交、不推送、不打tag、不创建GitHub Release。现有未跟踪 `.agents/skills` 和 stash 不动。

- 源槽1、活动且未旁路的映射槽2/3/4、参考图1/2、蒙版重绘最终PNG统一走实际字节上传复用。
- 图片/编码/蒙版改变产生新内容地址，不覆盖已排队任务。
- 同内容并发合并、失败不缓存、缓存有界、请求可取消。
- 服务端缺失可恢复；认证、网络、服务器、HEAD不支持不可冒充文件缺失。
- 原图对比仍使用本次准备的源快照与返回prompt关联；普通/批量/队首/占位符确认链不改。

## 实施

`connection/ImageUploadReuse.kt` 按最终上传字节SHA-256 + png/jpg扩展名建立key，同一ComfyUIClient认证epoch内128项访问顺序LRU；只保存descriptor，无图片字节/凭据持久化。不同角色相同字节允许共享一份。首次上传 `comfychair_<sha256>.<ext>`、`overwrite=false`；接受服务端实际返回name/subfolder/type，不假定期望文件名等于保存名。只接受input上传descriptor，保留子目录传给原有workflow处理。

命中缓存使用同一冻结认证epoch的 `HEAD /view?filename=...&subfolder=...&type=input`，200复用、404重新POST；其他状态和IO错误中止。HEAD不下载原图，不做HEAD不支持时的全图GET回退。上传响应上限8KiB；连接10s、读写30s、总call60s；禁止重定向携带认证到别处。进度只计图片payload，不计multipart envelope。

每key一个CompletableDeferred：首请求负责HTTP，后续await共享结果；取消等待者不影响首请求；取消首请求取消HTTP并使等待者失败，可重试。失败不插入缓存，finally用NonCancellable清理flight；并发不同内容最多128个，超出明确失败。认证变化/协议变化/shutdown失效旧epoch并取消它的HTTP，旧结果不能发布到新epoch。prepare在连接确认、上传后和快照后检查server/client/epoch，避免跨连接生成。

ViewModel仍从捕获state编码：编辑压缩槽JPEG、其余PNG；参考图PNG；重绘先合成source+mask的alpha PNG，hash覆盖最终真实字节。蒙版现在也从该次state读取，不从异步期间的新UI取值。任何已选择的必须上传图失败均中止，不再静默漏掉参考图或辅助图。

## 已知限制 / 未做

- **本版没有实现服务器Input/Output原路径零上传直用**。现有picker接受链丢弃原始descriptor，且会解码/重编码（帧、EXIF方向、ICC、alpha等语义可能变化）；没有建立经过验证的不可变来源/转换指纹和节点级支持白名单前，不把bitmap等同原文件。服务器选择仍首次编码上传，此后与本地源同样复用。
- 上游v0.43.0 `nodes.py` 的LoadImage加载/IS_CHANGED/VALIDATE_INPUTS使用 `folder_paths.get_annotated_filepath` / `exists_annotated_filepath`，`folder_paths.py`支持 `[output]`。证明原生LoadImage可行，不证明任意自定义映射节点接受output注解。源码保存于dev scratch `reuse-upstream-v0.43/`；实际部署报告0.43.0但未读取远程磁盘源码，不冒称源码逐字一致。
- LRU为会话内；新client、认证更新、重启/淘汰重新POST，服务端可自行去重/重命名。不删除旧文件。
- 每次仍编码/hash，优化网络POST，不是bitmap编码缓存。重复准备显示现有上传/检查状态，不增加新UI文案。
- HEAD只验证存在，无法抵御第三方同名覆盖或HEAD与执行之间删除；本应用不覆盖。外部修改/删除有TOCTOU，ComfyUI执行仍可能失败，不自动重试整个生成。
- 60s总超时是明确边界，极慢大图可能需要用户重试。取消发生在服务器已保存后可能留下无缓存文件，不自动删除。
- 测试PNG/JPEG/蒙版内容变化是实际HTTP byte fixture测试；Android Bitmap编码与实际蒙版画笔效果仍需设备验证。

## 验证记录

- 真实服务器 `192.168.4.36:8189/system_stats` GET：0.43.0、`--enable-assets`。
- 从已有history只读取得一张output descriptor，对 `/view` HEAD 实测200，Content-Length=3231075，读取响应体0字节。未对实际ComfyUI发POST/DELETE。
- 新增HTTP测试覆盖重复POST计数、真实saved子目录、变更字节/扩展名、404重传、401/403/500/405阻断、网络断开阻断、singleflight/失败/取消、client/server/auth隔离及在途认证变更、LRU淘汰、响应descriptor校验和payload进度。另有三个显式标记的源码接线契约测试，不冒充UI触摸测试。
- 完整构建、测试、签名、下载字节数及SHA256见下方最终交付记录。

## 最终交付记录

- `COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36 ./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=reuse-source`：BUILD SUCCESSFUL，6m7s；R8及资源裁剪执行。
- XML汇总：**110 tests /0 failures /0 errors /0 skipped**，新增13项HTTP/JVM测试+3项源码契约；包含显式启用真实服务器资产只读测试。最终日志：`/root/.hermes/profiles/dev/cache/scratch/reuse-release-final.log`。
- aapt：`sh.hnet.comfychair`，`v0.8.26-beta.1`，code55；APK：`comfychair-v0.8.26-beta.1-release-reuse-source-20261103-1659.apk`，**8,250,181 bytes**。
- 下载：http://192.168.4.168:9010/comfychair-v0.8.26-beta.1-release-reuse-source-20261103-1659.apk ，实际完整GET200，字节数及SHA256与本地相同。
- SHA256：`05088faa4e921991e06496056c10b5d162f1b7bd91cbfcf4d624dc0f67aaa686`。
- apksigner证书SHA256：`28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`，与已分享v0.8.25正式包完全相同，仍为Android Debug证书，不是生产签名。
- 匹配mapping归档：`/root/.hermes/profiles/dev/cache/scratch/reuse-source-code55/mapping.txt`（scratch会被清理，正式发布应持久化归档）。
- 初期测试编译因测试AuthCredentials导入包名错误失败，改为model包后通过。一次execute_code等待超过300s，构建进程仍存活，未并发重复构建；最终后台构建已等到成功。

设备：`adb devices -l` 为空，未验证安装、触摸、Android解码或裁剪后运行时。父agent独立review仍应执行。
