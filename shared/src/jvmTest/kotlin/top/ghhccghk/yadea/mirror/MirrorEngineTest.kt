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
import java.util.concurrent.CopyOnWriteArrayList

private class FakeTcpTransport : TcpTransport {
    val sentFrames = CopyOnWriteArrayList<ByteArray>()
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
    val sentPackets = CopyOnWriteArrayList<Pair<String, ByteArray>>()
    val broadcastPackets = CopyOnWriteArrayList<ByteArray>()
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
    private val statusLog = CopyOnWriteArrayList<String>()

    @Before fun setUp() {
        tcp = FakeTcpTransport(); udp = FakeUdpTransport()
        sender = FrameSender(tcp, udp); frameBus = FrameBus()
        statusLog.clear()
        engine = MirrorEngine(sender, frameBus) { statusLog.add(it) }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After fun tearDown() {
        if (::engine.isInitialized) engine.release()
        if (::scope.isInitialized) scope.cancel()
    }

    private fun dateTimeProvider() = DateTimeParts(2026, 10, 7, 14, 30, 0)

    private fun enqueueStartResponse(rate: Int = 10) {
        val configPayload = ByteArray(8)
        configPayload[0] = 0x03.toByte(); configPayload[1] = 0x20.toByte() // 800
        configPayload[2] = 0x01.toByte(); configPayload[3] = 0xE0.toByte() // 480
        configPayload[4] = 0.toByte()  // format
        configPayload[6] = 6.toByte()  // screenType
        configPayload[7] = rate.toByte()
        tcp.enqueueResponse(
            YadeaProtocol.buildFullFrame(YadeaProtocol.CMD_NAV_START_RESPONSE, configPayload),
        )
    }

    @Test fun initialState_isIdle() {
        assertEquals(MirrorState.Idle, engine.currentState)
    }

    @Test fun start_changesStateToRunning() = runBlocking {
        enqueueStartResponse()
        val result = engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertTrue(result)
        assertEquals(MirrorState.Running, engine.currentState)
    }

    @Test fun stop_changesStateToStopped() = runBlocking {
        enqueueStartResponse()
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        engine.stop()
        assertEquals(MirrorState.Stopped, engine.currentState)
    }

    @Test fun start_withResponse_parsesConfigAndUsesVehicleRate() = runBlocking {
        enqueueStartResponse(rate = 10)
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertEquals(800, engine.currentConfig.width)
        assertEquals(480, engine.currentConfig.height)
        assertEquals(10, engine.effectiveFps)
    }

    @Test fun start_userPreferenceTightensVehicleRate() = runBlocking {
        enqueueStartResponse(rate = 30)
        engine.start(
            "192.168.1.100",
            8080,
            scope = scope,
            currentTimeProvider = ::dateTimeProvider,
            userPreferredFps = 5,
        )
        assertEquals(5, engine.effectiveFps)
    }

    @Test fun start_withoutResponse_usesDefaultConfig() = runBlocking {
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        assertEquals(800, engine.currentConfig.width)
        assertEquals(480, engine.currentConfig.height)
        assertEquals(10, engine.effectiveFps)
    }

    @Test fun frameSending_sendsUdpPacketsAndPublishesMetrics() = runBlocking {
        enqueueStartResponse(rate = 30)
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        val bmp = PlatformBitmap(java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB))
        frameBus.submit(bmp)
        delay(500)
        engine.stop()

        assertTrue(udp.sentPackets.isNotEmpty())
        val metrics = engine.metrics.value
        assertTrue(metrics.sentFrames >= 1)
        assertTrue(metrics.encodedFrames >= 1)
        assertEquals(200, metrics.inputWidth)
        assertEquals(100, metrics.inputHeight)
        assertEquals(800, metrics.outputWidth)
        assertEquals(480, metrics.outputHeight)
        assertTrue(metrics.jpegBytes > 0)
        assertTrue(metrics.udpChunks > 0)
        assertEquals(1L, metrics.capturedFrames)
    }

    @Test fun heartbeat_sendsPeriodically() = runBlocking {
        enqueueStartResponse()
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        delay(2500)
        val heartbeats = tcp.sentFrames.filter {
            it.size >= 8 && (it[7].toInt() and 0xFF) == 0x1F
        }
        assertTrue(heartbeats.size >= 1)
    }

    @Test fun release_isIdempotentAndPreventsRestart() = runBlocking {
        engine.release()
        engine.release()
        engine.stop()

        assertEquals(MirrorState.Stopped, engine.currentState)
        assertFalse(
            engine.start(
                "192.168.1.100",
                8080,
                scope = scope,
                currentTimeProvider = ::dateTimeProvider,
            ),
        )
        assertEquals(MirrorState.Stopped, engine.currentState)
    }

    @Test fun stop_sendsNavEndAndWaitsForResponse() = runBlocking {
        enqueueStartResponse()
        engine.start("192.168.1.100", 8080, scope = scope, currentTimeProvider = ::dateTimeProvider)
        tcp.enqueueResponse(YadeaProtocol.buildFullFrame(YadeaProtocol.CMD_NAV_END_RESPONSE, byteArrayOf(1)))
        engine.stop()
        val navEndFrames = tcp.sentFrames.filter {
            it.size >= 8 && (it[7].toInt() and 0xFF) == 0x13
        }
        assertTrue(navEndFrames.isNotEmpty())
        assertTrue(statusLog.any { it.contains("收到 NAV_END 响应") })
    }
}
