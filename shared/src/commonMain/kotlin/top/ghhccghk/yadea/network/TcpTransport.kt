package top.ghhccghk.yadea.network

/**
 * TCP 传输接口
 *
 * 各平台提供实现：Android/JVM 使用 java.net.Socket，iOS 使用 NSStream。
 */
interface TcpTransport {
    suspend fun connect(host: String, port: Int, timeoutMs: Long = 5000)
    suspend fun send(data: ByteArray)
    suspend fun receive(maxBytes: Int, timeoutMs: Long = 3000): ByteArray?
    suspend fun readExact(length: Int, timeoutMs: Long = 3000): ByteArray?
    val isConnected: Boolean
    fun close()
}

/**
 * 创建平台默认的 TcpTransport 实例
 */
expect fun createTcpTransport(): TcpTransport
