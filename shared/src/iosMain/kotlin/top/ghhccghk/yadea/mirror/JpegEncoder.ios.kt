package top.ghhccghk.yadea.mirror

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/**
 * iOS JpegEncoder using UIImageJPEGRepresentation
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object JpegEncoder {

    actual fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray {
        return encodeAdaptiveJpeg(source, targetWidth, targetHeight)
    }

    actual fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray {
        val scaled = scaleImage(source.image, targetWidth, targetHeight)
        val data = UIImageJPEGRepresentation(scaled, quality / 100.0)
            ?: return ByteArray(0)

        val length = data.length.toInt()
        val result = ByteArray(length)
        if (length > 0) {
            result.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return result
    }

    private fun scaleImage(src: UIImage, targetWidth: Int, targetHeight: Int): UIImage {
        val targetSize = CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble())
        UIGraphicsBeginImageContextWithOptions(targetSize, true, 1.0)
        src.drawInRect(CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()))
        val scaled = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return scaled ?: src
    }
}
