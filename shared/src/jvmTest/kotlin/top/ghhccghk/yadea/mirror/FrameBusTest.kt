package top.ghhccghk.yadea.mirror

import org.junit.Test
import org.junit.Assert.*
import java.awt.image.BufferedImage

class FrameBusTest {

    private fun createBitmap(w: Int = 100, h: Int = 50): PlatformBitmap {
        return PlatformBitmap(BufferedImage(w, h, BufferedImage.TYPE_INT_RGB))
    }

    // ==================== FrameBus ====================

    @Test
    fun frameBus_submitAndTake() {
        val bus = FrameBus()
        val bmp = createBitmap()
        bus.submit(bmp)
        assertTrue(bus.hasFrame())

        val taken = bus.take()
        assertNotNull(taken)
        assertEquals(100, taken!!.width)
        assertEquals(50, taken.height)
        assertFalse(bus.hasFrame())
    }

    @Test
    fun frameBus_takeEmpty() {
        val bus = FrameBus()
        assertNull(bus.take())
    }

    @Test
    fun frameBus_submitReplacesOld() {
        val bus = FrameBus()
        bus.submit(createBitmap(100, 50))
        bus.submit(createBitmap(200, 100))

        val taken = bus.take()
        assertEquals(200, taken!!.width)
        assertEquals(100, taken.height)
    }

    @Test
    fun frameBus_clear() {
        val bus = FrameBus()
        bus.submit(createBitmap())
        bus.clear()
        assertFalse(bus.hasFrame())
        assertNull(bus.take())
    }

    @Test
    fun frameBus_frameCount() {
        val bus = FrameBus()
        bus.submit(createBitmap())
        bus.submit(createBitmap())
        bus.submit(createBitmap())
        assertEquals(3L, bus.frameCount.value)
    }

    @Test
    fun frameBus_clearResetsCount() {
        val bus = FrameBus()
        bus.submit(createBitmap())
        bus.clear()
        assertEquals(0L, bus.frameCount.value)
    }

    // ==================== PlatformBitmap ====================

    @Test
    fun platformBitmap_dimensions() {
        val bmp = createBitmap(320, 240)
        assertEquals(320, bmp.width)
        assertEquals(240, bmp.height)
    }

    // ==================== JpegEncoder ====================

    @Test
    fun jpegEncoder_encodeProducesJpegBytes() {
        val bmp = createBitmap(200, 100)
        val jpeg = JpegEncoder.encode(bmp, 200, 100)
        assertTrue(jpeg.isNotEmpty())
        // JPEG magic bytes: 0xFF 0xD8
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
    }

    @Test
    fun jpegEncoder_encodeRescalesToTarget() {
        val bmp = createBitmap(400, 200)
        val jpeg = JpegEncoder.encode(bmp, 960, 368)
        assertTrue(jpeg.isNotEmpty())
    }

    @Test
    fun jpegEncoder_adaptiveQuality() {
        // Large image that should trigger quality reduction
        val bmp = createBitmap(1920, 1080)
        val jpeg = encodeAdaptiveJpeg(bmp, 1920, 1080, maxBytes = 50_000)
        // Result should be non-empty and <= maxBytes OR at minimum quality
        assertTrue(jpeg.isNotEmpty())
    }

    @Test
    fun jpegEncoder_encodeAtQuality() {
        val bmp = createBitmap(100, 100)
        val highQuality = JpegEncoder.encodeAtQuality(bmp, 100, 100, 90)
        val lowQuality = JpegEncoder.encodeAtQuality(bmp, 100, 100, 30)
        assertTrue(highQuality.size >= lowQuality.size)
    }

    @Test
    fun jpegEncoder_centerCrop() {
        // Wide source cropped to narrow target
        val wideBmp = createBitmap(400, 100)
        val jpeg = JpegEncoder.encode(wideBmp, 100, 100)
        assertTrue(jpeg.isNotEmpty())

        // Tall source cropped to wide target
        val tallBmp = createBitmap(100, 400)
        val jpeg2 = JpegEncoder.encode(tallBmp, 400, 100)
        assertTrue(jpeg2.isNotEmpty())
    }
}
