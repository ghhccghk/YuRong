package top.ghhccghk.yadea.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import kotlin.math.abs

actual object JpegEncoder {

    actual fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray {
        return encodeAdaptiveJpeg(source, targetWidth, targetHeight)
    }

    actual fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray {
        val scaled = centerCropScale(source.native, targetWidth, targetHeight)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (scaled !== source.native) scaled.recycle()
        return stream.toByteArray()
    }

    /**
     * 覆盖缩放 + 居中裁剪 (原始 APK u() 方法):
     * scale = max(targetW/srcW, targetH/srcH) — 放大到覆盖目标
     * 从中心裁剪到 targetW x targetH
     */
    private fun centerCropScale(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (src.width == targetWidth && src.height == targetHeight) return src

        val targetAspect = targetWidth.toFloat() / targetHeight
        val sourceAspect = src.width.toFloat() / src.height
        // The source pipeline has already applied the user's projection-ratio
        // crop. Keep that selected region intact; only center-crop sources that
        // still have a materially different aspect ratio (for example an
        // uncropped camera frame).
        if (abs(sourceAspect - targetAspect) <= 0.02f) {
            return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
        }
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = src.height
            cropWidth = (cropHeight * targetAspect).toInt().coerceIn(1, src.width)
        } else {
            cropWidth = src.width
            cropHeight = (cropWidth / targetAspect).toInt().coerceIn(1, src.height)
        }
        val cropX = (src.width - cropWidth) / 2
        val cropY = (src.height - cropHeight) / 2
        val cropped = if (cropX == 0 && cropY == 0 && cropWidth == src.width && cropHeight == src.height) {
            src
        } else {
            Bitmap.createBitmap(src, cropX, cropY, cropWidth, cropHeight)
        }
        if (cropped.width == targetWidth && cropped.height == targetHeight) return cropped
        val result = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        if (cropped !== src) cropped.recycle()
        return result
    }
}
