package top.ghhccghk.yadea.ui

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.posix.memcpy

actual fun createVehicleBackgroundColorExtractor(): VehicleBackgroundColorExtractor =
    createEncodedImageColorExtractor()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual suspend fun downloadVehicleBackgroundImage(url: String): ByteArray? {
    val remoteUrl = NSURL(string = url)
    val data = NSData.dataWithContentsOfURL(remoteUrl) ?: return null
    val length = data.length.toInt()
    if (length <= 0) return null
    return ByteArray(length).also { bytes ->
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
}
