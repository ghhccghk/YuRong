package top.ghhccghk.yadea.image

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.posix.memcpy

internal actual fun createImageBytesStore(): ImageBytesStore =
    MemoryImageBytesStore(maxEntries = 16)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual suspend fun downloadImageBytes(url: String): ByteArray? {
    val remoteUrl = NSURL(string = url)
    val data = NSData.dataWithContentsOfURL(remoteUrl) ?: return null
    val length = data.length.toInt()
    if (length <= 0 || length > MAX_IMAGE_BYTES) return null
    return ByteArray(length).also { bytes ->
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
}

private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
