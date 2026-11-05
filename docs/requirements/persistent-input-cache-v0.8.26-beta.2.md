# 持久化缓存与 Input 原文件直用 — v0.8.26-beta.2 / code56

## 授权及根因
仅本地实施、测试、APK分享；不commit/push/tag/Release。保留beta.1未提交代码、全部未跟踪skills和原stash。beta.1只在内存保存上传descriptor，picker选择链丢弃原文件descriptor并重编码，所以Input首次仍POST。

## 实现与验收范围
- `PrivateImageDiskCache` 存放应用noBackupFilesDir/server-image-cache-v1；安装随机32字节密钥HMAC对服务器URL及结构化认证字段建立namespace，再对namespace+业务key建立文件名。不记录原始凭据；媒体/列表内容本身不是加密存储，仅Android应用私有隔离，不进入自动备份。认证epoch另在内存隔离；切换身份的旧结果不进入新身份缓存。
- 64 MiB/512项共享LRU，单项4 MiB；版本+写入时间+SHA256校验和，临时写入后rename；损坏/过期/缺文件安全miss。安装密钥丢失或损坏使旧namespace失效。失败写入不阻断正常业务；不删除服务端文件。
- Input/Output首屏列表按来源/查询/排序/账号/服务器持久化，TTL24小时。先显示缓存，后台从首屏重新验证，刷新保留现有网格/滚动；不使用陈旧分页cursor继续翻页，成功后建立新cursor。缓存仅首屏（旧Input保留发现选项最多5000项），不是全目录离线索引。后台失败保留缓存网格并显示错误。列表刷新期间暂不接受选图，避免把陈旧列表当新结果。
- 预览持久化本地采样后的最长边256px PNG，TTL24小时；内存前置12MiB/64项并按日分代。网络仍原生/view preview=webp;60，最多4MiB、并发3；**没有服务端resize支持假设**。手动刷新逐一淘汰当前已列出图片的磁盘预览及内存缓存，已渲染格子不立即清空；重新进入/重新请求时重取。服务端同名替换在TTL内可能仍见旧缩略图。
- 上传descriptor也改为7天磁盘缓存（每次成功HEAD后续期），128项内存前置仍保留；实际编码字节SHA256+扩展名索引。重建client/进程仍能HEAD命中；其他认证/服务器namespace不命中。淘汰后安全POST、overwrite=false，不覆盖旧任务。HEAD仅404重传，其他失败停；HTTP沿用已有超时/无重定向/冻结认证/singleflight/取消。

## 原生 Input 直用安全边界
- 下载接受时保留exact ServerImage descriptor（包括assetId）、client认证epoch、Bitmap对象和Bitmap.generationId，槽1–4独立；process-only，不给恢复/重新编码位图伪造provenance。
- 仅编辑模式、未压缩、无蒙版路径、同对象/同revision/同epoch，且工作流该占位符每一次出现均为原生LoadImage.inputs.image的完整值（可已有单个[input]后缀）才允许。未知/自定义节点、混合consumer、拼接路径、重复注解都回到编码上传。不是“任何节点接受文件名”。
- 显式使用**原始服务器文件语义**：ComfyUI LoadImage读取源文件（可有与Android解码不同的EXIF/ICC/帧/alpha处理），不是假称Android Bitmap与原文件等价。picker信息EN/ZH和README已说明。
- 准备时先冻结epoch，认证body-free HEAD /view验证exact filename/subfolder/type=input；200返回bare path，由WorkflowManager只附一次[input]。404才用当前捕获位图的原有编码上传；401/403/405/5xx/网络/取消停止，不发fallback POST。可安全重试的HEAD保留OkHttp默认重试，绝不转成POST。
- WorkflowManager在编辑替换前去掉占位符预先存在的单个[input]，避免双注解；不把用户真实文件名的尾部任意剥掉。Output直用未实现，Output选择仍编码上传再内容复用。
- 现有活动映射槽/旁路、参考图、蒙版最终PNG、源快照、普通/批量/队首/占位符确认链保持。HEAD存在性仍有TOCTOU，无法阻止第三方同名覆盖/删除；不自动重新生成。

## 验证
新增PersistentServerImageTest：实际文件重建/命中/miss/身份隔离、TTL/LRU/容量/损坏恢复、列表query/sort/source/owner隔离与后台刷新保留、provenance身份/revision/压缩/蒙版/自定义节点拒绝、MockWebServer Input仅HEAD无POST、404上传、401/403/405/500/网络停止、取消、变化场景POST、上传descriptor重建后HEAD。既有完整回归保留。

实际ComfyUI仅只读GET/HEAD，不做真实服务器上传/生成/删除。JVM测试不证明Android Bitmap编码、Compose手势、实机安装或R8运行时；adb无设备。

最终构建、测试计数、APK/签名/HTTP哈希在handoff记录。
