package top.ghhccghk.yadea.ota

/**
 * Wi-Fi OTA 本地 HTTP 文件服务。
 *
 * `serve` 会阻塞到仪表完成下载、主动停止或超时。`onProgress` 的进度按
 * 当前 HTTP 响应的已发送字节计算，和原 `FileServer` 一致。
 */
interface WifiOtaFileServer {
    suspend fun serve(
        firmware: ByteArray,
        fileName: String,
        port: Int = WifiOtaProtocol.HTTP_PORT,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onStarted: () -> Unit = {},
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Boolean

    fun stop()

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15 * 60 * 1_000L
    }
}

expect fun createWifiOtaFileServer(): WifiOtaFileServer
