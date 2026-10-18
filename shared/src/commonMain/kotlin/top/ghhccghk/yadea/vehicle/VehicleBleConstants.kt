package top.ghhccghk.yadea.vehicle

/**
 * 车辆整车 BLE 通道 UUID。
 *
 * 与投屏仪表的 `7e4000xx` 是两套协议，不能混用。
 */
object VehicleBleConstants {
    const val SERVICE_MAIN = "0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc"
    const val CHAR_MAIN = "0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc"

    const val SERVICE_DEVICE_INFO = "0000180a-0000-1000-8000-00805f9b34fb"
    const val CHAR_MANUFACTURER = "00002a29-0000-1000-8000-00805f9b34fb"
    const val CHAR_FIRMWARE_REVISION = "00002a26-0000-1000-8000-00805f9b34fb"
    const val CHAR_SOFTWARE_REVISION = "00002a28-0000-1000-8000-00805f9b34fb"
    const val CHAR_HARDWARE_REVISION = "00002a27-0000-1000-8000-00805f9b34fb"
    const val CHAR_CCCD = "00002902-0000-1000-8000-00805f9b34fb"

    const val SERVICE_NOTIFY_V1 = "e7810bd2-73ae-499d-8c15-faa9aef0c3f2"
    const val CHAR_NOTIFY_ENABLE_V1 = "bef8e820-9c21-4c9e-b632-bd58c1009f9f"
    const val CHAR_STATUS_QUERY_V1 = "bef8e821-9c21-4c9e-b632-bd58c1009f9f"

    const val SERVICE_NOTIFY_OTHER = "e7810b82-73ae-499d-8c15-faa9aef0c3f2"
    const val CHAR_NOTIFY_ENABLE_OTHER = "bef8e7db-9c21-4c9e-b632-bd58c1009f9f"
    const val CHAR_STATUS_QUERY_OTHER = "bef8e7da-9c21-4c9e-b632-bd58c1009f9f"

    fun isVehicleNotification(serviceUuid: String, characteristicUuid: String): Boolean {
        val service = serviceUuid.lowercase()
        val characteristic = characteristicUuid.lowercase()
        return service == SERVICE_MAIN ||
            service == SERVICE_NOTIFY_V1 ||
            service == SERVICE_NOTIFY_OTHER ||
            characteristic == CHAR_MAIN ||
            characteristic == CHAR_NOTIFY_ENABLE_V1 ||
            characteristic == CHAR_NOTIFY_ENABLE_OTHER
    }
}
