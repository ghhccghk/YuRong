package top.ghhccghk.yadea.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

actual fun createUdpTransport(): UdpTransport = JvmUdpTransport()

class JvmUdpTransport : UdpTransport {
    private var socket: DatagramSocket? = null
    override val isBound: Boolean get() = socket?.isBound == true && socket?.isClosed == false
    override suspend fun bind(port: Int) {
        withContext(Dispatchers.IO) {
            close()
            socket = DatagramSocket(port)
            socket?.soTimeout = 1000; socket?.broadcast = true
        }
    }
    override suspend fun send(data: ByteArray, host: String, port: Int) {
        withContext(Dispatchers.IO) {
            val s = socket ?: throw IllegalStateException("UDP not bound")
            s.send(DatagramPacket(data, data.size, InetAddress.getByName(host), port))
        }
    }
    override suspend fun broadcast(data: ByteArray, port: Int) {
        withContext(Dispatchers.IO) {
            val s = socket ?: throw IllegalStateException("UDP not bound")
            s.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), port))
        }
    }
    override suspend fun receive(maxBytes: Int, timeoutMs: Long): Pair<String, ByteArray>? {
        return withContext(Dispatchers.IO) {
            val s = socket ?: return@withContext null
            s.soTimeout = timeoutMs.toInt()
            try {
                val buffer = ByteArray(maxBytes)
                val packet = DatagramPacket(buffer, buffer.size)
                s.receive(packet)
                Pair(packet.address.hostAddress ?: "", packet.data.copyOf(packet.length))
            } catch (_: SocketException) { null } catch (_: Exception) { null }
        }
    }
    override fun close() { try { socket?.close() } catch (_: Exception) {}; socket = null }
}
