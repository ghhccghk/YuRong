package top.ghhccghk.yadea.ota

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.ghhccghk.yadea.network.TcpTransport
import top.ghhccghk.yadea.network.createTcpTransport
import top.ghhccghk.yadea.network.NetworkLog
import top.ghhccghk.yadea.wifi.WifiP2pManager

class WifiOtaInstallException(message: String) : Exception(message)

/**
 * 原 App Wi-Fi OTA 链路：
 *
 * BLE 0xFD -> wifiStatusNew=2 -> P2P -> TCP 8888 鉴权 -> 手机 HTTP 8080
 * 供仪表拉取固件。
 *
 * 高风险：该流程按 smali 直译，未经过完整真车回归。失败可能让车辆停在
 * bootloader，调用方必须提供明确的风险确认。
 */
class WifiOtaInstaller(
    private val p2pManager: WifiP2pManager?,
    private val localIpAddressProvider: suspend () -> String?,
    private val prepareWifi: suspend () -> Boolean = { true },
    private val targetHostProvider: suspend () -> String? = { null },
    private val expectedP2pAddressProvider: () -> String = { "" },
    private val expectedDvrNameProvider: () -> String = { "" },
    private val tcpTransportFactory: () -> TcpTransport = ::createTcpTransport,
    private val fileServerFactory: () -> WifiOtaFileServer = ::createWifiOtaFileServer,
    private val scope: CoroutineScope,
) {
    suspend fun install(
        firmware: ByteArray,
        fileName: String = "firmware.bin",
        isDvr: Boolean = false,
        onProgress: suspend (progress: Float, stage: OtaStage) -> Unit,
    ): Boolean {
        val versionByte = WifiOtaProtocol.firmwareVersionByte(firmware)
            ?: return fail(onProgress, "固件头不足 64 字节或文件类型无效")
        if (!prepareWifi()) {
            return fail(onProgress, "车辆 Wi-Fi/P2P 未就绪，请先连接 BLE 并保持车辆在线")
        }

        onProgress(0.18f, OtaStage.Installing)
        val host = resolveTargetHost(isDvr)
            ?: return fail(onProgress, "未找到车辆 P2P 的 GroupOwner")
        if (!WifiOtaProtocol.isValidGroupOwner(host)) {
            return fail(onProgress, "P2P GroupOwner($host) 不是目标车辆")
        }

        val localIp = localIpAddressProvider()
        val lastOctet = WifiOtaProtocol.localIpLastOctet(localIp)
            ?: return fail(onProgress, "无法读取手机 Wi-Fi IPv4 地址")

        val auth = WifiOtaProtocol.buildAuthPacket(versionByte, lastOctet)
        NetworkLog.write(
            "WIFI_OTA auth host=$host:${WifiOtaProtocol.TCP_PORT} " +
                "packet=${auth.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}",
        )
        val tcp = tcpTransportFactory()
        try {
            tcp.connect(
                host = host,
                port = WifiOtaProtocol.TCP_PORT,
                timeoutMs = WifiOtaProtocol.TCP_CONNECT_TIMEOUT_MS.toLong(),
            )
            tcp.send(auth)
            val response = tcp.readExact(
                length = AUTH_RESPONSE_BYTES,
                timeoutMs = AUTH_RESPONSE_TIMEOUT_MS,
            )
            if (response == null || !WifiOtaProtocol.isAuthAccepted(response)) {
                NetworkLog.write(
                    "WIFI_OTA auth rejected response=" +
                        response?.joinToString(" ") {
                            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
                        },
                )
                return fail(onProgress, "仪表拒绝 Wi-Fi OTA 鉴权")
            }
            NetworkLog.write("WIFI_OTA auth accepted")
        } catch (e: WifiOtaInstallException) {
            throw e
        } catch (e: Exception) {
            NetworkLog.write("WIFI_OTA auth failed: ${e.message}")
            return fail(onProgress, "连接仪表 TCP 失败：${e.message.orEmpty()}")
        } finally {
            tcp.close()
        }

        onProgress(0.22f, OtaStage.Installing)
        val progress = MutableStateFlow(0f)
        val reporter = scope.launch {
            while (true) {
                onProgress(
                    0.22f + progress.value.coerceIn(0f, 1f) * 0.78f,
                    OtaStage.Installing,
                )
                delay(200)
            }
        }
        val server = fileServerFactory()
        return try {
            val completed = server.serve(
                firmware = firmware,
                fileName = fileName,
                onStarted = {
                    NetworkLog.write(
                        "WIFI_OTA http server started port=${WifiOtaProtocol.HTTP_PORT}",
                    )
                },
                onProgress = { sent, total ->
                    if (total > 0L) {
                        progress.value = (sent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }
                },
            )
            if (completed) {
                onProgress(1f, OtaStage.Installing)
                true
            } else {
                fail(onProgress, "仪表未完成固件下载或文件服务超时")
            }
        } catch (e: WifiOtaInstallException) {
            throw e
        } catch (e: Exception) {
            fail(onProgress, "Wi-Fi 固件传输失败：${e.message.orEmpty()}")
        } finally {
            reporter.cancel()
            server.stop()
        }
    }

    private suspend fun resolveTargetHost(isDvr: Boolean): String? {
        targetHostProvider()?.takeIf { it.isNotBlank() }?.let { return it }
        val p2p = p2pManager?.takeIf { it.isAvailable } ?: return null
        p2p.groupOwnerIp.value.takeIf { WifiOtaProtocol.isValidGroupOwner(it) }?.let { return it }

        runCatching { p2p.discoverPeers(P2P_DISCOVER_TIMEOUT_MS) }
        val expected = expectedP2pAddressProvider().trim()
        val expectedDvrName = expectedDvrNameProvider().trim()
        val peers = p2p.discoveredPeers.value
        val peer = (
            if (isDvr) {
                peers.firstOrNull {
                    expectedDvrName.isNotBlank() &&
                        it.name.equals(expectedDvrName, ignoreCase = true)
                }
            } else if (expected.isNotBlank()) {
                peers.firstOrNull { it.address.equals(expected, ignoreCase = true) }
            } else {
                peers.firstOrNull()
            }
            ) ?: return null
        if (!runCatching { p2p.connect(peer) }.getOrDefault(false)) return null
        return withTimeoutOrNull(P2P_CONNECT_TIMEOUT_MS) {
            while (!WifiOtaProtocol.isValidGroupOwner(p2p.groupOwnerIp.value)) {
                delay(STATUS_POLL_INTERVAL_MS)
            }
            p2p.groupOwnerIp.value
        }
    }

    private suspend fun fail(
        onProgress: suspend (Float, OtaStage) -> Unit,
        message: String,
    ): Nothing {
        NetworkLog.write("WIFI_OTA !! $message")
        onProgress(0f, OtaStage.Failed)
        throw WifiOtaInstallException(message)
    }

    private companion object {
        const val P2P_DISCOVER_TIMEOUT_MS = 25_000L
        const val P2P_CONNECT_TIMEOUT_MS = 20_000L
        const val STATUS_POLL_INTERVAL_MS = 250L
        const val AUTH_RESPONSE_BYTES = 7
        const val AUTH_RESPONSE_TIMEOUT_MS = 3_000L
    }
}
