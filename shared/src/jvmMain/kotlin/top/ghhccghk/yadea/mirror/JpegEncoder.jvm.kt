package top.ghhccghk.yadea.mirror

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * JVM/Desktop JpegEncoder using javax.imageio
 */
actual object JpegEncoder {

    actual fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray {
        return encodeAdaptiveJpeg(source, targetWidth, targetHeight)
    }

    actual fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray {
        val scaled = centerCropScale(source.image, targetWidth, targetHeight)
        val stream = ByteArrayOutputStream()

        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        val writer = writers.next()
        val param = writer.defaultWriteParam
        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = quality / 100f

        val output = ImageIO.createImageOutputStream(stream)
        writer.output = output
        writer.write(null, IIOImage(scaled, null, null), param)
        writer.dispose()
        output.close()

        return stream.toByteArray()
    }

    private fun centerCropScale(src: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        if (src.width == targetWidth && src.height == targetHeight) return src

        val targetRatio = targetWidth.toFloat() / targetHeight
        val srcRatio = src.width.toFloat() / src.height

        val sx: Int; val sy: Int; val sw: Int; val sh: Int
        if (srcRatio > targetRatio) {
            sh = src.height
            sw = (src.height * targetRatio).toInt()
            sx = (src.width - sw) / 2
            sy = 0
        } else {
            sw = src.width
            sh = (src.width / targetRatio).toInt()
            sx = 0
            sy = (src.height - sh) / 2
        }

        val result = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = result.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, targetWidth, targetHeight, sx, sy, sx + sw, sy + sh, null)
        g.dispose()
        return result
    }
}
