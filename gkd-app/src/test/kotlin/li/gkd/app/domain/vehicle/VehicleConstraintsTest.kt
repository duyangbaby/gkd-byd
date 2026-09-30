package li.gkd.app.domain.vehicle

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VehicleConstraintsTest {
    private val wifi = VehicleConstraintSettings(wifiEnabled = true, wifiNames = listOf("home", "office"))
    private val home = VehicleEnvironmentSnapshot(true, "home")

    @Test fun selectedWifiImmediatelyStopsAndDisconnectRestoresScanning() {
        val stopped = VehicleConstraints.evaluate(wifi, home)
        assertTrue(stopped.paused)
        assertFalse(stopped.unlockAllowed)
        assertTrue(VehicleConstraints.evaluate(wifi, home.copy(wifiName = "office")).paused)
        val resumed = VehicleConstraints.evaluate(wifi, VehicleEnvironmentSnapshot())
        assertFalse(resumed.paused)
        assertTrue(resumed.unlockAllowed)
    }

    @Test fun unselectedWifiAndDisabledConstraintsKeepScanning() {
        assertFalse(VehicleConstraints.evaluate(wifi, home.copy(wifiName = "other")).paused)
        assertFalse(VehicleConstraints.evaluate(wifi.copy(wifiEnabled = false), home).paused)
        assertFalse(VehicleConstraints.evaluate(wifi.copy(wifiNames = emptyList()), home).paused)
    }

    @Test fun olderSavedSignalGateSettingsCannotOverrideImmediateStopRule() {
        // Existing exported settings keep selected networks; removed modes are ignored on load.
        val settings = Json { ignoreUnknownKeys = true }.decodeFromString<VehicleConstraintSettings>(
            """{"wifiEnabled":true,"wifiNames":["home"],"wifiMode":"SIGNAL_GATE","wifiThreshold":-88,"carWifi":false}""")
        assertEquals(listOf("home"), settings.wifiNames)
        assertTrue(VehicleConstraints.evaluate(settings, home).paused)
    }

    @Test fun missingSsidCannotBeTreatedAsASelectedNetwork() {
        val unavailable = VehicleConstraints.evaluate(wifi, home.copy(wifiName = null))
        assertFalse(unavailable.paused)
        assertFalse(unavailable.unlockAllowed)
        assertNotNull(unavailable.reason)
    }

    @Test fun anySelectedBluetoothPausesUntilAllSelectedDevicesDisconnect() {
        val settings = VehicleConstraintSettings(bluetoothEnabled = true, bluetoothAddresses = listOf("AA", "BB"))
        assertTrue(VehicleConstraints.evaluate(settings, VehicleEnvironmentSnapshot(connectedBluetooth = setOf("aa", "OTHER"))).paused)
        assertTrue(VehicleConstraints.evaluate(settings, VehicleEnvironmentSnapshot(connectedBluetooth = setOf("BB"))).paused)
        assertFalse(VehicleConstraints.evaluate(settings, VehicleEnvironmentSnapshot(connectedBluetooth = setOf("OTHER"))).paused)
    }

    @Test fun carMultimediaBluetoothImmediatelyPausesAndDisconnectResumes() {
        val settings = VehicleConstraintSettings(carBluetoothEnabled = true, carBluetoothAddress = "CAR")
        val connected = VehicleEnvironmentSnapshot(connectedBluetooth = setOf("car"))
        assertTrue(VehicleConstraints.evaluate(settings, connected).paused)
        assertFalse(VehicleConstraints.evaluate(settings, VehicleEnvironmentSnapshot()).paused)
        assertFalse(VehicleConstraints.evaluate(settings.copy(carBluetoothEnabled = false), connected).paused)
    }

    @Test fun disconnectingOneConstraintDoesNotResumeWhileAnotherStillMatches() {
        val settings = wifi.copy(carBluetoothEnabled = true, carBluetoothAddress = "CAR")
        assertTrue(VehicleConstraints.evaluate(settings, home.copy(connectedBluetooth = setOf("CAR"))).paused)
        assertTrue(VehicleConstraints.evaluate(settings, home).paused)
        assertTrue(VehicleConstraints.evaluate(settings, VehicleEnvironmentSnapshot(connectedBluetooth = setOf("CAR"))).paused)
        assertFalse(VehicleConstraints.evaluate(settings, VehicleEnvironmentSnapshot()).paused)
    }

    @Test fun deniedUnlockClearsCandidateWithoutBlockingDepartureLock() {
        val controller = ProximityController(-80, -90).apply { confirmState(VehicleState.LOCKED) }
        controller.observe(-95, 0)
        controller.observe(-70, 1000)
        for (time in 2000L..6000L step 1000) assertNull(controller.observe(-70, time, unlockAllowed = false))
        assertEquals(0, controller.status(6000).confirmations)
        assertNull(controller.observe(-70, 7000))
        assertNull(controller.observe(-70, 8000))
        assertEquals(VehicleCommand.UNLOCK, controller.observe(-70, 9000))
        controller.confirmState(VehicleState.UNLOCKED)
        controller.observe(-95, 10000, unlockAllowed = false)
        controller.observe(-95, 11000, unlockAllowed = false)
        assertEquals(VehicleCommand.LOCK, controller.observe(-95, 12000, unlockAllowed = false))
    }
}
