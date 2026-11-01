package top.ghhccghk.yadea.mirror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MirrorQualityTest {
    @Test
    fun presetsUseConfirmedStartingAndRetryQuality() {
        val cases = listOf(
            MirrorQualityPreset.Auto to (72 to 64),
            MirrorQualityPreset.Smooth to (60 to 52),
            MirrorQualityPreset.Balanced to (72 to 64),
            MirrorQualityPreset.Clear to (84 to 76),
        )

        for ((preset, expected) in cases) {
            val settings = MirrorQualitySettings(preset)
            val initial = settings.initialQuality(800, 480)
            assertEquals(expected.first, initial)
            assertEquals(expected.second, settings.retryQuality(initial))
        }
    }

    @Test
    fun autoUsesHigherQualityOnlyForSmallFrames() {
        val settings = MirrorQualitySettings(MirrorQualityPreset.Auto)

        assertEquals(72, settings.initialQuality(800, 480))
        assertEquals(80, settings.initialQuality(320, 240))
    }

    @Test
    fun customQualityIsClamped() {
        val low = MirrorQualitySettings(
            preset = MirrorQualityPreset.Custom,
            customQuality = 1,
        ).normalized()
        val high = MirrorQualitySettings(
            preset = MirrorQualityPreset.Custom,
            customQuality = 200,
        ).normalized()

        assertEquals(MirrorQualitySettings.MIN_CUSTOM_QUALITY, low.customQuality)
        assertEquals(MirrorQualitySettings.MAX_CUSTOM_QUALITY, high.customQuality)
        assertTrue(low.retryQuality(MirrorQualitySettings.MIN_CUSTOM_QUALITY) >= 40)
    }
}
