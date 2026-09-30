package li.gkd.app.feature.vehicle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.app
import li.gkd.app.ui.share.BaseViewModel

data class VehiclePairedDevice(val address: String, val name: String)

class VehicleConstraintsVm : BaseViewModel() {
    val devices: StateFlow<List<VehiclePairedDevice>>
        field = MutableStateFlow(emptyList())
    val wifiName: StateFlow<String?>
        field = MutableStateFlow(null)

    @SuppressLint("MissingPermission")
    fun refresh() {
        devices.value = if (VehicleService.hasPermissions()) try {
            app.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.map {
                VehiclePairedDevice(it.address.uppercase(), it.name ?: "未命名设备")
            }?.sortedBy { it.name } ?: emptyList()
        } catch (_: SecurityException) { emptyList() } else emptyList()
        @Suppress("DEPRECATION")
        val info = try { app.getSystemService(WifiManager::class.java).connectionInfo } catch (_: SecurityException) { null }
        wifiName.value = info?.ssid?.takeUnless { it == WifiManager.UNKNOWN_SSID }?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
    }

    fun addWifi(name: String): Boolean {
        val ssid = name.trim()
        if (ssid.isEmpty() || ssid.length > 32) return false
        if (ssid !in VehicleStore.config.value.constraints.wifiNames && VehicleStore.config.value.constraints.wifiNames.size >= 30) return false
        VehicleStore.update { it.copy(constraints = it.constraints.copy(wifiNames = (it.constraints.wifiNames + ssid).distinct())) }
        return true
    }

    fun selectBluetooth(device: VehiclePairedDevice, car: Boolean): String? {
        val config = VehicleStore.config.value
        val settings = config.constraints
        if (!car && device.address !in settings.bluetoothAddresses && settings.bluetoothAddresses.size >= 30) return "最多选择 30 个蓝牙设备"
        if (device.address.equals(config.address, true)) return "不能选择控车 BLE 作为制约设备"
        if (car && settings.bluetoothAddresses.any { it.equals(device.address, true) } ||
            !car && device.address.equals(settings.carBluetoothAddress, true)) return "车机蓝牙与制约蓝牙不能选择相同设备"
        VehicleStore.update {
            it.copy(constraints = if (car) it.constraints.copy(carBluetoothAddress = device.address,
                carBluetoothName = device.name, carBluetoothEnabled = true)
            else it.constraints.copy(bluetoothAddresses = if (device.address in it.constraints.bluetoothAddresses)
                it.constraints.bluetoothAddresses - device.address else it.constraints.bluetoothAddresses + device.address))
        }
        return null
    }
}
