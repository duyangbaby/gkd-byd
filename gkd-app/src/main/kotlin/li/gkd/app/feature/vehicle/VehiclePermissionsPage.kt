package li.gkd.app.feature.vehicle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.A11yService
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextSwitch

@Composable
fun VehiclePermissionsPage() = GkVehiclePage("权限管理") { GkVehiclePermissions() }

@Composable
fun GkVehiclePermissions() {
    val vm = viewModel<VehicleSettingsVm>()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val granted = remember(revision) {
        VehiclePermissionsSnapshot(
            VehicleService.hasPermissions(), PermissionStates.notification.refresh(),
            A11yService.instance != null, PermissionStates.ignoreBatteryOptimizations.refresh(),
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    GkVehicleSection("必要权限", "与 GKD 共用无障碍服务。每项授权由系统确认，返回此页后会重新检查。")
    GkSettingItem("附近设备", if (granted.bluetooth) "已授权" else "未授权 · 用于识别车辆蓝牙", onClick = {
        request.launch(VehicleService.permissions())
    })
    GkSettingItem("通知", if (granted.notifications) "已授权" else "未授权 · 显示运行状态与停止按钮", onClick = {
        if (Build.VERSION.SDK_INT >= 33) request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        else vm.openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName))
    })
    GkSettingItem("无障碍", if (granted.accessibility) "GKD 服务已连接" else "未连接 · 用于执行官方小组件操作", onClick = {
        vm.openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    })
    GkSettingItem("不限制电池使用", if (granted.battery) "已允许忽略电池优化" else "尚未允许 · 降低后台被终止的概率", onClick = {
        vm.openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${app.packageName}")))
    })
    GkVehicleSection("按功能授权", "定位用于 Wi-Fi / 地点约束。基础靠近解锁不需要持续获取 GPS。配置文件通过系统文件选择器读写。")
    GkSettingItem("精准定位", if (granted.location) "已授权" else "未授权 · 地点及 Wi-Fi 约束需要", onClick = {
        request.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    })
    GkSettingItem("后台定位", if (granted.location && granted.backgroundLocation) "已授权" else "按需在系统设置选择始终允许", onClick = {
        vm.openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
    })
    GkVehicleSection("vivo 等系统后台设置", "允许自启动、后台高耗电、后台弹出界面和锁屏显示。不同系统的入口不同，应用无法可靠检测每一项。请在系统应用设置中确认。")
    GkSettingItem("打开应用设置", "检查权限、电池和自启动", onClick = {
        vm.openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
    })
    GkTextSwitch(title = "我已确认系统后台设置", subtitle = "用户确认项，不能代表系统已授权",
        checked = config.backgroundConfirmed, onCheckedChange = { value -> VehicleStore.update { it.copy(backgroundConfirmed = value) } })
    GkSettingItem("请求基础权限", "附近设备与通知；其余项目按上方入口设置", onClick = {
        request.launch(VehicleService.permissions() + if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
    })
}

private data class VehiclePermissionsSnapshot(val bluetooth: Boolean, val notifications: Boolean,
    val accessibility: Boolean, val battery: Boolean, val location: Boolean, val backgroundLocation: Boolean)
