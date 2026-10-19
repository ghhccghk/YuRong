package top.ghhccghk.yadea.ota

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AndroidFirmwareDownloader : FirmwareDownloader {
    override suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val maxBytes = 64L * 1024L * 1024L
            val declared = connection.contentLengthLong
            if (declared > maxBytes) return@withContext null
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return@withContext null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

actual fun createFirmwareDownloader(): FirmwareDownloader = AndroidFirmwareDownloader()
