package li.gkd.app.feature.vehicle

import androidx.compose.runtime.Composable

@Composable
fun VehicleSectionPage(route: VehicleSectionRoute) {
    when (route.section) {
        VehicleSection.PERMISSIONS -> VehiclePermissionsPage()
        VehicleSection.GUIDE -> VehicleGuidePage()
        VehicleSection.DEVICES -> VehicleDevicesPage()
        VehicleSection.WIDGETS -> VehicleWidgetsPage()
        VehicleSection.UNLOCK -> VehicleThresholdPage(true)
        VehicleSection.LOCK -> VehicleThresholdPage(false)
        VehicleSection.SCAN -> VehicleScanPage()
        VehicleSection.RUN -> VehicleRunPage()
        VehicleSection.LOGS -> VehicleLogsPage()
        VehicleSection.AUTO -> VehicleAutoPage()
        VehicleSection.METHODS -> VehicleMethodsPage()
        VehicleSection.POWER -> VehiclePowerPage()
        VehicleSection.CONSTRAINTS -> VehicleConstraintsPage()
        else -> GkVehiclePage(route.section.title) {
            GkVehicleSection("迁移尚未完成", "此项在最新版中存在，当前还不能使用。完成迁移和验证后才能启用。")
        }
    }
}
