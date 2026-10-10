package top.ghhccghk.yadea.mirror

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import top.ghhccghk.yadea.network.FrameSender
import top.ghhccghk.yadea.network.TcpTransport
import top.ghhccghk.yadea.network.UdpTransport
import top.ghhccghk.yadea.protocol.YadeaProtocol

private class FakeTcpTransport : TcpTransport {
    val sentFrames = mutableListOf<ByteArray>()
    private val readBuffer = ArrayDeque<Byte>()
    override var isConnected = true; private set

    fun enqueueResponse(frame: ByteArray) { frame.forEach { readBuffer.addLast(it) } }
    override suspend fun connect(host: String, port: Int, timeoutMs: Long) { isConnected = true }
    override suspend fun send(data: ByteArray) { sentFrames.add(data) }
    override suspend fun receive(maxBytes: Int, timeoutMs: Long): ByteArray? {
        if (readBuffer.isEmpty()) return null
        val n = minOf(maxBytes, readBuffer.size)
        return ByteArray(n) { readBuffer.removeFirst() }
    }
    override suspend fun readExact(length: Int, timeoutMs: Long): ByteArray? {
        if (readBuffer.size < length) return null
        return ByteArray(length) { readBuffer.removeFirst() }
    }
    override fun close() { isConnected = false }
}

private class FakeUdpTransport : UdpTransport {
    val sentPackets = mutableListOf<Pair<String, ByteArray>>()
    val broadcastPackets = mutableListOf<ByteArray>()
    override var isBound = true; private set
    override suspend fun bind(port: Int) { isBound = true }
    override suspend fun send(data: ByteArray, host: String, port: Int) { sentPackets.add(Pair(host, data)) }
    override suspend fun broadcast(data: ByteArray, port: Int) { broadcastPackets.add(data) }
    override suspend fun receive(maxBytes: Int, timeoutMs: Long): Pair<String, ByteArray>? = null
    override fun close() { isBound = false }
}

class MirrorEngineTest {
    private lateinit var tcp: FakeTcpTransport
    private lateinit var udp: FakeUdpTransport
    private lateinit var sender: FrameSender
    private lateinit var frameBus: FrameBus
    private lateinit var engine: MirrorEngine
    private lateinit var scope: CoroutineScope
    private val statusLog = mutableListOf<String>()

    @Before fun setUp() {
        tcp = FakeTcpTransport(); udp = FakeUdpTransport()
        sender = FrameSender(tcp, udp); frameBus = FrameBus()
        statusLog.clear()
        engine = MirrorEngine(sender, frameBus) { statusLog.add(it) }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    @After fun tearDown() { scope.cancel() }
    private fun dateTimeProvider() = DateTimeParts(2026, 10, 7, 14, 30, 0)

    @Test fun initialState_isIdle() {
        assertEquals(MirrorState.Idle, engine.currentState)
        assertFalse(engine.isRunning)
    }

    @Test fun start_changesStateToRunning() = runBlocking {
        val result = engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertTrue(result)
        assertEquals(MirrorState.Running, engine.currentState)
    }

    @Test fun stop_changesStateToStopped() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        engine.stop()
        assertEquals(MirrorState.Stopped, engine.currentState)
    }

    @Test fun start_twice_returnsTrue() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        val second = engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertTrue(second)
    }

    @Test fun start_sendsNavStartFrame() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertTrue(tcp.sentFrames.isNotEmpty())
        val frame = tcp.sentFrames[0]
        assertEquals(0x20, frame[7].toInt() and 0xFF)
    }

    @Test fun start_withResponse_parsesConfig() = runBlocking {
        val configPayload = ByteArray(8)
        configPayload[0] = 0x04.toByte() // shift width = 4*256 = 1024
        configPayload[1] = 0x02.toByte() // shift height = 2*256 = 512
        configPayload[2] = 0x00.toByte()
        configPayload[3] = 0x00.toByte()
        configPayload[7] = 2.toByte()
        val responseFrame = YadeaProtocol.buildFrame(YadeaProtocol.CMD_NAV_START, configPayload)
        tcp.enqueueResponse(responseFrame)
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertEquals(1024, engine.currentConfig.width)
        assertEquals(512, engine.currentConfig.height)
    }

    @Test fun start_withoutResponse_usesDefaultConfig() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertEquals(960, engine.currentConfig.width)
        assertEquals(368, engine.currentConfig.height)
    }

    @Test fun frameSending_sendsUdpPackets() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        val bmp = PlatformBitmap(java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB))
        frameBus.submit(bmp)
        delay(500)
        assertTrue(udp.sentPackets.isNotEmpty())
    }

    @Test fun frameSending_incrementsCounters() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        val bmp = PlatformBitmap(java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB))
        frameBus.submit(bmp)
        delay(500)
        assertTrue(engine.sentFrameCount > 0)
        assertTrue(engine.sentByteCount > 0)
    }

    @Test fun broadcast_sendsBroadcastPackets() = runBlocking {
        engine.start("192.168.1.100", 8080, broadcastPort = 9090, scope = scope, currentTimeProvider = ::dateTimeProvider)
        val bmp = PlatformBitmap(java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB))
        frameBus.submit(bmp)
        delay(500)
        assertTrue(udp.broadcastPackets.isNotEmpty())
    }

    @Test fun heartbeat_sendsPeriodically() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        delay(2500)
        val heartbeatFrames = tcp.sentFrames.filter { (it[7].toInt() and 0xFF) == 0x1F }
        assertTrue(heartbeatFrames.size >= 2)
    }

    @Test fun stop_sendsNavEnd() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        engine.stop()
        val navEndFrames = tcp.sentFrames.filter { (it[7].toInt() and 0xFF) == 0x02 }
        assertTrue(navEndFrames.isNotEmpty())
    }

    @Test fun stop_clearsFrameBus() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        val bmp = PlatformBitmap(java.awt.image.BufferedImage(100, 50, java.awt.image.BufferedImage.TYPE_INT_RGB))
        frameBus.submit(bmp)
        engine.stop()
        assertFalse(frameBus.hasFrame())
    }

    @Test fun statusLog_containsMessages() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertTrue(statusLog.isNotEmpty())
        engine.stop()
        assertTrue(statusLog.any { it.contains("停止") })
    }
}
