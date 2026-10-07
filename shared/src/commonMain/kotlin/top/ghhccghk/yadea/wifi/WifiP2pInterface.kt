package top.ghhccghk.yadea.wifi

import kotlinx.coroutines.flow.StateFlow

/**
 * 通用 WiFi P2P 管理接口
 */
interface WifiP2pManager {
    val isAvailable: Boolean
    val discoveredPeers: StateFlow<List<P2pDevice>>
    val connectionState: StateFlow<P2pStatus>
    val isDiscovering: StateFlow<Boolean>
    val isGroupFormed: StateFlow<Boolean>
    val groupOwnerIp: StateFlow<String>

    suspend fun discoverPeers(timeoutMs: Long = 30_000)
    suspend fun connect(device: P2pDevice): Boolean
    suspend fun disconnect()
    fun close()
}

data class P2pDevice(
    val name: String,
    val address: String,
    val status: P2pStatus = P2pStatus.Available
)

enum class P2pStatus {
    Available, Invited, Connected, Failed
}

/**
 * 创建平台默认的 WifiP2pManager
 */
expect fun createWifiP2pManager(): WifiP2pManager
