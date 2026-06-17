package com.xiangqin.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiangqin.app.ui.theme.*

@Composable
fun OnboardingPage(
    onGoToPermissions: () -> Unit,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BrandPrimary, BrandGradientEnd))
            )
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            0 -> WelcomeStep(onNext = { step = 1 }, onDismiss = onDismiss)
            1 -> PermissionStep(
                onGrantAll = { onRequestPermissions(); step = 2 },
                onSkip = onDismiss
            )
            2 -> SpecialPermStep(
                onDone = onGoToPermissions,
                onSkip = onDismiss
            )
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit, onDismiss: () -> Unit) {
    Text("🏠", fontSize = 72.sp)
    Spacer(Modifier.height(20.dp))
    Text("欢迎使用乡亲", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text(
        "家庭守护 · 安心相伴",
        fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f)
    )
    Spacer(Modifier.height(40.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            FeatureItem("📞", "通话 & 短信监控")
            Spacer(Modifier.height(10.dp))
            FeatureItem("📍", "实时定位追踪")
            Spacer(Modifier.height(10.dp))
            FeatureItem("📸", "远程拍照 & 录音")
            Spacer(Modifier.height(10.dp))
            FeatureItem("🚨", "智能异常告警")
        }
    }

    Spacer(Modifier.height(40.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
    ) {
        Text("下一步", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onDismiss) {
        Text("稍后设置", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}

@Composable
private fun PermissionStep(onGrantAll: () -> Unit, onSkip: () -> Unit) {
    Text("🔐", fontSize = 56.sp)
    Spacer(Modifier.height(16.dp))
    Text("授权权限", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text(
        "乡亲需要以下权限来保护您的家人",
        fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            PermItem("📞", "通话记录", "记录来电去电")
            PermItem("💬", "短信", "监控短信内容")
            PermItem("📍", "位置", "GPS 定位追踪")
            PermItem("📷", "相机", "远程拍照")
            PermItem("🎤", "麦克风", "远程录音")
            PermItem("📇", "联系人", "联系人管理")
            PermItem("📅", "日历", "日程同步")
            PermItem("📶", "蓝牙/WiFi", "设备扫描")
            PermItem("🖼️", "媒体文件", "文件索引")
            PermItem("🔔", "通知", "消息推送")
        }
    }

    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onGrantAll,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
    ) {
        Text("一键授权所有权限", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onSkip) {
        Text("跳过", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}

@Composable
private fun SpecialPermStep(onDone: () -> Unit, onSkip: () -> Unit) {
    Text("⚙️", fontSize = 56.sp)
    Spacer(Modifier.height(16.dp))
    Text("特殊权限", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text(
        "以下权限需要在系统设置中手动开启",
        fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SpecialPermItem("📊", "使用情况访问", "监控应用使用时长")
            Spacer(Modifier.height(10.dp))
            SpecialPermItem("🔔", "通知访问权限", "读取所有通知内容")
            Spacer(Modifier.height(10.dp))
            SpecialPermItem("🛡️", "设备管理员", "防卸载保护")
            Spacer(Modifier.height(10.dp))
            SpecialPermItem("🚀", "自启动权限", "开机自动运行")
        }
    }

    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
    ) {
        Text("前往设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onSkip) {
        Text("跳过", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}

@Composable
private fun FeatureItem(emoji: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PermItem(emoji: String, name: String, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SpecialPermItem(emoji: String, name: String, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}
