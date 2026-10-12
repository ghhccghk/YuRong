package top.ghhccghk.yadea.mirror

/**
 * JpegEncoder - 平台无关的 JPEG 编码接口
 *
 * 按原始 APK 编码参数:
 * - 质量初始 80，范围 72..86
 * - 目标压缩体 30-50KB (0x7800..0xC800)
 * - 硬上限 80KB (0x14000)
 */
expect object JpegEncoder {
    fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray
    fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray
}

/**
 * 自适应 JPEG 编码 (原始 APK 算法):
 * - quality 初始 80，范围 72..86
 * - 目标 30720..51200 bytes
 * - 超过 81920 bytes 继续降 quality（降幅 3，超目标1.5倍降幅 6）
 */
fun encodeAdaptiveJpeg(
    source: PlatformBitmap,
    targetWidth: Int,
    targetHeight: Int
): ByteArray {
    var quality = 80
    var result: ByteArray
    var retries = 0

    while (retries < 3) {
        result = JpegEncoder.encodeAtQuality(source, targetWidth, targetHeight, quality)
        val size = result.size

        when {
            size < 30_720 -> {
                // 太小，提高质量
                if (quality < 86) quality += 3 else break
            }
            size in 30_720..51_200 -> {
                return result // 目标范围
            }
            size <= 81_920 -> {
                // 超过目标但未超硬上限
                val step = if (size > 51_200 * 3 / 2) 6 else 3
                quality = (quality - step).coerceAtLeast(72)
            }
            else -> {
                // 超过硬上限，大幅降低
                quality = (quality - 6).coerceAtLeast(72)
            }
        }
        retries++
    }

    return JpegEncoder.encodeAtQuality(source, targetWidth, targetHeight, quality)
}
