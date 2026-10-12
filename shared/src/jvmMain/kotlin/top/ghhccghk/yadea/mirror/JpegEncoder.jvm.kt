package top.ghhccghk.yadea.mirror

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

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

        val scaleW = targetWidth.toFloat() / src.width
        val scaleH = targetHeight.toFloat() / src.height
        val scale = maxOf(scaleW, scaleH)

        val scaledW = (src.width * scale).toInt()
        val scaledH = (src.height * scale).toInt()

        val scaled = BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB)
        val g1 = scaled.createGraphics()
        g1.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g1.drawImage(src, 0, 0, scaledW, scaledH, null)
        g1.dispose()

        val cropX = (scaledW - targetWidth) / 2
        val cropY = (scaledH - targetHeight) / 2

        val result = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2 = result.createGraphics()
        g2.drawImage(scaled, 0, 0, targetWidth, targetHeight, cropX, cropY, cropX + targetWidth, cropY + targetHeight, null)
        g2.dispose()
        return result
    }
}
