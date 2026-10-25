package top.ghhccghk.yadea.instrumentskin

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JvmInstrumentRaster(val image: BufferedImage) : InstrumentRaster {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
}

actual suspend fun decodeInstrumentImage(source: String): InstrumentRaster? =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = java.io.File(source)
            if (file.isFile) {
                decodeBitmap(readLimited(file.inputStream(), MAX_SOURCE_BYTES))
            } else {
                val url = java.net.URL(source)
                url.openStream().use { decodeBitmap(readLimited(it, MAX_SOURCE_BYTES)) }
            }
        }.getOrNull()
    }

actual suspend fun decodeInstrumentImage(bytes: ByteArray): InstrumentRaster? =
    withContext(Dispatchers.IO) {
        runCatching { decodeBitmap(bytes) }.getOrNull()
    }

actual fun createInstrumentCanvas(width: Int, height: Int): InstrumentRaster =
    JvmInstrumentRaster(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB))

actual fun InstrumentRaster.fillColor(argb: Int) {
    val graphics = (this as JvmInstrumentRaster).image.createGraphics()
    try {
        graphics.color = Color(argb, true)
        graphics.fillRect(0, 0, width, height)
    } finally {
        graphics.dispose()
    }
}

actual fun InstrumentRaster.drawCenterCrop(
    photo: InstrumentRaster,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    val graphics = (this as JvmInstrumentRaster).image.createGraphics()
    try {
        paint(graphics)
        val source = (photo as JvmInstrumentRaster).image
        val dstW = right - left
        val dstH = bottom - top
        val scale = maxOf(dstW / source.width, dstH / source.height)
        val drawW = source.width * scale
        val drawH = source.height * scale
        val offsetX = left + dstW / 2f - drawW / 2f
        val offsetY = top + dstH / 2f - drawH / 2f
        graphics.drawImage(
            source,
            AffineTransform.getTranslateInstance(offsetX.toDouble(), offsetY.toDouble())
                .apply { scale(scale.toDouble(), scale.toDouble()) },
            null,
        )
    } finally {
        graphics.dispose()
    }
}

actual fun InstrumentRaster.drawCirclePortrait(
    photo: InstrumentRaster,
    cx: Float,
    cy: Float,
    radius: Float,
) {
    val graphics = (this as JvmInstrumentRaster).image.createGraphics()
    try {
        paint(graphics)
        val clip = Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f)
        graphics.clip = clip
        val source = (photo as JvmInstrumentRaster).image
        val scale = maxOf(radius * 2f / source.width, radius * 2f / source.height)
        val drawW = source.width * scale
        val drawH = source.height * scale
        val offsetX = cx - drawW / 2f
        val offsetY = cy - drawH / 2f
        graphics.drawImage(
            source,
            AffineTransform.getTranslateInstance(offsetX.toDouble(), offsetY.toDouble())
                .apply { scale(scale.toDouble(), scale.toDouble()) },
            null,
        )
    } finally {
        graphics.dispose()
    }
}

actual fun InstrumentRaster.drawCircleColor(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
) {
    val graphics = (this as JvmInstrumentRaster).image.createGraphics()
    try {
        paint(graphics)
        graphics.color = Color(color, true)
        graphics.fill(Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f))
    } finally {
        graphics.dispose()
    }
}

actual fun InstrumentRaster.drawCircleStroke(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
    strokeWidth: Float,
) {
    val graphics = (this as JvmInstrumentRaster).image.createGraphics()
    try {
        paint(graphics)
        graphics.color = Color(color, true)
        graphics.stroke = java.awt.BasicStroke(strokeWidth)
        graphics.draw(Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f))
    } finally {
        graphics.dispose()
    }
}

actual suspend fun encodeInstrumentJpeg(image: InstrumentRaster, quality: Int): ByteArray =
    withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        val source = (image as JvmInstrumentRaster).image
        // JPEG 不支持透明通道；先铺白底，和 Android 分支保持一致。
        val rgb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rgb.width, rgb.height)
            graphics.drawImage(source, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
            ?: error("JPEG 编码器不可用")
        val imageOutput = ImageIO.createImageOutputStream(output)
            ?: error("JPEG 输出流不可用")
        try {
            writer.output = imageOutput
            val parameter = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality.coerceIn(1, 100) / 100f
            }
            writer.write(null, IIOImage(rgb, null, null), parameter)
        } finally {
            imageOutput.close()
            writer.dispose()
        }
        output.toByteArray()
    }

actual fun loadInstrumentSkinReferencePack(): ByteArray? =
    runCatching {
        JvmInstrumentRaster::class.java.classLoader?.getResourceAsStream("skin/captured_800x480.bin")
            ?.use { it.readBytes() }
    }.getOrNull()

private fun decodeBitmap(bytes: ByteArray): InstrumentRaster? {
    val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
    return JvmInstrumentRaster(
        BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB).apply {
            val graphics = createGraphics()
            try {
                graphics.drawImage(image, 0, 0, null)
            } finally {
                graphics.dispose()
            }
        },
    )
}

private fun paint(graphics: Graphics2D) {
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    )
    graphics.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON,
    )
}

private fun readLimited(input: java.io.InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "图片超过 ${maxBytes / (1024 * 1024)} MiB" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private const val MAX_SOURCE_BYTES = 30 * 1024 * 1024
