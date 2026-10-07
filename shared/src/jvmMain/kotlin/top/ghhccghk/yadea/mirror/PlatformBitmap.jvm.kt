package top.ghhccghk.yadea.mirror

import java.awt.image.BufferedImage

/**
 * JVM/Desktop PlatformBitmap wrapping java.awt.image.BufferedImage
 */
actual class PlatformBitmap(val image: BufferedImage) {
    actual val width: Int get() = image.width
    actual val height: Int get() = image.height

    actual fun recycle() {
        // BufferedImage is GC-managed; no explicit recycle needed
        image.flush()
    }
}
