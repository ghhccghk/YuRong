package top.ghhccghk.yadea.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRatePolicyTest {

    @Test fun intervalsMatchSupportedRates() {
        assertEquals(1_000L, FrameRatePolicy.intervalMillis(1))
        assertEquals(200L, FrameRatePolicy.intervalMillis(5))
        assertEquals(100L, FrameRatePolicy.intervalMillis(10))
        assertEquals(34L, FrameRatePolicy.intervalMillis(30))
    }

    @Test fun invalidVehicleRateFallsBackToSafeDefault() {
        listOf(0, -1, 31, 100).forEach { invalid ->
            assertEquals(10, FrameRatePolicy.normalizeVehicleMaxFps(invalid))
        }
        assertEquals(1, FrameRatePolicy.normalizeVehicleMaxFps(1))
        assertEquals(30, FrameRatePolicy.normalizeVehicleMaxFps(30))
    }

    @Test fun userPreferenceCanOnlyTightenVehicleLimit() {
        assertEquals(30, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 30))
        assertEquals(10, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 10))
        assertEquals(1, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 1))
        assertEquals(5, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 30, userPreferredFps = 5))
        assertEquals(10, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 10, userPreferredFps = 30))
        assertEquals(10, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 0, userPreferredFps = 30))
    }

    @Test fun invalidUserPreferenceDoesNotRaiseVehicleLimit() {
        assertEquals(10, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 10, userPreferredFps = 0))
        assertEquals(10, FrameRatePolicy.resolveEffectiveFps(vehicleMaxFps = 10, userPreferredFps = -5))
    }
}
