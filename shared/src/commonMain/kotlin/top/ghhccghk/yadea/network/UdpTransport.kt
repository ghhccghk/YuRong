package top.ghhccghk.yadea.network

interface UdpTransport {
    suspend fun bind(port: Int = 0)
    suspend fun send(data: ByteArray, host: String, port: Int)
    suspend fun broadcast(data: ByteArray, port: Int)
    suspend fun receive(maxBytes: Int, timeoutMs: Long = 1000): Pair<String, ByteArray>?
    val isBound: Boolean
    fun close()
}

expect fun createUdpTransport(): UdpTransport
