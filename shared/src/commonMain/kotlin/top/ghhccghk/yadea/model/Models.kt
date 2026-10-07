package top.ghhccghk.yadea.model

/**
 * 仪表显示配置，由设备协商返回
 */
data class NavDisplayConfig(
    val width: Int = 960,
    val height: Int = 368,
    val rotation: Int = 0,
    val offsetX: Int = 0,
    val packetMultiplier: Int = 1
)

/**
 * 投屏内容类型
 */
sealed class MirrorContent {
    data object Demo : MirrorContent()
    data class Image(val uri: String, val name: String) : MirrorContent()
    data class Live(val sourceName: String) : MirrorContent()
}

/**
 * 投屏传输方式
 */
enum class NavigationTransport { WifiMirror, BleTiles }

/**
 * BLE 瓦片数据源
 */
enum class BleTileSource { Simulated, AmapTest }

/**
 * 连接候选面板
 */
enum class CandidatePanel(val title: String) {
    Ap("AP 热点"),
    P2p("P2P 设备")
}

/**
 * 图像分片包
 */
data class FramePacket(
    val crc16: Int,
    val chunkLength: Int,
    val imageSequence: Int,
    val totalSize: Int,
    val offset: Int,
    val timestamp: Long,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FramePacket) return false
        return crc16 == other.crc16 && chunkLength == other.chunkLength &&
                imageSequence == other.imageSequence && totalSize == other.totalSize &&
                offset == other.offset && timestamp == other.timestamp &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = crc16
        result = 31 * result + chunkLength
        result = 31 * result + imageSequence
        result = 31 * result + totalSize
        result = 31 * result + offset
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
