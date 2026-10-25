package top.ghhccghk.yadea.instrumentskin

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun prepareInstrumentWallpaper(
    source: String,
    width: Int,
    height: Int,
    format: Int,
): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val decoded = decodeInstrumentImage(source) ?: return@withContext null
        val src = (decoded as JvmInstrumentRaster).image

        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        try {
            if (format != 1) {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, width, height)
            }
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics.drawImage(src, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }

        val output = ByteArrayOutputStream()
        if (format == 1) {
            check(ImageIO.write(scaled, "png", output)) { "PNG 编码失败" }
        } else if (format == 0 || format == 2) {
            val raster = JvmInstrumentRaster(scaled)
            for (quality in 100 downTo 5 step 5) {
                val encoded = encodeInstrumentJpeg(raster, quality)
                if (encoded.size <= 131_072) return@withContext encoded
            }
            error("仪表壁纸压缩后仍超过 128 KiB")
        } else if (format == 3) {
            error("杰理 RLE 壁纸需要原 App 的 arm64 原生编码库，当前平台不支持")
        } else {
            error("不支持的仪表壁纸格式 $format")
        }
        output.toByteArray()
    }.getOrNull()
}
