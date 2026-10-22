package top.ghhccghk.yadea.ble

/** JS Web Bluetooth 尚未接入；必须显式 fail closed。 */
class JsBleAdapter : UnsupportedBleAdapter("JavaScript Web Bluetooth")

actual fun createBleAdapter(): BleAdapter = JsBleAdapter()