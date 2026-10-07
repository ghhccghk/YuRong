package top.ghhccghk.yadea.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.ByteArrayOutputStream

/**
 * Android JpegEncoder using android.graphics.Bitmap.compress()
 */
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
     * Center-crop + scale to target dimensions.
     * Preserves aspect ratio by cropping the longer dimension.
     */
    private fun centerCropScale(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (src.width == targetWidth && src.height == targetHeight) return src

        val targetRatio = targetWidth.toFloat() / targetHeight
        val srcRatio = src.width.toFloat() / src.height

        val srcRect: Rect = if (srcRatio > targetRatio) {
            // Source is wider -> crop sides
            val cropWidth = (src.height * targetRatio).toInt()
            val left = (src.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, src.height)
        } else {
            // Source is taller -> crop top/bottom
            val cropHeight = (src.width / targetRatio).toInt()
            val top = (src.height - cropHeight) / 2
            Rect(0, top, src.width, top + cropHeight)
        }

        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, srcRect, RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()), paint)
        return result
    }
}
