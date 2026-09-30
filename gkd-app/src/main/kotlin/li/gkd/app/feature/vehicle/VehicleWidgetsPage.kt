package li.gkd.app.feature.vehicle

import android.appwidget.AppWidgetProviderInfo
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.domain.vehicle.VehicleCommand
import li.gkd.app.ui.component.GkSettingItem

@Composable
fun VehicleWidgetsPage() {
    val vm = viewModel<VehicleSettingsVm>()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    var secondary by rememberSaveable { mutableStateOf(false) }
    var showStyles by rememberSaveable { mutableStateOf(false) }
    var providers by remember { mutableStateOf(VehicleWidgets.providers()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { providers = VehicleWidgets.providers() }
    val context = LocalContext.current
    val id = if (secondary) config.secondaryWidgetId else config.widgetId
    val info = remember(id) { VehicleWidgets.manager.getAppWidgetInfo(id) }
    val preview = remember(id) { info?.let { VehicleWidgets.createView(context, id, it) } }
    var selecting by remember(id) { mutableStateOf<VehicleCommand?>(null) }
    DisposableEffect(preview) {
        if (preview != null) VehicleWidgets.acquire()
        onDispose { if (preview != null) VehicleWidgets.release() }
    }
    GkVehiclePage("控车小组件") {
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !secondary, onClick = { secondary = false }, label = { Text("主控车组件") })
            FilterChip(selected = secondary, onClick = { secondary = true }, label = { Text("关窗组件") })
        }
        GkVehicleSection(if (secondary) "组件 2 · 关闭车窗" else "组件 1 · 主控车",
            if (secondary) "仅需要自动关窗时绑定。请在官方配置中把第 4 个功能设为关闭车窗。"
            else "配置顺序：1 车门解锁 · 2 车门上锁 · 3 开启空调 · 4 一键熄火。先在官方 App 登录并选车。")
        if (preview != null && info != null) {
            GkVehicleSection("组件预览", "预览只标记按钮，不会执行控车。先选择下面的用途，再点预览中的对应按钮。")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val actions = if (secondary) listOf(VehicleCommand.CLOSE_WINDOWS) else listOf(VehicleCommand.UNLOCK,
                    VehicleCommand.LOCK, VehicleCommand.POWER_ON, VehicleCommand.POWER_OFF)
                actions.forEach { action ->
                    val marked = when (action) {
                        VehicleCommand.UNLOCK -> config.unlockViewId
                        VehicleCommand.LOCK -> config.lockViewId
                        VehicleCommand.POWER_ON -> config.powerOnViewId
                        VehicleCommand.POWER_OFF -> config.powerOffViewId
                        VehicleCommand.CLOSE_WINDOWS -> config.windowViewId
                    } >= 0
                    FilterChip(selected = selecting == action, onClick = { selecting = action },
                        label = { Text("${action.label}${if (marked) " ✓" else " · 未标记"}") })
                }
            }
            if (selecting != null) GkVehicleSection("请点预览里的${selecting?.label}按钮")
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxWidth().padding(16.dp)
                .height((info.minHeight / context.resources.displayMetrics.density).coerceAtLeast(160f).dp),
                update = { view -> view.onSelect = { viewId -> selecting?.let { vm.selectAction(it, viewId); selecting = null } } })
            GkSettingItem("重新绑定 / 更换样式", "会清除本组件的按钮标记", onClick = { showStyles = !showStyles })
            GkSettingItem("移除这个组件", onClick = { vm.unbind(secondary) })
        } else {
            GkVehicleSection("选择组件样式", "下面是官方 App 提供的不同样式，不是三个必绑功能。选择一种并在系统授权、官方配置页完成设置即可。")
        }
        if (preview == null || showStyles) {
            if (providers.isEmpty()) {
                GkVehicleSection("未找到官方组件", "请安装比亚迪官方 App，登录并选择车辆，再返回此页。")
            } else {
                val sizes = providers.groupBy { "${it.minWidth}x${it.minHeight}" }
                val preferred = sizes.values.firstOrNull { it.size > 1 }?.let { if (secondary) it[1] else it[0] } ?: providers.first()
                ProviderCard(preferred, true) { vm.bind(preferred, secondary); showStyles = false }
                var alternatives by rememberSaveable(secondary) { mutableStateOf(false) }
                TextButton(onClick = { alternatives = !alternatives }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(if (alternatives) "收起其他样式" else "查看其他 ${providers.size - 1} 种样式")
                }
                if (alternatives) providers.filter { it.provider != preferred.provider }.forEach { provider ->
                    ProviderCard(provider, false) { vm.bind(provider, secondary); showStyles = false }
                }
            }
        }
        GkVehicleSection("执行设置", "绑定和修改设置会暂停车辆功能，完成后请重新启动。")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("点击延迟：${config.clickDelayMs} 毫秒")
            Slider(value = config.clickDelayMs.toFloat(), onValueChange = { value -> VehicleStore.update { it.copy(clickDelayMs = value.toInt()) } }, valueRange = 0f..1500f)
        }
    }
}

@Composable
private fun ProviderCard(info: AppWidgetProviderInfo, preferred: Boolean, bind: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${info.loadLabel(context.packageManager)}${if (preferred) " · 建议先试" else ""}")
            Text("尺寸约 ${(info.minWidth / density).toInt()} × ${(info.minHeight / density).toInt()} dp")
            val drawable = remember(info.provider) { info.loadPreviewImage(context, 0) }
            if (drawable != null) AndroidView(factory = {
                ImageView(it).apply { setImageDrawable(drawable); scaleType = ImageView.ScaleType.FIT_CENTER }
            }, modifier = Modifier.fillMaxWidth().height(140.dp))
            Button(onClick = bind) { Text("绑定此样式") }
        }
    }
}
