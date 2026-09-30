package li.gkd.app.domain.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleRssiWatchdogTest {
    @Test fun reconnectGetsSixGraceChecksBeforeThreeMissingSamplesRestart() {
        val watchdog = VehicleRssiWatchdog(500)
        repeat(8) { assertFalse(watchdog.check(it * 500L)) }
        assertTrue(watchdog.check(4000))
    }
    @Test fun freshSamplesClearMissingCountAndInclusiveIntervalRemainsValid() {
        val watchdog = VehicleRssiWatchdog(500)
        repeat(6) { watchdog.check(it * 500L) }
        assertFalse(watchdog.check(3000))
        assertFalse(watchdog.check(3500))
        watchdog.sample(3500)
        assertFalse(watchdog.check(4000))
        assertFalse(watchdog.check(4500))
        assertFalse(watchdog.check(5000))
        assertTrue(watchdog.check(5500))
    }
}
