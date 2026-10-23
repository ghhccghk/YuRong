package top.ghhccghk.yadea.vehicle

/** 底层连接器状态。 */ 
enum class LinkState {
    IDLE,
    CONNECTING,
    CONNECTED,
    FAILURE,
    DISCONNECT,
}

/** 对外只暴露的连接状态。 */ 
enum class PublicState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

/** 断开原因。数值与原实现一致，便于日志和事件对拍。 */ 
enum class DisconnectType(val code: Int) {
    TARGET_DISCONNECTED(1),
    CONNECT_FAILED(2),
    TIMEOUT_15S(3),
    SCAN_NOT_FOUND(4),
    PAIR_ERROR_5(5),
    FW_VERSION_FAILED(6),
    PAIR_FAILED(7),
    PAIR_ERROR_8(8),
    PAIR_ERROR_9(9),
}

/** 车辆连接层异常分类。 */ 
enum class VehicleException(val code: Int) {
    TIMEOUT(100),
    GATT(101),
    OTHER(102),
    TASK(201),
    BLE(300),
}

/** 可注入的单调时钟。测试中使用假时钟推进超时。 */ 
fun interface Clock {
    fun nowMs(): Long
}

/**
 * 连接阶段确认的超时与重试常量。
 *
 * 全部来自 smali 常量；未确认的"看门狗 tick"没有收录为行为常量。
 */
object VehicleTimeouts {
    const val SCAN_MS = 15_000L
    const val GATT_CONNECT_MS = 20_000L
    const val GATT_OP_MS = 5_000L
    const val POST_CONNECT_DELAY_MS = 500L
    const val RECONNECT_DELAY_MS = 5_000L
    const val MAX_RECONNECT = 1
    const val TASK_MAX_RETRIES = 2
    const val TASK_RETRY_INTERVAL_MS = 200L
    const val TASK_SUCCESS_INTERVAL_MS = 50L
    /** `toPairCar` 在握手成功后 1000ms 重新注册状态通知。 */
    const val POST_AUTH_STATUS_RESUBSCRIBE_MS = 1_000L
    /** `toPairCar` 在重新注册后 500ms 发送 `0x34` 状态激活查询。 */
    const val POST_AUTH_STATUS_ACTIVATION_MS = 500L
    const val REQUESTED_MTU = 512
    const val MTU_MIN = 24
    const val MTU_MAX = 512
    const val NOTIFY_THROTTLE_MS = 100L
    const val NOTIFY_DELAY_MS = 110L
    const val STATUS_REPORT_THROTTLE_MS = 2_000L
    const val ERROR_133 = 0x85
}
