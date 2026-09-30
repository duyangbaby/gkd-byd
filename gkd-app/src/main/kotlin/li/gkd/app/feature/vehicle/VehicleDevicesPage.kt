package li.gkd.app.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.ui.component.GkSettingItem

@Composable
fun VehicleDevicesPage() {
    val vm = viewModel<VehicleSettingsVm>()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val devices by VehicleService.devices.collectAsStateWithLifecycle()
    val discovering by VehicleService.discovering.collectAsStateWithLifecycle()
    val running by VehicleService.running.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable(config.address) { mutableStateOf(config.address) }
    var selected by remember { mutableStateOf<VehicleDevice?>(null) }
    GkVehiclePage("车辆蓝牙") {
        GkVehicleSignal(Modifier.padding(16.dp))
        GkVehicleSection("已绑定控车 BLE", config.address.ifEmpty { "尚未绑定。搜索后先确认名称和地址。" })
        if (config.address.isNotEmpty()) GkSettingItem(config.deviceName.ifEmpty { "控车蓝牙" }, config.address, imageVector = null)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::discover, enabled = !running && !discovering) { Text("搜索附近设备") }
                if (discovering) TextButton(onClick = VehicleService::stop) { Text("停止搜索") }
            }
            Text(if (running) "请先暂停车辆功能再搜索。" else if (discovering) "搜索中，最多 30 秒…" else "搜索不会配对，也不会发送控车指令。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text("BYD 名称") })
                FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text("全部设备") })
            }
        }
        val visible = devices.filter { showAll || it.name.contains("BYD", true) || it.name.contains("比亚迪") }
        if (visible.isEmpty()) GkVehicleSection("暂无设备", if (showAll) "确认车辆附近有 BLE 广播，并检查附近设备权限。" else "车辆可能使用其他名称，可切换到全部设备。名称筛选不能证明设备身份。")
        visible.forEach { device ->
            GkSettingItem(device.name, "${device.address} · ${device.rssi} dBm", onClick = { selected = device })
        }
        GkSettingItem("手动填写地址", "适用于已知控车 BLE 地址", onClick = { manual = !manual })
        if (manual) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(address, { address = it }, label = { Text("蓝牙 MAC 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.selectDevice(address.trim(), "手动绑定设备") }) { Text("保存地址") }
        }
    }
    selected?.let { device ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("绑定控车蓝牙？") },
            text = { Text("${device.name}\n${device.address}\n\n请确认它是车辆控车 BLE，不能用多媒体蓝牙代替。绑定后需重新实测靠近 / 远离。") },
            confirmButton = { TextButton(onClick = { vm.selectDevice(device.address, device.name); selected = null }) { Text("确认绑定") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("取消") } })
    }
}
