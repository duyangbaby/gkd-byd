package li.gkd.app.feature.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.app
import li.gkd.app.domain.vehicle.VehicleConstraintSettings

@Serializable
enum class VehicleScanMode(val label: String) { SCAN("低功耗扫描"), GATT("GATT 轮询"), MIXED("混合扫描") }

@Serializable
data class VehicleConfig(
    val vehicleName: String = "我的比亚迪",
    val controlMethod: VehicleControlMethod = VehicleControlMethod.WIDGET,
    val deviceName: String = "",
    val guideCompleted: Boolean = false,
    val backgroundConfirmed: Boolean = false,
    val lockTestConfirmed: Boolean = false,
    val proximityTestConfirmed: Boolean = false,
    val address: String = "",
    val widgetId: Int = -1,
    val provider: String = "",
    val providerVersion: Long = -1,
    val unlockViewId: Int = -1,
    val lockViewId: Int = -1,
    val powerOnViewId: Int = -1,
    val powerOffViewId: Int = -1,
    val windowViewId: Int = -1,
    val secondaryWidgetId: Int = -1,
    val secondaryProvider: String = "",
    val secondaryProviderVersion: Long = -1,
    val unlockRssi: Int = -87,
    val lockRssi: Int = -82,
    val unlockEnabled: Boolean = true,
    val lockEnabled: Boolean = true,
    val unlockCount: Int = 4,
    val lockCount: Int = 4,
    val unlockOffset: Int = 2,
    val lockOffset: Int = 2,
    val unlockPocketOffset: Int = 6,
    val lockPocketOffset: Int = 8,
    val cooldownSeconds: Int = 15,
    val lockscreenOnly: Boolean = false,
    val intermittent: Boolean = true,
    val stationaryPause: Boolean = true,
    val screenOffLowPower: Boolean = true,
    val scanMode: VehicleScanMode = VehicleScanMode.MIXED,
    val readIntervalMs: Int = 500,
    val gattSleepSeconds: Int = 2,
    val gattFallbackEnabled: Boolean = false,
    val constraints: VehicleConstraintSettings = VehicleConstraintSettings(),
    val powerOnEnabled: Boolean = false,
    val powerOffEnabled: Boolean = false,
    val powerOnRssi: Int = -82,
    val powerOffRssi: Int = -95,
    val powerConfirmations: Int = 6,
    val powerOnDelaySeconds: Int = 20,
    val closeWindowsAfterOff: Boolean = false,
    val clickDelayMs: Int = 300,
    val stationarySeconds: Int = 30,
    val movementSensitivity: Int = 2,
) {
    val ready get() = address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) &&
        widgetId >= 0 && provider.isNotEmpty() && unlockViewId != -1 && lockViewId != -1 &&
        unlockViewId != lockViewId && unlockRssi in -110..-30 && lockRssi in -110..-30
}

object VehicleStore {
    private val prefs by lazy { app.getSharedPreferences("byd_vehicle", 0) }
    private val json = Json { ignoreUnknownKeys = true }
    val config: StateFlow<VehicleConfig>
        field = MutableStateFlow(load())
    val status: StateFlow<String>
        field = MutableStateFlow("车辆功能未启动")
    val logs: StateFlow<List<String>>
        field = MutableStateFlow(emptyList())

    private fun load(): VehicleConfig = try {
        json.decodeFromString<VehicleConfig>(prefs.getString("config", null) ?: "{}")
    } catch (_: IllegalArgumentException) {
        VehicleConfig()
    }

    fun update(change: (VehicleConfig) -> VehicleConfig) {
        // Configuration changes invalidate proximity evidence and require explicit re-arming.
        VehicleService.stop()
        val value = change(config.value)
        prefs.edit().putString("config", json.encodeToString(value)).apply()
        config.value = value
    }

    fun report(message: String) {
        status.value = message
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
        logs.value = (listOf("$time $message") + logs.value).take(80)
    }

    fun clearLogs() { logs.value = emptyList() }

    fun exportConfig() = json.encodeToString(config.value)

    fun importConfig(text: String) {
        val value = json.decodeFromString<VehicleConfig>(text)
        require(value.unlockRssi in -110..-30 && value.lockRssi in -110..-30 &&
            value.unlockCount in 1..10 && value.lockCount in 1..10 && value.unlockOffset in 0..8 && value.lockOffset in 0..8 &&
            value.unlockPocketOffset in 0..20 && value.lockPocketOffset in 0..20 && value.cooldownSeconds in 0..60 &&
            value.clickDelayMs in 0..1500 && value.stationarySeconds in 20..180 && value.movementSensitivity in 1..5)
        require(value.readIntervalMs in 200..1000 && value.gattSleepSeconds in 1..5)
        require(value.constraints.wifiNames.size <= 30 &&
            value.constraints.wifiNames.all { it.isNotBlank() && it.length <= 32 })
        require(value.constraints.bluetoothAddresses.size <= 30 &&
            (value.constraints.bluetoothAddresses + value.constraints.carBluetoothAddress.takeIf { it.isNotEmpty() }.let(::listOfNotNull))
                .all { it.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) })
        require(value.constraints.bluetoothAddresses.none { it.equals(value.address, true) || it.equals(value.constraints.carBluetoothAddress, true) })
        require(value.constraints.carBluetoothAddress.isEmpty() || !value.constraints.carBluetoothAddress.equals(value.address, true))
        require(value.powerOnRssi in -110..-20 && value.powerOffRssi in -110..-20 &&
            value.powerOffRssi < value.powerOnRssi && value.powerConfirmations in 1..10 && value.powerOnDelaySeconds in 0..60)
        require(value.address.isEmpty() || value.address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")))
        // AppWidget IDs and provider view IDs belong to the device on which they were bound.
        val old = config.value
        update { value.copy(widgetId = -1, provider = "", providerVersion = -1, unlockViewId = -1, lockViewId = -1,
            powerOnViewId = -1, powerOffViewId = -1, secondaryWidgetId = -1, secondaryProvider = "", secondaryProviderVersion = -1,
            windowViewId = -1, guideCompleted = false, backgroundConfirmed = false, lockTestConfirmed = false, proximityTestConfirmed = false) }
        listOf(old.widgetId, old.secondaryWidgetId).filter { it >= 0 }.distinct().forEach(VehicleWidgets.host::deleteAppWidgetId)
        report("配置已导入；请重新绑定小组件并完成实测")
    }
}
