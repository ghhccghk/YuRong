package top.ghhccghk.yadea.ble

/** WasmJS Web Bluetooth 互操作尚未验证；必须显式 fail closed。 */
class WasmBleAdapter : UnsupportedBleAdapter("WasmJS Web Bluetooth")

actual fun createBleAdapter(): BleAdapter = WasmBleAdapter()