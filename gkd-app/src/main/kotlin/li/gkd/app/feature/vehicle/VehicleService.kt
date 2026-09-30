package li.gkd.app.feature.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.app.app
import li.gkd.app.domain.vehicle.ProximityController
import li.gkd.app.domain.vehicle.VehicleState
import li.gkd.app.service.A11yService
import kotlin.math.abs
import kotlin.math.sqrt

data class VehicleDevice(val address: String, val name: String, val rssi: Int)

@SuppressLint("MissingPermission")
class VehicleService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null
    private var actionJob: Job? = null
    private var controller: ProximityController? = null
    private lateinit var config: VehicleConfig
    private var discovery = false
    private var scanning = false
    private var paused = false
    private var triggerArmed = false
    private var lastMovement = SystemClock.elapsedRealtime()
    private var gravity = 9.81f
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val adapter get() = getSystemService(BluetoothManager::class.java).adapter
    private val wakeSensor by lazy { sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) }

    private val motion = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            triggerArmed = false
            lastMovement = SystemClock.elapsedRealtime()
            paused = false
            VehicleStore.report("检测到运动，恢复蓝牙扫描")
        }
    }

    private val bluetoothState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) != BluetoothAdapter.STATE_ON) {
                VehicleStore.report("蓝牙已关闭，车辆功能已停止；重新开启后请确认车辆状态")
                stopSelf()
            }
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch {
                if (!scanning || !hasPermissions()) return@launch
                val now = SystemClock.elapsedRealtime()
                val sampleTime = result.timestampNanos / 1_000_000
                if (now - sampleTime !in 0..5000) return@launch
                if (discovery) {
                    val item = VehicleDevice(result.device.address,
                        result.scanRecord?.deviceName ?: result.device.name ?: "未命名设备", result.rssi)
                    devices.value = (devices.value.filterNot { it.address == item.address } + item)
                        .sortedByDescending { it.rssi }.take(40)
                    return@launch
                }
                if (result.device.address != config.address) return@launch
                rssi.value = result.rssi
                val action = controller?.observe(result.rssi, sampleTime) ?: return@launch
                actionJob = scope.launch {
                    val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GKD:VehicleWidget")
                    wakeLock.acquire(10_000)
                    try {
                        VehicleStore.report("靠近/远离条件已满足，准备点击 $action 小组件")
                        val dispatched = VehicleWidgets.click(config, action)
                        controller?.complete(dispatched)
                        VehicleStore.report(if (dispatched) "$action 点击已发出，尚未确认车端结果"
                            else "$action 未发出，需要重新确认车辆状态")
                        if (!dispatched) stopSelf()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        controller?.complete(false)
                        VehicleStore.report("小组件执行失败：${e.message}；请重新确认车辆状态")
                        stopSelf()
                    } finally {
                        if (wakeLock.isHeld) wakeLock.release()
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                VehicleStore.report("蓝牙扫描失败($errorCode)，已停止，请检查权限和系统限制")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ContextCompat.registerReceiver(this, bluetoothState, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED)
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stop()
            return START_NOT_STICKY
        }
        // No persisted enabled flag: process death/reboot cannot guess the door state.
        if (intent == null || !hasPermissions() || adapter?.isEnabled != true) {
            VehicleStore.report("缺少蓝牙权限或蓝牙未开启")
            stopSelf()
            return START_NOT_STICKY
        }
        discovery = intent.action == "discover"
        config = VehicleStore.config.value
        if (!discovery && (!config.ready || A11yService.instance == null)) {
            VehicleStore.report("请先绑定小组件、标记按钮并开启 GKD 无障碍服务")
            stopSelf()
            return START_NOT_STICKY
        }
        showNotification()
        scanJob?.cancel()
        stopScan()
        actionJob?.cancel()
        controller = if (discovery) null else ProximityController(config.unlockRssi, config.lockRssi).apply {
            confirmState(VehicleState.valueOf(intent.getStringExtra("initialState") ?: "UNKNOWN"))
        }
        lastMovement = SystemClock.elapsedRealtime()
        paused = false
        running.value = !discovery
        discovering.value = discovery
        rssi.value = null
        if (discovery) devices.value = emptyList()
        VehicleStore.report(if (discovery) "搜索附近蓝牙设备（30 秒）" else "车辆功能已启动：先远离，再靠近；按钮点击不代表车端确认")
        scanJob = scope.launch {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (true) {
                if (discovery && SystemClock.elapsedRealtime() >= deadline) {
                    VehicleStore.report("设备搜索结束，请选择车辆蓝牙设备")
                    stopSelf()
                    break
                }
                val stationary = !discovery && config.stationaryPause && controller?.canPauseStationary() == true &&
                    SystemClock.elapsedRealtime() - lastMovement >= 30_000 && wakeSensor != null
                if (stationary && !triggerArmed) triggerArmed = sensors.requestTriggerSensor(motion, wakeSensor)
                val shouldPause = stationary && triggerArmed
                if (shouldPause != paused) {
                    paused = shouldPause
                    if (paused) {
                        stopScan()
                        controller?.resetSamples()
                        VehicleStore.report("已静止且处于上锁阶段，暂停扫描；运动传感器负责恢复")
                    }
                }
                if (paused) {
                    delay(1000)
                    continue
                }
                startScan()
                // Never rest while waiting to complete a departure or a widget click.
                val pulse = !discovery && config.intermittent && controller?.canPauseStationary() == true
                delay(if (pulse) 6000 else 30_000)
                stopScan()
                if (pulse) delay(3000)
            }
        }
        return START_NOT_STICKY
    }

    private fun startScan() {
        if (scanning) return
        val filters = if (discovery) emptyList() else listOf(ScanFilter.Builder().setDeviceAddress(config.address).build())
        val lowPower = !discovery && config.screenOffLowPower && !power.isInteractive && controller?.canPauseStationary() == true
        val settings = ScanSettings.Builder().setScanMode(if (lowPower) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_BALANCED).build()
        try {
            val scanner = adapter?.bluetoothLeScanner ?: throw IllegalStateException("蓝牙扫描器不可用")
            scanning = true
            scanner.startScan(filters, settings, callback)
        } catch (e: Exception) {
            scanning = false
            VehicleStore.report("无法扫描：${e.message}")
            stopSelf()
        }
    }

    private fun stopScan() {
        if (scanning) {
            scanning = false
            try { adapter?.bluetoothLeScanner?.stopScan(callback) }
            catch (_: SecurityException) { /* Permission may have been revoked while scanning. */ }
        }
    }

    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("byd_vehicle", "车辆靠近/远离", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 26921, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 26922, Intent(this, VehicleService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "byd_vehicle").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (discovery) "搜索车辆蓝牙" else "靠近解锁 / 远离上锁")
            .setContentText("点击查看配置；重启后需重新确认车辆状态")
            .setContentIntent(open).setOngoing(true).addAction(0, "停止", stop).build()
        startForeground(26921, notification)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val magnitude = sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
        gravity = gravity * 0.8f + magnitude * 0.2f
        if (abs(magnitude - gravity) > 0.8f) {
            lastMovement = SystemClock.elapsedRealtime()
            if (paused) VehicleStore.report("检测到运动，恢复蓝牙扫描")
            paused = false
            if (triggerArmed) sensors.cancelTriggerSensor(motion, wakeSensor)
            triggerArmed = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        stopScan()
        sensors.unregisterListener(this)
        if (triggerArmed) sensors.cancelTriggerSensor(motion, wakeSensor)
        unregisterReceiver(bluetoothState)
        instance = null
        running.value = false
        discovering.value = false
        super.onDestroy()
    }

    companion object {
        private var instance: VehicleService? = null
        val running: StateFlow<Boolean>
            field = MutableStateFlow(false)
        val discovering: StateFlow<Boolean>
            field = MutableStateFlow(false)
        val devices: StateFlow<List<VehicleDevice>>
            field = MutableStateFlow(emptyList())
        val rssi: StateFlow<Int?>
            field = MutableStateFlow(null)

        fun permissions() = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

        fun hasPermissions() = Build.VERSION.SDK_INT >= 31 &&
            permissions().all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }

        fun start(state: VehicleState) = ContextCompat.startForegroundService(app,
            Intent(app, VehicleService::class.java).putExtra("initialState", state.name))

        fun discover() = ContextCompat.startForegroundService(app, Intent(app, VehicleService::class.java).setAction("discover"))
        fun stop() {
            instance?.run {
                scanJob?.cancel()
                actionJob?.cancel()
                stopScan()
                controller?.confirmState(VehicleState.UNKNOWN)
                VehicleStore.report("车辆功能已停止")
                stopSelf()
            }
        }
    }
}
