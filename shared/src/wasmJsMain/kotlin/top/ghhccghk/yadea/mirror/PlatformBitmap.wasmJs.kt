package top.ghhccghk.yadea.mirror

/**
 * WasmJS PlatformBitmap - wraps raw RGBA pixel data
 */
actual class PlatformBitmap(val pixels: ByteArray, actual val width: Int, actual val height: Int) {
    actual fun recycle() {
        // Wasm GC-managed
    }
}
