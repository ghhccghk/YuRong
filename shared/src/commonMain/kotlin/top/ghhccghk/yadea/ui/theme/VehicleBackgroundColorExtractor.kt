package top.ghhccghk.yadea.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import top.ghhccghk.yadea.image.AppImages
import kotlin.math.abs
import kotlin.math.max

private const val MAX_SAMPLE_EDGE = 160

/**
 * 从当前车辆背景图提取可用的主题种子色。
 *
 * 平台不支持图片下载或解码时返回 `null`，上层继续使用用户原来的种子色。
 */
fun interface VehicleBackgroundColorExtractor {
    suspend fun extractSeedArgb(url: String): Int?
}

expect fun createVehicleBackgroundColorExtractor(): VehicleBackgroundColorExtractor

internal fun createEncodedImageColorExtractor(): VehicleBackgroundColorExtractor =
    EncodedImageVehicleBackgroundColorExtractor()

private class EncodedImageVehicleBackgroundColorExtractor : VehicleBackgroundColorExtractor {
    override suspend fun extractSeedArgb(url: String): Int? {
        val image = AppImages.loadImage(url) ?: return null
        val pixelMap = image.toPixelMap()
        val step = max(1, max(pixelMap.width, pixelMap.height) / MAX_SAMPLE_EDGE)
        val pixels = ArrayList<Int>((pixelMap.width / step + 1) * (pixelMap.height / step + 1))
        var y = 0
        while (y < pixelMap.height) {
            var x = 0
            while (x < pixelMap.width) {
                pixels += pixelMap[x, y].toArgb()
                x += step
            }
            y += step
        }
        return VehicleBackgroundPalette.dominantArgb(pixels.toIntArray())
    }
}

internal object VehicleBackgroundPalette {
    fun dominantArgb(pixels: IntArray): Int? {
        val weights = FloatArray(HISTOGRAM_SIZE)
        val red = FloatArray(HISTOGRAM_SIZE)
        val green = FloatArray(HISTOGRAM_SIZE)
        val blue = FloatArray(HISTOGRAM_SIZE)
        var fallbackR = 0f
        var fallbackG = 0f
        var fallbackB = 0f
        var fallbackCount = 0

        for (pixel in pixels) {
            if (pixel ushr 24 < 128) continue
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            val hsv = rgbToHsv(r, g, b)
            val value = hsv[2]
            if (value >= 0.12f && value <= 0.98f) {
                fallbackR += r
                fallbackG += g
                fallbackB += b
                fallbackCount++
            }
            if (hsv[1] < MIN_SATURATION || value < 0.12f || value > 0.98f) continue

            val key = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
            val weight = 0.2f + hsv[1] * hsv[1] * (0.5f + value)
            weights[key] += weight
            red[key] += r
            green[key] += g
            blue[key] += b
        }

        val bestKey = weights.indices.maxByOrNull { weights[it] }
            ?.takeIf { weights[it] > 0f }
        if (bestKey == null) {
            if (fallbackCount == 0) return null
            return opaque(
                (fallbackR / fallbackCount).toInt(),
                (fallbackG / fallbackCount).toInt(),
                (fallbackB / fallbackCount).toInt(),
            )
        }

        var count = 0
        for (pixel in pixels) {
            if (pixel ushr 24 < 128) continue
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            if (((r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)) == bestKey) count++
        }
        count = max(1, count)
        val seeded = opaque(
            (red[bestKey] / count).toInt(),
            (green[bestKey] / count).toInt(),
            (blue[bestKey] / count).toInt(),
        )
        val hsv = argbToHsv(seeded)
        hsv[1] = max(hsv[1], 0.55f)
        hsv[2] = max(hsv[2], 0.62f)
        return hsvToOpaqueArgb(hsv)
    }

    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxValue = max(rf, max(gf, bf))
        val minValue = minOf(rf, gf, bf)
        val delta = maxValue - minValue
        val hue = when {
            delta == 0f -> 0f
            maxValue == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxValue == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (maxValue == 0f) 0f else delta / maxValue
        return floatArrayOf(hue, saturation, maxValue)
    }

    private fun argbToHsv(argb: Int): FloatArray =
        rgbToHsv(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)

    private fun hsvToOpaqueArgb(hsv: FloatArray): Int {
        val hue = ((hsv[0] % 360f) + 360f) % 360f
        val saturation = hsv[1].coerceIn(0f, 1f)
        val value = hsv[2].coerceIn(0f, 1f)
        val chroma = value * saturation
        val x = chroma * (1f - abs((hue / 60f) % 2f - 1f))
        val m = value - chroma
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(chroma, x, 0f)
            hue < 120f -> Triple(x, chroma, 0f)
            hue < 180f -> Triple(0f, chroma, x)
            hue < 240f -> Triple(0f, x, chroma)
            hue < 300f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        return opaque(
            ((r1 + m) * 255f).toInt(),
            ((g1 + m) * 255f).toInt(),
            ((b1 + m) * 255f).toInt(),
        )
    }

    private fun opaque(r: Int, g: Int, b: Int): Int =
        Color(
            red = r.coerceIn(0, 255) / 255f,
            green = g.coerceIn(0, 255) / 255f,
            blue = b.coerceIn(0, 255) / 255f,
            alpha = 1f,
        ).toArgb()

    private const val MIN_SATURATION = 0.18f
    private const val HISTOGRAM_SIZE = 16 * 16 * 16
}
