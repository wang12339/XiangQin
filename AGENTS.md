# AGENTS.md

## 项目概述

Android 家庭安全监控应用（"乡亲"）。Kotlin + Jetpack Compose + Room/SQLCipher + Ktor 服务端。Vue 3 + Vite Web 管理面板，由手机 Ktor 服务端在 8080 端口托管。VPS 中继（43.99.13.12）用于远程访问。

## 构建命令

```bash
# 完整构建（Web 前端 + Android APK）
cd ~/AndroidStudioProjects/XiangQin/web && npm run build
cd ~/AndroidStudioProjects/XiangQin && ./gradlew :app:assembleDebug

# APK 输出
app/build/outputs/apk/debug/app-debug.apk
```

**Web 构建必须在 Android 构建之前**：`npm run build` 输出到 `app/src/main/assets/web/`，`vite.config.ts` 中 `emptyOutDir: true` 会完全清空该目录。

## 开发调试

```bash
# Web 开发服务器（热重载，代理 /api 和 /ws 到 localhost:8080）
cd web && npm run dev

# 构建 + 安装到手机
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# 重启 App 并建立 adb forward（⚠️ 每次 App 重启后必须重新执行）
adb shell am force-stop com.xiangqin.app.debug
adb shell am start -n com.xiangqin.app.debug/com.xiangqin.app.MainActivity
sleep 12
adb forward tcp:8080 tcp:8080

# 启用无障碍服务（⚠️ MIUI 重启 App 后会清除，需重新执行）
adb shell settings put secure enabled_accessibility_services com.xiangqin.app.debug/com.xiangqin.app.service.PermissionAccessibilityService
```

## 核心架构

- **Ktor 服务端**（`app/.../server/WebServer.kt`，~1800 行）：单文件单体架构，30+ API 路由。
- **Room 数据库**（版本 5，17 Entity）：`app/.../data/db/AppDatabase.kt`，SQLCipher 加密。Schema 导出到 `app/schemas/`。增删改 Entity 后需 `./gradlew :app:kspDebugKotlin`。
- **23 个 Monitor**（`app/.../monitor/`）：`MonitoringService.kt` 前台服务编排。
- **Vue SPA**（`web/src/views/`，32 页面）：API 客户端 `web/src/api/client.ts`（axios，`baseURL: ''`）。
- **VPS 中继**：nginx（443）→ Python WebSocket relay（8765/8766）→ 手机 WebServer（8080）。

## 构建配置

- Kotlin 2.0.10，JDK 17，compileSdk 34，minSdk 26。
- NDK 只构建 `arm64-v8a`（减少 ~9MB SO 库体积）。
- Debug 和 Release 都启用 `isMinifyEnabled = true` + `isShrinkResources = true`，ProGuard 规则生效。
- Debug 包名 `com.xiangqin.app.debug`（`applicationIdSuffix = ".debug"`）。
- 依赖版本：`gradle/libs.versions.toml`（版本目录），Ktor Client 硬编码 2.3.12。
- 签名文件：`app/xiangqin-release.jks`（密码：`xiangqin123`）。
- ProGuard 必须 keep：Room Entity、SQLCipher、Ktor、`data.db`/`server`/`monitor` 包下所有类。

## 易踩坑

### WebServer.kt 编辑风险（⚠️ 最高频错误源）

- 1600+ 行大文件，多次 Edit 容易破坏括号平衡、丢失端点代码。
- **`server?.start()` 必须在 `embeddedServer` lambda 外部**：在 lambda 内部时 `server` 为 null，`start()` 从未执行，WebServer 不启动（无端口监听，curl 返回 HTTP 000）。
- 编辑后务必 `./gradlew :app:assembleDebug` 验证编译。

### Ktor `respondBytes` Content-Type（⚠️ 中继图片加载失败根因）

- `call.response.header(HttpHeaders.ContentType, mime.toString())` + `call.respondBytes(bytes)` **无效**：`respondBytes` 会覆盖为 `application/octet-stream`。
- **必须用**：`call.respondBytes(bytes, contentType = mime)`。
- 后果：中继 `isBin` 检查 `ct.startsWith("image/")` 失败，二进制图片被当作文本传输→乱码→浏览器显示不了。

### 文件路径 canonicalPath（⚠️ 截屏/照片 403 根因）

- `File.canonicalPath` 在 Android 上会将 `/data/user/0/` 解析为 `/data/data/`（符号链接），`/sdcard` 解析为 `/storage/emulated/0/`。
- 路径安全检查必须同时包含：`/sdcard`、`/storage`、`/data/user/0`、`/data/data`、`context.filesDir.absolutePath`。

### VPS 中继静态文件

- VPS nginx 从 `/opt/relay/static/` 提供 SPA 静态文件。
- 修改 Web 代码后，需将 `app/src/main/assets/web/` 部署到 VPS：`tar czf /tmp/relay-static.tar.gz -C app/src/main/assets/web . && cat /tmp/relay-static.tar.gz | ssh root@43.99.13.12 "cat > /tmp/relay-static.tar.gz && rm -rf /opt/relay/static/* && tar xzf /tmp/relay-static.tar.gz -C /opt/relay/static/"`。
- **⚠️ 禁止用 scp**（Colima SSH 干扰），必须用 `cat | ssh` 管道方式。

### Web API 路由必须匹配前端

- 前端 Remote.vue 调用：`POST /api/camera/capture`、`GET /api/camera/photos`、`POST /api/audio/start`、`POST /api/audio/stop`、`GET /api/audio/recordings`。
- 截屏：`POST /api/device/screenshot`，文件加载：`GET /api/media/file?path=...` 或 `GET /api/files/{path...}`。
- 前端照片/录音 URL 格式：`/api/media/file?path=${encodeURIComponent(filePath)}`（避免双斜杠路由问题）。

### 其他已知问题

- `XiangQinApp.instance` 是全局单例，Database 和 DataStore 在 `onCreate()` 中初始化。
- WebServer 绑定 `0.0.0.0:8080`（非 localhost）。
- 数据库密码 = SHA-256(`ANDROID_ID:PIN`)，默认 PIN "0000"，固定密码 `xiangqin123`。
- 无 CI，无自动化 lint/typecheck，验证方式为 `./gradlew :app:assembleDebug`。
- `coreLibraryDesugaring` 已启用，API < 26 可用 `java.time`。
- Netty 原生传输在 Android 上禁用（`io.netty.noNativeTransport=true`）。
- AccessibilityService 截屏：`AccessibilityService.takeScreenshot()` API（Android 11+），需 `canTakeScreenshot="true"` XML 属性。a11y 不可用时 fallback 到 `screencap` 命令。
- CameraCaptureHelper 默认使用**前置摄像头**，拍照后必须 `camera.close()` 避免 error 3。
- 录音格式为 MPEG_4（.m4a），AAC_ADTS（.aac）在浏览器 `<audio>` 标签中不兼容。
- Kotlin 字符串模板中不允许安全调用 `${e.message?.replace(...)}`，必须先提取变量。
- `call.receive<Map<String, Any>>()` 在混合类型下会失败，用 `call.receiveText()` + 手动解析。

## VPS 资源

- SSH：`ssh root@43.99.13.12`（密码：Whanghui123）
- 中继：`/opt/relay/server.py`（WebSocket :8765 + HTTP :8766）
- nginx 配置：`/etc/nginx/conf.d/xiangqin-relay.conf`
- **⚠️ 修改 VPS Python 代码禁止 sed 注入**：Python 缩进敏感，必须用完整文件替换（cat + heredoc）。
