package top.ghhccghk.yadea.ota

import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState

/**
 * 高风险 BLE OTA 直译实现。
 *
 * 只发送 smali 中确认存在的命令；任何控制命令或数据包超时都会立即终止。
 * 该路径没有官方 App 的完整错误恢复，失败后可能停在 bootloader，必须提示
 * 用户优先使用云端或原软件升级。
 */
class BleOtaInstaller(
    private val adapter: BleAdapter,
    private val ackTimeoutMs: Long = 5_000L,
) {
    suspend fun install(
        firmware: ByteArray,
        onProgress: suspend (Float, OtaStage) -> Unit,
    ): Boolean {
        if (adapter.connectionState.value !in setOf(
                BleConnectionState.Connected,
                BleConnectionState.Ready,
            )
        ) {
            return false
        }
        val info = OtaFirmware.parse(firmware) ?: return false

        onProgress(0.18f, OtaStage.Verifying)
        if (!adapter.setNotification(
                OtaFirmware.OTA_SERVICE,
                OtaFirmware.OTA_CHARACTERISTIC,
                true,
            )
        ) {
            return false
        }
        runCatching { adapter.requestMtu(512) }

        if (!sendAndAwait(OtaFirmware.bootJump(info), 8_000L)) return false
        delay(500)
        if (!sendAndAwait(OtaFirmware.erase(info), 15_000L)) return false
        if (!sendAndAwait(OtaFirmware.start(info), ackTimeoutMs)) return false

        val packets = OtaFirmware.packetCount(firmware.size)
        for (index in 0 until packets) {
            if (!sendAndAwait(OtaFirmware.data(info, firmware, index))) return false
            onProgress(
                (0.2f + 0.7f * (index + 1).toFloat() / packets).coerceIn(0f, 0.9f),
                OtaStage.Installing,
            )
        }
        if (!sendAndAwait(OtaFirmware.end(info), 10_000L)) return false
        if (!sendAndAwait(OtaFirmware.integrity(info), 15_000L)) return false
        runCatching {
            adapter.writeCharacteristic(
                OtaFirmware.OTA_SERVICE,
                OtaFirmware.OTA_CHARACTERISTIC,
                OtaFirmware.reset(info),
                withResponse = false,
            )
        }
        onProgress(1f, OtaStage.Completed)
        return true
    }

    private suspend fun sendAndAwait(
        command: ByteArray,
        timeoutMs: Long = ackTimeoutMs,
    ): Boolean = coroutineScope {
        if (command.isEmpty()) return@coroutineScope false
        val ack = async {
            withTimeoutOrNull(timeoutMs) {
                adapter.notifications.first { notification ->
                    notification.serviceUuid.equals(OtaFirmware.OTA_SERVICE, true) &&
                        notification.characteristicUuid.equals(
                            OtaFirmware.OTA_CHARACTERISTIC,
                            true,
                        )
                }
            }
        }
        val submitted = runCatching {
            adapter.writeCharacteristic(
                OtaFirmware.OTA_SERVICE,
                OtaFirmware.OTA_CHARACTERISTIC,
                command,
                withResponse = false,
            )
        }.getOrDefault(false)
        if (!submitted) {
            ack.cancel()
            return@coroutineScope false
        }
        ack.await() != null
    }
}
