package top.ghhccghk.yadea.mirror

/**
 * JpegEncoder - 平台无关的 JPEG 编码接口
 *
 * 视频热路径使用单次 JPEG 编码；仅当结果超过 80KB 硬上限时按当前画质档位
 * 降低一次质量重试，避免对同一帧反复压缩造成投屏卡顿。
 */
private const val IMAGE_HARD_LIMIT = 81_920

expect object JpegEncoder {
    fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray
    fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray
}

/**
 * 兼容旧调用点：没有显式画质配置时保持原自动策略。
 */
fun encodeAdaptiveJpeg(
    source: PlatformBitmap,
    targetWidth: Int,
    targetHeight: Int
): ByteArray = encodeAdaptiveJpeg(
    source = source,
    targetWidth = targetWidth,
    targetHeight = targetHeight,
    quality = MirrorQualitySettings(),
).bytes

/**
 * 按用户档位编码一帧，并在超过协议硬上限时只降质重试一次。
 */
fun encodeAdaptiveJpeg(
    source: PlatformBitmap,
    targetWidth: Int,
    targetHeight: Int,
    quality: MirrorQualitySettings,
): JpegEncoding {
    val initialQuality = quality.initialQuality(targetWidth, targetHeight)
    val first = JpegEncoder.encodeAtQuality(
        source,
        targetWidth,
        targetHeight,
        initialQuality,
    )
    if (first.size <= IMAGE_HARD_LIMIT) {
        return JpegEncoding(first, initialQuality, retried = false)
    }

    // 视频热路径只做一次降质重编码，避免每帧多次 JPEG 压缩造成播放卡顿。
    val retryQuality = quality.retryQuality(initialQuality)
    val retried = JpegEncoder.encodeAtQuality(
        source,
        targetWidth,
        targetHeight,
        retryQuality,
    )
    return JpegEncoding(retried, retryQuality, retried = true)
}
