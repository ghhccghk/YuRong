package top.ghhccghk.yadea.ota

private class UnsupportedWifiOtaFileServer : WifiOtaFileServer {
    override suspend fun serve(
        firmware: ByteArray,
        fileName: String,
        port: Int,
        timeoutMs: Long,
        onStarted: () -> Unit,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean = throw UnsupportedOperationException("当前平台不支持本地 OTA 文件服务")

    override fun stop() = Unit
}

actual fun createWifiOtaFileServer(): WifiOtaFileServer = UnsupportedWifiOtaFileServer()
