package top.ghhccghk.yadea.ble

enum class BleErrorCode {
    Unsupported,
    PermissionDenied,
    BluetoothDisabled,
    UserGestureRequired,
    UserCancelled,
    ScanFailed,
    DeviceNotFound,
    ConnectTimeout,
    ServiceDiscoveryTimeout,
    GattOperationTimeout,
    Disconnected,
    NotificationOverflow,
    MissingVehicleMac,
    AuthenticationFailed,
    InitializationFailed,
    PlatformFailure,
}

open class BleException(
    val code: BleErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class BleUnsupportedException(
    feature: String,
) : BleException(
    code = BleErrorCode.Unsupported,
    message = "$feature is not supported on this platform",
)