package top.ghhccghk.yadea.ota

actual fun createFirmwareDownloader(): FirmwareDownloader =
    object : FirmwareDownloader {
        override suspend fun download(url: String): ByteArray? = null
    }
