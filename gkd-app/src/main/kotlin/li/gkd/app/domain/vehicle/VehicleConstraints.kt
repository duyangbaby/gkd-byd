package li.gkd.app.domain.vehicle

import kotlinx.serialization.Serializable

@Serializable
data class VehicleConstraintSettings(
    val wifiEnabled: Boolean = false,
    val wifiNames: List<String> = emptyList(),
    val bluetoothEnabled: Boolean = false,
    val bluetoothAddresses: List<String> = emptyList(),
    val carBluetoothEnabled: Boolean = false,
    val carBluetoothAddress: String = "",
    val carBluetoothName: String = "",
)

data class VehicleEnvironmentSnapshot(
    val wifiConnected: Boolean = false,
    val wifiName: String? = null,
    val connectedBluetooth: Set<String> = emptySet(),
)

data class VehicleConstraintDecision(
    val paused: Boolean = false,
    val unlockAllowed: Boolean = true,
    val reason: String? = null,
)

object VehicleConstraints {
    fun evaluate(settings: VehicleConstraintSettings, environment: VehicleEnvironmentSnapshot): VehicleConstraintDecision {
        fun connected(address: String) = address.isNotEmpty() && environment.connectedBluetooth.any { it.equals(address, true) }
        if (settings.bluetoothEnabled && settings.bluetoothAddresses.any(::connected))
            return VehicleConstraintDecision(true, false, "已连接制约蓝牙，暂停控车蓝牙扫描")
        if (settings.carBluetoothEnabled && connected(settings.carBluetoothAddress))
            return VehicleConstraintDecision(true, false, "已连接车机多媒体蓝牙，暂停控车蓝牙扫描；断开后恢复")
        if (!settings.wifiEnabled || settings.wifiNames.isEmpty() || !environment.wifiConnected) return VehicleConstraintDecision()
        if (environment.wifiName == null) return VehicleConstraintDecision(false, false,
            "无法读取当前 Wi-Fi 名称，请检查定位权限和系统定位开关")
        if (environment.wifiName !in settings.wifiNames) return VehicleConstraintDecision()
        return VehicleConstraintDecision(true, false, "已连接制约 Wi-Fi ${environment.wifiName}，暂停控车蓝牙扫描；断开后恢复")
    }
}
