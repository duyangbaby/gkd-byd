package li.gkd.app.feature.vehicle

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import li.gkd.app.domain.vehicle.ProximityStatus

data class VehicleSignal(
    val rssi: Int? = null,
    val sampledAt: Long? = null,
    val source: String = "",
    val samples: Int = 0,
    val mode: String = "车辆功能已暂停",
    val decision: ProximityStatus? = null,
    val blocked: String? = null,
)

@Composable
fun GkVehicleSignal(modifier: Modifier = Modifier) {
    val signal by VehicleService.signal.collectAsStateWithLifecycle()
    val running by VehicleService.running.collectAsStateWithLifecycle()
    val config by VehicleStore.config.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lifecycleOwner, signal.sampledAt != null) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            now = SystemClock.elapsedRealtime()
            if (signal.sampledAt != null) while (true) { delay(1000); now = SystemClock.elapsedRealtime() }
        }
    }
    val age = signal.sampledAt?.let { ((now - it).coerceAtLeast(0) / 1000) }
    val fresh = running && age != null && age <= 5
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("车辆蓝牙信号", style = MaterialTheme.typography.titleSmall)
            Text(signal.rssi?.let { "$it dBm${if (fresh) "" else " · 上次信号"}" } ?: "尚未收到信号",
                style = MaterialTheme.typography.headlineMedium,
                color = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(config.deviceName.ifEmpty { "控车 BLE" } + " · " + config.address.ifEmpty { "尚未绑定" }, style = MaterialTheme.typography.bodySmall)
            Text(signal.mode + (if (age != null) " · ${signal.source} · ${age} 秒前更新" else ""), style = MaterialTheme.typography.bodySmall)
            if (signal.samples > 0) Text("本次已接收 ${signal.samples} 条有效信号", style = MaterialTheme.typography.bodySmall)
            signal.decision?.let { decision ->
                Text("${if (decision.pocket) "口袋" else "手持"}：解锁 ≥ ${decision.unlockThreshold} · 上锁 ≤ ${decision.lockThreshold} dBm",
                    style = MaterialTheme.typography.bodySmall)
                Text(when {
                    !running -> "车辆功能已暂停"
                    signal.blocked != null -> signal.blocked!!
                    age == null -> "未收到绑定设备的广播或 GATT 信号，请检查蓝牙地址、车辆广播和权限"
                    age > 10 -> "超过 10 秒没有新信号，确认计数不能继续；请检查连接及车辆广播"
                    else -> decision.reason
                }, style = MaterialTheme.typography.bodyMedium)
                if (fresh && decision.target != null) Text("本次确认目标 ${decision.target} dBm", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
