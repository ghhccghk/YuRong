package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleService
import top.ghhccghk.yadea.mirror.DateTimeParts

/**
 * 真实车辆 Token 握手。
 *
 * 流程与 smali `wi.Q0/B0/E0` 对齐：
 * 1. 订阅 `bef8e7e0` 等待车端 RandKey；
 * 2. 订阅 `bef8e7e1` 等待鉴权结果；
 * 3. 读取 `iot-auth/ble/token` 的 `dkInfo`；
 * 4. 生成 32 字节握手帧写入 `bef8e7e0`；
 * 5. 结果码 `0` 才允许连接进入初始化任务。
 *
 * 刷写 SecretKey 分支依赖 `secret-key/get` 与额外加密流程，当前明确拒绝，
 * 不会用空密钥或猜测帧继续连接。
 */
class VehicleTokenPairingStep(
    private val tokenProvider: VehicleBleTokenProvider?,
    private val vinProvider: () -> String,
    private val timeProvider: VehicleLocalTimeProvider,
    private val notificationTimeoutMs: Long = 5_000L,
    private val onDebug: (String) -> Unit = {},
) : VehiclePairingStep {

    override suspend fun pair(
        adapter: BleAdapter,
        services: List<BleService>,
        mac: String,
    ): Boolean {
        val vin = vinProvider().trim()
        if (vin.isBlank()) {
            onDebug("BLE 鉴权失败：当前账号没有绑定车辆 VIN")
            return false
        }
        val provider = tokenProvider
        if (provider == null) {
            onDebug("BLE 鉴权失败：Token 网络层未接入")
            return false
        }

        val protocol = resolveVehicleNotificationProfile(services)
        val handshakeService = services.firstOrNull {
            it.uuid.equals(HANDSHAKE_SERVICE, ignoreCase = true)
        }
        if (handshakeService == null) {
            onDebug(
                "BLE 鉴权失败：未发现握手服务 $HANDSHAKE_SERVICE；" +
                    "已发现服务=${services.joinToString { it.uuid }}",
            )
            return false
        }
        val randomKeyCharacteristic = handshakeService.characteristics.firstOrNull {
            it.uuid.equals(RANDOM_KEY_CHARACTERISTIC, ignoreCase = true)
        }
        if (randomKeyCharacteristic == null) {
            onDebug(
                "BLE 鉴权失败：握手服务中没有 RandKey 特征 $RANDOM_KEY_CHARACTERISTIC；" +
                    "服务特征=${handshakeService.characteristics.joinToString { it.uuid }}",
            )
            return false
        }
        val authResultCharacteristic = handshakeService.characteristics.firstOrNull {
            it.uuid.equals(AUTH_RESULT_CHARACTERISTIC, ignoreCase = true)
        }
        if (authResultCharacteristic == null) {
            onDebug(
                "BLE 鉴权失败：握手服务中没有鉴权结果特征 $AUTH_RESULT_CHARACTERISTIC；" +
                    "服务特征=${handshakeService.characteristics.joinToString { it.uuid }}",
            )
            return false
        }
        onDebug(
            "握手特征：RandKey properties=${randomKeyCharacteristic.properties} " +
                "authResult properties=${authResultCharacteristic.properties}",
        )
        return coroutineScope {
            val responseDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                awaitNotification(
                    adapter = adapter,
                    serviceUuid = HANDSHAKE_SERVICE,
                    characteristicUuid = AUTH_RESULT_CHARACTERISTIC,
                )
            }

            if (!adapter.setNotification(HANDSHAKE_SERVICE, AUTH_RESULT_CHARACTERISTIC, true)) {
                onDebug(
                    "BLE 鉴权失败：无法订阅鉴权结果通知（properties=" +
                        "${authResultCharacteristic.properties}）",
                )
                return@coroutineScope false
            }

            val randomKey = runCatching {
                adapter.readCharacteristic(HANDSHAKE_SERVICE, RANDOM_KEY_CHARACTERISTIC)
            }.getOrNull()
            if (randomKey == null) {
                onDebug(
                    "BLE 鉴权失败：读取 RandKey 失败（properties=" +
                        "${randomKeyCharacteristic.properties}）",
                )
                return@coroutineScope false
            }
            val authProfile = VehicleAuthProfile.parse(randomKey)
            onDebug(
                "RandKey 鉴权能力：token=${authProfile.tokenAuth} " +
                    "v2=${authProfile.authVersion2} " +
                    "flashKey=${authProfile.requiresFlashKey} " +
                    "version=${authProfile.authVersion}",
            )
            if (!authProfile.supportsTokenHandshake) {
                onDebug("BLE 鉴权失败：该车辆要求旧握手或 SecretKey 刷写流程")
                return@coroutineScope false
            }

            val token = provider.fetch(
                mac = mac,
                vin = vin,
                refresh = false,
            )
            if (token == null) {
                onDebug("BLE 鉴权失败：云端未返回 dkInfo")
                return@coroutineScope false
            }

            runCatching { adapter.requestMtu(VehicleTimeouts.REQUESTED_MTU) }
                .onFailure { onDebug("请求 MTU 失败，继续尝试握手：${it.message}") }

            val frame = VehicleHandshakeFrame.build(
                token = token.dkInfo,
                randomKey = randomKey,
                mac = mac,
                protocolVersion = protocol.protocolVersion,
                localTime = timeProvider.now(),
                networkAvailable = true,
            )
            if (frame == null) {
                onDebug("BLE 鉴权失败：握手帧构造失败")
                return@coroutineScope false
            }
            if (!adapter.writeCharacteristic(
                    serviceUuid = HANDSHAKE_SERVICE,
                    characteristicUuid = RANDOM_KEY_CHARACTERISTIC,
                    data = frame,
                    withResponse = false,
                )
            ) {
                onDebug("BLE 鉴权失败：握手帧写入失败")
                return@coroutineScope false
            }

            val responseBytes = responseDeferred.await()
            if (responseBytes == null) {
                onDebug("BLE 鉴权失败：等待车端鉴权结果超时")
                return@coroutineScope false
            }
            val response = VehicleHandshakeResponse.parse(responseBytes)
            onDebug("BLE 鉴权结果：result=${response.result} remaining=${response.remainingUses}")
            response.success
        }
    }

    private suspend fun awaitNotification(
        adapter: BleAdapter,
        serviceUuid: String,
        characteristicUuid: String,
    ): ByteArray? = withTimeoutOrNull(notificationTimeoutMs) {
        adapter.notifications.first { notification ->
            notification.serviceUuid.equals(serviceUuid, ignoreCase = true) &&
                notification.characteristicUuid.equals(characteristicUuid, ignoreCase = true)
        }.data
    }

    companion object {
        const val HANDSHAKE_SERVICE = "e7810b92-73ae-499d-8c15-faa9aef0c3f2"
        const val RANDOM_KEY_CHARACTERISTIC = "bef8e7e0-9c21-4c9e-b632-bd58c1009f9f"
        const val AUTH_RESULT_CHARACTERISTIC = "bef8e7e1-9c21-4c9e-b632-bd58c1009f9f"
    }
}
