package li.gkd.app.domain.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleScanTimingTest {
    // Protect the recovered app's external 5-second scan / 2-second silence cadence.
    @Test fun defaultScanAndFailedGattBothRepeatEverySevenSeconds() {
        val timing = VehicleScanTiming(2)
        assertEquals(7000L, timing.scanWindowMs + timing.sleepMs)
        assertEquals(7000L, timing.connectTimeoutMs + timing.sleepMs)
        assertEquals(5000L, timing.scanWindowMs)
    }
    @Test fun oneSecondSilenceStillScansAtLeastFiveSecondsButGattHasShorterDeadline() {
        val timing = VehicleScanTiming(1)
        assertEquals(6000L, timing.scanWindowMs + timing.sleepMs)
        assertEquals(3500L, timing.connectTimeoutMs + timing.sleepMs)
        assertTrue(timing.restartAfterAttempts * (timing.connectTimeoutMs + timing.sleepMs) in 295000L..300000L)
    }
    @Test fun fiveSecondSilenceLengthensBothActiveWindows() {
        val timing = VehicleScanTiming(5)
        assertEquals(17500L, timing.scanWindowMs + timing.sleepMs)
        assertEquals(17500L, timing.connectTimeoutMs + timing.sleepMs)
    }
}
