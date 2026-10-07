package top.ghhccghk.yadea.mirror

/**
 * Android PlatformBitmap wrapping android.graphics.Bitmap
 */
actual class PlatformBitmap(val native: android.graphics.Bitmap) {
    actual val width: Int get() = native.width
    actual val height: Int get() = native.height

    actual fun recycle() {
        if (!native.isRecycled) native.recycle()
    }
}
