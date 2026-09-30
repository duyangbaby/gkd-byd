package li.gkd.app.feature.vehicle

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import li.gkd.app.app
import li.gkd.app.MainViewModel
import li.gkd.app.domain.vehicle.VehicleState
import li.gkd.app.service.A11yService
import li.gkd.app.ui.share.BaseViewModel
import kotlinx.coroutines.launch
import li.gkd.app.domain.vehicle.VehicleCommand
import li.gkd.app.domain.vehicle.VehiclePowerState

class VehicleSettingsVm : BaseViewModel() {
    fun exportConfig(uri: Uri) {
        val text = VehicleStore.exportConfig()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(app.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
                }
                VehicleStore.report("配置已导出；账号凭据不包含在配置中")
            } catch (e: Exception) { VehicleStore.report("配置导出失败：${e.javaClass.simpleName}") }
        }
    }

    fun importConfig(uri: Uri) {
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    checkNotNull(app.contentResolver.openInputStream(uri)).use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 1_048_576)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray().toString(Charsets.UTF_8)
                    }
                }
                VehicleStore.importConfig(text)
            } catch (e: Exception) { VehicleStore.report("导入失败：文件格式或参数不正确") }
        }
    }
    fun openSettings(intent: Intent) {
        val mainVm = MainViewModel.requireCurrent()
        scope.launch {
            try { mainVm.activityResults.startActivity(intent) }
            catch (e: Exception) { VehicleStore.report("无法打开设置：${e.message}") }
        }
    }

    fun selectDevice(address: String, name: String) {
        val constraints = VehicleStore.config.value.constraints
        if (address.equals(constraints.carBluetoothAddress, true) || constraints.bluetoothAddresses.any { it.equals(address, true) }) {
            VehicleStore.report("控车 BLE 不能同时作为车机或制约蓝牙，请先解除冲突绑定")
            return
        }
        if (address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))) {
            VehicleStore.update { it.copy(address = address.uppercase(), deviceName = name, proximityTestConfirmed = false) }
            VehicleStore.report("已绑定控车蓝牙：${name.ifEmpty { address }}")
        } else VehicleStore.report("蓝牙地址格式不正确")
    }
    fun bind(info: AppWidgetProviderInfo, secondary: Boolean = false) {
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
                val oldId = VehicleStore.config.value.let { if (secondary) it.secondaryWidgetId else it.widgetId }
                VehicleStore.update {
                    if (secondary) it.copy(secondaryWidgetId = id, secondaryProvider = info.provider.flattenToString(),
                        secondaryProviderVersion = VehicleWidgets.version(info.provider), windowViewId = -1)
                    else it.copy(widgetId = id, provider = info.provider.flattenToString(),
                        providerVersion = VehicleWidgets.version(info.provider), unlockViewId = -1, lockViewId = -1,
                        powerOnViewId = -1, powerOffViewId = -1, lockTestConfirmed = false, proximityTestConfirmed = false)
                }
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

    fun selectAction(command: VehicleCommand, id: Int) {
        VehicleStore.update { when (command) {
            VehicleCommand.UNLOCK -> it.copy(unlockViewId = id)
            VehicleCommand.LOCK -> it.copy(lockViewId = id)
            VehicleCommand.POWER_ON -> it.copy(powerOnViewId = id)
            VehicleCommand.POWER_OFF -> it.copy(powerOffViewId = id)
            VehicleCommand.CLOSE_WINDOWS -> it.copy(windowViewId = id)
        } }
        VehicleStore.report("已标记${command.label}按钮；标记未发送指令")
    }

    fun unbind(secondary: Boolean) {
        val id = VehicleStore.config.value.let { if (secondary) it.secondaryWidgetId else it.widgetId }
        VehicleStore.update {
            if (secondary) it.copy(secondaryWidgetId = -1, secondaryProvider = "", secondaryProviderVersion = -1, windowViewId = -1)
            else it.copy(widgetId = -1, provider = "", providerVersion = -1, unlockViewId = -1, lockViewId = -1,
                powerOnViewId = -1, powerOffViewId = -1, lockTestConfirmed = false, proximityTestConfirmed = false)
        }
        if (id >= 0) VehicleWidgets.host.deleteAppWidgetId(id)
    }

    fun test(command: VehicleCommand) {
        VehicleService.stop()
        scope.launch {
            try {
                val dispatched = VehicleActions.execute(VehicleStore.config.value, command)
                VehicleStore.report(if (dispatched) "${command.label}点击已发出，请实际检查车辆结果" else "${command.label}未发出")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { VehicleStore.report("手动测试失败：${e.message}") }
        }
    }

    fun save(address: String, unlock: String, lock: String) {
        val near = unlock.toIntOrNull()
        val far = lock.toIntOrNull()
        if (!address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) ||
            near == null || far == null || near !in -110..-30 || far !in -110..-30) {
            VehicleStore.report("请填写正确蓝牙地址和 -110～-30 的阈值")
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

    fun start(state: VehicleState, power: VehiclePowerState = VehiclePowerState.UNKNOWN) {
        if (!checkBluetooth()) return
        if (!VehicleActions.ready(VehicleStore.config.value)) {
            VehicleStore.report("请完成当前控车方式的配置：小组件需标记按钮和无障碍，Auto 需登录并选车")
            return
        }
        if ((VehicleStore.config.value.powerOnEnabled || VehicleStore.config.value.powerOffEnabled) && power == VehiclePowerState.UNKNOWN) {
            VehicleStore.report("已启用自动上下电，请先确认车辆当前上电状态")
            return
        }
        val config = VehicleStore.config.value
        if (config.controlMethod == VehicleControlMethod.WIDGET &&
            ((config.powerOnEnabled && config.powerOnViewId < 0) ||
             (config.powerOffEnabled && config.powerOffViewId < 0) ||
             (config.powerOffEnabled && config.closeWindowsAfterOff && (config.secondaryWidgetId < 0 || config.windowViewId < 0)))) {
            VehicleStore.report("请先标记已启用的上电、下电及关窗按钮")
            return
        }
        try { VehicleService.start(state, power) }
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
