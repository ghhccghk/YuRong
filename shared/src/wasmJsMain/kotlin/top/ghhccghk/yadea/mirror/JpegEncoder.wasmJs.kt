package top.ghhccghk.yadea.mirror

/**
 * WasmJS JpegEncoder stub
 */
actual object JpegEncoder {
    actual fun encode(source: PlatformBitmap, targetWidth: Int, targetHeight: Int): ByteArray {
        return encodeAdaptiveJpeg(source, targetWidth, targetHeight)
    }

    actual fun encodeAtQuality(source: PlatformBitmap, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray {
        // TODO: Implement using HTML5 Canvas + toBlob
        throw UnsupportedOperationException("WasmJS JPEG encoding not yet implemented")
    }
}
