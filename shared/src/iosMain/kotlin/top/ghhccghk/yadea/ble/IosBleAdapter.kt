package top.ghhccghk.yadea.ble

/** iOS CoreBluetooth 尚未接入；必须显式 fail closed。 */
class IosBleAdapter : UnsupportedBleAdapter("iOS")

actual fun createBleAdapter(): BleAdapter = IosBleAdapter()