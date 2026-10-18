package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 云端车辆资料（`VehicleInfoBean` 的 UI 子集）。
 *
 * 车图来自 `VehicleInfoBean.getVehicleImage()`：
 * `skinPicUrl` 非空时优先，否则回退 `imgUrl`。
 */
data class VehicleProfile(
    val vin: String = "",
    val modelNo: String = "",
    /** 联动款标记；原实现用 `dLinkTag`/车型号判断联动款。 */
    val dLinkTag: String = "",
    val vehicleImageUrl: String = "",
    val bikeNickName: String = "",
    val iotService: VehicleIotServiceInfo? = null,
) {
    val isLinkedModel: Boolean
        get() = dLinkTag.isNotBlank()
}

/**
 * IoT 车联网服务信息，对应 `VehicleInfoBean` / `VehicleManageDLineBean`。
 *
 * [expireDays] 为负数表示已到期；小于 `-90` 时原实现会提示已自动注销。
 */
data class VehicleIotServiceInfo(
    val expireDays: Int? = null,
    val cardExpireTime: String = "",
    val iccid: String = "",
    val cardStatus: Int? = null,
) {
    val isExpired: Boolean
        get() = expireDays != null && expireDays < 0

    val isPermanentlyExpired: Boolean
        get() = expireDays != null && expireDays < -90
}

interface VehicleProfileSource {
    val profile: StateFlow<VehicleProfile?>
}

class EmptyVehicleProfileSource : VehicleProfileSource {
    private val _profile = MutableStateFlow<VehicleProfile?>(null)
    override val profile: StateFlow<VehicleProfile?> = _profile.asStateFlow()
}

/** 可由车辆资料接口写入的简单数据源。 */
class MutableVehicleProfileSource : VehicleProfileSource {
    private val _profile = MutableStateFlow<VehicleProfile?>(null)
    override val profile: StateFlow<VehicleProfile?> = _profile.asStateFlow()

    fun update(profile: VehicleProfile?) {
        _profile.value = profile
    }
}

/**
 * IoT 续费 H5 链接。
 *
 * `IotWxSmartDialogHelper.toIotService` 使用
 * `p4$g.T = https://tspapph5.yadeaiot.com.cn/#/flow-recharge-iot-new-index`
 * 并拼接 `vin`、`bikeNickName`。
 */
object IotRenewalLinks {
    const val BASE_URL = "https://tspapph5.yadeaiot.com.cn/#/flow-recharge-iot-new-index"

    fun build(profile: VehicleProfile): String {
        val vin = percentEncode(profile.vin)
        val nickname = percentEncode(profile.bikeNickName)
        return "$BASE_URL?vin=$vin&bikeNickName=$nickname"
    }

    fun actionLabel(service: VehicleIotServiceInfo?): String =
        if (service?.isPermanentlyExpired == true) "查看服务" else "立即续费"

    private fun percentEncode(value: String): String {
        val out = StringBuilder()
        for (byte in value.encodeToByteArray()) {
            val v = byte.toInt() and 0xFF
            when {
                v in 'A'.code..'Z'.code ||
                    v in 'a'.code..'z'.code ||
                    v in '0'.code..'9'.code ||
                    v == '-'.code || v == '_'.code || v == '.'.code || v == '~'.code ->
                    out.append(v.toChar())
                else -> {
                    out.append('%')
                    out.append("0123456789ABCDEF"[v ushr 4])
                    out.append("0123456789ABCDEF"[v and 0x0F])
                }
            }
        }
        return out.toString()
    }
}
