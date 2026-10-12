package top.ghhccghk.yadea.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.ByteArrayOutputStream

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

        val scaleW = targetWidth.toFloat() / src.width
        val scaleH = targetHeight.toFloat() / src.height
        val scale = maxOf(scaleW, scaleH) // 覆盖缩放

        val scaledW = (src.width * scale).toInt()
        val scaledH = (src.height * scale).toInt()

        // 先等比放大到覆盖
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)

        // 从中心裁剪到目标尺寸
        val cropX = (scaledW - targetWidth) / 2
        val cropY = (scaledH - targetHeight) / 2

        val result = Bitmap.createBitmap(scaled, cropX, cropY, targetWidth, targetHeight)
        if (scaled !== src) scaled.recycle()
        return result
    }
}
