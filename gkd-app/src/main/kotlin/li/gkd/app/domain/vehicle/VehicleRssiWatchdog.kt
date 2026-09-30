package li.gkd.app.domain.vehicle

/** BluetoothService's coordinated reader: six grace checks, then three missing samples. */
class VehicleRssiWatchdog(private val intervalMs: Long) {
    private var checks = 0
    private var misses = 0
    private var lastSample: Long? = null

    fun sample(time: Long) { lastSample = time }

    fun check(now: Long): Boolean {
        val grace = checks < 6
        val fresh = lastSample?.let { now - it in 0..(if (grace) 4000 else intervalMs) } == true
        if (fresh) misses = 0 else if (!grace) misses++
        checks++
        return misses >= 3
    }
}
