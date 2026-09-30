package li.gkd.app.domain.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehiclePowerControllerTest {
    private fun controller(state: VehiclePowerState) = VehiclePowerController(state, true, true, -82, -95, 3, 5000)

    @Test fun unknownPowerNeverTriggersAndLockedDoorCannotPowerOn() {
        val unknown = controller(VehiclePowerState.UNKNOWN)
        val off = controller(VehiclePowerState.OFF)
        for (time in 0L..10_000L step 1000) {
            assertNull(unknown.observe(-70, time, VehicleState.UNLOCKED))
            assertNull(off.observe(-70, time, VehicleState.LOCKED))
        }
    }

    @Test fun powerOnWaitsAfterUnlockAndRequiresFreshSustainedSamples() {
        val c = controller(VehiclePowerState.OFF)
        c.afterUnlock(0)
        for (time in 0L..4000L step 1000) assertNull(c.observe(-70, time, VehicleState.UNLOCKED))
        assertNull(c.observe(-70, 5000, VehicleState.UNLOCKED))
        assertNull(c.observe(-70, 5010, VehicleState.UNLOCKED))
        assertNull(c.observe(-70, 6000, VehicleState.UNLOCKED))
        assertEquals(VehicleCommand.POWER_ON, c.observe(-70, 7000, VehicleState.UNLOCKED))
        assertNull(c.observe(-70, 8000, VehicleState.UNLOCKED))
        c.complete(true)
        assertEquals(VehiclePowerState.ON, c.state)
        assertTrue(c.needsDepartureMonitoring())
    }

    @Test fun staleOrInterruptedWeakSignalCannotPowerOff() {
        val c = controller(VehiclePowerState.ON)
        c.observe(-100, 0, VehicleState.UNLOCKED)
        c.observe(-100, 1000, VehicleState.UNLOCKED)
        assertNull(c.observe(-100, 20_000, VehicleState.UNLOCKED))
        assertNull(c.observe(-80, 21_000, VehicleState.UNLOCKED))
        assertNull(c.observe(-100, 22_000, VehicleState.UNLOCKED))
        assertNull(c.observe(-100, 23_000, VehicleState.UNLOCKED))
        assertEquals(VehicleCommand.POWER_OFF, c.observe(-100, 24_000, VehicleState.UNLOCKED))
        c.complete(false)
        assertEquals(VehiclePowerState.UNKNOWN, c.state)
        assertNull(c.observe(-100, 25_000, VehicleState.UNLOCKED))
    }
}
