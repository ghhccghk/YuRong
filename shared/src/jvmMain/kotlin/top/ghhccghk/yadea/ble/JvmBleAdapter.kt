package top.ghhccghk.yadea.ble

/**
 * Desktop BLE 尚未按 Windows、macOS、Linux 分别接入。
 * 该类型只代表显式 unsupported，不代表任何桌面系统已经支持 BLE。
 */
class JvmBleAdapter : UnsupportedBleAdapter("Desktop JVM")

actual fun createBleAdapter(): BleAdapter = JvmBleAdapter()