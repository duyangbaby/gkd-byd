package li.gkd.app.feature.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VehicleScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VehicleService.receiveScan(intent)
    }
}
