package top.ghhccghk.yadea.image

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap

private const val MEMORY_IMAGE_LIMIT = 12

internal data class CachedAppImage(
    val bytes: ByteArray,
    val bitmap: ImageBitmap,
)

/**
 * 编码字节缓存。Android/JVM 使用文件，其余平台使用内存实现；业务层只依赖
 * 这一接口，不直接接触平台文件系统或浏览器存储。
 */
internal interface ImageBytesStore {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, bytes: ByteArray)
    suspend fun delete(key: String)
    suspend fun clear()
}

internal class MemoryImageBytesStore(
    private val maxEntries: Int = 8,
    private val maxBytes: Long = 32L * 1024L * 1024L,
) : ImageBytesStore {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, ByteArray>()
    private var totalBytes = 0L

    override suspend fun read(key: String): ByteArray? = mutex.withLock {
        val value = entries.remove(key) ?: return@withLock null
        entries[key] = value
        value
    }

    override suspend fun write(key: String, bytes: ByteArray) {
        mutex.withLock {
            entries.remove(key)?.let { previous ->
                totalBytes -= previous.size
            }
            entries[key] = bytes.copyOf()
            totalBytes += bytes.size
            while (entries.size > maxEntries || totalBytes > maxBytes) {
                val oldest = entries.keys.firstOrNull() ?: break
                entries.remove(oldest)?.let { removed ->
                    totalBytes -= removed.size
                }
            }
        }
    }

    override suspend fun delete(key: String) {
        mutex.withLock {
            entries.remove(key)?.let { removed ->
                totalBytes -= removed.size
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            entries.clear()
            totalBytes = 0L
        }
    }
}

internal class AppImageCache(
    private val bytesStore: ImageBytesStore,
    private val downloader: suspend (String) -> ByteArray?,
    private val memoryLimit: Int = MEMORY_IMAGE_LIMIT,
) {
    private val mutex = Mutex()
    private val memory = LinkedHashMap<String, CachedAppImage>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<CachedAppImage?>>()

    suspend fun loadImage(url: String, forceRefresh: Boolean = false): ImageBitmap? =
        load(url, forceRefresh)?.bitmap

    suspend fun loadBytes(url: String, forceRefresh: Boolean = false): ByteArray? =
        load(url, forceRefresh)?.bytes

    suspend fun clear() {
        mutex.withLock {
            memory.clear()
        }
        bytesStore.clear()
    }

    private suspend fun load(url: String, forceRefresh: Boolean): CachedAppImage? {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return null
        val key = imageCacheKey(normalizedUrl)

        if (!forceRefresh) {
            val memoryHit = mutex.withLock {
                memory.remove(normalizedUrl)?.let { cached ->
                    memory[normalizedUrl] = cached
                    cached
                }
            }
            if (memoryHit != null) return memoryHit
        }

        var ownsRequest = false
        var memoryHit: CachedAppImage? = null
        val request = mutex.withLock {
            if (!forceRefresh) {
                memoryHit = memory[normalizedUrl]
            }
            memoryHit?.let { return@withLock null }
            inFlight[normalizedUrl] ?: CompletableDeferred<CachedAppImage?>().also {
                inFlight[normalizedUrl] = it
                ownsRequest = true
            }
        } ?: return memoryHit
        memoryHit?.let { return it }
        if (!ownsRequest) return request.await()

        var result: CachedAppImage? = null
        try {
            var diskBytes = if (forceRefresh) null else readCachedBytes(key)
            var image = diskBytes?.let(::decodeCachedImage)
            if (image == null && diskBytes != null) {
                runCatching { bytesStore.delete(key) }
                diskBytes = null
            }

            if (image == null) {
                val downloaded = try {
                    downloader(normalizedUrl)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
                if (downloaded != null && downloaded.isNotEmpty()) {
                    val downloadedImage = decodeCachedImage(downloaded)
                    if (downloadedImage != null) {
                        writeCachedBytes(key, downloaded)
                        diskBytes = downloaded
                        image = downloadedImage
                    }
                }
            }

            if (image == null && forceRefresh) {
                val fallbackBytes = readCachedBytes(key)
                image = fallbackBytes?.let(::decodeCachedImage)
                if (image != null) diskBytes = fallbackBytes
            }
            result = image?.let { CachedAppImage(diskBytes ?: ByteArray(0), it) }
            mutex.withLock {
                result?.let {
                    memory.remove(normalizedUrl)
                    memory[normalizedUrl] = it
                    while (memory.size > memoryLimit) {
                        val oldest = memory.keys.firstOrNull() ?: break
                        memory.remove(oldest)
                    }
                }
            }
        } finally {
            mutex.withLock {
                if (inFlight[normalizedUrl] === request) {
                    inFlight.remove(normalizedUrl)
                }
            }
            request.complete(result)
        }
        return result
    }

    private suspend fun readCachedBytes(key: String): ByteArray? =
        try {
            bytesStore.read(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

    private suspend fun writeCachedBytes(key: String, bytes: ByteArray) {
        try {
            bytesStore.write(key, bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }

    private fun decodeCachedImage(bytes: ByteArray): ImageBitmap? =
        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}

/**
 * 应用统一图片缓存入口。所有远程图片和取色流程都必须经过这里，以共享
 * 请求合并、内存缓存和平台字节缓存。
 */
object AppImages {
    private val cache = AppImageCache(
        bytesStore = createImageBytesStore(),
        downloader = ::downloadImageBytes,
    )

    suspend fun loadImage(url: String, forceRefresh: Boolean = false): ImageBitmap? =
        cache.loadImage(url, forceRefresh)

    suspend fun loadBytes(url: String, forceRefresh: Boolean = false): ByteArray? =
        cache.loadBytes(url, forceRefresh)

    suspend fun clear() {
        cache.clear()
    }
}

internal expect fun createImageBytesStore(): ImageBytesStore

internal expect suspend fun downloadImageBytes(url: String): ByteArray?

internal fun imageCacheKey(url: String): String {
    var hash = FNV_OFFSET_BASIS
    for (byte in url.encodeToByteArray()) {
        hash = hash xor (byte.toLong() and 0xFFL)
        hash *= FNV_PRIME
    }
    return "${hash.toULong().toString(16)}-${url.length.toString(16)}"
}

private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
