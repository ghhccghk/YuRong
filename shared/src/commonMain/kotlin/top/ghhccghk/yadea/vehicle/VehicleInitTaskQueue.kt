package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.delay
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleService

/** 连接成功后固定执行的初始化任务。顺序与 smali 一致。 */
enum class VehicleInitTaskName(val label: String) {
    READ_MANUFACTURER_NAME("读取制造商名称"),
    READ_FIRMWARE_VERSION("读取固件版本"),
    READ_HARDWARE_VERSION("读取硬件版本"),
    ENABLE_BLUETOOTH_NOTIFICATION("启用蓝牙通知"),
    QUERY_VEHICLE_STATUS("查询整车状态"),
}

data class VehicleInitTaskResult(
    val name: VehicleInitTaskName,
    val success: Boolean,
    val attempts: Int,
    val value: String? = null,
    val error: String? = null,
)

data class VehicleDeviceInfo(
    val manufacturer: String? = null,
    val firmwareVersion: String? = null,
    val hardwareVersion: String? = null,
)

data class VehicleInitTaskReport(
    val results: List<VehicleInitTaskResult>,
    val deviceInfo: VehicleDeviceInfo,
) {
    val successfulCount: Int get() = results.count { it.success }
}

data class VehicleNotificationProfile(
    val protocolVersion: Int,
    val serviceUuid: String,
    val notifyCharacteristicUuid: String,
    val queryCharacteristicUuid: String,
)

/**
 * 从服务发现结果选择状态通知通道。
 *
 * b360 主服务是命令/响应通道；状态通知按协议版本落在 e7810bd2 或 e7810b82。
 */
internal fun resolveVehicleNotificationProfile(
    services: List<BleService>,
): VehicleNotificationProfile {
    val uuids = services.map { it.uuid.lowercase() }.toSet()
    return if (VehicleBleConstants.SERVICE_NOTIFY_V1.lowercase() in uuids) {
        VehicleNotificationProfile(
            protocolVersion = 1,
            serviceUuid = VehicleBleConstants.SERVICE_NOTIFY_V1,
            notifyCharacteristicUuid = VehicleBleConstants.CHAR_NOTIFY_ENABLE_V1,
            queryCharacteristicUuid = VehicleBleConstants.CHAR_STATUS_QUERY_V1,
        )
    } else {
        VehicleNotificationProfile(
            protocolVersion = 0xF1,
            serviceUuid = VehicleBleConstants.SERVICE_NOTIFY_OTHER,
            notifyCharacteristicUuid = VehicleBleConstants.CHAR_NOTIFY_ENABLE_OTHER,
            queryCharacteristicUuid = VehicleBleConstants.CHAR_STATUS_QUERY_OTHER,
        )
    }
}

/**
 * 初始化任务串行队列。
 *
 * 每个任务最多尝试 3 次：成功或最终失败后都等待 50ms 再继续下一项，重试前
 * 等待 200ms。单个任务失败只记录并跳过，不中断后续任务。
 */
class VehicleInitTaskQueue(
    private val adapter: BleAdapter,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val onDebug: (String) -> Unit = {},
) {

    suspend fun run(
        services: List<BleService>,
    ): VehicleInitTaskReport {
        val profile = resolveVehicleNotificationProfile(services)
        val results = mutableListOf<VehicleInitTaskResult>()
        var manufacturer: String? = null
        var firmware: String? = null
        var hardware: String? = null

        suspend fun execute(
            name: VehicleInitTaskName,
            action: suspend () -> String?,
        ) {
            var lastError: String? = null
            for (attempt in 1..(VehicleTimeouts.TASK_MAX_RETRIES + 1)) {
                val value = try {
                    action()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    lastError = error.message ?: error::class.simpleName
                    null
                }

                if (value != null) {
                    results += VehicleInitTaskResult(
                        name = name,
                        success = true,
                        attempts = attempt,
                        value = value,
                    )
                    onDebug("初始化任务成功：${name.label}（第 $attempt 次）")
                    pause(VehicleTimeouts.TASK_SUCCESS_INTERVAL_MS)
                    return
                }

                if (attempt <= VehicleTimeouts.TASK_MAX_RETRIES) {
                    onDebug("初始化任务失败，准备重试：${name.label}（第 $attempt 次）")
                    pause(VehicleTimeouts.TASK_RETRY_INTERVAL_MS)
                }
            }

            results += VehicleInitTaskResult(
                name = name,
                success = false,
                attempts = VehicleTimeouts.TASK_MAX_RETRIES + 1,
                error = lastError,
            )
            onDebug("初始化任务跳过：${name.label}，错误=${lastError ?: "unknown"}")
            pause(VehicleTimeouts.TASK_SUCCESS_INTERVAL_MS)
        }

        execute(VehicleInitTaskName.READ_MANUFACTURER_NAME) {
            readText(
                VehicleBleConstants.SERVICE_DEVICE_INFO,
                VehicleBleConstants.CHAR_MANUFACTURER,
            )?.also { manufacturer = it }
        }

        execute(VehicleInitTaskName.READ_FIRMWARE_VERSION) {
            readText(
                VehicleBleConstants.SERVICE_DEVICE_INFO,
                VehicleBleConstants.CHAR_FIRMWARE_REVISION,
            )?.also { firmware = it }
        }

        execute(VehicleInitTaskName.READ_HARDWARE_VERSION) {
            // 原实现误读了固件特征。先读标准的 2a27，失败再兼容回退到 2a26。
            val hardwareRevision = readText(
                VehicleBleConstants.SERVICE_DEVICE_INFO,
                VehicleBleConstants.CHAR_HARDWARE_REVISION,
            )
            (hardwareRevision ?: readText(
                VehicleBleConstants.SERVICE_DEVICE_INFO,
                VehicleBleConstants.CHAR_FIRMWARE_REVISION,
            ))?.also { hardware = it }
        }

        execute(VehicleInitTaskName.ENABLE_BLUETOOTH_NOTIFICATION) {
            runCatching { adapter.requestMtu(VehicleTimeouts.REQUESTED_MTU) }
                .onSuccess { onDebug("状态通知 MTU 协商成功：$it") }
                .onFailure { onDebug("状态通知 MTU 协商失败，继续：${it.message}") }
            val statusNotificationEnabled = adapter.setNotification(
                serviceUuid = profile.serviceUuid,
                characteristicUuid = profile.notifyCharacteristicUuid,
                enable = true,
            )
            // b360 主特征承担命令响应；服务存在时一并订阅，但不影响本任务成败。
            if (services.any {
                    it.uuid.equals(VehicleBleConstants.SERVICE_MAIN, ignoreCase = true)
                }
            ) {
                runCatching {
                    adapter.setNotification(
                        serviceUuid = VehicleBleConstants.SERVICE_MAIN,
                        characteristicUuid = VehicleBleConstants.CHAR_MAIN,
                        enable = true,
                    )
                }
            }
            if (statusNotificationEnabled) "enabled" else null
        }

        execute(VehicleInitTaskName.QUERY_VEHICLE_STATUS) {
            val frame = VehicleStatusQueryEncoder.encode(profile.protocolVersion)
                ?: return@execute null
            if (adapter.writeCharacteristic(
                    serviceUuid = profile.serviceUuid,
                    characteristicUuid = profile.queryCharacteristicUuid,
                    data = frame,
                    withResponse = true,
                )
            ) {
                "sent"
            } else {
                null
            }
        }

        return VehicleInitTaskReport(
            results = results,
            deviceInfo = VehicleDeviceInfo(
                manufacturer = manufacturer,
                firmwareVersion = firmware,
                hardwareVersion = hardware,
            ),
        )
    }

    private suspend fun readText(serviceUuid: String, characteristicUuid: String): String? {
        val bytes = adapter.readCharacteristic(serviceUuid, characteristicUuid) ?: return null
        val text = bytes.decodeToString().trim { it == '\u0000' || it <= ' ' }
        return text.ifBlank { null }
    }
}
