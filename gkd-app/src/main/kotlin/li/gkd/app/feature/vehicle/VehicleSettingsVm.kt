package li.gkd.app.feature.vehicle

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import li.gkd.app.MainViewModel
import li.gkd.app.domain.vehicle.VehicleState
import li.gkd.app.service.A11yService
import li.gkd.app.ui.share.BaseViewModel
import kotlinx.coroutines.launch

class VehicleSettingsVm : BaseViewModel() {
    fun bind(info: AppWidgetProviderInfo) {
        val mainVm = MainViewModel.requireCurrent()
        VehicleService.stop()
        scope.launch {
            val id = VehicleWidgets.host.allocateAppWidgetId()
            var committed = false
            try {
                val allowed = VehicleWidgets.manager.bindAppWidgetIdIfAllowed(id, info.provider)
                if (!allowed) {
                    val result = mainVm.activityResults.startActivity(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
                    if (result.resultCode != Activity.RESULT_OK) return@launch
                }
                if (info.configure != null) {
                    val result = mainVm.activityResults.startActivity(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    if (result.resultCode != Activity.RESULT_OK) return@launch
                }
                val oldId = VehicleStore.config.value.widgetId
                VehicleStore.update { it.copy(widgetId = id, provider = info.provider.flattenToString(),
                    providerVersion = VehicleWidgets.version(info.provider), unlockViewId = -1, lockViewId = -1) }
                committed = true
                if (oldId >= 0 && oldId != id) VehicleWidgets.host.deleteAppWidgetId(oldId)
                VehicleStore.report("小组件已绑定，请分别标记解锁和上锁按钮；标记不会点击车辆")
            } catch (e: Exception) {
                VehicleStore.report("绑定失败：${e.message}")
            } finally {
                if (!committed) VehicleWidgets.host.deleteAppWidgetId(id)
            }
        }
    }

    fun selectButton(unlock: Boolean, id: Int) {
        VehicleStore.update { if (unlock) it.copy(unlockViewId = id) else it.copy(lockViewId = id) }
        VehicleStore.report(if (unlock) "解锁按钮已标记" else "上锁按钮已标记")
    }

    fun save(address: String, unlock: String, lock: String) {
        val near = unlock.toIntOrNull()
        val far = lock.toIntOrNull()
        if (!address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) ||
            near == null || far == null || near !in -110..-30 || far !in -110..-30 || far >= near) {
            VehicleStore.report("请填写正确蓝牙地址和 -110～-30 的阈值；上锁值须小于解锁值，例如 -90 / -80")
        } else {
            VehicleStore.update { it.copy(address = address.uppercase(), unlockRssi = near, lockRssi = far) }
            VehicleStore.report("蓝牙与阈值已保存，请确认实际车辆状态后启动")
        }
    }

    fun discover() {
        if (checkBluetooth()) {
            try { VehicleService.discover() }
            catch (e: Exception) { VehicleStore.report("无法搜索设备：${e.message}") }
        }
    }

    fun start(state: VehicleState) {
        if (!checkBluetooth()) return
        if (!VehicleStore.config.value.ready || A11yService.instance == null) {
            VehicleStore.report("请先绑定小组件、标记两个不同按钮并开启 GKD 无障碍服务")
            return
        }
        try { VehicleService.start(state) }
        catch (e: Exception) { VehicleStore.report("无法启动：${e.message}") }
    }

    private fun checkBluetooth(): Boolean {
        if (Build.VERSION.SDK_INT < 31) {
            VehicleStore.report("车辆功能第一版支持 Android 12 及以上")
            return false
        }
        if (!VehicleService.hasPermissions()) {
            VehicleStore.report("请先授予附近设备权限")
            return false
        }
        return true
    }
}
