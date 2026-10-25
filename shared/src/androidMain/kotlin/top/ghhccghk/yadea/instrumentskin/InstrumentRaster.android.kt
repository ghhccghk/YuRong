package top.ghhccghk.yadea.instrumentskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.ByteArrayOutputStream

object AndroidInstrumentImageContext {
    @Volatile
    var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun requireContext(): Context =
        appContext ?: error("AndroidInstrumentImageContext 未初始化")
}

internal class AndroidInstrumentRaster(val bitmap: Bitmap) : InstrumentRaster {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

actual suspend fun decodeInstrumentImage(source: String): InstrumentRaster? =
    withContext(Dispatchers.IO) {
        val context = AndroidInstrumentImageContext.requireContext()
        runCatching {
            val uri = Uri.parse(source)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                decodeBitmap(readLimited(stream, MAX_SOURCE_BYTES))
            }
        }.getOrNull()
    }

actual suspend fun decodeInstrumentImage(bytes: ByteArray): InstrumentRaster? =
    withContext(Dispatchers.IO) {
        runCatching { decodeBitmap(bytes) }.getOrNull()
    }

actual fun createInstrumentCanvas(width: Int, height: Int): InstrumentRaster =
    AndroidInstrumentRaster(
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
    )

actual fun InstrumentRaster.fillColor(argb: Int) {
    val bitmap = (this as AndroidInstrumentRaster).bitmap
    Canvas(bitmap).drawColor(argb)
}

actual fun InstrumentRaster.drawCenterCrop(
    photo: InstrumentRaster,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    val canvas = Canvas((this as AndroidInstrumentRaster).bitmap)
    val source = (photo as AndroidInstrumentRaster).bitmap
    val dst = RectF(left, top, right, bottom)
    val scale = maxOf(
        dst.width() / source.width,
        dst.height() / source.height,
    )
    val drawWidth = source.width * scale
    val drawHeight = source.height * scale
    val drawRect = RectF(
        dst.centerX() - drawWidth / 2f,
        dst.centerY() - drawHeight / 2f,
        dst.centerX() + drawWidth / 2f,
        dst.centerY() + drawHeight / 2f,
    )
    canvas.drawBitmap(source, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
}

actual fun InstrumentRaster.drawCirclePortrait(
    photo: InstrumentRaster,
    cx: Float,
    cy: Float,
    radius: Float,
) {
    val canvas = Canvas((this as AndroidInstrumentRaster).bitmap)
    val source = (photo as AndroidInstrumentRaster).bitmap
    val path = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
    canvas.save()
    canvas.clipPath(path)
    val scale = maxOf(radius * 2f / source.width, radius * 2f / source.height)
    val drawWidth = source.width * scale
    val drawHeight = source.height * scale
    val drawRect = RectF(
        cx - drawWidth / 2f,
        cy - drawHeight / 2f,
        cx + drawWidth / 2f,
        cy + drawHeight / 2f,
    )
    canvas.drawBitmap(source, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

actual fun InstrumentRaster.drawCircleColor(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
) {
    val canvas = Canvas((this as AndroidInstrumentRaster).bitmap)
    canvas.drawCircle(cx, cy, radius, Paint().apply { this.color = color })
}

actual fun InstrumentRaster.drawCircleStroke(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
    strokeWidth: Float,
) {
    val canvas = Canvas((this as AndroidInstrumentRaster).bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        this.color = color
    }
    canvas.drawCircle(cx, cy, radius, paint)
}

actual suspend fun encodeInstrumentJpeg(image: InstrumentRaster, quality: Int): ByteArray =
    withContext(Dispatchers.IO) {
        val bitmap = (image as AndroidInstrumentRaster).bitmap
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        output.toByteArray()
    }

actual fun loadInstrumentSkinReferencePack(): ByteArray? {
    val context = AndroidInstrumentImageContext.appContext ?: return null
    return runCatching {
        context.assets.open("skin/captured_800x480.bin").use { it.readBytes() }
    }.getOrNull()
}

private fun decodeBitmap(bytes: ByteArray): InstrumentRaster? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) {
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
    return AndroidInstrumentRaster(applyExifOrientation(bitmap, bytes))
}

private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val orientation = runCatching {
        ExifInterface(bytes.inputStream()).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (oriented !== bitmap) bitmap.recycle()
    return oriented
}

internal fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
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

internal const val MAX_SOURCE_BYTES = 30 * 1024 * 1024
