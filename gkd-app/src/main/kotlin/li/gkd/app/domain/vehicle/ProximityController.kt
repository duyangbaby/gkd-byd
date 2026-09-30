package li.gkd.app.domain.vehicle

enum class VehicleState { UNKNOWN, LOCKED, UNLOCKED }
enum class VehicleCommand(val label: String) { UNLOCK("解锁"), LOCK("上锁"), POWER_ON("上电"), POWER_OFF("下电"), CLOSE_WINDOWS("关窗") }

data class ProximityStatus(
    val state: VehicleState,
    val reason: String,
    val unlockThreshold: Int,
    val lockThreshold: Int,
    val pocket: Boolean,
    val target: Int? = null,
    val confirmations: Int = 0,
    val required: Int = 0,
)

/** RSSI is evidence of proximity, never proof of the physical door state. */
class ProximityController(
    private val unlockRssi: Int,
    private val lockRssi: Int,
    private val unlockCount: Int = 3,
    private val lockCount: Int = 3,
    private val unlockOffset: Int = 0,
    private val lockOffset: Int = 0,
    private val cooldownMs: Long = 15_000,
    private val unlockEnabled: Boolean = true,
    private val lockEnabled: Boolean = true,
    private val unlockPocketOffset: Int = 0,
    private val lockPocketOffset: Int = 0,
) {
    init {
        require(lockRssi in -110..-30 && unlockRssi in -110..-30)
        require(unlockCount in 1..10 && lockCount in 1..10 && unlockOffset in 0..8 && lockOffset in 0..8 && cooldownMs >= 0)
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
    private var lastRssi: Int? = null
    private var lockProhibited = false
    private var unlockProhibited = false
    private var targetRssi: Int? = null
    private var lastPocket = false
    private var appliedUnlock = unlockRssi
    private var appliedLock = lockRssi

    fun confirmState(value: VehicleState) {
        state = value
        pending = null
        approachedFromFar = false
        lastCommandTime = null
        lockProhibited = false
        unlockProhibited = false
        resetSamples()
    }

    fun resetSamples() {
        candidate = null
        count = 0
        lastSample = null
        targetRssi = null
    }

    fun observe(rssi: Int, now: Long, pocket: Boolean = false, unlockAllowed: Boolean = true): VehicleCommand? {
        if (rssi !in -127..-1 || state == VehicleState.UNKNOWN || pending != null) return null
        if (pocket != lastPocket) {
            resetSamples()
            lastPocket = pocket
        }
        val unlockRssi = (this.unlockRssi - if (pocket) unlockPocketOffset else 0).coerceAtLeast(-110)
        val lockRssi = (this.lockRssi - if (pocket) lockPocketOffset else 0).coerceAtLeast(-110)
        appliedUnlock = unlockRssi
        appliedLock = lockRssi
        val previous = lastSample
        if (previous != null && now - previous < 300) return null
        if (previous != null && now - previous > 10_000) resetSamples()
        lastSample = now
        lastRssi = rssi
        if (rssi <= minOf(lockRssi, unlockRssi - 5)) approachedFromFar = true
        if (lockProhibited && (rssi >= lockRssi + 5 || rssi <= unlockRssi - 5)) lockProhibited = false
        if (unlockProhibited && rssi <= unlockRssi - 5) unlockProhibited = false
        if (lastCommandTime?.let { now - it < cooldownMs } == true) return null
        val action = when {
            state == VehicleState.LOCKED && unlockAllowed && unlockEnabled && !unlockProhibited && approachedFromFar && rssi >= unlockRssi -> VehicleCommand.UNLOCK
            state == VehicleState.UNLOCKED && lockEnabled && !lockProhibited && rssi <= lockRssi -> VehicleCommand.LOCK
            else -> null
        }
        if (action == null) {
            candidate = null
            count = 0
            targetRssi = null
            return null
        }
        if (candidate != action) {
            candidate = action
            firstSample = now
            count = 0
            targetRssi = if (action == VehicleCommand.UNLOCK) rssi + unlockOffset else rssi - lockOffset
        }
        if (now - firstSample > 15_000) {
            candidate = null
            count = 0
            targetRssi = null
            return null
        }
        val reached = if (action == VehicleCommand.UNLOCK) rssi >= targetRssi!! else rssi <= targetRssi!!
        count = if (reached) count + 1 else 0
        if (count < (if (action == VehicleCommand.UNLOCK) unlockCount else lockCount) || now - firstSample < 2_000) return null
        pending = action
        return action
    }

    /** Dispatched means only that the widget accepted the click, not that BYD confirmed it. */
    fun complete(dispatched: Boolean) {
        val action = pending ?: return
        state = if (!dispatched) VehicleState.UNKNOWN else when (action) {
            VehicleCommand.UNLOCK -> VehicleState.UNLOCKED
            VehicleCommand.LOCK -> VehicleState.LOCKED
            else -> VehicleState.UNKNOWN
        }
        pending = null
        lockProhibited = dispatched && action == VehicleCommand.UNLOCK && (lastRssi ?: Int.MIN_VALUE) <= appliedLock
        unlockProhibited = dispatched && action == VehicleCommand.LOCK && (lastRssi ?: Int.MAX_VALUE) >= appliedUnlock
        lastCommandTime = lastSample
        approachedFromFar = false
        resetSamples()
    }

    fun canPauseStationary() = state == VehicleState.LOCKED && pending == null

    fun status(now: Long): ProximityStatus {
        val cooling = lastCommandTime?.let { (cooldownMs - (now - it)).coerceAtLeast(0) } ?: 0
        val required = if (candidate == VehicleCommand.UNLOCK) unlockCount else lockCount
        val reason = when {
            pending != null -> "正在执行${pending!!.label}"
            state == VehicleState.UNKNOWN -> "请先确认当前车门状态"
            cooling > 0 -> "指令冷却中，剩余 ${(cooling + 999) / 1000} 秒"
            state == VehicleState.LOCKED && !unlockEnabled -> "靠近自动解锁已关闭"
            state == VehicleState.UNLOCKED && !lockEnabled -> "远离自动上锁已关闭"
            state == VehicleState.LOCKED && unlockProhibited -> "临时禁止解锁，等待远离至 ${appliedUnlock - 5} dBm"
            state == VehicleState.UNLOCKED && lockProhibited -> "临时禁止上锁，需靠近至 ${appliedLock + 5} 或远离至 ${appliedUnlock - 5} dBm"
            state == VehicleState.LOCKED && !approachedFromFar -> "等待远处信号 ≤ ${minOf(appliedLock, appliedUnlock - 5)} dBm，再检测靠近"
            candidate != null -> "${candidate!!.label}确认 $count/$required 次，需至少持续 2 秒"
            state == VehicleState.LOCKED -> "等待靠近至 ≥ $appliedUnlock dBm"
            else -> "等待远离至 ≤ $appliedLock dBm"
        }
        return ProximityStatus(state, reason, appliedUnlock, appliedLock, lastPocket, targetRssi, count,
            if (candidate != null) required else 0)
    }
}
