package li.gkd.app.domain.vehicle

enum class VehicleState { UNKNOWN, LOCKED, UNLOCKED }
enum class VehicleCommand { UNLOCK, LOCK }

/** RSSI is evidence of proximity, never proof of the physical door state. */
class ProximityController(
    private val unlockRssi: Int,
    private val lockRssi: Int,
) {
    init {
        require(lockRssi in -110..-30 && unlockRssi in -110..-30 && lockRssi < unlockRssi)
    }

    var state = VehicleState.UNKNOWN
        private set
    var pending: VehicleCommand? = null
        private set
    private var approachedFromFar = false
    private var candidate: VehicleCommand? = null
    private var firstSample = 0L
    private var lastSample: Long? = null
    private var count = 0
    private var lastCommandTime: Long? = null

    fun confirmState(value: VehicleState) {
        state = value
        pending = null
        approachedFromFar = false
        lastCommandTime = null
        resetSamples()
    }

    fun resetSamples() {
        candidate = null
        count = 0
        lastSample = null
    }

    fun observe(rssi: Int, now: Long): VehicleCommand? {
        if (rssi !in -110..-20 || state == VehicleState.UNKNOWN || pending != null) return null
        val previous = lastSample
        if (previous != null && now - previous < 300) return null
        if (previous != null && now - previous > 10_000) resetSamples()
        lastSample = now
        if (rssi <= lockRssi) approachedFromFar = true
        if (lastCommandTime?.let { now - it < 15_000 } == true) return null
        val action = when {
            state == VehicleState.LOCKED && approachedFromFar && rssi >= unlockRssi -> VehicleCommand.UNLOCK
            state == VehicleState.UNLOCKED && rssi <= lockRssi -> VehicleCommand.LOCK
            else -> null
        }
        if (action == null) {
            candidate = null
            count = 0
            return null
        }
        if (candidate != action) {
            candidate = action
            firstSample = now
            count = 0
        }
        count++
        if (count < 3 || now - firstSample < 2_000) return null
        pending = action
        return action
    }

    /** Dispatched means only that the widget accepted the click, not that BYD confirmed it. */
    fun complete(dispatched: Boolean) {
        val action = pending ?: return
        state = if (!dispatched) VehicleState.UNKNOWN else when (action) {
            VehicleCommand.UNLOCK -> VehicleState.UNLOCKED
            VehicleCommand.LOCK -> VehicleState.LOCKED
        }
        pending = null
        lastCommandTime = lastSample
        approachedFromFar = false
        resetSamples()
    }

    fun canPauseStationary() = state == VehicleState.LOCKED && pending == null
}
