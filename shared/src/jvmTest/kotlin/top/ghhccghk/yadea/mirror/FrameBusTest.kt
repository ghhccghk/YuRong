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

    @Test fun frameBus_submitReplacesOldAndCountsOverwrite() {
        val bus = FrameBus()
        bus.submit(createBitmap(100, 50))
        bus.submit(createBitmap(200, 100))
        val taken = bus.takeFrame()
        assertEquals(200, taken!!.bitmap.width)
        assertEquals(2L, bus.capturedFrameCount.value)
        assertEquals(1L, bus.overwrittenFrameCount.value)
    }

    @Test fun frameBus_preservesMonotonicTimestamps() {
        val bus = FrameBus()
        val capturedAt = top.ghhccghk.yadea.currentMonotonicMillis() - 1
        bus.submit(createBitmap(), capturedAtMillis = capturedAt)
        val frame = bus.takeFrame()!!
        assertEquals(capturedAt, frame.capturedAtMillis)
        assertTrue(frame.submittedAtMillis >= frame.capturedAtMillis)
    }

    @Test fun frameBus_discardIfRemovesMatchingFrame() {
        val bus = FrameBus()
        val submitted = bus.submit(createBitmap())

        assertTrue(bus.discardIf(submitted))
        assertFalse(bus.hasFrame())
        assertFalse(bus.discardIf(submitted))
    }

    @Test fun frameBus_discardIfDoesNotRemoveNewerFrame() {
        val bus = FrameBus()
        val stale = bus.submit(createBitmap(100, 50))
        val newer = bus.submit(createBitmap(200, 100))

        assertFalse(bus.discardIf(stale))
        assertTrue(bus.hasFrame())
        assertSame(newer, bus.takeFrame())
    }

    @Test fun frameBus_discardLatestFrameKeepsCumulativeCounters() {
        val bus = FrameBus()
        bus.submit(createBitmap())
        bus.submit(createBitmap())
        bus.discardLatestFrame()
        assertFalse(bus.hasFrame())
        assertEquals(2L, bus.capturedFrameCount.value)
        assertEquals(1L, bus.overwrittenFrameCount.value)
    }

    @Test fun frameBus_clear() {
        val bus = FrameBus()
        bus.submit(createBitmap())
        bus.submit(createBitmap())
        bus.clear()
        assertFalse(bus.hasFrame())
        assertEquals(0L, bus.capturedFrameCount.value)
        assertEquals(0L, bus.overwrittenFrameCount.value)
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
