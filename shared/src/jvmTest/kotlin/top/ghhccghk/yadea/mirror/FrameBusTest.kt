package top.ghhccghk.yadea.mirror

import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage

class FrameBusTest {

    private fun createBitmap(w: Int = 100, h: Int = 50): PlatformBitmap {
        return PlatformBitmap(BufferedImage(w, h, BufferedImage.TYPE_INT_RGB))
    }

    @Test fun frameBus_submitAndTake() {
        val bus = FrameBus()
        val bmp = createBitmap()
        bus.submit(bmp)
        assertTrue(bus.hasFrame())
        val taken = bus.take()
        assertNotNull(taken)
        assertFalse(bus.hasFrame())
    }

    @Test fun frameBus_takeEmpty() {
        val bus = FrameBus()
        assertNull(bus.take())
    }

    @Test fun frameBus_submitReplacesOld() {
        val bus = FrameBus()
        bus.submit(createBitmap(100, 50))
        bus.submit(createBitmap(200, 100))
        val taken = bus.take()
        assertEquals(200, taken!!.width)
    }

    @Test fun frameBus_clear() {
        val bus = FrameBus()
        bus.submit(createBitmap())
        bus.clear()
        assertFalse(bus.hasFrame())
    }

    @Test fun frameBus_frameCount() {
        val bus = FrameBus()
        bus.submit(createBitmap())
        bus.submit(createBitmap())
        assertEquals(2L, bus.frameCount.value)
    }

    @Test fun platformBitmap_dimensions() {
        val bmp = createBitmap(320, 240)
        assertEquals(320, bmp.width)
        assertEquals(240, bmp.height)
    }

    @Test fun jpegEncoder_encode() {
        val bmp = createBitmap(200, 100)
        val jpeg = JpegEncoder.encode(bmp, 200, 100)
        assertTrue(jpeg.isNotEmpty())
    }

    @Test fun jpegEncoder_centerCrop() {
        val wideBmp = createBitmap(400, 100)
        val jpeg = JpegEncoder.encode(wideBmp, 100, 100)
        assertTrue(jpeg.isNotEmpty())
    }
}
