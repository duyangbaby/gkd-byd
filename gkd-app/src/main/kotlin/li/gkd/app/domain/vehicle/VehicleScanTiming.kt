package li.gkd.app.domain.vehicle

/** Timing recovered from BluetoothService.Z1/R1 and bp. Both modes share scan_sleep_seconds. */
data class VehicleScanTiming(val sleepSeconds: Int) {
    init { require(sleepSeconds in 1..5) }
    val sleepMs = sleepSeconds * 1000L
    val scanWindowMs = (sleepMs * 5 / 2).coerceIn(5000, 60_000)
    val connectTimeoutMs = sleepMs * 5 / 2
    val restartAfterAttempts = maxOf(10, (300_000 / (connectTimeoutMs + sleepMs)).toInt())
    fun logCycle(cycle: Int) = cycle <= 5 || cycle % 10 == 0
}
