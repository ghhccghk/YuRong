package top.ghhccghk.yadea.mirror.source

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaCvFrameSourceTest {

    @Test
    fun mediaSource_decodesImageFile(): Unit = runBlocking {
        val image = BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB)
        val file = File.createTempFile("javacv-frame-source", ".png").apply {
            deleteOnExit()
            ImageIO.write(image, "png", this)
        }

        val received = CompletableDeferred<Pair<Int, Int>>()
        val source = JavaCvFrameSource.media(file.absolutePath)
        try {
            source.start { bitmap ->
                received.complete(bitmap.width to bitmap.height)
            }
            val size = withTimeout(5_000) { received.await() }
            assertEquals(32, size.first)
            assertEquals(16, size.second)
        } finally {
            source.stop()
        }
    }

    @Test
    fun mediaSource_rejectsBlankUri() {
        val error = runCatching { JavaCvFrameSource.media(" ") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
