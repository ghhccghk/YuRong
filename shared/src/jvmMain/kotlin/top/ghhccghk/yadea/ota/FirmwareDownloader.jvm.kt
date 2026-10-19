package top.ghhccghk.yadea.ota

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class JvmFirmwareDownloader : FirmwareDownloader {
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
            connection.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

actual fun createFirmwareDownloader(): FirmwareDownloader = JvmFirmwareDownloader()
