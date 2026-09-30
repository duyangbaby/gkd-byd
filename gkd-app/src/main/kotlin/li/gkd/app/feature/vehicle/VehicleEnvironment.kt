package li.gkd.app.feature.vehicle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.domain.vehicle.VehicleEnvironmentSnapshot

/** Passive connection callbacks only. Never scans Wi-Fi or opens a connection to a constraint device. */
@SuppressLint("MissingPermission")
class VehicleEnvironment(private val context: Context, private val scope: CoroutineScope,
    private val changed: (VehicleEnvironmentSnapshot) -> Unit) {
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val links = mutableMapOf<Int, Set<String>>()
    private val acl = mutableSetOf<String>()
    private val profiles = mutableMapOf<Int, BluetoothProfile>()
    private val networks = mutableMapOf<Network, WifiInfo?>()
    private var active = false
    private var networkRegistered = false
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!active) { bluetooth.adapter?.closeProfileProxy(profile, proxy); return }
            scope.launch {
                if (!active) { bluetooth.adapter?.closeProfileProxy(profile, proxy); return@launch }
                profiles[profile] = proxy
                refreshBluetooth()
                publish()
            }
        }
        override fun onServiceDisconnected(profile: Int) { scope.launch {
            profiles.remove(profile)
            links.remove(profile)
            if (active) publish()
        } }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { scope.launch {
            if (active) {
                networks[network] = capabilities.transportInfo as? WifiInfo
                publish()
            }
        } }
        override fun onLost(network: Network) { scope.launch {
            networks.remove(network)
            if (active) publish()
        } }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (device != null) {
                val address = device.address.uppercase()
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> acl.add(address)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> acl.remove(address)
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) acl.add(address)
                        else if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_DISCONNECTED) acl.remove(address)
                    }
                }
                refreshBluetooth()
            }
            if (active) publish()
        }
    }

    fun start() {
        if (active) return
        active = true
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_EXPORTED)
        connectivity.allNetworks.forEach { network -> connectivity.getNetworkCapabilities(network)?.let { caps ->
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) networks[network] = caps.transportInfo as? WifiInfo
        } }
        try {
            connectivity.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), networkCallback)
            networkRegistered = true
        } catch (_: SecurityException) { VehicleStore.report("无法监听 Wi-Fi，请检查网络状态权限") }
        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { bluetooth.adapter?.getProfileProxy(context, profileListener, it) }
        refreshBluetooth()
        publish()
    }

    private fun refreshBluetooth() {
        try {
            profiles.forEach { (profile, proxy) -> links[profile] = proxy.connectedDevices.map { it.address.uppercase() }.toSet() }
            links[BluetoothProfile.GATT] = bluetooth.getConnectedDevices(BluetoothProfile.GATT).map { it.address.uppercase() }.toSet()
            links[BluetoothProfile.GATT_SERVER] = bluetooth.getConnectedDevices(BluetoothProfile.GATT_SERVER).map { it.address.uppercase() }.toSet()
        } catch (_: SecurityException) { VehicleStore.report("读取蓝牙连接状态失败，请重新授予附近设备权限") }
    }

    private fun publish() {
        @Suppress("DEPRECATION")
        val current = try { wifi.connectionInfo } catch (_: SecurityException) { null }
        fun name(info: WifiInfo?) = info?.ssid?.takeUnless { it == WifiManager.UNKNOWN_SSID }?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
        val callbackInfo = networks.values.firstOrNull { name(it) != null }
        val wifiName = name(current) ?: name(callbackInfo)
        changed(VehicleEnvironmentSnapshot(networks.isNotEmpty(), wifiName, (acl + links.values.flatten()).toSet()))
    }

    fun stop() {
        if (!active) return
        active = false
        context.unregisterReceiver(receiver)
        if (networkRegistered) connectivity.unregisterNetworkCallback(networkCallback)
        networkRegistered = false
        profiles.forEach { (profile, proxy) -> bluetooth.adapter?.closeProfileProxy(profile, proxy) }
        profiles.clear()
        links.clear()
        acl.clear()
        networks.clear()
    }
}
