package top.ghhccghk.yadea.image

import js.typedarrays.toByteArray
import web.http.bytes
import web.http.fetch

private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024

internal actual fun createImageBytesStore(): ImageBytesStore =
    MemoryImageBytesStore(maxEntries = 12)

internal actual suspend fun downloadImageBytes(url: String): ByteArray? =
    runCatching {
        val resource = fetch(url)
        try {
            val response = resource.value
            if (!response.ok) return null
            response.bytes().toByteArray().takeIf { it.size <= MAX_IMAGE_BYTES }
        } finally {
            resource.close()
        }
    }.getOrNull()
