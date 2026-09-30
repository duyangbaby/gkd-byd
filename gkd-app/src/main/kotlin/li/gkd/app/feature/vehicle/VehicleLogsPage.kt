package li.gkd.app.feature.vehicle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.ui.component.GkSettingItem

@Composable
fun VehicleLogsPage() {
    val vm = viewModel<VehicleSettingsVm>()
    val logs by VehicleStore.logs.collectAsStateWithLifecycle()
    val status by VehicleStore.status.collectAsStateWithLifecycle()
    var importConfirm by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportConfig) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importConfig) }
    GkVehiclePage("运行日志与配置") {
        GkVehicleSignal(Modifier.padding(16.dp))
        GkVehicleSection("配置备份", "导出当前设置，不包含账号凭据。导入会暂停车辆功能，并要求重新绑定组件和实测。")
        GkSettingItem("导出配置", onClick = { export.launch("gkd-byd-config.json") })
        GkSettingItem("导入配置", onClick = { importConfirm = true })
        GkVehicleSection("运行状态", status)
        GkSettingItem("清空日志", "最近 80 条，仅内存保存", onClick = VehicleStore::clearLogs)
        if (logs.isEmpty()) GkVehicleSection("暂无运行日志")
        logs.forEach { GkSettingItem(it, imageVector = null) }
    }
    if (importConfirm) AlertDialog(onDismissRequest = { importConfirm = false }, title = { Text("导入配置？") },
        text = { Text("将替换当前 BYD 设置并暂停控车。导入后需要重新绑定官方小组件。") },
        confirmButton = { TextButton(onClick = { importConfirm = false; import.launch(arrayOf("application/json", "text/plain")) }) { Text("选择文件") } },
        dismissButton = { TextButton(onClick = { importConfirm = false }) { Text("取消") } })
}
