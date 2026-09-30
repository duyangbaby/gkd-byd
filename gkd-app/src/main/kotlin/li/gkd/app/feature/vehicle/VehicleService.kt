package li.gkd.app.feature.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
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
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.app.app
import li.gkd.app.domain.vehicle.ProximityController
import li.gkd.app.domain.vehicle.VehicleState
import li.gkd.app.domain.vehicle.VehiclePowerState
import li.gkd.app.domain.vehicle.VehiclePowerController
import li.gkd.app.domain.vehicle.VehicleCommand
import li.gkd.app.domain.vehicle.VehicleSignalFallback
import li.gkd.app.domain.vehicle.VehicleConstraints
import li.gkd.app.domain.vehicle.VehicleConstraintDecision
import li.gkd.app.domain.vehicle.VehicleEnvironmentSnapshot
import li.gkd.app.domain.vehicle.VehicleScanTiming
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
    private var powerController: VehiclePowerController? = null
    private lateinit var config: VehicleConfig
    private var discovery = false
    private var scanning = false
    private var paused = false
    private var triggerArmed = false
    private var lastMovement = SystemClock.elapsedRealtime()
    private var gravity = 9.81f
    private var pocket = false
    private var fallback = VehicleSignalFallback()
    private var scanWindowUntil = 0L
    private var scanRestUntil = 0L
    private var scanPendingIntent: PendingIntent? = null
    private var scanPulse = false
    private var pulseWakeLock: PowerManager.WakeLock? = null
    private var scanCycle = 0
    private var scanEpoch = 0L
    private var sensorsActive = false
    private var constraint = VehicleConstraintDecision()
    private val wakeEvents = Channel<Unit>(Channel.CONFLATED)
    private var environmentMonitor: VehicleEnvironment? = null
    private var lastSignalLog = 0L
    private val gatt by lazy { VehicleGatt(this, scope, { value, time ->
        if (value !in -127..-1) return@VehicleGatt
        fallback.sample(time)
        consumeRssi(value, time, "GATT")
    }, { mode -> signal.value = signal.value.copy(mode = mode) }) }
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val adapter get() = getSystemService(BluetoothManager::class.java).adapter
    private val wakeSensor by lazy { sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) }

    private val motion = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            triggerArmed = false
            lastMovement = SystemClock.elapsedRealtime()
            paused = false
            signal.value = signal.value.copy(mode = "运动恢复，等待扫描器启动", blocked = null)
            VehicleStore.report("检测到运动，恢复蓝牙扫描")
            wakeEvents.trySend(Unit)
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

    private val screenState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!discovery && ::config.isInitialized && config.scanMode == VehicleScanMode.MIXED)
                VehicleStore.report(if (intent.action == Intent.ACTION_SCREEN_ON)
                    "检测到亮屏，停止 GATT 重连，启动低功耗扫描" else "检测到息屏，停止低功耗扫描，启动 GATT 重连")
            wakeEvents.trySend(Unit)
        }
    }

    private fun acceptScan(result: ScanResult) {
        if (!scanning || !hasPermissions()) return
        val now = SystemClock.elapsedRealtime()
        val sampleTime = result.timestampNanos / 1_000_000
        if (now - sampleTime !in 0..5000) return
        if (discovery) {
            val item = VehicleDevice(result.device.address,
                result.scanRecord?.deviceName ?: result.device.name ?: "未命名设备", result.rssi)
            devices.value = (devices.value.filterNot { it.address == item.address } + item)
                .sortedByDescending { it.rssi }.take(40)
        } else if (result.device.address.equals(config.address, true)) consumeRssi(result.rssi, sampleTime, "BLE 广播")
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch { acceptScan(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                VehicleStore.report("蓝牙扫描失败($errorCode)，已停止，请检查权限和系统限制")
                stopSelf()
            }
        }
    }

    private fun consumeRssi(value: Int, sampleTime: Long, source: String) {
        if (value !in -127..-1) return
        rssi.value = value
        signal.value = signal.value.copy(rssi = value, sampledAt = sampleTime, source = source,
            samples = signal.value.samples + 1, blocked = null)
        constraint = evaluateConstraint()
        if (constraint.paused) { updateDecision(constraint.reason); wakeEvents.trySend(Unit); return }
        if (actionJob?.isActive == true) { updateDecision("正在执行控车指令"); return }
        if (config.lockscreenOnly && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            controller?.resetSamples()
            powerController?.resetSamples()
            updateDecision("已开启仅锁屏控车；手机未锁屏")
            return
        }
        val powerAction = powerController?.observe(value, sampleTime, controller?.state ?: VehicleState.UNKNOWN)
        val action = powerAction ?: controller?.observe(value, sampleTime, pocket, constraint.unlockAllowed)
        updateDecision(constraint.reason)
        if (lastSignalLog == 0L || sampleTime - lastSignalLog >= 5000) {
            lastSignalLog = sampleTime
            VehicleStore.report("[$source] $value dBm · ${signal.value.decision?.reason ?: "等待判断"}")
        }
        if (action == null) return
        actionJob = scope.launch {
            val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GKD:VehicleCommand")
            wakeLock.acquire(if (config.controlMethod == VehicleControlMethod.AUTO) 30_000 else 10_000)
            try {
                VehicleStore.report("条件已满足，通过${config.controlMethod.label}执行${action.label}")
                val dispatched = VehicleActions.execute(config, action)
                if (powerAction != null) powerController?.complete(dispatched) else controller?.complete(dispatched)
                if (dispatched && action == VehicleCommand.UNLOCK) powerController?.afterUnlock(sampleTime)
                updateDecision(if (dispatched) null else "指令未发出，请重新确认车辆状态")
                if (dispatched && action == VehicleCommand.POWER_OFF && config.closeWindowsAfterOff) {
                    delay(2000)
                    val window = VehicleActions.execute(config, VehicleCommand.CLOSE_WINDOWS)
                    VehicleStore.report(if (window) "关窗指令已发出" else "关窗指令未发出")
                }
                VehicleStore.report(if (dispatched) "${action.label}指令已发出，尚未确认车端结果"
                    else "${action.label}未发出，需要重新确认车辆状态")
                if (!dispatched) stopSelf()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                controller?.complete(false)
                powerController?.complete(false)
                VehicleStore.report("控车失败：${e.message}；请重新确认车辆状态")
                stopSelf()
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                wakeEvents.trySend(Unit)
            }
        }
    }

    private fun updateDecision(blocked: String? = null) {
        signal.value = signal.value.copy(decision = controller?.status(SystemClock.elapsedRealtime()), blocked = blocked)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ContextCompat.registerReceiver(this, bluetoothState, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, screenState, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun setSensors(active: Boolean) {
        if (sensorsActive == active) return
        sensorsActive = active
        sensors.unregisterListener(this)
        if (active) {
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun evaluateConstraint() = if (discovery) VehicleConstraintDecision() else
        VehicleConstraints.evaluate(config.constraints, environment.value)

    private suspend fun waitForEvent(timeoutMs: Long = 1000) {
        withTimeoutOrNull(timeoutMs.coerceAtLeast(1)) { wakeEvents.receive() }
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
        environmentMonitor?.stop()
        environmentMonitor = null
        config = VehicleStore.config.value
        if (!discovery && !VehicleActions.ready(config)) {
            VehicleStore.report("请先完成所选控车方式的配置")
            stopSelf()
            return START_NOT_STICKY
        }
        showNotification()
        scanJob?.cancel()
        stopScan()
        gatt.stop()
        actionJob?.cancel()
        controller = if (discovery) null else ProximityController(config.unlockRssi, config.lockRssi,
            config.unlockCount, config.lockCount, config.unlockOffset, config.lockOffset,
            config.cooldownSeconds * 1000L, config.unlockEnabled, config.lockEnabled,
            config.unlockPocketOffset, config.lockPocketOffset).apply {
            confirmState(VehicleState.valueOf(intent.getStringExtra("initialState") ?: "UNKNOWN"))
        }
        powerController = if (discovery) null else VehiclePowerController(
            VehiclePowerState.valueOf(intent.getStringExtra("initialPower") ?: "UNKNOWN"), config.powerOnEnabled,
            config.powerOffEnabled, config.powerOnRssi, config.powerOffRssi, config.powerConfirmations, config.powerOnDelaySeconds * 1000L)
        lastMovement = SystemClock.elapsedRealtime()
        paused = false
        if (triggerArmed) sensors.cancelTriggerSensor(motion, wakeSensor)
        triggerArmed = false
        setSensors(!discovery)
        constraint = VehicleConstraintDecision()
        environment.value = VehicleEnvironmentSnapshot()
        if (!discovery && (config.constraints.wifiEnabled || config.constraints.bluetoothEnabled || config.constraints.carBluetoothEnabled)) {
            environmentMonitor = VehicleEnvironment(this, scope) { snapshot ->
                environment.value = snapshot
                wakeEvents.trySend(Unit)
            }.also { it.start() }
        }
        running.value = !discovery
        discovering.value = discovery
        rssi.value = null
        signal.value = VehicleSignal(mode = if (discovery) "搜索附近设备" else "正在启动扫描器",
            decision = controller?.status(SystemClock.elapsedRealtime()))
        fallback = VehicleSignalFallback()
        scanWindowUntil = 0
        scanRestUntil = 0
        lastSignalLog = 0
        scanCycle = 0
        if (discovery) devices.value = emptyList()
        VehicleStore.report(if (discovery) "搜索附近蓝牙设备（30 秒）" else "车辆功能已启动：先远离，再靠近；按钮点击不代表车端确认")
        scanJob = scope.launch {
            val timing = VehicleScanTiming(config.gattSleepSeconds)
            var constrained = false
            var lastConstraintReason: String? = null
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (true) {
                if (discovery && SystemClock.elapsedRealtime() >= deadline) {
                    VehicleStore.report("设备搜索结束，请选择车辆蓝牙设备")
                    stopSelf()
                    break
                }
                constraint = evaluateConstraint()
                if (constraint.reason != lastConstraintReason) {
                    if (constraint.reason != null) VehicleStore.report(constraint.reason!!)
                    else if (lastConstraintReason != null) VehicleStore.report("制约条件已解除，恢复蓝牙服务")
                    lastConstraintReason = constraint.reason
                }
                if (constraint.paused) {
                    if (!constrained) {
                        stopScan()
                        gatt.stop()
                        controller?.resetSamples()
                        powerController?.resetSamples()
                        setSensors(false)
                        if (triggerArmed) sensors.cancelTriggerSensor(motion, wakeSensor)
                        triggerArmed = false
                        paused = false
                        constrained = true
                    }
                    signal.value = signal.value.copy(mode = "制约暂停扫描", blocked = constraint.reason)
                    updateDecision(constraint.reason)
                    wakeEvents.receive()
                    continue
                }
                if (constrained) {
                    constrained = false
                    lastMovement = SystemClock.elapsedRealtime()
                    scanWindowUntil = 0
                    scanRestUntil = 0
                    setSensors(true)
                }
                val stationary = !discovery && config.stationaryPause && canRest() &&
                    SystemClock.elapsedRealtime() - lastMovement >= config.stationarySeconds * 1000L && wakeSensor != null
                if (stationary && !triggerArmed) triggerArmed = sensors.requestTriggerSensor(motion, wakeSensor)
                val shouldPause = stationary && triggerArmed
                if (shouldPause != paused) {
                    paused = shouldPause
                    if (paused) {
                        stopScan()
                        gatt.stop()
                        setSensors(false)
                        controller?.resetSamples()
                        powerController?.resetSamples()
                        signal.value = signal.value.copy(mode = "静止暂停扫描", blocked = "处于上锁阶段且静止，等待运动恢复")
                        VehicleStore.report("已静止且处于上锁阶段，暂停扫描；运动传感器负责恢复")
                    }
                }
                if (paused) {
                    updateDecision(signal.value.blocked)
                    wakeEvents.receive()
                    continue
                }
                if (!discovery) setSensors(true)
                val now = SystemClock.elapsedRealtime()
                val requestedGatt = !discovery && (config.scanMode == VehicleScanMode.GATT ||
                    (config.scanMode == VehicleScanMode.MIXED && !power.isInteractive))
                val useGatt = fallback.useGatt(now, requestedGatt, config.scanMode == VehicleScanMode.MIXED && config.gattFallbackEnabled)
                if (useGatt) {
                    stopScan()
                    scanWindowUntil = 0
                    scanRestUntil = 0
                    adapter?.let { gatt.start(it, config.address, config.readIntervalMs, config.gattSleepSeconds) }
                    waitForEvent()
                    continue
                }
                val leavingGatt = gatt.active
                gatt.stop()
                if (leavingGatt && requestedGatt) VehicleStore.report("GATT 15 秒未收到有效信号，退回 BLE 扫描 30 秒")
                // Re-evaluate screen, motion and door state every second, without restarting a continuous scan.
                val nearby = signal.value.sampledAt?.let { now - it in 0..5000 } == true
                val pulse = !discovery && config.intermittent && canRest() && !nearby
                if (!pulse) {
                    scanWindowUntil = 0
                    scanRestUntil = 0
                    startScan(false)
                } else if (scanRestUntil > now) {
                    signal.value = signal.value.copy(mode = "分段扫描休息中", blocked = "静默 ${timing.sleepSeconds} 秒后继续扫描")
                } else if (scanning && scanWindowUntil != 0L && now >= scanWindowUntil) {
                    stopScan()
                    scanRestUntil = now + timing.sleepMs
                    signal.value = signal.value.copy(mode = "分段扫描休息中", blocked = "静默 ${timing.sleepSeconds} 秒后继续扫描")
                } else {
                    if (!scanning || scanWindowUntil == 0L) {
                        scanWindowUntil = now + timing.scanWindowMs
                        scanCycle++
                        if (timing.logCycle(scanCycle)) VehicleStore.report("正在进行第${scanCycle}次低功耗扫描[${if (pocket) "口袋" else "手持"}][${if (power.isInteractive) "C" else "P"}]，扫 ${timing.scanWindowMs / 1000.0} 秒 / 静默 ${timing.sleepSeconds} 秒")
                    }
                    scanRestUntil = 0
                    startScan(true)
                }
                updateDecision(constraint.reason ?: signal.value.blocked)
                val transition = if (pulse) (if (scanning) scanWindowUntil else scanRestUntil) - SystemClock.elapsedRealtime() else 1000
                waitForEvent(minOf(1000, transition.coerceAtLeast(1)))
            }
        }
        return START_NOT_STICKY
    }

    private fun startScan(pulse: Boolean) {
        val pending = !discovery && config.screenOffLowPower && !power.isInteractive
        if (scanning && scanPulse == pulse && (scanPendingIntent != null) == pending) return
        stopScan()
        val filters = if (discovery) emptyList() else listOf(ScanFilter.Builder().setDeviceAddress(config.address.uppercase()).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT).setReportDelay(0).build()
        try {
            val scanner = adapter?.bluetoothLeScanner ?: throw IllegalStateException("蓝牙扫描器不可用")
            scanning = true
            scanPulse = pulse
            if (pending) {
                scanEpoch = SystemClock.elapsedRealtimeNanos()
                val intent = Intent(this, VehicleScanReceiver::class.java).setAction("li.gkd.app.VEHICLE_SCAN")
                    .setData(Uri.parse("gkd://vehicle-scan/$scanEpoch"))
                val target = PendingIntent.getBroadcast(this, 26923, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                scanPendingIntent = target
                val error = scanner.startScan(filters, settings, target)
                check(error == 0) { "蓝牙扫描失败($error)" }
            } else scanner.startScan(filters, settings, callback)
            if (pulse) {
                pulseWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GKD:VehicleScanPulse").apply {
                    acquire((scanWindowUntil - SystemClock.elapsedRealtime()).coerceAtLeast(1) + 2000)
                }
            }
            signal.value = signal.value.copy(mode = if (pulse) "低功耗分段扫描中" else "BLE 连续扫描中", blocked = constraint.reason)
        } catch (e: Exception) {
            stopScan()
            VehicleStore.report("无法扫描：${e.message}")
            stopSelf()
        }
    }

    private fun stopScan() {
        if (scanning) {
            scanning = false
            try {
                val scanner = adapter?.bluetoothLeScanner
                scanPendingIntent?.let { scanner?.stopScan(it) } ?: scanner?.stopScan(callback)
            } catch (_: SecurityException) { /* Permission may have been revoked while scanning. */ }
        }
        scanPendingIntent?.cancel()
        scanPendingIntent = null
        pulseWakeLock?.let { if (it.isHeld) it.release() }
        pulseWakeLock = null
    }

    private fun canRest() = controller?.canPauseStationary() == true && powerController?.needsDepartureMonitoring() != true

    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("byd_vehicle", "车辆靠近/远离", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 26921, Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("gkd://page?tab=4")), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 26922, Intent(this, VehicleService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "byd_vehicle").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (discovery) "搜索车辆蓝牙" else "靠近解锁 / 远离上锁")
            .setContentText("点击查看配置；重启后需重新确认车辆状态")
            .setContentIntent(open).setOngoing(true).addAction(0, "停止", stop).build()
        startForeground(26921, notification)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            pocket = event.values[0] < event.sensor.maximumRange
            return
        }
        val magnitude = sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
        gravity = gravity * 0.8f + magnitude * 0.2f
        if (abs(magnitude - gravity) > 1.2f - configSensitivity() * 0.2f) {
            lastMovement = SystemClock.elapsedRealtime()
            val wasPaused = paused
            if (paused) VehicleStore.report("检测到运动，恢复蓝牙扫描")
            paused = false
            if (triggerArmed) sensors.cancelTriggerSensor(motion, wakeSensor)
            triggerArmed = false
            if (wasPaused) wakeEvents.trySend(Unit)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    private fun configSensitivity() = if (::config.isInitialized) config.movementSensitivity else 2
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        environmentMonitor?.stop()
        environmentMonitor = null
        scope.cancel()
        stopScan()
        gatt.stop()
        sensors.unregisterListener(this)
        if (triggerArmed) sensors.cancelTriggerSensor(motion, wakeSensor)
        unregisterReceiver(bluetoothState)
        unregisterReceiver(screenState)
        instance = null
        running.value = false
        discovering.value = false
        signal.value = signal.value.copy(mode = "车辆功能已暂停", blocked = null)
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
        val environment: StateFlow<VehicleEnvironmentSnapshot>
            field = MutableStateFlow(VehicleEnvironmentSnapshot())
        val signal: StateFlow<VehicleSignal>
            field = MutableStateFlow(VehicleSignal())

        fun receiveScan(intent: Intent) {
            val service = instance ?: return
            if (!service.scanning || service.scanPendingIntent == null || intent.data?.lastPathSegment != service.scanEpoch.toString()) return
            val error = intent.getIntExtra(android.bluetooth.le.BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
            if (error != 0) { service.callback.onScanFailed(error); return }
            @Suppress("DEPRECATION")
            val results = intent.getParcelableArrayListExtra<ScanResult>(android.bluetooth.le.BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT).orEmpty()
            service.scope.launch { results.forEach(service::acceptScan) }
        }

        fun permissions() = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

        fun hasPermissions() = Build.VERSION.SDK_INT >= 31 &&
            permissions().all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }

        fun start(state: VehicleState, power: VehiclePowerState = VehiclePowerState.UNKNOWN) = ContextCompat.startForegroundService(app,
            Intent(app, VehicleService::class.java).putExtra("initialState", state.name).putExtra("initialPower", power.name))

        fun discover() = ContextCompat.startForegroundService(app, Intent(app, VehicleService::class.java).setAction("discover"))
        fun stop() {
            instance?.run {
                scanJob?.cancel()
                actionJob?.cancel()
                stopScan()
                gatt.stop()
                controller?.confirmState(VehicleState.UNKNOWN)
                VehicleStore.report("车辆功能已停止")
                stopSelf()
            }
        }
    }
}
