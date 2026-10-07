package top.ghhccghk.yadea.mirror

import org.junit.Assert.*
import org.junit.Test

class FrameCropConfigTest {

    // ==================== 默认值 ====================

    @Test
    fun fullCrop_isValid() {
        val crop = FrameCropConfig.FULL
        assertTrue(crop.isValid)
        assertEquals(0f, crop.left)
        assertEquals(0f, crop.top)
        assertEquals(1f, crop.right)
        assertEquals(1f, crop.bottom)
    }

    @Test
    fun defaultNormalizedDimensions() {
        val crop = FrameCropConfig.FULL
        assertEquals(1f, crop.normalizedWidth)
        assertEquals(1f, crop.normalizedHeight)
    }

    // ==================== 有效性 ====================

    @Test
    fun validCrop_passes() {
        assertTrue(FrameCropConfig(0.1f, 0.1f, 0.9f, 0.9f).isValid)
        assertTrue(FrameCropConfig(0f, 0f, 0.5f, 0.5f).isValid)
        assertTrue(FrameCropConfig(0.3f, 0.4f, 0.7f, 0.8f).isValid)
    }

    @Test
    fun invalidCrop_leftEqualsRight() {
        assertFalse(FrameCropConfig(0.5f, 0f, 0.5f, 1f).isValid)
    }

    @Test
    fun invalidCrop_topEqualsBottom() {
        assertFalse(FrameCropConfig(0f, 0.5f, 1f, 0.5f).isValid)
    }

    @Test
    fun invalidCrop_leftGreaterThanRight() {
        assertFalse(FrameCropConfig(0.8f, 0f, 0.2f, 1f).isValid)
    }

    @Test
    fun invalidCrop_outOfRange() {
        assertFalse(FrameCropConfig(-0.1f, 0f, 1f, 1f).isValid)
        assertFalse(FrameCropConfig(0f, 0f, 1.1f, 1f).isValid)
        assertFalse(FrameCropConfig(0f, -0.1f, 1f, 1f).isValid)
        assertFalse(FrameCropConfig(0f, 0f, 1f, 1.1f).isValid)
    }

    // ==================== 像素矩形转换 ====================

    @Test
    fun toPixelRect_full() {
        val rect = FrameCropConfig.FULL.toPixelRect(1920, 1080)
        assertEquals(0, rect.x)
        assertEquals(0, rect.y)
        assertEquals(1920, rect.width)
        assertEquals(1080, rect.height)
    }

    @Test
    fun toPixelRect_center() {
        val rect = FrameCropConfig.CENTER.toPixelRect(1000, 500)
        assertEquals(250, rect.x)
        assertEquals(125, rect.y)
        assertEquals(500, rect.width)
        assertEquals(250, rect.height)
    }

    @Test
    fun toPixelRect_smallRegion() {
        val crop = FrameCropConfig(0.25f, 0.25f, 0.75f, 0.75f)
        val rect = crop.toPixelRect(100, 100)
        assertEquals(25, rect.x)
        assertEquals(25, rect.y)
        assertEquals(50, rect.width)
        assertEquals(50, rect.height)
    }

    @Test
    fun toPixelRect_minimumSize() {
        val crop = FrameCropConfig(0.499f, 0.499f, 0.501f, 0.501f)
        val rect = crop.toPixelRect(10, 10)
        assertTrue(rect.width >= 1)
        assertTrue(rect.height >= 1)
    }

    // ==================== 宽高比适配 ====================

    @Test
    fun withAspectRation_wideTarget() {
        // 源: 1000x500 (2:1), 目标: 1:1
        val result = FrameCropConfig.FULL.withAspectRation(1f, 1000, 500)
        assertTrue(result.isValid)
        // 应该裁剪宽度
        assertTrue(result.normalizedWidth < 1f)
    }

    @Test
    fun withAspectRation_tallTarget() {
        // 源: 500x1000 (1:2), 目标: 1:1
        val result = FrameCropConfig.FULL.withAspectRation(1f, 500, 1000)
        assertTrue(result.isValid)
        // 应该裁剪高度
        assertTrue(result.normalizedHeight < 1f)
    }

    // ==================== 预设值 ====================

    @Test
    fun presets_areValid() {
        assertTrue(FrameCropConfig.FULL.isValid)
        assertTrue(FrameCropConfig.CENTER.isValid)
        assertTrue(FrameCropConfig.TOP_HALF.isValid)
        assertTrue(FrameCropConfig.BOTTOM_HALF.isValid)
    }

    @Test
    fun topHalf_hasCorrectBounds() {
        val crop = FrameCropConfig.TOP_HALF
        assertEquals(0f, crop.top)
        assertEquals(0.5f, crop.bottom)
        assertEquals(0f, crop.left)
        assertEquals(1f, crop.right)
    }

    @Test
    fun bottomHalf_hasCorrectBounds() {
        val crop = FrameCropConfig.BOTTOM_HALF
        assertEquals(0.5f, crop.top)
        assertEquals(1f, crop.bottom)
    }

    // ==================== 数据类 ====================

    @Test
    fun copy_works() {
        val crop = FrameCropConfig(0.1f, 0.2f, 0.8f, 0.9f)
        val modified = crop.copy(left = 0.15f)
        assertEquals(0.15f, modified.left)
        assertEquals(0.2f, modified.top)
        assertEquals(0.8f, modified.right)
        assertEquals(0.9f, modified.bottom)
    }

    @Test
    fun equality_works() {
        val a = FrameCropConfig(0.1f, 0.2f, 0.8f, 0.9f)
        val b = FrameCropConfig(0.1f, 0.2f, 0.8f, 0.9f)
        assertEquals(a, b)
    }
}
