package top.ghhccghk.yadea.mirror

/**
 * JpegEncoder - 平台无关的 JPEG 编码接口
 *
 * 视频热路径使用单次 JPEG 编码；仅当结果超过 80KB 硬上限时降到质量 72 重试一次，
 * 避免对同一帧反复压缩造成投屏卡顿。
 */
private const val IMAGE_HARD_LIMIT = 81_920
private const val LARGE_FRAME_PIXELS = 320 * 240

expect object JpegEncoder {
    fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray
    fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray
}

/**
 * 快速 JPEG 编码：首选质量 80，超过硬上限后仅重试一次质量 72。
 */
fun encodeAdaptiveJpeg(
    source: PlatformBitmap,
    targetWidth: Int,
    targetHeight: Int
): ByteArray {
    val initialQuality =
        if (targetWidth * targetHeight > LARGE_FRAME_PIXELS) 72 else 80
    val first = JpegEncoder.encodeAtQuality(
        source,
        targetWidth,
        targetHeight,
        initialQuality,
    )
    if (first.size <= IMAGE_HARD_LIMIT) return first

    // 视频热路径只做一次降质重编码，避免每帧多次 JPEG 压缩造成播放卡顿。
    return JpegEncoder.encodeAtQuality(
        source,
        targetWidth,
        targetHeight,
        (initialQuality - 8).coerceAtLeast(56),
    )
}
