package top.ghhccghk.yadea.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
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

    /** HSV 分解要符合常见定义：纯红为 0 度、纯绿 120 度、纯蓝 240 度。 */
    @Test
    fun hsvComponentsMatchPrimaryColors() {
        assertHsv(Color(1f, 0f, 0f), hue = 0f, saturation = 1f, value = 1f)
        assertHsv(Color(0f, 1f, 0f), hue = 120f, saturation = 1f, value = 1f)
        assertHsv(Color(0f, 0f, 1f), hue = 240f, saturation = 1f, value = 1f)
        // 灰色没有色相，饱和度也应当为 0。
        assertHsv(Color(0.5f, 0.5f, 0.5f), hue = 0f, saturation = 0f, value = 0.5f)
    }

    /** HSV 往返：拆成分量再合成回来，除浮点误差外应当还原原色。 */
    @Test
    fun hsvRoundTripsForArbitraryColors() {
        val samples = listOf(
            Color(1f, 0f, 0f),
            Color(0.2f, 0.6f, 0.9f),
            Color(0.05f, 0.05f, 0.05f),
            Color(1f, 1f, 1f),
            Color(0f, 0f, 0f),
        )
        samples.forEach { color ->
            val hsv = color.toAppHsv()
            val restored = appHsvToColor(hsv.hue, hsv.saturation, hsv.value)
            assertTrue(abs(restored.red - color.red) < 0.01f, "红分量不一致：$color")
            assertTrue(abs(restored.green - color.green) < 0.01f, "绿分量不一致：$color")
            assertTrue(abs(restored.blue - color.blue) < 0.01f, "蓝分量不一致：$color")
        }
    }

    /** HSV 分量越界时必须收敛，不能产生非法颜色或崩溃。 */
    @Test
    fun hsvComponentsClampOutOfRangeInput() {
        val clamped = appHsvToColor(hue = 720f, saturation = 5f, value = 2f)
        assertEquals(Color(1f, 0f, 0f), clamped)
        assertEquals(Color(0f, 0f, 0f), appHsvToColor(hue = -30f, saturation = 1f, value = 0f))
    }

    /** 网格调色盘的行列越界要收敛到最近格子，供点击命中和布局复用。 */
    @Test
    fun paletteGridClampsOutOfRangeIndices() {
        assertEquals(AppColorPaletteGrid.colorAt(0, 0), AppColorPaletteGrid.colorAt(-3, -1))
        val lastRow = AppColorPaletteGrid.Rows - 1
        val lastColumn = AppColorPaletteGrid.ColumnCount - 1
        assertEquals(
            AppColorPaletteGrid.colorAt(lastRow, lastColumn),
            AppColorPaletteGrid.colorAt(999, 999),
        )
    }

    /** 每个格子的颜色都能反查到自身，保证选中描边不会落到别的格子上。 */
    @Test
    fun paletteGridNearestCellFindsExactCell() {
        repeat(AppColorPaletteGrid.Rows) { row ->
            repeat(AppColorPaletteGrid.ColumnCount) { column ->
                assertEquals(
                    row to column,
                    AppColorPaletteGrid.nearestCell(AppColorPaletteGrid.colorAt(row, column)),
                )
            }
        }
    }

    // Compose 会把浮点颜色量化到 8 位，因此比较分量时留出量化误差。
    private fun assertHsv(color: Color, hue: Float, saturation: Float, value: Float) {
        val hsv = color.toAppHsv()
        assertTrue(abs(hsv.hue - hue) < 0.01f, "色相不一致：${hsv.hue} != $hue")
        assertTrue(abs(hsv.saturation - saturation) < 0.01f, "饱和度不一致：${hsv.saturation}")
        assertTrue(abs(hsv.value - value) < 0.01f, "明度不一致：${hsv.value}")
    }
}