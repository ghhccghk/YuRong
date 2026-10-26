package top.ghhccghk.yadea.image

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val IMAGE_CACHE_DIRECTORY = "app-image-cache"
private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
private const val MAX_DISK_IMAGE_BYTES = 64L * 1024L * 1024L
private const val MAX_DISK_IMAGE_FILES = 64

object AndroidImageCacheContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun contextOrNull(): Context? = appContext
}

internal actual fun createImageBytesStore(): ImageBytesStore =
    AndroidFileImageBytesStore()

internal actual suspend fun downloadImageBytes(url: String): ByteArray? =
    withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "image/*,*/*;q=0.8")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            if (connection.contentLengthLong > MAX_IMAGE_BYTES) return@withContext null
            connection.inputStream.use(::readLimited)
        } finally {
            connection.disconnect()
        }
    }

private class AndroidFileImageBytesStore : ImageBytesStore {
    private val fileMutex = Mutex()

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val file = cacheFile(key, createDirectory = false) ?: return@withLock null
            if (!file.isFile) return@withLock null
            try {
                file.setLastModified(System.currentTimeMillis())
                file.readBytes()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
    }

    override suspend fun write(key: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val target = cacheFile(key, createDirectory = true) ?: return@withLock
                val temporary = File(target.parentFile, "${target.name}.tmp")
                try {
                    temporary.writeBytes(bytes)
                    if (target.exists() && !target.delete()) {
                        return@withLock
                    }
                    if (!temporary.renameTo(target)) {
                        target.writeBytes(bytes)
                        temporary.delete()
                    }
                    target.setLastModified(System.currentTimeMillis())
                    trim(target.parentFile)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                } finally {
                    temporary.delete()
                }
            }
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                cacheFile(key, createDirectory = false)?.delete()
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val directory = cacheDirectory(create = false) ?: return@withLock
                directory.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
        }
    }

    private fun cacheFile(key: String, createDirectory: Boolean): File? =
        cacheDirectory(createDirectory)?.let { File(it, "$key.img") }

    private fun cacheDirectory(create: Boolean): File? {
        val context = AndroidImageCacheContext.contextOrNull() ?: return null
        val directory = File(context.cacheDir, IMAGE_CACHE_DIRECTORY)
        if (create && !directory.exists()) directory.mkdirs()
        return directory.takeIf { it.isDirectory }
    }

    private fun trim(directory: File?) {
        val files = directory
            ?.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedBy { it.lastModified() }
            ?: return
        var totalBytes = files.sumOf { it.length() }
        var count = files.size
        for (file in files) {
            if (count <= MAX_DISK_IMAGE_FILES && totalBytes <= MAX_DISK_IMAGE_BYTES) break
            totalBytes -= file.length()
            count--
            file.delete()
        }
    }
}

private fun readLimited(stream: java.io.InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        if (total > MAX_IMAGE_BYTES) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
