package top.ghhccghk.yadea.mirror

/**
 * JS PlatformBitmap - wraps raw RGBA pixel data
 */
actual class PlatformBitmap(val pixels: ByteArray, actual val width: Int, actual val height: Int) {
    actual fun recycle() {
        // JS GC-managed; no explicit recycle
    }
}
