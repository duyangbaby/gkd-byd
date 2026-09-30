package li.gkd.app.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import li.gkd.app.MainViewModel
import li.gkd.app.service.A11yService
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.feature.vehicle.auto.BydAccountStore

@Composable
fun VehicleGuidePage() {
    val mainVm = MainViewModel.requireCurrent()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val account by BydAccountStore.summary.collectAsStateWithLifecycle()
    val a11y by A11yService.isRunning.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRevision++ }
    val bluetoothGranted = remember(permissionRevision) { VehicleService.hasPermissions() }
    GkVehiclePage("新手指引") {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1 权限", "2 控车方式", "3 蓝牙").forEachIndexed { index, label ->
                TextButton(onClick = { step = index }) {
                    Text(label, color = if (step == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when (step) {
            0 -> GkVehiclePermissions()
            1 -> {
                GkVehicleSection("选择控车方式", "默认使用官方比亚迪小组件。先在官方 App 登录并选择车辆，再绑定这里的主控车组件。")
                GkSettingItem("控车方式", "查看各方式的配置条件", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.METHODS)) })
                if (config.controlMethod == VehicleControlMethod.WIDGET) {
                    GkSettingItem("绑定控车小组件", if (config.widgetId < 0) "尚未绑定" else "已绑定 · 检查按钮标记", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.WIDGETS)) })
                    GkVehicleSection("完成官方组件配置", "主组件四个功能按原版顺序配置为：车门解锁、车门上锁、开启空调、一键熄火。关窗放在第二个组件。绑定时的系统授权请选择允许。")
                } else {
                    GkSettingItem("登录 Auto 并选择车辆", if (account.loggedIn) "已登录" else "未登录", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.AUTO)) })
                }
                GkVehicleSection("息屏执行准备", "按官方 App 提示处理控车密码和锁屏权限；如果息屏不能执行，再检查后台弹出界面及防误触设置。请保留能够手动开车的方式。")
                GkTextSwitch(title = "已在息屏时实测上锁成功", subtitle = "请在车辆旁通过启动页手动测试并确认车门结果",
                    checked = config.lockTestConfirmed, onCheckedChange = { value -> VehicleStore.update { it.copy(lockTestConfirmed = value) } })
                GkSettingItem("手动测试", "测试前确认车辆状态及周围环境", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.RUN)) })
            }
            2 -> {
                GkVehicleSection("绑定控车 BLE", "选择车辆的控车蓝牙。多媒体蓝牙另有用途，请不要将它作为靠近信号。")
                GkSettingItem("选择车辆蓝牙", config.deviceName.ifEmpty { config.address.ifEmpty { "尚未选择" } }, onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.DEVICES)) })
                GkSettingItem("校准靠近解锁", "站在希望解锁的位置查看信号并设定阈值", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.UNLOCK)) })
                GkSettingItem("校准远离上锁", "手持与放入口袋分别确认", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.LOCK)) })
                GkVehicleSection("往返实测", "启用后远离车辆约 5 米并确认上锁，再走近并确认解锁。距离只用于测试，实际判断依据蓝牙信号；受车位和手机放置影响，需要现场校准。")
                GkTextSwitch(title = "靠近 / 远离实测通过", checked = config.proximityTestConfirmed,
                    onCheckedChange = { value -> VehicleStore.update { it.copy(proximityTestConfirmed = value) } })
                GkSettingItem("进入启动页", "先确认当前车门状态", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.RUN)) })
            }
        }
        Column(Modifier.padding(16.dp)) {
            Button(onClick = {
                if (step < 2) step++ else {
                    VehicleStore.update { it.copy(guideCompleted = true) }
                    mainVm.popPage()
                }
            }, enabled = when (step) {
                0 -> bluetoothGranted && (config.controlMethod == VehicleControlMethod.AUTO || a11y)
                1 -> config.lockTestConfirmed && if (config.controlMethod == VehicleControlMethod.WIDGET)
                    config.widgetId >= 0 && config.unlockViewId >= 0 && config.lockViewId >= 0 else account.loggedIn && account.selectedVin.isNotEmpty()
                else -> VehicleActions.ready(config) && config.proximityTestConfirmed
            }) { Text(if (step == 2) "完成指引" else "下一步") }
            TextButton(onClick = mainVm::popPage) { Text("稍后继续") }
        }
    }
}
