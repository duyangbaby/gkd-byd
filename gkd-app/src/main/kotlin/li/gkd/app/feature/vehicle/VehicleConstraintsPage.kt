package li.gkd.app.feature.vehicle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.MainViewModel
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextSwitch

@Composable
fun VehicleConstraintsPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<VehicleConstraintsVm>()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val environment by VehicleService.environment.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val currentWifi by vm.wifiName.collectAsStateWithLifecycle()
    val settings = config.constraints
    var wifi by remember { mutableStateOf("") }
    var wifiEditor by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = vm::refresh)
    GkVehiclePage("条件约束") {
        GkVehicleSection("省电制约", "连接指定网络 / 设备时暂停控车蓝牙，保留连接事件监听；断开后恢复。参数修改后需按实车状态重新启动。")
        GkSettingItem("授权与后台运行", "Wi-Fi 名称需要定位权限及系统定位开关；息屏读取还需按系统要求允许后台定位", onClick = {
            mainVm.navigatePage(VehicleSectionRoute(VehicleSection.PERMISSIONS))
        })
        GkVehicleSection("Wi-Fi 制约", "${settings.wifiNames.size} 个网络 · 当前 ${environment.wifiName ?: currentWifi ?: "未连接或名称不可读"}")
        GkTextSwitch(title = "启用 Wi-Fi 制约", checked = settings.wifiEnabled,
            onCheckedChange = { value -> VehicleStore.update { it.copy(constraints = it.constraints.copy(wifiEnabled = value)) } })
        GkVehicleSection("连接即暂停", "选择家里或公司的 Wi-Fi。连接任一所选网络后立即停止扫描和 GATT 轮询，不等待门锁或电源状态；断开后自动恢复。")
        settings.wifiNames.forEach { name -> GkSettingItem(name, "点击移除", onClick = {
            VehicleStore.update { it.copy(constraints = it.constraints.copy(wifiNames = it.constraints.wifiNames - name)) }
        }) }
        GkSettingItem("添加 Wi-Fi", "当前网络或手动填写 SSID，不进行持续 Wi-Fi 扫描", onClick = { wifiEditor = !wifiEditor })
        if (wifiEditor) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(wifi, { wifi = it }, label = { Text("Wi-Fi 名称（SSID）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { if (vm.addWifi(wifi)) { wifi = ""; error = null } else error = "名称须为 1～32 字符，最多添加 30 个网络" }) { Text("添加网络") }
            TextButton(onClick = { currentWifi?.let { vm.addWifi(it) } }, enabled = currentWifi != null) { Text("添加当前连接的 Wi-Fi") }
            error?.let { Text(it) }
        }
        GkVehicleSection("蓝牙制约", "连接任意所选设备时暂停控车扫描；全部断开后恢复。这里选择系统已配对的设备。")
        GkTextSwitch(title = "启用蓝牙制约", checked = settings.bluetoothEnabled,
            onCheckedChange = { value -> VehicleStore.update { it.copy(constraints = it.constraints.copy(bluetoothEnabled = value)) } })
        GkSettingItem("选择制约蓝牙", "已选 ${settings.bluetoothAddresses.size} 个 · 点击选择", onClick = { vm.refresh(); error = null; picker = false })
        settings.bluetoothAddresses.forEach { address ->
            GkSettingItem(devices.find { it.address == address }?.name ?: address,
                if (address in environment.connectedBluetooth) "已连接 · 点击移除" else "点击移除", onClick = {
                    VehicleStore.update { it.copy(constraints = it.constraints.copy(bluetoothAddresses = it.constraints.bluetoothAddresses - address)) }
                })
        }
        GkVehicleSection("车机多媒体蓝牙", "连接车机多媒体蓝牙后立即停止扫描和 GATT 轮询，不等待门锁或电源状态；断开后自动恢复。请选择多媒体蓝牙，而非控车 BLE。")
        GkTextSwitch(title = "启用车机蓝牙", checked = settings.carBluetoothEnabled,
            onCheckedChange = { value -> VehicleStore.update { it.copy(constraints = it.constraints.copy(carBluetoothEnabled = value)) } })
        GkSettingItem("选择车机蓝牙", settings.carBluetoothName.ifEmpty { "尚未绑定" }, onClick = { vm.refresh(); error = null; picker = true })
        if (settings.carBluetoothAddress.isNotEmpty()) GkSettingItem("清除车机绑定", settings.carBluetoothAddress, onClick = {
            VehicleStore.update { it.copy(constraints = it.constraints.copy(carBluetoothEnabled = false, carBluetoothAddress = "", carBluetoothName = "")) }
        })
        GkVehicleSection("连接监听范围", "支持启动时识别已连接音频及 GATT 设备。其他类型设备若未被识别，请断开再连接；运行期间会监听连接和断开。时间与地点制约仍待迁移。")
    }
    picker?.let { car -> AlertDialog(onDismissRequest = { picker = null }, title = { Text(if (car) "选择车机蓝牙" else "选择制约蓝牙") },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            if (devices.isEmpty()) {
                Text("未读取到配对设备，请先授予附近设备权限并在系统蓝牙设置中配对。")
                TextButton(onClick = { permission.launch(VehicleService.permissions()) }) { Text("申请附近设备权限") }
            }
            error?.let { Text(it) }
            devices.forEach { device ->
                val selected = if (car) settings.carBluetoothAddress == device.address else device.address in settings.bluetoothAddresses
                GkSettingItem(device.name + if (selected) " · 已选" else "", device.address, onClick = {
                    error = vm.selectBluetooth(device, car)
                    if (car && error == null) picker = null
                })
            }
            GkPageBottomSpace()
        } }, confirmButton = { TextButton(onClick = { picker = null }) { Text("完成") } }) }
}
