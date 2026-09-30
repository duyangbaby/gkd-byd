package li.gkd.app.feature.vehicle

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import li.gkd.app.domain.vehicle.VehicleCommand
import li.gkd.app.feature.vehicle.auto.BydAccountStore
import li.gkd.app.feature.vehicle.auto.BydCloudClient
import li.gkd.app.service.A11yService

@Serializable
enum class VehicleControlMethod(val label: String) { WIDGET("小组件控车"), AUTO("Auto 云端控车") }

object VehicleActions {
    private val commandLock = Mutex()
    fun ready(config: VehicleConfig) = config.address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) &&
        when (config.controlMethod) {
            VehicleControlMethod.WIDGET -> config.ready && A11yService.instance != null
            VehicleControlMethod.AUTO -> BydAccountStore.session() != null && BydAccountStore.selectedVehicle() != null
        }

    suspend fun execute(config: VehicleConfig, command: VehicleCommand): Boolean = commandLock.withLock {
        when (config.controlMethod) {
            VehicleControlMethod.WIDGET -> VehicleWidgets.click(config, command)
            VehicleControlMethod.AUTO -> BydCloudClient.control(checkNotNull(BydAccountStore.session()) { "请先登录 Auto" },
                checkNotNull(BydAccountStore.selectedVehicle()) { "请先选择 Auto 车辆" }, command)
        }
    }
}
