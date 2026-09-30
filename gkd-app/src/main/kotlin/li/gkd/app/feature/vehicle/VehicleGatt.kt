package li.gkd.app.feature.vehicle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.gkd.app.domain.vehicle.VehicleScanTiming
import li.gkd.app.domain.vehicle.VehicleRssiWatchdog

/** Only reads link RSSI. Does not discover/write vehicle command characteristics. */
@SuppressLint("MissingPermission")
class VehicleGatt(private val context: Context, private val scope: CoroutineScope, private val sample: (Int, Long) -> Unit,
    private val mode: (String) -> Unit) {
    private var gatt: BluetoothGatt? = null
    private var connected = false
    private var job: Job? = null
    private var readTime: Long? = null
    private var watchdog: VehicleRssiWatchdog? = null
    private var retryUntil = 0L
    private var retrySleepMs = 2000L
    val active get() = job?.isActive == true
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(value: BluetoothGatt, status: Int, state: Int) {
            scope.launch {
                if (value !== gatt) return@launch
                connected = status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED
                if (state == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) closeLink()
            }
        }
        override fun onReadRemoteRssi(value: BluetoothGatt, rssi: Int, status: Int) {
            scope.launch {
                if (value !== gatt) return@launch
                val time = readTime ?: return@launch
                readTime = null
                val now = SystemClock.elapsedRealtime()
                if (connected && status == BluetoothGatt.GATT_SUCCESS && rssi in -127..-1 && now - time in 0..5000) {
                    watchdog?.sample(now)
                    sample(rssi, now)
                }
            }
        }
    }

    fun start(adapter: BluetoothAdapter, address: String, readIntervalMs: Int, sleepSeconds: Int) {
        if (job?.isActive == true) return
        job = scope.launch {
            val timing = VehicleScanTiming(sleepSeconds)
            retrySleepMs = timing.sleepMs
            retryUntil = 0
            var attempts = 0
            while (true) {
                try {
                    if (gatt == null) {
                        val remaining = retryUntil - SystemClock.elapsedRealtime()
                        if (remaining > 0) delay(remaining)
                        attempts++
                        if (timing.logCycle(attempts)) VehicleStore.report("正在进行第${attempts}次 GATT 重连，最多等待 ${timing.connectTimeoutMs / 1000.0} 秒")
                        mode("GATT 连接中，尚未收到信号")
                        gatt = adapter.getRemoteDevice(address).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                        val deadline = SystemClock.elapsedRealtime() + timing.connectTimeoutMs
                        while (!connected && gatt != null && SystemClock.elapsedRealtime() < deadline) delay(250)
                        if (!connected) {
                            closeLink()
                            mode("GATT 连接失败，等待重试")
                            delay(timing.sleepMs)
                            if (attempts >= timing.restartAfterAttempts) {
                                attempts = 0
                                VehicleStore.report("GATT 连续重试约 5 分钟，清理连接后重新开始")
                            }
                            continue
                        }
                        VehicleStore.report("GATT 已连接，开始读取信号")
                        mode("GATT 已连接，读取信号中")
                        attempts = 0
                        watchdog = VehicleRssiWatchdog(readIntervalMs.toLong())
                    }
                    if (watchdog?.check(SystemClock.elapsedRealtime()) == true) {
                        VehicleStore.report("GATT 连续 3 次未检测到新信号，清理连接并重新轮询")
                        closeLink()
                    }
                    if (readTime != null && SystemClock.elapsedRealtime() - readTime!! > 5000) closeLink()
                    if (connected && readTime == null) {
                        readTime = SystemClock.elapsedRealtime()
                        if (gatt?.readRemoteRssi() != true) { readTime = null; closeLink() }
                    }
                    if (gatt == null) {
                        mode("GATT 已断开，静默 ${sleepSeconds} 秒后重试")
                        delay(timing.sleepMs)
                    } else delay(readIntervalMs.toLong())
                } catch (_: SecurityException) {
                    closeLink()
                    VehicleStore.report("GATT 权限已失效，请暂停并重新授权")
                    break
                }
            }
        }
    }

    private fun closeLink() {
        val current = gatt
        if (current != null) retryUntil = SystemClock.elapsedRealtime() + retrySleepMs
        gatt = null
        connected = false
        readTime = null
        watchdog = null
        try { current?.disconnect() } catch (_: SecurityException) { }
        try { current?.close() } catch (_: SecurityException) { }
    }

    fun stop() { job?.cancel(); job = null; closeLink() }
}
