package li.gkd.app.domain.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleSignalFallbackTest {
    @Test fun mixedModeFallsBackWithoutSamplesThenRetriesGatt() {
        val policy = VehicleSignalFallback()
        assertTrue(policy.useGatt(0, true, true))
        assertTrue(policy.useGatt(14_999, true, true))
        assertFalse(policy.useGatt(15_000, true, true))
        assertFalse(policy.useGatt(44_999, true, true))
        assertTrue(policy.useGatt(45_000, true, true))
        assertFalse(policy.useGatt(60_000, true, true))
    }

    @Test fun FreshSamplesKeepGattAndStaleSamplesCannotKeepNewSessionAlive() {
        val policy = VehicleSignalFallback()
        assertTrue(policy.useGatt(0, true, true))
        for (time in 10_000L..60_000L step 10_000) {
            policy.sample(time)
            assertTrue(policy.useGatt(time, true, true))
        }
        assertFalse(policy.useGatt(75_000, true, true))
        assertTrue(policy.useGatt(105_000, true, true))
        assertTrue(policy.useGatt(119_999, true, true))
        assertFalse(policy.useGatt(120_000, true, true))
    }

    @Test fun explicitGattModeDoesNotSilentlyChangeTheUsersSelection() {
        val policy = VehicleSignalFallback()
        assertTrue(policy.useGatt(0, true, false))
        assertTrue(policy.useGatt(100_000, true, false))
        assertFalse(policy.useGatt(100_001, false, false))
    }
}
