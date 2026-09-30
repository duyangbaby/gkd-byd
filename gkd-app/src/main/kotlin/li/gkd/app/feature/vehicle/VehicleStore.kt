package li.gkd.app.feature.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.app

@Serializable
data class VehicleConfig(
    val address: String = "",
    val widgetId: Int = -1,
    val provider: String = "",
    val providerVersion: Long = -1,
    val unlockViewId: Int = -1,
    val lockViewId: Int = -1,
    val unlockRssi: Int = -80,
    val lockRssi: Int = -90,
    val intermittent: Boolean = true,
    val stationaryPause: Boolean = true,
    val screenOffLowPower: Boolean = true,
) {
    val ready get() = address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) &&
        widgetId >= 0 && provider.isNotEmpty() && unlockViewId != -1 && lockViewId != -1 &&
        unlockViewId != lockViewId && lockRssi < unlockRssi
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
}
