package top.ghhccghk.yadea.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VehicleBackgroundSeedColorTest {
    @Test
    fun vehicleBackgroundSourceUsesExtractedSeedWhenAvailable() {
        val appearance = AppAppearance(colorSource = AppColorSource.VehicleBackground)

        assertEquals(
            Color(0xFFE91E63),
            appSeedColor(
                appearance = appearance,
                systemAccentColor = Color.Blue,
                vehicleBackgroundSeedColor = Color(0xFFE91E63),
            ),
        )
    }

    @Test
    fun vehicleBackgroundSourceFallsBackToStoredSeed() {
        val appearance = AppAppearance(
            colorSource = AppColorSource.VehicleBackground,
            seedColorArgb = 0xFF336699.toInt(),
        )

        assertEquals(
            Color(0xFF336699),
            appSeedColor(
                appearance = appearance,
                systemAccentColor = null,
                vehicleBackgroundSeedColor = null,
            ),
        )
    }

    @Test
    fun dominantPalettePrefersSaturatedBackgroundColorOverBlack() {
        val argb = assertNotNull(
            VehicleBackgroundPalette.dominantArgb(
                intArrayOf(
                    0xFF000000.toInt(),
                    0xFF101010.toInt(),
                    0xFFE60055.toInt(),
                    0xFFF21A68.toInt(),
                    0xFFD8004F.toInt(),
                ),
            ),
        )

        assertTrue((argb shr 16 and 0xFF) > (argb shr 8 and 0xFF))
        assertTrue((argb shr 16 and 0xFF) > (argb and 0xFF))
        assertEquals(0xFF, argb ushr 24)
    }
}
