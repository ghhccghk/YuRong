package top.ghhccghk.yadea.vehicle

import top.ghhccghk.yadea.mirror.DateTimeParts

/** 握手随机块依赖的本机 HH:mm:ss，测试时可注入固定值。 */
fun interface VehicleLocalTimeProvider {
    fun now(): DateTimeParts
}

/**
 * 车端 RandKey 字节 13 中的鉴权能力位。
 *
 * 对应 `wi.j0`：bit3=Token 鉴权、bit6=刷写密钥、bit4..5=0b01 表示 V2。
 */
data class VehicleAuthProfile(
    val tokenAuth: Boolean,
    val requiresFlashKey: Boolean,
    val authVersion2: Boolean,
    val authVersion: Int,
) {
    val supportsTokenHandshake: Boolean
        get() = tokenAuth && authVersion2 && !requiresFlashKey

    companion object {
        fun parse(randomKey: ByteArray): VehicleAuthProfile {
            if (randomKey.size < 14) {
                return VehicleAuthProfile(
                    tokenAuth = false,
                    requiresFlashKey = true,
                    authVersion2 = false,
                    authVersion = -1,
                )
            }
            val flags = randomKey[13].toInt() and 0xFF
            return VehicleAuthProfile(
                tokenAuth = flags and 0x08 != 0,
                requiresFlashKey = flags and 0x40 == 0,
                authVersion2 = (flags ushr 4) and 0x03 == 0x01,
                authVersion = (flags ushr 2) and 0x0F,
            )
        }
    }
}

/** 车端鉴权回包：`[4]` 为结果码，`[2]` 为剩余 Token 次数。 */
data class VehicleHandshakeResponse(
    val result: Int,
    val remainingUses: Int,
) {
    val success: Boolean get() = result == 0

    companion object {
        fun parse(data: ByteArray): VehicleHandshakeResponse = VehicleHandshakeResponse(
            result = Ttp.m(data, 4),
            remainingUses = Ttp.m(data, 2),
        )
    }
}

/**
 * Token 鉴权握手帧，对应 `wi.E` + `wi.F`。
 *
 * 与普通 `5944...4B4A` 控制帧完全独立：固定 32 字节，最后一位是前 31 位的
 * XOR 折叠值。smali 中 F 的拼接顺序是
 * `token + 5944FFFFFFFF + tail + b0 + rand[6..8] + X + Y + FFFF`。
 */
object VehicleHandshakeFrame {

    const val MARKER = "5944FFFFFFFF"
    private const val DEFAULT_MODE = "10"
    private const val DEFAULT_PAD = "FF"

    /**
     * @param token 服务端返回的 `dkInfo`，作为 hex 字符串原样放在帧首
     * @param randomKey e7810b92 / bef8e7e0 收到的车端随机块
     * @param mac 蓝牙 MAC，可带或不带冒号
     * @param protocolVersion 1 时随机块为 14 字节，其他版本为 9 字节
     */
    fun build(
        token: String,
        randomKey: ByteArray,
        mac: String,
        protocolVersion: Int,
        localTime: DateTimeParts,
        networkAvailable: Boolean,
        mode: String = DEFAULT_MODE,
        isFactory: Boolean = false,
    ): ByteArray? {
        val randomBlock = buildRandomBlock(
            randomKey = randomKey,
            mac = mac,
            protocolVersion = protocolVersion,
            localTime = localTime,
            mode = mode,
        ) ?: return null
        val tokenHex = token.trim()
            .removePrefix("0x")
            .removePrefix("0X")
        if (tokenHex.isBlank() || tokenHex.length % 2 != 0 || tokenHex.hexToBytes() == null) {
            return null
        }

        val tail = if (networkAvailable) "FE" else "FF"
        val b0 = ((if (isFactory) 0xFF else 0xF1) and modeMask(mode)).toHexByte()
        val randomSlice = if (randomBlock.size >= 9) {
            randomBlock.copyOfRange(6, 9).toHex()
        } else {
            DEFAULT_PAD
        }
        val raw = buildString {
            append(tokenHex)
            append(MARKER)
            append(tail)
            append(b0)
            append(randomSlice)
            append(DEFAULT_PAD)
            append(DEFAULT_PAD)
            append("FFFF")
        }
        val decoded = raw.hexToBytes() ?: return null
        val frame = decoded.copyOf(32)
        frame[31] = Checksums.xorFold(frame, 31)
        return frame
    }

    /**
     * `wi.U` 的非账号分支。
     *
     * A 是 RandKey 前三字节大端值，时间取本机 HH:mm:ss，MAC 前后三字节分别与
     * `A or time` 异或。协议 1 再追加 `41 05 03 mode (45 xor mode)`。
     */
    internal fun buildRandomBlock(
        randomKey: ByteArray,
        mac: String,
        protocolVersion: Int,
        localTime: DateTimeParts,
        mode: String,
    ): ByteArray? {
        if (randomKey.size < 3) return null
        val macHex = mac.filter { it.isLetterOrDigit() }
        if (macHex.length < 12) return null
        val macPrefix = macHex.substring(0, 6).toIntOrNull(16) ?: return null
        val macSuffix = macHex.substring(6, 12).toIntOrNull(16) ?: return null

        val randomValue =
            ((randomKey[0].toInt() and 0xFF) shl 16) or
                ((randomKey[1].toInt() and 0xFF) shl 8) or
                (randomKey[2].toInt() and 0xFF)
        val timeValue =
            (localTime.hour.coerceIn(0, 23) shl 16) or
                (localTime.minute.coerceIn(0, 59) shl 8) or
                localTime.second.coerceIn(0, 59)
        val common = randomValue or timeValue
        val first = macPrefix xor common
        val second = macSuffix xor common

        val base = byteArrayOf(
            (randomValue ushr 16).toByte(),
            (randomValue ushr 8).toByte(),
            randomValue.toByte(),
            (first ushr 16).toByte(),
            (first ushr 8).toByte(),
            first.toByte(),
            (second ushr 16).toByte(),
            (second ushr 8).toByte(),
            second.toByte(),
        )
        if (protocolVersion != 1) return base

        val modeByte = modeValue(mode)
        return base + byteArrayOf(
            0x41,
            0x05,
            0x03,
            modeByte.toByte(),
            (0x45 xor modeByte).toByte(),
        )
    }

    internal fun modeMask(mode: String): Int = when (mode) {
        "20" -> 0x20
        "30" -> 0x30
        "40" -> 0x40
        "10" -> 0x10
        else -> 0
    }

    private fun modeValue(mode: String): Int = when (mode) {
        "20" -> 0x20
        "30" -> 0x30
        "40" -> 0x40
        else -> 0x07
    }

    private fun Int.toHexByte(): String =
        (this and 0xFF).toString(16).uppercase().padStart(2, '0')
}
