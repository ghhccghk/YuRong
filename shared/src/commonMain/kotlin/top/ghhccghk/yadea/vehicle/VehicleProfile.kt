package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 云端车辆资料（`VehicleInfoBean` 的 UI 子集）。
 *
 * 两个图片字段语义不同：
 * - `skinPicUrl` 是车辆页背景图；
 * - `imgUrl` 是车辆本体图（`getVehicleImage()` 也只在车本体位置使用它）。
 */
data class VehicleProfile(
    val vin: String = "",
    /** 云端车辆主键，解绑接口需要 `bikeId`。 */
    val bikeId: Long = 0L,
    /** 车型主键，自定义音效等资源接口需要 `modelId`。 */
    val bikeModelId: Long = 0L,
    val modelNo: String = "",
    val modelName: String = "",
    /** 联动款标记；原实现用 `dLinkTag`/车型号判断联动款。 */
    val dLinkTag: String = "",
    /**
     * `VehicleInfoBean.experimental`。
     *
     * OTA、升级任务确认和任务标记接口都会原样提交这个字段；缺失时为空白，
     * 不能由客户端自行猜测。
     */
    val experimental: String = "",
    /** `VehicleInfoBean.skinPicUrl`，车辆页背景图。 */
    val backgroundImageUrl: String = "",
    /** `VehicleInfoBean.imgUrl`，车辆本体图。 */
    val vehicleImageUrl: String = "",
    val bikeNickName: String = "",
    /** 车辆 BLE MAC；扫描已有车辆时用于匹配广播地址。 */
    val mac: String = "",
    /** 车辆 BLE 广播名。 */
    val bluetoothName: String = "",
    /** 车辆蓝牙 SN，部分车型名称中会包含该值。 */
    val bluetoothSn: String = "",
    val iotService: VehicleIotServiceInfo? = null,
    /** `VehicleInfoBean.remMileage`，云端最近一次剩余续航。 */
    val remainingMileage: String = "",
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
    val profiles: StateFlow<List<VehicleProfile>>

    fun select(vin: String) {}
}

class EmptyVehicleProfileSource : VehicleProfileSource {
    private val _profile = MutableStateFlow<VehicleProfile?>(null)
    private val _profiles = MutableStateFlow<List<VehicleProfile>>(emptyList())
    override val profile: StateFlow<VehicleProfile?> = _profile.asStateFlow()
    override val profiles: StateFlow<List<VehicleProfile>> = _profiles.asStateFlow()
}

/** 可由车辆资料接口写入的简单数据源。 */
class MutableVehicleProfileSource : VehicleProfileSource {
    private val _profile = MutableStateFlow<VehicleProfile?>(null)
    private val _profiles = MutableStateFlow<List<VehicleProfile>>(emptyList())
    override val profile: StateFlow<VehicleProfile?> = _profile.asStateFlow()
    override val profiles: StateFlow<List<VehicleProfile>> = _profiles.asStateFlow()

    fun update(profile: VehicleProfile?) {
        if (profile == null) {
            _profiles.value = emptyList()
            _profile.value = null
            return
        }
        _profiles.value = listOf(profile)
        _profile.value = profile
    }

    /** 云端返回的完整车库；优先保留当前选中的 VIN。 */
    fun updateAll(
        profiles: List<VehicleProfile>,
        preferredVin: String = _profile.value?.vin.orEmpty(),
    ) {
        val deduped = profiles
            .filter { it.vin.isNotBlank() }
            .distinctBy { it.vin }
        _profiles.value = deduped
        _profile.value = deduped.firstOrNull { it.vin == preferredVin }
            ?: deduped.firstOrNull()
    }

    override fun select(vin: String) {
        _profiles.value.firstOrNull { it.vin == vin }?.let { _profile.value = it }
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
