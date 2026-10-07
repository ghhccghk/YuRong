package top.ghhccghk.yadea.mirror

/**
 * JS JpegEncoder stub - use Canvas API in real implementation
 */
actual object JpegEncoder {
    actual fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray {
        return encodeAdaptiveJpeg(source, targetWidth, targetHeight)
    }

    actual fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray {
        // TODO: Implement using HTML5 Canvas + toBlob('image/jpeg', quality/100)
        throw UnsupportedOperationException("JS JPEG encoding not yet implemented")
    }
}
