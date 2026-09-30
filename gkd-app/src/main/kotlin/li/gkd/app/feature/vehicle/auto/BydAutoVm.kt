package li.gkd.app.feature.vehicle.auto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import li.gkd.app.feature.vehicle.VehicleStore
import li.gkd.app.ui.share.BaseViewModel

class BydAutoVm : BaseViewModel() {
    private var requestJob: Job? = null
    private var requestGeneration = 0
    val busy: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val message: StateFlow<String>
        field = MutableStateFlow("登录后选择车辆。车辆切换会暂停自动控车。")
    val realtime: StateFlow<JsonObject?>
        field = MutableStateFlow(null)

    fun login(username: String, password: String, pin: String, brand: String) = work("正在登录…") {
        BydAccountStore.signIn(BydCloudClient.login(username, password, pin, brand))
        BydAccountStore.updateVehicles(BydCloudClient.vehicles(checkNotNull(BydAccountStore.session())))
        message.value = "登录成功，请明确选择要控制的车辆"
    }

    fun refreshVehicles() = work("正在读取车辆…") {
        BydAccountStore.updateVehicles(BydCloudClient.vehicles(checkNotNull(BydAccountStore.session())))
        message.value = "车辆列表已更新"
    }

    fun select(vehicle: BydVehicle) {
        requestGeneration++
        requestJob?.cancel()
        busy.value = false
        BydAccountStore.select(vehicle.vin)
        VehicleStore.update { it.copy(vehicleName = vehicle.name.ifEmpty { vehicle.model }, proximityTestConfirmed = false) }
        realtime.value = null
        message.value = "已选择车辆，请重新确认车门状态后启动"
    }

    fun refreshRealtime() = work("正在查询车况…") {
        realtime.value = BydCloudClient.realtime(checkNotNull(BydAccountStore.session()), checkNotNull(BydAccountStore.selectedVehicle()))
        message.value = "车况已更新，车辆结果以数据时间为准"
    }

    fun logout() {
        requestGeneration++
        requestJob?.cancel()
        busy.value = false
        BydAccountStore.signOut()
        realtime.value = null
        message.value = "已退出 Auto，自动控车已暂停"
    }

    private fun work(progress: String, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = progress
        val generation = ++requestGeneration
        requestJob = scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: BydCloudException) { message.value = e.message ?: "云端请求失败" }
            catch (_: Exception) { message.value = "请求未完成，请检查网络、账号信息或稍后重试" }
            finally { if (generation == requestGeneration) busy.value = false }
        }
    }
}
