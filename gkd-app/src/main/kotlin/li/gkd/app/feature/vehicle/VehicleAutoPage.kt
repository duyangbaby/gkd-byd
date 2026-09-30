package li.gkd.app.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.MainViewModel
import li.gkd.app.feature.vehicle.auto.BydAccountStore
import li.gkd.app.feature.vehicle.auto.BydAutoVm
import li.gkd.app.ui.component.GkSettingItem

@Composable
fun VehicleAutoPage() {
    val vm = viewModel<BydAutoVm>()
    val account by BydAccountStore.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val realtime by vm.realtime.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("1") }
    GkVehiclePage("Auto 账号与车辆") {
        GkVehicleSection("账号状态", if (account.loggedIn) "已登录 ${account.nickname}" else "未登录")
        GkVehicleSection("当前能力", "大陆账号登录、车辆列表、云端控车及车况查询。Auto 蓝牙钥匙、扫码登录和境外账号尚未接入。登录可能使其他设备的会话失效。")
        if (!account.loggedIn) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("王朝", "海洋", "腾势", "仰望", "方程豹").forEachIndexed { index, label ->
                    FilterChip(selected = brand == (index + 1).toString(), onClick = { brand = (index + 1).toString() }, label = { Text(label) })
                }
            }
            OutlinedTextField(username, { username = it }, label = { Text("比亚迪账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("账号密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pin, { pin = it }, label = { Text("控车密码（如已设置）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.login(username, password, pin, brand); password = ""; pin = "" }, enabled = !busy && username.isNotBlank() && password.isNotBlank()) { Text("登录并读取车辆") }
            Text("密码仅用于本次登录。会话和控车密码摘要加密保存，配置导出不包含账号信息。")
        }
        GkVehicleSection("请求状态", message)
        if (account.loggedIn) {
            GkSettingItem("刷新车辆列表", onClick = if (busy) null else vm::refreshVehicles)
            if (account.vehicles.isEmpty()) GkVehicleSection("暂无车辆", "确认账号具有车辆授权，再刷新列表。")
            account.vehicles.forEach { vehicle ->
                GkSettingItem("${vehicle.name.ifEmpty { vehicle.model }}${if (vehicle.vin == account.selectedVin) " · 当前车辆" else ""}",
                    "${vehicle.plate} · VIN 尾号 ${vehicle.vin.takeLast(6)}", onClick = { vm.select(vehicle) })
            }
            if (account.selectedVin.isNotEmpty()) GkSettingItem("获取车辆实时信息", "按需查询，可能唤醒车辆；页面不会持续轮询", onClick = if (busy) null else vm::refreshRealtime)
            realtime?.let { data ->
                GkVehicleSection("车辆信息", "以下为最近一次查询结果。在线状态及车门字段不能由蓝牙 RSSI 推断。")
                val labels = linkedMapOf("time" to "数据时间", "onlineStatus" to "在线状态", "elecPercent" to "电量 %", "evEndurance" to "续航 km",
                    "totalMileage" to "总里程", "leftFrontDoor" to "主驾车门", "leftFrontDoorLock" to "主驾门锁", "powerGear" to "电源档位",
                    "leftFrontTirepressure" to "左前胎压", "rightFrontTirepressure" to "右前胎压", "leftRearTirepressure" to "左后胎压", "rightRearTirepressure" to "右后胎压")
                labels.forEach { (key, label) -> (data[key] as? JsonPrimitive)?.content?.let { GkSettingItem(label, it, imageVector = null) } }
            }
            GkSettingItem("退出 Auto 账号", "暂停控车并删除本地会话", onClick = vm::logout)
        }
    }
}

@Composable
fun VehicleMethodsPage() {
    val mainVm = MainViewModel.requireCurrent()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    GkVehiclePage("控车方式") {
        GkVehicleSection("选择方式", "更换方式会暂停自动控车。完成对应配置并实测后重新启动。")
        VehicleControlMethod.entries.forEach { method ->
            GkSettingItem(method.label, if (method == config.controlMethod) "已选择" else "点击选择", onClick = {
                VehicleStore.update { it.copy(controlMethod = method, lockTestConfirmed = false, proximityTestConfirmed = false) }
            })
        }
        GkSettingItem(if (config.controlMethod == VehicleControlMethod.WIDGET) "配置官方小组件" else "登录 Auto 并选择车辆", onClick = {
            mainVm.navigatePage(VehicleSectionRoute(if (config.controlMethod == VehicleControlMethod.WIDGET) VehicleSection.WIDGETS else VehicleSection.AUTO))
        })
        GkVehicleSection("原版其他控车方式", "手表 App、官方 App 无障碍、ROOT、Android 15 旧方式、外部广播、自定义指令和直接蓝牙钥匙尚未完成迁移，当前不能选择。")
    }
}
