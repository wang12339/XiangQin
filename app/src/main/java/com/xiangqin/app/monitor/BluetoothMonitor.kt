package com.xiangqin.app.monitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.BluetoothDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class BluetoothMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun sync() {
        if (!hasPermission()) return

        withContext(Dispatchers.IO) {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext

                // 1. 已配对设备（无额外功耗）
                val pairedDevices = bluetoothAdapter.bondedDevices ?: emptySet()
                val now = System.currentTimeMillis()
                for (device in pairedDevices) {
                    upsertDevice(device.name, device.address, device.bondState, null, now, device.bluetoothClass?.toString())
                }

                // 2. BLE 低功耗扫描（替代经典 Discovery，功耗降低 ~60%）
                val discovered = bleScan(bluetoothAdapter)
                val scanTime = System.currentTimeMillis()
                for (result in discovered) {
                    upsertDevice(result.device.name, result.device.address, result.device.bondState, result.rssi, scanTime, result.device.bluetoothClass?.toString())
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    /**
     * BLE 低功耗扫描 — SCAN_MODE_LOW_POWER 最省电
     * 扫描 5 秒（经典 Discovery 需 8-12 秒且使用经典蓝牙射频）
     */
    private suspend fun bleScan(bluetoothAdapter: BluetoothAdapter): List<BleScanResult> {
        if (!bluetoothAdapter.isEnabled) return emptyList()
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return emptyList()

        return try {
            withTimeout(6_000L) {
                val results = mutableMapOf<String, BleScanResult>()

                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val device = result.device
                        results[device.address] = BleScanResult(device, result.rssi)
                    }
                    override fun onBatchScanResults(batchResults: MutableList<ScanResult>) {
                        for (r in batchResults) {
                            val device = r.device
                            results[device.address] = BleScanResult(device, r.rssi)
                        }
                    }
                    override fun onScanFailed(errorCode: Int) {}
                }

                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .build()

                scanner.startScan(null, settings, callback)

                kotlinx.coroutines.delay(5_000)

                try { scanner.stopScan(callback) } catch (e: Exception) { android.util.Log.w("XiangQin/BT", "停止扫描失败: ${e.message}") }

                results.values.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun upsertDevice(
        deviceName: String?, deviceAddress: String, bondState: Int,
        rssi: Int?, timestamp: Long, deviceClass: String?
    ) {
        val dao = app.database.bluetoothDeviceDao()
        val existing = dao.getByAddress(deviceAddress)
        if (existing != null) {
            dao.update(existing.copy(
                deviceName = deviceName ?: existing.deviceName,
                bondState = bondState, rssi = rssi ?: existing.rssi,
                lastSeen = timestamp, deviceClass = deviceClass ?: existing.deviceClass
            ))
        } else {
            dao.insert(BluetoothDeviceEntity(
                deviceName = deviceName, deviceAddress = deviceAddress,
                bondState = bondState, rssi = rssi,
                firstSeen = timestamp, lastSeen = timestamp, deviceClass = deviceClass
            ))
        }
    }

    private data class BleScanResult(val device: BluetoothDevice, val rssi: Int)
}
