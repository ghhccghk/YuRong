package top.ghhccghk.yadea.instrumentskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

actual suspend fun prepareInstrumentWallpaper(
    source: String,
    width: Int,
    height: Int,
    format: Int,
): ByteArray? = withContext(Dispatchers.IO) {
    val context = AndroidInstrumentImageContext.requireContext()
    runCatching {
        val uri = Uri.parse(source)
        val sourceBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            decodeBitmap(readLimited(stream, MAX_SOURCE_BYTES))
        } ?: return@withContext null

        // 直接拉伸到仪表尺寸，不做裁剪。
        // createScaledBitmap 在尺寸相同时会直接返回原 Bitmap，不能随后无条件回收。
        val scaled = if (sourceBitmap.bitmap.width == width && sourceBitmap.bitmap.height == height) {
            sourceBitmap.bitmap
        } else {
            Bitmap.createScaledBitmap(sourceBitmap.bitmap, width, height, true)
                .also { sourceBitmap.bitmap.recycle() }
        }

        // 非 PNG 先铺白底，避免透明区域发黑。
        val canvasBitmap = if (format != 1) {
            val white = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(white)
            canvas.drawColor(0xFFFFFFFF.toInt())
            canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            scaled.recycle()
            white
        } else {
            scaled
        }

        val output = ByteArrayOutputStream()
        when (format) {
            1 -> {
                val ok = canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                check(ok) { "PNG 编码失败" }
            }
            2 -> compressJpeg(canvasBitmap, output)
            0 -> compressNv21Jpeg(canvasBitmap, output)
            3 -> error("杰理 RLE 壁纸需要原 App 的 arm64 原生编码库，当前版本不支持")
            else -> error("不支持的仪表壁纸格式 $format")
        }
        canvasBitmap.recycle()
        output.toByteArray()
    }.getOrNull()
}

private fun compressJpeg(bitmap: Bitmap, output: ByteArrayOutputStream) {
    for (quality in 100 downTo 5 step 5) {
        output.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (output.size() <= 131_072) return
    }
    error("仪表壁纸压缩后仍超过 128 KiB")
}

private fun compressNv21Jpeg(bitmap: Bitmap, output: ByteArrayOutputStream) {
    val width = bitmap.width
    val height = bitmap.height
    require(width % 2 == 0 && height % 2 == 0) { "NV21 壁纸尺寸必须为偶数" }
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val nv21 = ByteArray(width * height * 3 / 2)
    var yIndex = 0
    var uvIndex = width * height
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixels[y * width + x]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            nv21[yIndex++] = (((red * 77 + green * 150 + blue * 29 + 128) shr 8)
                .coerceIn(0, 255)).toByte()
            if (y % 2 == 0 && x % 2 == 0) {
                nv21[uvIndex++] = ((((red * 127 - green * 106 - blue * 21 + 128) shr 8) + 128)
                    .coerceIn(0, 255)).toByte()
                nv21[uvIndex++] = ((((red * -43 - green * 84 + blue * 127 + 128) shr 8) + 128)
                    .coerceIn(0, 255)).toByte()
            }
        }
    }
    val image = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val rect = Rect(0, 0, width, height)
    for (quality in 100 downTo 5 step 5) {
        output.reset()
        check(image.compressToJpeg(rect, quality, output)) { "NV21 JPEG 编码失败" }
        if (output.size() <= 131_072) return
    }
    error("NV21 壁纸压缩后仍超过 128 KiB")
}

private fun decodeBitmap(bytes: ByteArray): AndroidInstrumentRaster? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 30_000_000 || bounds.outHeight / sampleSize > 30_000_000) {
        sampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return null
    return AndroidInstrumentRaster(bitmap)
}
