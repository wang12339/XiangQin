package com.xiangqin.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xiangqin.app.service.MonitoringService
import com.xiangqin.app.ui.screens.OnboardingPage
import com.xiangqin.app.ui.theme.*
import com.xiangqin.app.util.PermissionHelper
import android.app.Activity
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val screenRecordingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenRecording(result.resultCode, result.data!!)
        }
    }

    private fun startScreenRecording(resultCode: Int, data: Intent) {
        val intent = android.content.Intent(this, com.xiangqin.app.service.ScreenRecordingService::class.java).apply {
            action = "START"
        }
        // 通过 Service 启动并传递授权结果
        com.xiangqin.app.service.ScreenRecordingManager.start(this, resultCode, data)
    }

    fun requestScreenRecording() {
        val projectionManager = getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        screenRecordingLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiangQinTheme {
                AppScreen()
            }
        }
    }

    @Composable
    fun AppScreen() {
        val app = XiangQinApp.instance
        var isRunning by remember { mutableStateOf(MonitoringService.isRunning) }
        var localIp by remember { mutableStateOf("获取中...") }
        var callCount by remember { mutableIntStateOf(0) }
        var smsCount by remember { mutableIntStateOf(0) }
        var locationCount by remember { mutableIntStateOf(0) }
        var showOnboarding by remember { mutableStateOf(false) }
        var showPermissions by remember { mutableStateOf(false) }
        var webPassword by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }

        // 每次进入页面都重新读取密码
        LaunchedEffect(Unit) {
            try {
                val pw = app.dataStore.getWebPassword()
                webPassword = pw
                android.util.Log.i("XiangQin", "APP 显示密码: $pw")
            } catch (e: Exception) { android.util.Log.e("XiangQin", "读取密码失败", e) }
        }

        // 每 5 秒刷新密码（防止外部修改后不同步）
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(5_000L)
                try {
                    webPassword = app.dataStore.getWebPassword()
                } catch (e: Exception) { android.util.Log.e("XiangQin", "刷新密码失败", e) }
            }
        }

        LaunchedEffect(Unit) {
            try {
                if (app.dataStore.isFirstLaunch()) {
                    showOnboarding = true
                    app.dataStore.markFirstLaunchDone()
                }
            } catch (e: Exception) { android.util.Log.e("XiangQin", "首次启动检查失败", e) }
        }

        LaunchedEffect(Unit) {
            while (true) {
                isRunning = MonitoringService.isRunning
                localIp = getLocalIpv4() ?: "未连接WiFi"
                if (isRunning) {
                    try {
                        val todayStart = java.time.LocalDate.now()
                            .atStartOfDay(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                        val todayEnd = todayStart + 86_400_000L
                        callCount = app.database.callDao().countByDate(todayStart, todayEnd)
                        smsCount = app.database.smsDao().countByDate(todayStart, todayEnd)
                        locationCount = app.database.locationDao().count()
                    } catch (e: Exception) { android.util.Log.e("XiangQin", "统计查询失败", e) }
                }
                kotlinx.coroutines.delay(3_000L)
            }
        }

        if (showOnboarding) {
            OnboardingPage(
                onGoToPermissions = { showOnboarding = false; showPermissions = true },
                onDismiss = { showOnboarding = false }
            )
            return
        }

        if (showPermissions) {
            PermissionPage(onBack = { showPermissions = false })
            return
        }

        val uriHandler = LocalUriHandler.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // Logo + Title
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏠", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("乡亲", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary)
                Text("家庭守护 · 安心相伴", fontSize = 14.sp, color = BrandTextSecondary)
            }

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd))
                    ).padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(
                                if (isRunning) Color(0xFF00FF88) else Color.White.copy(alpha = 0.5f),
                                RoundedCornerShape(50)
                            ))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (isRunning) "监控运行中" else "监控已停止",
                                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        if (isRunning) {
                            Spacer(Modifier.height(16.dp))
                            Text("局域网访问", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "http://$localIp:8080",
                                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("http://$localIp:8080")
                                }
                            )
                        }
                    }
                }
            }

            // Stats
            if (isRunning) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatItem("📞", "通话", "$callCount", Modifier.weight(1f))
                    StatItem("💬", "短信", "$smsCount", Modifier.weight(1f))
                    StatItem("📍", "位置", "$locationCount", Modifier.weight(1f))
                }
            }

            // Action Buttons
            if (isRunning) {
                // 屏幕录制按钮
                val isRecording by remember { mutableStateOf(com.xiangqin.app.service.ScreenRecordingService.isRecording()) }
                
                Button(
                    onClick = { requestScreenRecording() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) BrandDanger else BrandPrimary
                    )
                ) {
                    Text(if (isRecording) "⏹ 录制中..." else "⏺ 开始录屏", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        uriHandler.openUri("http://$localIp:8080")
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("🌐 打开 Web 面板", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = ::stopMonitoring,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDanger)
                ) {
                    Text("停止监控", fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = ::startMonitoring,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("▶ 启动监控", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Settings
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showPermissions = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BrandCard)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("权限与保活设置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BrandTextPrimary)
                        Text("确保所有权限已授予", fontSize = 12.sp, color = BrandTextSecondary)
                    }
                    Text("›", fontSize = 20.sp, color = BrandTextSecondary)
                }
            }

            // 管理面板密码
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BrandCard)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔑", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("管理面板密码", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BrandTextPrimary)
                            Text("用于登录 Web 管理面板", fontSize = 12.sp, color = BrandTextSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (showPassword) webPassword else "••••••••",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "隐藏" else "显示", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = {
                            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("password", webPassword)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(this@MainActivity, "密码已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制密码", fontSize = 13.sp, color = BrandPrimary)
                        }
                    }
                }
            }

            // Legal
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = {
                    try {
                        val html = assets.open("privacy_policy.md").bufferedReader().use { it.readText() }
                        val intent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("data:text/html,<pre>${java.net.URLEncoder.encode(html, "UTF-8")}</pre>"))
                        startActivity(intent)
                    } catch (e: Exception) { android.util.Log.e("XiangQin", "打开隐私政策失败", e) }
                }) { Text("隐私政策", fontSize = 12.sp, color = BrandTextSecondary) }
                Text(" · ", fontSize = 12.sp, color = BrandTextSecondary)
                TextButton(onClick = {
                    try {
                        val html = assets.open("user_agreement.md").bufferedReader().use { it.readText() }
                        val intent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("data:text/html,<pre>${java.net.URLEncoder.encode(html, "UTF-8")}</pre>"))
                        startActivity(intent)
                    } catch (e: Exception) { android.util.Log.e("XiangQin", "打开用户协议失败", e) }
                }) { Text("用户协议", fontSize = 12.sp, color = BrandTextSecondary) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    @Composable
    fun StatItem(icon: String, label: String, value: String, modifier: Modifier = Modifier) {
        Card(modifier = modifier, shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BrandCard)) {
            Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary)
                Text(label, fontSize = 11.sp, color = BrandTextSecondary)
            }
        }
    }

    @Composable
    fun PermissionPage(onBack: () -> Unit) {
        val permissions = remember { PermissionHelper.getMiuiPermissions(this) }
        val keepAliveItems = remember { PermissionHelper.getMiuiKeepAliveItems(this) }

        Column(
            modifier = Modifier.fillMaxSize().background(BrandBackground)
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("权限设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary)
            Text("请逐一授权以确保监控正常运行", fontSize = 13.sp, color = BrandTextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            // 保活状态
            val keepAliveCount = keepAliveItems.count { it.isDone }
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (keepAliveCount >= 3) BrandSuccess.copy(alpha = 0.1f)
                    else BrandWarning.copy(alpha = 0.15f)
                )) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (keepAliveCount >= 3) "🛡️" else "⚠️", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (keepAliveCount >= 3) "保活状态良好" else "保活待完善",
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = BrandTextPrimary)
                        Text(PermissionHelper.getKeepAliveSummary(this@MainActivity),
                            color = BrandTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            // 保活设置
            Text("保活增强", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary,
                modifier = Modifier.padding(bottom = 8.dp))
            keepAliveItems.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isDone) BrandSuccess.copy(alpha = 0.08f) else BrandCard
                    )) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.icon, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BrandTextPrimary)
                            Text(item.description, fontSize = 11.sp, color = BrandTextSecondary)
                        }
                        if (!item.isDone) {
                            TextButton(onClick = { item.action() }) { Text(item.actionLabel, fontSize = 12.sp) }
                        } else {
                            Text("✓", color = BrandSuccess, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 系统权限
            Text("系统权限", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandTextPrimary,
                modifier = Modifier.padding(bottom = 8.dp))
            permissions.forEach { perm ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (perm.isGranted) BrandSuccess.copy(alpha = 0.08f) else BrandCard
                    )) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(perm.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BrandTextPrimary)
                            Text(perm.description, fontSize = 11.sp, color = BrandTextSecondary)
                        }
                        if (perm.isGranted) {
                            Text("✓", color = BrandSuccess, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            TextButton(onClick = { perm.action() }) { Text(perm.actionLabel, fontSize = 12.sp) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { requestRuntimePermissions() },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)) {
                Text("申请运行时权限", fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)) {
                Text("返回", fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    private fun startMonitoring() {
        val hasCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasCallLog || !hasSms) { requestRuntimePermissions(); return }

        val intent = Intent(this, MonitoringService::class.java)
        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("sdk_gphone")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isEmulator) startForegroundService(intent) else startService(intent)
    }

    private fun stopMonitoring() {
        stopService(Intent(this, MonitoringService::class.java))
    }

    private fun requestRuntimePermissions() {
        val needed = PermissionHelper.allRuntimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun getLocalIpv4(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val addr = enumIpAddr.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
