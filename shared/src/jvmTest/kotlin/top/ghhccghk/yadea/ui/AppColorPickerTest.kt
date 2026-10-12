package top.ghhccghk.yadea.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppColorPickerTest {
    @Test
    fun hexColorRoundTrips() {
        val color = parseHexColor("#12ABEF")!!
        assertEquals("#12ABEF", color.toHexColor())
    }

    @Test
    fun invalidHexColorReturnsNull() {
        assertNull(parseHexColor("#123"))
        assertNull(parseHexColor("not-a-color"))
        assertNull(parseHexColor("#GGGGGG"))
    }

    @Test
    fun presetsHaveStableUniqueIds() {
        val ids = AppThemePresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains(AppThemePresets.Default.id))
    }
}
