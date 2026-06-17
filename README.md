# 乡亲 (XiangQin)

Android 家庭安全监控应用。通过手机内置传感器和系统 API，实现对家庭成员手机的远程监控、告警和管理。

## 功能特性

### 监控能力（23 个 Monitor）
- **通讯监控**：通话记录、短信（含实时接收）、来电状态
- **位置追踪**：GPS 定位、基站变化检测
- **网络监控**：WiFi 扫描 + 安全分析、蓝牙设备发现、流量统计
- **设备感知**：活动识别（步行/驾车/静止）、传感器数据、系统设置变化
- **应用监控**：使用时长、媒体文件索引、日历事件、账户信息
- **远程操作**：截屏、屏幕录制、远程拍照/录音、远程拨号/短信、文件管理
- **设备控制**：震动、手电筒、闹钟、锁屏/解锁、强制停止应用、重启/关机

### 告警引擎
- 深夜离家检测、低电量告警、心跳丢失、凌晨来电
- 设备重启、SIM 卡变化、应用安装、WiFi 变化、基站变化
- 飞书 Webhook 推送

### Web 管理面板
- Vue 3 + Element Plus + Tailwind CSS + Leaflet
- 32 个页面，覆盖所有监控数据的可视化
- 实时 WebSocket 推送
- 数据导出（CSV/JSON）

### 安全特性
- SQLCipher 加密数据库
- 速率限制 + 会话管理
- 安全响应头（HSTS/X-Frame-Options/CSP）

## 技术栈

| 层 | 技术 |
|---|------|
| Android | Kotlin, Jetpack Compose, Room, SQLCipher, Ktor (Netty) |
| Web 前端 | Vue 3, TypeScript, Vite, Element Plus, Tailwind CSS |
| 远程中继 | Python WebSocket, nginx (VPS 43.99.13.12) |
| 构建 | Gradle 9.3, Kotlin 2.0.10, JDK 17 |

## 快速开始

### 构建

```bash
# Web 前端 + Android APK
cd web && npm run build && cd ..
./gradlew :app:assembleDebug

# APK 输出
app/build/outputs/apk/debug/app-debug.apk
```

### 开发调试

```bash
# Web 热重载（代理 /api 到 localhost:8080）
cd web && npm run dev

# 构建 + 安装到手机
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# 重启 App + 建立 adb forward（每次重启后必须执行）
adb shell am force-stop com.xiangqin.app.debug
adb shell am start -n com.xiangqin.app.debug/com.xiangqin.app.MainActivity
sleep 12
adb forward tcp:8080 tcp:8080
```

### CI

```bash
./ci.sh  # Web 构建 → TypeScript 类型检查 → Android APK
```

## 项目结构

```
app/src/main/java/com/xiangqin/app/
├── server/              # Ktor 服务端（拆分为 9 个模块）
│   ├── WebServer.kt     # 入口（90 行）
│   ├── AuthModule.kt    # 认证/token/速率限制
│   ├── Dto.kt           # 30+ 个 @Serializable DTO
│   ├── StaticRoutes.kt  # 静态资源服务
│   ├── DataRoutes.kt    # 数据查询/统计
│   ├── DeviceRoutes.kt  # 设备控制/拍照/录音
│   ├── AlertRoutes.kt   # 告警管理
│   ├── MiscRoutes.kt    # 文件/联系人/系统设置/导出
│   └── WebSocketRoutes.kt
├── service/
│   ├── MonitoringService.kt   # 前台服务（268 行）
│   └── MonitorScheduler.kt    # 14 个轮询任务编排
├── monitor/             # 23 个数据采集器
├── data/db/             # Room 数据库（17 Entity，版本 5）
├── relay/               # VPS WebSocket 中继客户端
├── receiver/            # 系统广播接收器
└── util/                # 权限/WiFi/Root 工具

web/src/
├── views/               # 32 个 Vue 页面
├── api/client.ts        # Axios + Basic Auth
├── stores/auth.ts       # Pinia 认证状态
└── router/index.ts      # Vue Router
```

## 构建配置

- compileSdk 34, minSdk 26, NDK 仅 arm64-v8a
- Debug/Release 均启用 ProGuard（minifyEnabled + shrinkResources）
- Debug 包名：`com.xiangqin.app.debug`
- 签名文件：`app/xiangqin-release.jks`

## License

Private - 仅供个人使用
