package com.xiangqin.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiangqin.app.ui.theme.*

@Composable
fun OnboardingPage(
    onGoToPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("👋", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "欢迎使用乡亲",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = BrandTextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "家庭守护 · 安心相伴",
            fontSize = 15.sp,
            color = BrandTextSecondary
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandCard)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FeatureItem("📞", "通话 & 短信监控", "记录所有通话和短信往来")
                Spacer(Modifier.height(12.dp))
                FeatureItem("📍", "实时定位追踪", "GPS 轨迹记录，随时掌握位置")
                Spacer(Modifier.height(12.dp))
                FeatureItem("📸", "远程拍照 & 录音", "通过 Web 面板远程触发拍照录音")
                Spacer(Modifier.height(12.dp))
                FeatureItem("🚨", "智能告警", "异常行为检测，飞书实时推送")
                Spacer(Modifier.height(12.dp))
                FeatureItem("🛡️", "防卸载保活", "设备管理员 + 多维度保活机制")
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onGoToPermissions,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
        ) {
            Text("开始设置权限", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("稍后再说", fontSize = 15.sp)
        }
    }
}

@Composable
private fun FeatureItem(emoji: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandTextPrimary)
            Text(desc, fontSize = 12.sp, color = BrandTextSecondary)
        }
    }
}
