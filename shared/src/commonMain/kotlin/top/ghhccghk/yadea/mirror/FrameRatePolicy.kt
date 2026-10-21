package top.ghhccghk.yadea.mirror

/**
 * Vehicle 0x20 response byte 7 is the vehicle-supported maximum frame rate.
 * YadeaProtocol accepts 1..30 and substitutes 10 for missing or invalid values.
 */
object FrameRatePolicy {
    const val MIN_FPS = 1
    const val MAX_FPS = 30
    const val SAFE_DEFAULT_FPS = 10

    fun normalizeVehicleMaxFps(value: Int): Int =
        value.takeIf { it in MIN_FPS..MAX_FPS } ?: SAFE_DEFAULT_FPS

    /**
     * A user preference may tighten the vehicle limit but can never raise it.
     * Source cadence is handled by FrameBus conflation and is not a send limit.
     */
    fun resolveEffectiveFps(
        vehicleMaxFps: Int,
        userPreferredFps: Int? = null,
    ): Int {
        val vehicleLimit = normalizeVehicleMaxFps(vehicleMaxFps)
        val userLimit = userPreferredFps
            ?.takeIf { it > 0 }
            ?.coerceAtMost(MAX_FPS)
            ?: MAX_FPS
        return minOf(vehicleLimit, userLimit)
    }

    /** Round up so the scheduler never exceeds the configured maximum rate. */
    fun intervalMillis(fps: Int): Long {
        val safeFps = normalizeVehicleMaxFps(fps)
        return (1_000L + safeFps - 1L) / safeFps
    }
}
