package li.gkd.app.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.ui.component.GkTextSwitch

@Composable
fun VehiclePowerPage() {
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    var near by rememberSaveable(config.powerOnRssi) { mutableStateOf(config.powerOnRssi.toString()) }
    var far by rememberSaveable(config.powerOffRssi) { mutableStateOf(config.powerOffRssi.toString()) }
    GkVehiclePage("车辆上下电") {
        GkVehicleSection("执行方式", if (config.controlMethod == VehicleControlMethod.AUTO)
            "Auto 云端执行开启 / 关闭空调，不保证等同整车电源切换。当前按控车 BLE 信号判断；原版车机蓝牙、开门与 Auto 接管分支仍待迁移。"
            else "通过已标记的开启空调 / 一键熄火按钮执行。当前按控车 BLE 信号判断，车机蓝牙与开门触发分支仍待迁移。")
        GkTextSwitch(title = "靠近自动上电", subtitle = "已解锁且强信号连续达标后执行，解锁后还需等待设定时间",
            checked = config.powerOnEnabled, onCheckedChange = { value -> VehicleStore.update { it.copy(powerOnEnabled = value) } })
        GkTextSwitch(title = "远离自动下电", subtitle = "仅在启动时确认已上电或发出上电指令后监测；未知状态不触发",
            checked = config.powerOffEnabled, onCheckedChange = { value -> VehicleStore.update { it.copy(powerOffEnabled = value) } })
        GkVehicleSection("控车蓝牙阈值", "强信号触发上电，弱信号触发下电。下电阈值须弱于上电阈值；这些是当前 BLE 分支的设置，不是原版车机蓝牙默认值。")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(near, { near = it }, label = { Text("上电阈值（dBm）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(far, { far = it }, label = { Text("下电阈值（dBm）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val on = near.toIntOrNull()
                val off = far.toIntOrNull()
                if (on == null || off == null || on !in -110..-20 || off !in -110..-20 || off >= on)
                    VehicleStore.report("阈值须在 -110～-20，且下电阈值小于上电阈值")
                else VehicleStore.update { it.copy(powerOnRssi = on, powerOffRssi = off) }
            }) { Text("保存阈值") }
            Text("连续确认：${config.powerConfirmations} 次（至少 2 秒）")
            Slider(config.powerConfirmations.toFloat(), { value -> VehicleStore.update { it.copy(powerConfirmations = value.toInt()) } }, valueRange = 1f..10f, steps = 8)
            Text("解锁后等待：${config.powerOnDelaySeconds} 秒")
            Slider(config.powerOnDelaySeconds.toFloat(), { value -> VehicleStore.update { it.copy(powerOnDelaySeconds = value.toInt()) } }, valueRange = 0f..60f, steps = 59)
        }
        GkTextSwitch(title = "下电后关窗", subtitle = "下电指令受理 2 秒后发送关窗，需配置关窗组件或 Auto；实车结果仍需检查",
            checked = config.closeWindowsAfterOff, onCheckedChange = { value -> VehicleStore.update { it.copy(closeWindowsAfterOff = value) } })
        GkVehicleSection("启动与确认", "启用自动上下电后，启动页还需确认当前车辆电源状态。任何参数修改都会暂停运行，重新确认状态后才能启动。")
    }
}
