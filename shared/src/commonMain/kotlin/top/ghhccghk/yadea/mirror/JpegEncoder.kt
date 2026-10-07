package top.ghhccghk.yadea.mirror

/**
 * JpegEncoder - platform-specific JPEG encoding
 *
 * Encodes a PlatformBitmap to JPEG bytes with adaptive quality:
 * starts at quality 60, decreases by 15 if output exceeds 240KB, minimum 35.
 *
 * Also handles center-crop scaling to match target aspect ratio.
 */
expect object JpegEncoder {
    /**
     * Encode bitmap to JPEG with center-crop scaling to target dimensions.
     * @param source Source bitmap
     * @param targetWidth Target width in pixels
     * @param targetHeight Target height in pixels
     * @return JPEG bytes
     */
    fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray

    /**
     * Encode at a specific JPEG quality level.
     * @param quality JPEG quality 1-100
     */
    fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray
}

/**
 * Shared adaptive quality encoding logic.
 * Starts at quality 60, reduces by 15 if output > maxBytes, minimum quality 35.
 */
fun encodeAdaptiveJpeg(
    source: PlatformBitmap,
    targetWidth: Int,
    targetHeight: Int,
    maxBytes: Int = 245_760 // 240KB
): ByteArray {
    var quality = 60
    var result: ByteArray
    do {
        result = JpegEncoder.encodeAtQuality(source, targetWidth, targetHeight, quality)
        if (result.size <= maxBytes) break
        quality -= 15
    } while (quality >= 35)
    return result
}
