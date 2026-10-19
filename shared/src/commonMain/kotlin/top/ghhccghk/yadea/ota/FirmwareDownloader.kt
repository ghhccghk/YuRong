package top.ghhccghk.yadea.ota

interface FirmwareDownloader {
    suspend fun download(url: String): ByteArray?
}

expect fun createFirmwareDownloader(): FirmwareDownloader
