package li.gkd.app.domain.vehicle

enum class VehiclePowerState { UNKNOWN, OFF, ON }

/** Power evidence is independent of door state; an unknown power state cannot trigger a command. */
class VehiclePowerController(
    initial: VehiclePowerState,
    private val onEnabled: Boolean,
    private val offEnabled: Boolean,
    private val onRssi: Int,
    private val offRssi: Int,
    private val confirmations: Int,
    private val afterUnlockDelayMs: Long,
) {
    init {
        require(onRssi in -110..-20 && offRssi in -110..-20 && offRssi < onRssi)
        require(confirmations > 0 && afterUnlockDelayMs >= 0)
    }
    var state = initial
        private set
    var pending: VehicleCommand? = null
        private set
    private var lastSample: Long? = null
    private var unlockedAt: Long? = null
    private var candidate: VehicleCommand? = null
    private var count = 0
    private var firstSample = 0L

    fun afterUnlock(now: Long) { unlockedAt = now; resetSamples() }
    fun resetSamples() { candidate = null; count = 0; lastSample = null }
    fun needsDepartureMonitoring() = offEnabled && state == VehiclePowerState.ON

    fun observe(rssi: Int, now: Long, door: VehicleState): VehicleCommand? {
        if (state == VehiclePowerState.UNKNOWN || pending != null || rssi !in -127..-1) return null
        val previous = lastSample
        if (previous != null && now - previous < 300) return null
        if (previous != null && now - previous > 10_000) resetSamples()
        lastSample = now
        val action = when {
            onEnabled && state == VehiclePowerState.OFF && door == VehicleState.UNLOCKED && rssi >= onRssi &&
                (unlockedAt == null || now - unlockedAt!! >= afterUnlockDelayMs) -> VehicleCommand.POWER_ON
            offEnabled && state == VehiclePowerState.ON && rssi <= offRssi -> VehicleCommand.POWER_OFF
            else -> null
        }
        if (action == null) { candidate = null; count = 0; return null }
        if (action != candidate) { candidate = action; count = 0; firstSample = now }
        count++
        if (count < confirmations || now - firstSample < 2000) return null
        pending = action
        return action
    }

    fun complete(dispatched: Boolean) {
        val action = pending ?: return
        state = if (!dispatched) VehiclePowerState.UNKNOWN else if (action == VehicleCommand.POWER_ON) VehiclePowerState.ON else VehiclePowerState.OFF
        pending = null
        resetSamples()
    }
}
