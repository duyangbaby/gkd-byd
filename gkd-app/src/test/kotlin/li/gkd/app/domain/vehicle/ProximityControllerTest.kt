package li.gkd.app.domain.vehicle

import org.junit.Assert.*
import org.junit.Test

class ProximityControllerTest {
    private fun controller(state: VehicleState) = ProximityController(-80, -90).apply { confirmState(state) }

    @Test fun diagnosticsDistinguishWaitingForFarSignalFromCountingApproach() {
        val c = ProximityController(-87, -82, unlockCount = 4, unlockOffset = 2).apply { confirmState(VehicleState.LOCKED) }
        c.observe(-70, 0)
        assertTrue(c.status(0).reason.contains("等待远处信号"))
        assertEquals(0, c.status(0).required)
        c.observe(-95, 1000)
        c.observe(-86, 2000)
        assertEquals(-84, c.status(2000).target)
        assertEquals(0, c.status(2000).confirmations)
        c.observe(-83, 3000)
        assertEquals(1, c.status(3000).confirmations)
        assertEquals(4, c.status(3000).required)
        assertEquals(-87, c.status(3000).unlockThreshold)
    }

    @Test fun strongAndVeryWeakValidSignalsAreNotDiscardedAtTheOldRssiLimits() {
        val c = controller(VehicleState.LOCKED)
        c.observe(-115, 0)
        assertNull(c.observe(-15, 1000))
        assertNull(c.observe(-15, 2000))
        assertEquals(VehicleCommand.UNLOCK, c.observe(-15, 3000))
    }

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

    @Test fun overlappingThresholdsCannotRelockUntilBufferIsCleared() {
        val c = ProximityController(-87, -82, cooldownMs = 0).apply { confirmState(VehicleState.LOCKED) }
        c.observe(-95, 0)
        c.observe(-85, 1000)
        c.observe(-85, 2000)
        assertEquals(VehicleCommand.UNLOCK, c.observe(-85, 3000))
        c.complete(true)
        for (time in 4000L..12_000L step 1000) assertNull(c.observe(-85, time))
        c.observe(-76, 13_000) // Original +5 dB near-side condition removes lock inhibition.
        assertNull(c.observe(-85, 14_000))
        assertNull(c.observe(-85, 15_000))
        assertEquals(VehicleCommand.LOCK, c.observe(-85, 16_000))
        c.complete(true)
        for (time in 17_000L..22_000L step 1000) assertNull(c.observe(-85, time))
    }

    @Test fun confirmationRequiresConfiguredSignalIncrease() {
        val c = ProximityController(-87, -82, unlockOffset = 2).apply { confirmState(VehicleState.LOCKED) }
        c.observe(-95, 0)
        for (time in 1000L..5000L step 1000) assertNull(c.observe(-87, time))
        assertNull(c.observe(-85, 6000))
        assertNull(c.observe(-85, 7000))
        assertEquals(VehicleCommand.UNLOCK, c.observe(-85, 8000))
    }

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
