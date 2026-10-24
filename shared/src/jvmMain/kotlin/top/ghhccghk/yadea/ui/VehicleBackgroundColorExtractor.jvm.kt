package top.ghhccghk.yadea.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

actual fun createVehicleBackgroundColorExtractor(): VehicleBackgroundColorExtractor =
    createEncodedImageColorExtractor()

internal actual suspend fun downloadVehicleBackgroundImage(url: String): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching { URL(url).readBytes() }.getOrNull()
    }
