package li.gkd.app.feature.vehicle

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.MainViewModel
import li.gkd.app.domain.vehicle.VehicleState
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar

@Serializable
data object VehicleSettingsRoute : NavKey

@Composable
fun VehicleSettingsPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<VehicleSettingsVm>()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val status by VehicleStore.status.collectAsStateWithLifecycle()
    val logs by VehicleStore.logs.collectAsStateWithLifecycle()
    val running by VehicleService.running.collectAsStateWithLifecycle()
    val discovering by VehicleService.discovering.collectAsStateWithLifecycle()
    val devices by VehicleService.devices.collectAsStateWithLifecycle()
    val rssi by VehicleService.rssi.collectAsStateWithLifecycle()
    var address by rememberSaveable(config.address) { mutableStateOf(config.address) }
    var unlock by rememberSaveable(config.unlockRssi) { mutableStateOf(config.unlockRssi.toString()) }
    var lock by rememberSaveable(config.lockRssi) { mutableStateOf(config.lockRssi.toString()) }
    var selecting by remember { mutableStateOf<Boolean?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        VehicleStore.report(if (VehicleService.hasPermissions()) "附近设备权限已授予" else "附近设备权限未授予")
    }
    val providers = remember { VehicleWidgets.providers() }
    val context = LocalContext.current
    val info = remember(config.widgetId) { VehicleWidgets.manager.getAppWidgetInfo(config.widgetId) }
    val preview = remember(config.widgetId) { info?.let { VehicleWidgets.createView(context, config.widgetId, it) } }
    DisposableEffect(preview) {
        if (preview != null) VehicleWidgets.acquire()
        onDispose { if (preview != null) VehicleWidgets.release() }
    }

    Scaffold(topBar = {
        GkTopAppBar(title = { Text("车辆靠近 / 远离") }, navigationIcon = {
            GkIconButton(imageVector = GkIcons.ArrowBack, onClick = mainVm::popPage)
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("仅使用官方比亚迪小组件上锁/解锁，不操作上下电。首次需绑定小组件，登录官方比亚迪 App 并确认所选车辆。")
            Text(status)
            Text("RSSI：${rssi?.let { "$it dBm" } ?: "暂无"}")
            TextButton(onClick = {
                permissions.launch(VehicleService.permissions() + if (Build.VERSION.SDK_INT >= 33)
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
            }, enabled = Build.VERSION.SDK_INT >= 31) { Text("授予附近设备和通知权限") }
            Row {
                Button(onClick = vm::discover, enabled = !discovering && !running) { Text("搜索车辆蓝牙") }
                TextButton(onClick = VehicleService::stop) { Text("停止") }
            }
            devices.forEach { device ->
                TextButton(onClick = { address = device.address }) {
                    Text("${device.name} · ${device.address} · ${device.rssi} dBm")
                }
            }
            OutlinedTextField(address, { address = it }, label = { Text("车辆 BLE 地址（不要选择多媒体蓝牙）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(unlock, { unlock = it }, label = { Text("靠近解锁 RSSI，例如 -80") })
            OutlinedTextField(lock, { lock = it }, label = { Text("远离上锁 RSSI，例如 -90") })
            Button(onClick = { vm.save(address, unlock, lock) }) { Text("保存蓝牙和阈值") }
            Text("请选择官方比亚迪控车小组件；绑定/更改配置会停止车辆功能。")
            if (providers.isEmpty()) Text("未找到比亚迪小组件，请先安装官方 App。")
            providers.forEach { provider ->
                TextButton(onClick = { vm.bind(provider) }) {
                    Text("绑定 ${provider.loadLabel(context.packageManager)} (${provider.provider.className.substringAfterLast('.')})")
                }
            }
            if (preview != null && info != null) {
                Text("预览仅用于标记，不会发送控车指令。选择标记后，请点对应的小组件按钮。")
                Row {
                    TextButton(onClick = { selecting = true }) { Text(if (config.unlockViewId != -1) "重新标记解锁" else "标记解锁") }
                    TextButton(onClick = { selecting = false }) { Text(if (config.lockViewId != -1) "重新标记上锁" else "标记上锁") }
                }
                if (selecting != null) Text(if (selecting == true) "请点解锁按钮" else "请点上锁按钮")
                AndroidView(factory = { preview }, modifier = Modifier.fillMaxWidth()
                    .height((info.minHeight / context.resources.displayMetrics.density).coerceAtLeast(160f).dp), update = {
                    it.onSelect = { id -> selecting?.let { target -> vm.selectButton(target, id); selecting = null } }
                })
            }
            GkTextSwitch(title = "分段扫描", subtitle = "上锁阶段扫描 6 秒、休息 3 秒；待远离上锁时连续扫描",
                checked = config.intermittent, onCheckedChange = { value -> VehicleStore.update { it.copy(intermittent = value) } })
            GkTextSwitch(title = "静止暂停 / 运动恢复", subtitle = "上锁阶段静止 30 秒后暂停；无唤醒型运动传感器时保持扫描",
                checked = config.stationaryPause, onCheckedChange = { value -> VehicleStore.update { it.copy(stationaryPause = value) } })
            GkTextSwitch(title = "熄屏低功耗扫描", subtitle = "上锁阶段熄屏降低扫描功耗，可能增加靠近识别延迟",
                checked = config.screenOffLowPower, onCheckedChange = { value -> VehicleStore.update { it.copy(screenOffLowPower = value) } })
            Text("确认车辆当前的实际状态后启动。已锁启动需先收到远处信号，再连续靠近；车辆功能与 GKD 规则使用同一个无障碍服务。")
            Row {
                Button(onClick = { vm.start(VehicleState.LOCKED) }, enabled = config.ready && !running && !discovering) { Text("车已锁，启动") }
                TextButton(onClick = { vm.start(VehicleState.UNLOCKED) }, enabled = config.ready && !running && !discovering) { Text("车未锁，启动") }
            }
            Text("仅连续弱信号触发远离上锁，断开或信号消失不会直接上锁。点击发出不等于车辆成功执行；失败会停止自动判断，需手动核对后重新启动。")
            Text("运行日志（最近 80 条，仅内存保存）")
            logs.forEach { Text(it) }
            GkPageBottomSpace()
        }
    }
}
