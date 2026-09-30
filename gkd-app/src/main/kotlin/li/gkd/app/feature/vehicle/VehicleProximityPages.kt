package li.gkd.app.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import li.gkd.app.MainViewModel
import li.gkd.app.domain.vehicle.VehicleCommand
import li.gkd.app.domain.vehicle.VehicleState
import li.gkd.app.domain.vehicle.VehiclePowerState
import li.gkd.app.domain.vehicle.VehicleScanTiming
import li.gkd.app.feature.vehicle.auto.BydAccountStore
import li.gkd.app.service.A11yService
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextSwitch

@Composable
fun VehicleThresholdPage(unlock: Boolean) {
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val rssi by VehicleService.rssi.collectAsStateWithLifecycle()
    val threshold = if (unlock) config.unlockRssi else config.lockRssi
    val count = if (unlock) config.unlockCount else config.lockCount
    val offset = if (unlock) config.unlockOffset else config.lockOffset
    var text by rememberSaveable(threshold) { mutableStateOf(threshold.toString()) }
    val pocketOffset = if (unlock) config.unlockPocketOffset else config.lockPocketOffset
    var pocket by rememberSaveable(pocketOffset) { mutableStateOf(pocketOffset.toString()) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    GkVehiclePage(if (unlock) "靠近解锁" else "远离上锁") {
        GkVehicleSignal(Modifier.padding(16.dp))
        GkTextSwitch(title = if (unlock) "靠近自动解锁" else "远离自动上锁", checked = if (unlock) config.unlockEnabled else config.lockEnabled,
            onCheckedChange = { value -> VehicleStore.update { if (unlock) it.copy(unlockEnabled = value) else it.copy(lockEnabled = value) } })
        GkVehicleSection("信号阈值", "数值越接近 0，信号越强。手持 ${threshold} dBm，口袋 ${threshold - pocketOffset} dBm。阈值重叠时，临时抑制条件避免原地反复开锁。")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, label = { Text("手持阈值（-110～-30 dBm）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pocket, { pocket = it }, label = { Text("口袋减弱量（0～20 dB）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(rssi?.let { "当前信号 $it dBm" } ?: "等待运行中的车辆信号")
            TextButton(onClick = { rssi?.let { text = it.toString() } }, enabled = rssi != null) { Text("使用当前位置的信号") }
            Button(onClick = {
                val value = text.toIntOrNull()
                val pocketValue = pocket.toIntOrNull()
                if (value == null || value !in -110..-30 || pocketValue == null || pocketValue !in 0..20)
                    VehicleStore.report("阈值须在 -110～-30，口袋减弱量须在 0～20")
                else VehicleStore.update {
                    if (unlock) it.copy(unlockRssi = value, unlockPocketOffset = pocketValue)
                    else it.copy(lockRssi = value, lockPocketOffset = pocketValue)
                }
            }) { Text("保存阈值") }
        }
        GkVehicleSection("确认策略", "连续达标并满足最短确认时间才触发；初始信号基础上${if (unlock) "增强" else "减弱"}指定幅度。")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("灵敏", "均衡", "稳定").forEachIndexed { index, label ->
                FilterChip(selected = count == index + 3 && offset == index + 1,
                    onClick = { VehicleStore.update {
                        if (unlock) it.copy(unlockCount = index + 3, unlockOffset = index + 1)
                        else it.copy(lockCount = index + 3, lockOffset = index + 1)
                    } }, label = { Text(label) })
            }
        }
        GkSettingItem("高级设置", "$count 次确认 · ${if (unlock) "+" else "−"}$offset dB", onClick = { advanced = !advanced })
        if (advanced) Column(Modifier.padding(horizontal = 16.dp)) {
            Text("确认次数：$count")
            Slider(count.toFloat(), { value -> VehicleStore.update {
                if (unlock) it.copy(unlockCount = value.toInt()) else it.copy(lockCount = value.toInt())
            } }, valueRange = 1f..10f, steps = 8)
            Text("${if (unlock) "增强" else "减弱"}量：$offset dB")
            Slider(offset.toFloat(), { value -> VehicleStore.update {
                if (unlock) it.copy(unlockOffset = value.toInt()) else it.copy(lockOffset = value.toInt())
            } }, valueRange = 0f..8f, steps = 7)
        }
        Column(Modifier.padding(16.dp)) {
            Text("指令冷却：${config.cooldownSeconds} 秒")
            Slider(config.cooldownSeconds.toFloat(), { value -> VehicleStore.update { it.copy(cooldownSeconds = value.toInt()) } }, valueRange = 0f..60f, steps = 59)
        }
        GkSettingItem("恢复原版默认阈值", if (unlock) "手持 -87 / 口袋 -93" else "手持 -82 / 口袋 -90", onClick = {
            VehicleStore.update { if (unlock) it.copy(unlockRssi = -87, unlockPocketOffset = 6, unlockCount = 4, unlockOffset = 2)
                else it.copy(lockRssi = -82, lockPocketOffset = 8, lockCount = 4, lockOffset = 2) }
        })
    }
}

@Composable
fun VehicleScanPage() {
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val timing = VehicleScanTiming(config.gattSleepSeconds)
    GkVehiclePage("扫描与省电") {
        GkVehicleSection("扫描模式", "混合模式：亮屏扫描、息屏 GATT 重连并读取信号。默认使用原版重试时序；系统守候与智能模式暂未接入。")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VehicleScanMode.entries.forEach { mode ->
                FilterChip(selected = config.scanMode == mode, onClick = { VehicleStore.update { it.copy(scanMode = mode) } }, label = { Text(mode.label) })
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text("GATT 读取间隔：${config.readIntervalMs} 毫秒")
            Slider(config.readIntervalMs.toFloat(), { value -> VehicleStore.update { it.copy(readIntervalMs = value.toInt()) } }, valueRange = 200f..1000f)
            Text("扫描 / GATT 重试静默：${config.gattSleepSeconds} 秒")
            Slider(config.gattSleepSeconds.toFloat(), { value -> VehicleStore.update { it.copy(gattSleepSeconds = value.toInt()) } }, valueRange = 1f..5f, steps = 3)
            Text("每轮扫描 ${timing.scanWindowMs / 1000.0} 秒；GATT 连接超时 ${timing.connectTimeoutMs / 1000.0} 秒")
        }
        GkTextSwitch(title = "分段扫描", subtitle = "远离且已锁时扫描 ${timing.scanWindowMs / 1000.0} 秒、休息 ${config.gattSleepSeconds} 秒；近车与待远离上锁时保持扫描",
            checked = config.intermittent, onCheckedChange = { value -> VehicleStore.update { it.copy(intermittent = value) } })
        GkTextSwitch(title = "静止暂停 / 运动恢复", subtitle = "只在已锁阶段暂停；没有唤醒型运动传感器时保持扫描",
            checked = config.stationaryPause, onCheckedChange = { value -> VehicleStore.update { it.copy(stationaryPause = value) } })
        Column(Modifier.padding(16.dp)) {
            Text("静止超时：${config.stationarySeconds} 秒")
            Slider(config.stationarySeconds.toFloat(), { value -> VehicleStore.update { it.copy(stationarySeconds = value.toInt()) } }, valueRange = 20f..180f)
            Text("运动灵敏度：${config.movementSensitivity} / 5")
            Slider(config.movementSensitivity.toFloat(), { value -> VehicleStore.update { it.copy(movementSensitivity = value.toInt()) } }, valueRange = 1f..5f, steps = 3)
        }
        GkTextSwitch(title = "息屏系统投递扫描", subtitle = "BLE 扫描模式息屏时通过系统投递结果；混合模式息屏仍优先 GATT",
            checked = config.screenOffLowPower, onCheckedChange = { value -> VehicleStore.update { it.copy(screenOffLowPower = value) } })
        GkTextSwitch(title = "GATT 无信号时回退 BLE", subtitle = "可选增强：15 秒无有效信号后扫描 30 秒；原版时序默认关闭",
            checked = config.gattFallbackEnabled, onCheckedChange = { value -> VehicleStore.update { it.copy(gattFallbackEnabled = value) } })
        GkSettingItem("Wi-Fi / 蓝牙制约", "指定连接条件下停扫，断开后恢复", onClick = {
            MainViewModel.requireCurrent().navigatePage(VehicleSectionRoute(VehicleSection.CONSTRAINTS))
        })
        GkTextSwitch(title = "仅锁屏控车", subtitle = "手机未锁屏时不触发自动指令",
            checked = config.lockscreenOnly, onCheckedChange = { value -> VehicleStore.update { it.copy(lockscreenOnly = value) } })
    }
}

@Composable
fun VehicleRunPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<VehicleSettingsVm>()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val running by VehicleService.running.collectAsStateWithLifecycle()
    val status by VehicleStore.status.collectAsStateWithLifecycle()
    val account by BydAccountStore.summary.collectAsStateWithLifecycle()
    val a11y by A11yService.isRunning.collectAsStateWithLifecycle()
    val ready = if (config.controlMethod == VehicleControlMethod.WIDGET) config.ready && a11y else
        config.address.isNotEmpty() && account.loggedIn && account.selectedVin.isNotEmpty()
    var test by remember { mutableStateOf<VehicleCommand?>(null) }
    var power by rememberSaveable { mutableStateOf(VehiclePowerState.UNKNOWN) }
    val powerRequired = config.powerOnEnabled || config.powerOffEnabled
    GkVehiclePage("启动车辆功能") {
        GkVehicleSignal(Modifier.padding(16.dp))
        GkVehicleSection("先确认当前车门状态", "确认实车状态后启动。点击已发出只能证明组件接受了操作，车辆结果需要检查。进程重启后也要重新确认。")
        GkSettingItem("检查配置", if (ready) "${config.controlMethod.label}已配置" else "尚未完成配置", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.GUIDE)) })
        if (powerRequired) {
            GkVehicleSection("确认当前车辆电源", "电源状态与门锁状态独立。请检查实车；组件和云控的指令受理不代表实际电源状态。")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = power == VehiclePowerState.OFF, onClick = { power = VehiclePowerState.OFF }, label = { Text("已下电") })
                FilterChip(selected = power == VehiclePowerState.ON, onClick = { power = VehiclePowerState.ON }, label = { Text("已上电") })
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.start(VehicleState.LOCKED, power) }, enabled = ready && !running && (!powerRequired || power != VehiclePowerState.UNKNOWN)) { Text("车已锁，启动") }
            Button(onClick = { vm.start(VehicleState.UNLOCKED, power) }, enabled = ready && !running && (!powerRequired || power != VehiclePowerState.UNKNOWN)) { Text("车未锁，启动") }
            TextButton(onClick = VehicleService::stop, enabled = running) { Text("暂停自动控车") }
            Text(status)
        }
        GkVehicleSection("手动测试", "测试会暂停自动控车，并实际执行所选指令。请站在车辆旁确认。")
        VehicleCommand.entries.forEach { command -> GkSettingItem("测试${command.label}", onClick = { test = command }) }
    }
    test?.let { command -> AlertDialog(onDismissRequest = { test = null }, title = { Text("执行${command.label}测试？") },
        text = { Text("会通过${config.controlMethod.label}实际发送指令。执行后请检查车辆结果。") },
        confirmButton = { TextButton(onClick = { vm.test(command); test = null }) { Text("执行") } },
        dismissButton = { TextButton(onClick = { test = null }) { Text("取消") } }) }
}
