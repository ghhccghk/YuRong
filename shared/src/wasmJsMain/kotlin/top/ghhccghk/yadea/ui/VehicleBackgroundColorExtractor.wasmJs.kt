package top.ghhccghk.yadea.ui

import js.typedarrays.toByteArray
import web.http.bytes
import web.http.fetch

actual fun createVehicleBackgroundColorExtractor(): VehicleBackgroundColorExtractor =
    createEncodedImageColorExtractor()

internal actual suspend fun downloadVehicleBackgroundImage(url: String): ByteArray? =
    runCatching {
        val resource = fetch(url)
        try {
            val response = resource.value
            if (!response.ok) return null
            response.bytes().toByteArray()
        } finally {
            resource.close()
        }
    }.getOrNull()
