package top.ghhccghk.yadea.mirror

/**
 * PlatformBitmap - cross-platform bitmap abstraction
 *
 * Wraps the platform-native bitmap type:
 * - Android: android.graphics.Bitmap
 * - JVM/Desktop: java.awt.image.BufferedImage
 * - iOS: UIImage
 * - JS/Wasm: raw RGBA pixel data
 */
expect class PlatformBitmap {
    val width: Int
    val height: Int
    fun recycle()
}
