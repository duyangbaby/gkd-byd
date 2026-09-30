package li.gkd.app.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.MainViewModel
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.home.ResetPageScrollOnRequest
import li.gkd.app.ui.home.ScaffoldExt

@Serializable
data object VehicleSettingsRoute : NavKey

@Serializable
data class VehicleSectionRoute(val section: VehicleSection) : NavKey

@Serializable
enum class VehicleSection(val title: String) {
    PERMISSIONS("权限管理"), GUIDE("新手指引"), DEVICES("车辆蓝牙"), WIDGETS("控车小组件"),
    UNLOCK("靠近解锁"), LOCK("远离上锁"), SCAN("扫描与省电"), RUN("启动车辆功能"),
    LOGS("运行日志与配置"), METHODS("控车方式"), POWER("车辆上下电"),
    CONSTRAINTS("条件约束"), AUTO("Auto 账号与车辆"), AUTOMATION("按键与定时任务"),
    REMOTE("远程与车机"), APPEARANCE("声音与桌面组件")
}

@Composable
fun useBydPage(): ScaffoldExt {
    val scroll = rememberScrollState()
    ResetPageScrollOnRequest(BottomNavItem.Byd) { scroll.animateScrollTo(0) }
    return ScaffoldExt(navItem = BottomNavItem.Byd) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(padding)) {
            BydOverview()
            GkPageBottomSpace()
        }
    }
}

@Composable
fun VehicleSettingsPage() = GkVehiclePage("BYD") { BydOverview() }

@Composable
private fun BydOverview() {
    val mainVm = MainViewModel.requireCurrent()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val running by VehicleService.running.collectAsStateWithLifecycle()
    val status by VehicleStore.status.collectAsStateWithLifecycle()
    Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(config.vehicleName, style = MaterialTheme.typography.titleLarge)
            Text(if (running) "靠近 / 远离运行中" else "车辆功能已暂停", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (running) VehicleService.stop()
                    else mainVm.navigatePage(VehicleSectionRoute(VehicleSection.RUN))
                }) { Text(if (running) "暂停" else "启动") }
                TextButton(onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.GUIDE)) }) {
                    Text(if (config.guideCompleted) "重看指引" else "开始配置")
                }
            }
        }
    }
    GkVehicleSignal(Modifier.padding(horizontal = 16.dp))
    GkVehicleSection("连接与控车")
    GkSettingItem("权限管理", "查看授权状态与后台运行设置", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.PERMISSIONS)) })
    GkSettingItem("控车方式", "小组件、Auto、无障碍及其他方式", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.METHODS)) })
    GkSettingItem("车辆蓝牙", config.deviceName.ifEmpty { "未绑定 · 搜索并选择控车 BLE" }, onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.DEVICES)) })
    GkSettingItem("控车小组件", if (config.widgetId >= 0) "主组件已绑定 · 查看预览与按钮" else "未绑定 · 主组件与关窗组件", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.WIDGETS)) })
    GkVehicleSection("自动控车")
    GkSettingItem("靠近解锁", "信号阈值、确认策略与冷却", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.UNLOCK)) })
    GkSettingItem("远离上锁", "信号阈值、二次上锁与车端确认", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.LOCK)) })
    GkSettingItem("车辆上下电", "靠近上电、远离下电与关窗", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.POWER)) })
    GkSettingItem("扫描与省电", "扫描模式、运动监测与熄屏策略", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.SCAN)) })
    GkSettingItem("条件约束", "Wi-Fi、制约蓝牙与车机蓝牙", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.CONSTRAINTS)) })
    GkVehicleSection("车辆与扩展")
    GkSettingItem("Auto 账号与车辆", "比亚迪账号、车辆信息与云端设置", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.AUTO)) })
    GkSettingItem("按键与定时任务", "音量键、签到、预启动与预约", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.AUTOMATION)) })
    GkSettingItem("远程与车机", "巴法云远控与车机交互", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.REMOTE)) })
    GkSettingItem("声音与桌面组件", "控车提示音、浮窗与桌面样式", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.APPEARANCE)) })
    GkSettingItem("运行日志与配置", "查看日志、导入或导出配置", onClick = { mainVm.navigatePage(VehicleSectionRoute(VehicleSection.LOGS)) })
}
