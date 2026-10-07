package top.ghhccghk.yadea.mirror

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImage

/**
 * iOS PlatformBitmap wrapping UIImage
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformBitmap(val image: UIImage) {
    actual val width: Int get() = (image.size.width * image.scale).toInt()
    actual val height: Int get() = (image.size.height * image.scale).toInt()

    actual fun recycle() {
        // UIImage is ARC-managed in Kotlin/Native
    }
}
