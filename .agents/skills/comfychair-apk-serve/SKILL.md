---
name: comfychair-apk-serve
description: ComfyChair APK 直链生成 — 编译后用 Python HTTP server 提供下载链接
triggers:
  - 编译完 ComfyChair APK 需要给 Nick 下载链接
  - APK 直链
---

# ComfyChair APK 直链生成

## 编译（构建）

ComfyChair 只能用 **Java 17** 编译，Java 21 不兼容：

```bash
cd /root/comfychair
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug --no-daemon
# 或带功能标识：
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug --no-daemon -PapkSuffix=workflow-save-loading
```

**关键**：`--no-daemon` 必须加，不加会报 TLS handshake 错误（Gradle daemon 的 HTTP 客户端协议版本问题）。`-q` 会吞掉编译错误输出，调试阶段去掉 `-q`。

**Pitfall - strings.xml 字符串重复**：
新增字符串 ID 之前先用 `grep -n` 查是否已存在。`content_description_decrease/increase` 等常见名容易重复，新字符串加到 `<!-- Batch generation strings -->` 独立 block 下方，避免与配置面板等已有字符串撞名。

**Pitfall - http.server 工作目录**：
`python3 -m http.server` 必须在 APK 所在目录启动，或用 `--directory` 指定目录。在 `/root/comfychair` 启动会 404，在 `/root/comfychair/app/build/outputs/apk/debug` 启动才正确。

APK 输出位置：
```bash
/root/comfychair/app/build/outputs/apk/debug/comfychair-<version>-debug[-<apkSuffix>]-<timestamp>.apk
```

说明：
- 默认文件名格式：`comfychair-v0.8.18-debug-20260811-1442.apk`
- 传 `-PapkSuffix=<tag>` 后：`comfychair-v0.8.18-debug-workflow-save-loading-20260811-1442.apk`
- `apkSuffix` 会自动清洗为文件名安全字符

## 启动 HTTP 服务器（推荐稳定做法）

**当前推荐方式：复制到 `/root/share/comfychair/`，统一用 9010 端口分享。**

```bash
mkdir -p /root/share/comfychair
cp -f /root/comfychair/app/build/outputs/apk/debug/comfychair-*.apk \
  /root/share/comfychair/

# 后台启动分享服务
python3 -m http.server 9010 --directory /root/share
```

在 Hermes 里要用 `terminal(background=true)` 启动 server，不要在 foreground command 里手写 `&`。

### 2. 确认服务器在线
```bash
# 本地检测具体 APK
python3 - <<'PY'
import glob, os, urllib.request
apk = max(glob.glob('/root/share/comfychair/comfychair-*.apk'), key=os.path.getmtime)
url = 'http://127.0.0.1:9010/comfychair/' + os.path.basename(apk)
with urllib.request.urlopen(url, timeout=10) as r:
    print(os.path.basename(apk))
    print(r.status)
    print(r.getheader('Content-Length'))
PY
```

如果返回 `200`，链接可分享。

### 3. 生成分享链接
格式：`http://192.168.4.69:9010/comfychair/<apk文件名>`

示例：
```
http://192.168.4.69:9010/comfychair/comfychair-v0.8.18-debug-workflow-save-loading-20260811-1442.apk
```

### 4. 验证可下载
优先验证 **具体文件 URL**，不要只测目录页：
```bash
curl -s -o /dev/null -w "%{http_code}" \
  http://127.0.0.1:9010/comfychair/comfychair-v0.8.18-debug-workflow-save-loading-20260811-1442.apk
# 期望: 200
```

## 命名与验证注意事项

- 文件名默认包含版本、buildType、时间戳；传 `-PapkSuffix=<tag>` 时会额外包含功能标识
- 推荐分享前显式传 `-PapkSuffix`，例如 `workflow-save-loading`、`multi-image-slot`，便于 Nick 区分
- 先 `stat` 或读取 `Content-Length` 记录 APK 大小，再发链接
- `Connection reset by peer` 常见于本地探测时提前断开，不代表分享失效；只要具体文件 URL 返回 `200` 即可

## GitHub 推送

remote `nick` 使用 HTTPS + token，需要手动拼接：

```bash
cd /root/comfychair
GITHUB_TOKEN=$(sed -n 's/^\s*oauth_token:\s*\([^ ]*\).*/\1/p' /root/.config/gh/hosts.yml | head -1)
git remote set-url nick "https://nickdlkk:${GITHUB_TOKEN}@github.com/nickdlkk/comfychair.git"
git push nick HEAD
```

注意：`~/.config/gh/hosts.yml` 路径是固定的，不能用 `~/.hermes/...` 下的同名文件。

## 注意事项
- 手机和 PC 需要和 Hermes 宿主机网络互通（同一 LAN 或穿透）
- APK 大约 88MB，提醒 Nick 用 WiFi 下载
- nc TCP 检测成功但 HTTP 链接打不开 → 防火墙/路由没放行该端口
