package top.ghhccghk.yadea.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

actual fun createTcpTransport(): TcpTransport = JvmTcpTransport()

class JvmTcpTransport : TcpTransport {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    override val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false
    override suspend fun connect(host: String, port: Int, timeoutMs: Long) {
        withContext(Dispatchers.IO) {
            close()
            val s = Socket()
            s.connect(InetSocketAddress(host, port), timeoutMs.toInt())
            s.soTimeout = timeoutMs.toInt()
            socket = s; input = s.getInputStream(); output = s.getOutputStream()
        }
    }
    override suspend fun send(data: ByteArray) {
        withContext(Dispatchers.IO) {
            val out = output ?: throw IllegalStateException("TCP not connected")
            out.write(data); out.flush()
        }
    }
    override suspend fun receive(maxBytes: Int, timeoutMs: Long): ByteArray? {
        return withContext(Dispatchers.IO) {
            val inp = input ?: return@withContext null
            socket?.soTimeout = timeoutMs.toInt()
            try {
                val buffer = ByteArray(maxBytes)
                val bytesRead = inp.read(buffer)
                if (bytesRead > 0) buffer.copyOf(bytesRead) else null
            } catch (_: Exception) { null }
        }
    }
    override suspend fun readExact(length: Int, timeoutMs: Long): ByteArray? {
        return withContext(Dispatchers.IO) {
            val inp = input ?: return@withContext null
            socket?.soTimeout = timeoutMs.toInt()
            try {
                val buffer = ByteArray(length)
                var totalRead = 0
                while (totalRead < length) {
                    val bytesRead = inp.read(buffer, totalRead, length - totalRead)
                    if (bytesRead == -1) return@withContext null
                    totalRead += bytesRead
                }
                buffer
            } catch (_: Exception) { null }
        }
    }
    override fun close() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
    }
}