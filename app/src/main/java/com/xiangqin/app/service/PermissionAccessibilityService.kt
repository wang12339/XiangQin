package com.xiangqin.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PermissionAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 3000
        }
        serviceInfo = info
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onInterrupt() {}
    override fun onDestroy() { serviceInstance = null; super.onDestroy() }

    private var lastPackage = ""
    private var lastClass = ""
    private var handledTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""

        val now = System.currentTimeMillis()
        if (pkg == lastPackage && cls == lastClass && (now - handledTime) < 1500) return
        lastPackage = pkg
        lastClass = cls
        handledTime = now

        // 仅对乡亲 App 的权限弹窗生效
        when {
            isSystemPermissionDialog(pkg, cls) -> {
                if (pkg == getTargetPkg()) {
                    Log.d(TAG, "乡亲权限弹窗 → 自动授权")
                    clickAllow()
                }
            }
            isMiuiPermissionDialog(pkg, cls) -> {
                if (pkg == getTargetPkg()) {
                    Log.d(TAG, "MIUI权限弹窗 → 自动授权")
                    clickAllow()
                }
            }
            isUsageStatsPage(pkg, cls) -> {
                Log.d(TAG, "检测到使用情况访问设置页")
                toggleUsageStats()
            }
        }
    }

    private fun isSystemPermissionDialog(pkg: String, cls: String): Boolean {
        return pkg == "com.android.permissioncontroller" ||
                pkg == "com.google.android.permissioncontroller" ||
                pkg == "com.android.packageinstaller" ||
                cls.contains("GrantPermissionActivity") ||
                cls.contains("PermissionDialog")
    }

    private fun isMiuiPermissionDialog(pkg: String, cls: String): Boolean {
        return cls.contains("PermissionDialog") ||
                (pkg == "com.android.systemui" && cls.contains("Permission"))
    }

    private fun isUsageStatsPage(pkg: String, cls: String): Boolean {
        return cls.contains("UsageAccessSettings") || cls.contains("UsageStats")
    }

    private fun clickAllow() {
        val root = rootInActiveWindow ?: return
        val targets = listOf("允许", "Allow", "Grant", "仅在使用中允许", "While using the app", "仅本次允许", "始终允许")
        for (text in targets) {
            if (clickNodeByText(root, text)) {
                Log.d(TAG, "已点击「$text」")
                return
            }
        }
    }

    private fun toggleUsageStats() {
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByText("乡亲")
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                postDelayed({
                    val r = rootInActiveWindow ?: return@postDelayed
                    findSwitchAndToggle(r)
                }, 800)
                return
            }
        }
        findSwitchAndToggle(root)
    }

    private fun findSwitchAndToggle(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.className?.toString()?.contains("Switch", ignoreCase = true) == true) {
                if (child.isChecked != true && child.isEnabled) {
                    child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
            if (findSwitchAndToggle(child)) return true
        }
        return false
    }

    private fun clickNodeByText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text) ?: return false
        for (node in nodes) {
            if (!node.isEnabled || node.isCheckable && node.isChecked) continue
            // 找到文本节点后，向父级递归找可点击的容器
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                current = current.parent
            }
        }
        return false
    }

    private fun postDelayed(action: () -> Unit, delayMs: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMs)
    }

    companion object {
        const val TAG = "PermAutoGrant"
        @Volatile private var serviceInstance: PermissionAccessibilityService? = null

        private fun getTargetPkg(): String {
            return try { com.xiangqin.app.XiangQinApp.instance.packageName } catch (_: Exception) { "com.xiangqin.app.debug" }
        }

        fun isRunning(): Boolean = serviceInstance != null

        fun screenshot(callback: (android.graphics.Bitmap?) -> Unit) {
            val svc = serviceInstance
            if (svc == null) { callback(null); return }
            try {
                svc.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    svc.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            try {
                                val bmp = android.graphics.Bitmap.wrapHardwareBuffer(
                                    result.hardwareBuffer, result.colorSpace
                                )?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                result.hardwareBuffer.close()
                                callback(bmp)
                            } catch (e: Exception) { callback(null) }
                        }
                        override fun onFailure(errorCode: Int) { callback(null) }
                    }
                )
            } catch (e: Exception) { callback(null) }
        }
    }
}
