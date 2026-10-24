package top.ghhccghk.yadea.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual fun createVehicleBackgroundColorExtractor(): VehicleBackgroundColorExtractor =
    createEncodedImageColorExtractor()

internal actual suspend fun downloadVehicleBackgroundImage(url: String): ByteArray? =
    withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
