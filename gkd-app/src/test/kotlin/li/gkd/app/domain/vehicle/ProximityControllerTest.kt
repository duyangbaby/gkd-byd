package li.gkd.app.domain.vehicle

import org.junit.Assert.*
import org.junit.Test

class ProximityControllerTest {
    private fun controller(state: VehicleState) = ProximityController(-80, -90).apply { confirmState(state) }

    @Test fun unknownStateAndStartingNearbyNeverUnlock() {
        val unknown = controller(VehicleState.UNKNOWN)
        val locked = controller(VehicleState.LOCKED)
        for (time in 0L..10_000L step 1000) {
            assertNull(unknown.observe(-70, time))
            assertNull(locked.observe(-70, time))
        }
    }

    @Test fun approachRequiresFarEvidenceAndSustainedNearSignal() {
        val c = controller(VehicleState.LOCKED)
        c.observe(-95, 0)
        assertNull(c.observe(-78, 1000))
        assertNull(c.observe(-78, 2000))
        assertEquals(VehicleCommand.UNLOCK, c.observe(-78, 3000))
        assertNull(c.observe(-70, 4000)) // No duplicate while click is pending.
        c.complete(true)
        assertEquals(VehicleState.UNLOCKED, c.state)
    }

    @Test fun fluctuationAndDuplicatePacketsDoNotCauseLock() {
        val c = controller(VehicleState.UNLOCKED)
        assertFalse(c.canPauseStationary())
        assertNull(c.observe(-95, 0))
        assertNull(c.observe(-95, 10))
        assertNull(c.observe(-85, 1000))
        assertNull(c.observe(-95, 2000))
        assertNull(c.observe(-95, 3000))
        assertEquals(VehicleCommand.LOCK, c.observe(-95, 4000))
    }

    @Test fun scanGapCannotCompleteOldConfirmation() {
        val c = controller(VehicleState.UNLOCKED)
        c.observe(-95, 0)
        c.observe(-95, 1000)
        assertNull(c.observe(-95, 20_000))
        assertNull(c.observe(-95, 21_000))
        assertEquals(VehicleCommand.LOCK, c.observe(-95, 22_000))
    }

    @Test fun failedClickRequiresManualReconciliation() {
        val c = controller(VehicleState.UNLOCKED)
        c.observe(-95, 0)
        c.observe(-95, 1000)
        c.observe(-95, 2000)
        c.complete(false)
        assertEquals(VehicleState.UNKNOWN, c.state)
        assertFalse(c.canPauseStationary())
        assertNull(c.observe(-95, 3000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlappingThresholdsAreRejected() { ProximityController(-90, -80) }

    @Test fun recentUnlockDoesNotImmediatelyRelockOnBodyShadow() {
        val c = controller(VehicleState.LOCKED)
        c.observe(-95, 0)
        c.observe(-70, 1000)
        c.observe(-70, 2000)
        c.observe(-70, 3000)
        c.complete(true)
        for (time in 4000L..17_000L step 1000) assertNull(c.observe(-95, time))
        assertNull(c.observe(-95, 18_000))
        assertNull(c.observe(-95, 19_000))
        assertEquals(VehicleCommand.LOCK, c.observe(-95, 20_000))
    }
}
