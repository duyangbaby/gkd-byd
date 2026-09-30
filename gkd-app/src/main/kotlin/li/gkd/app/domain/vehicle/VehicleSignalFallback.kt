package li.gkd.app.domain.vehicle

/** A mixed-mode GATT link without fresh samples must yield to advertising scans. */
class VehicleSignalFallback {
    private var gattStartedAt: Long? = null
    private var fallbackUntil = 0L
    private var lastGattSample: Long? = null

    fun useGatt(now: Long, requested: Boolean, mixed: Boolean): Boolean {
        if (!requested) { gattStartedAt = null; return false }
        if (!mixed) return true
        if (now < fallbackUntil) return false
        val started = gattStartedAt ?: now.also { gattStartedAt = it }
        val freshSince = lastGattSample?.takeIf { it >= started } ?: started
        if (now - freshSince >= 15_000) {
            fallbackUntil = now + 30_000
            gattStartedAt = null
            return false
        }
        return true
    }

    fun sample(now: Long) { lastGattSample = now }
}
