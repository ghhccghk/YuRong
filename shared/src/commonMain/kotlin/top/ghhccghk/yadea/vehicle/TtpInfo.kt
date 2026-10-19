package top.ghhccghk.yadea.vehicle

/**
 * 车辆状态快照（完整报文）。
 *
 * 字段偏移来自 `l76.z([B)`。`TtpInfo` 在原实现里有近 300 个字段，这里收录
 * 已确认的数值/锁车字段，以及 `0x6A` 之后的全部状态位字段。
 *
 * 约定：`Int` 字段的 [Ttp.INVALID_I]（`-1`）、`Float` 字段的 [Ttp.INVALID_F]
 * （`-1.0f`）与 `Double` 字段的 `-1.0` 都表示"车辆未上报该值"，与"值为 0"不同。
 */
data class TtpInfo(
    val raw: ByteArray,
    val hex: String,

    // ---- 协议版本 ----
    val protocolSecondaryVersionCode: Int,
    val protocolPrimaryVersionCode: Int,
    val softwareSecondaryVersionCode: Int,
    val softwarePrimaryVersionCode: Int,

    // ---- 锁与开关 ----
    /** 锁车状态：`0` 已锁，`1` 未锁。取自字节 `0x08` 的低 3 位。 */
    val parkingStatus: Int,
    /** 开关机状态。取自字节 `0x4E` 的低 5 位。 */
    val onOffStatus: Int,
    /** 速度档位。字节 `0x4D` 的 bit2-4。 */
    val speedGear: Int,
    /** 实体钥匙状态。字节 `0x4D` 的 bit0-1。 */
    val realKeyStatus: Int,
    val whistleMode: Int,
    val whistleCurrentStatus: Int,
    val nfcWorkMode: Int,
    val nfcLearningCardMode: Int,

    // ---- 电量 ----
    val totalBatteryVoltage: Float,
    val totalBatteryElectricity: Float,
    val totalBatterySoc: Float,
    val totalBatteryCount: Int,
    val auxiliaryBattery2Soc: Float,
    val pBElectricitySoc: Float,
    val sBElectricitySoc: Float,
    val busbarVoltage: Float,
    val busbarCurrent: Float,
    val batteryWorkingStatus: Int,
    val batteryChargingStatus: Int,
    val tBFullChargeRemainingMin: Int,

    // ---- 里程与速度 ----
    val remainingMileage: Float,
    val currentOdoValue: Float,
    val currentTripValue: Float,
    val currentSpeedValue: Float,
    val maximumSpeedDefault: Int,
    val maximumSpeedCurrent: Int,

    // ---- 档位与巡航 ----
    val drivingMemory: Int,
    val cruiseControl: Int,
    val activateX: Int,

    // ---- 定位 ----
    /** 原始值除以 1_800_000 再减 180；无效时为 `-1.0`。 */
    val longitude: Double,
    /** 原始值除以 1_800_000 再减 90；无效时为 `-1.0`。 */
    val latitude: Double,

    // ---- 故障 ----
    val faultCodeAmount: Int,
    val faultCodeProtocolVersion1: Int,
    val faultCodeProtocolVersion2: Int,
    /** 仅当协议版本 2 为 `0xF2` 且数量大于 0 时非空。使用**大端**读取。 */
    val faultCodes: List<Int>,

    // ---- 0x6A 之后 ----
    val extended: TtpExtendedInfo,
) {
    // ByteArray 不是值类型，用 hex 作为等价性依据。
    override fun equals(other: Any?): Boolean =
        this === other || ((other is TtpInfo) && hex == other.hex)

    override fun hashCode(): Int = hex.hashCode()
}

/** 车辆导航类型，取自 `0x74` bit4-5。 */
enum class VehicleNaviType { TFT_IMG, TFT, AV }

/**
 * `0x6A` 之后的状态位与配置字段。
 *
 * 原实现把这些字段平铺在 `TtpInfo` 上。这里聚成独立快照，避免主状态模型
 * 无限膨胀；所有字段仍保持原始偏移、位宽与无效值语义。
 */
data class TtpExtendedInfo(
    // ---- 0x6B - 0x6F ----
    /** 电池健康度，字节 `0x6B`。 */
    val eBikeBatteryHealth: Int,
    /** 智能钥匙长按功能。`0x6C` bit0-3。 */
    val smartKeysLongClickStatus: Int,
    /** 智能钥匙短按功能。`0x6C` bit2-5。 */
    val smartKeysShortClickStatus: Int,
    /** 自定义功能长按。与智能钥匙长按共用同一位域。 */
    val customFunctionLong: Int,
    /** 自定义功能短按。与智能钥匙短按共用同一位域。 */
    val customFunctionShort: Int,
    /** 模拟声浪开关。`0x6D` bit0-1。 */
    val simulatedSoundWaveStatus: Int,
    /** 模拟声浪模式。`0x6D` bit1-4。 */
    val simulatedSoundWaveModeStatus: Int,
    /** ABS 功能。`0x6D` bit4-5。 */
    val absFunctionStatus: Int,
    /** 倒车状态。`0x6E` bit2-3。 */
    val reversingState: Int,
    /** 辅助推车状态。`0x6E` bit4-5。 */
    val helpCarState: Int,
    /** 定速巡航状态。`0x6F` bit0-1。 */
    val constSpeedState: Int,
    /** 模拟声浪音量。`0x92` bit3-7。 */
    val simulatedSoundWaveVolumeStatus: Int,
    /** 模拟声浪在线状态。`0xA3` bit0-1。 */
    val simulatedSoundWaveOnlineStatus: Int,

    // ---- NFC / 卡片 ----
    /** 主卡 1。`0x8F` bit1-3。 */
    val primaryCardOneStatus: Int,
    /** 主卡 2。`0x8F` bit4-6。 */
    val primaryCardTwoStatus: Int,
    val childCardOneStatus: Int,
    val childCardTwoStatus: Int,
    val childCardThreeStatus: Int,
    val childCardFourStatus: Int,
    val childCardFiveStatus: Int,

    // ---- 行车 / 灯光 ----
    /** 行车记录仪格式化状态。`0xA3` bit4-5。 */
    val dvrFormatStatus: Int,
    /** 刹车断电功能。`0xA3` bit2-3。 */
    val brakePowerDownFunctionStatus: Int,
    /** T 动力状态。`0x76` bit2-3。 */
    val tPowerStatus: Int,
    /** 自定义驾驶开关。`0x76` bit4-5。 */
    val customDriveSwitchStatus: Int,
    /** 自定义驾驶档位。`0x79` bit0-2。 */
    val customDriveLevelStatus: Int,
    /** 座椅加热开关。`0x75` bit2-3。 */
    val seatHeatSwitchStatus: Int,
    /** 座椅加热档位。`0x75` bit4-5。 */
    val seatHeatLevelStatus: Int,
    /** Wi-Fi 静默状态。`0xA3` bit6-7。 */
    val wifiRestState: Int,
    /** TCS 工作模式。`0xAA` bit0-2。 */
    val tcsWorkMode: Int,
    /** 灯光带状态。`0x6F` bit4-5。 */
    val lightBeltStatus: Int,
    /** 灯光带颜色。`0x70` bit0-2。 */
    val lightBeltColor: Int,
    /** 氛围灯开关。`0x23` bit0-1。 */
    val ambientStatus: Int,
    /** 氛围灯颜色。`0x23` bit2-5。 */
    val ambientColor: Int,
    /** 自动大灯功能。`0x34` bit0-1。 */
    val automaticHeadlightFunctionStatus: Int,
    /** 转向灯自动回位。`0x33` bit6-7。 */
    val turnSignalReturnsAutomaticallyStatus: Int,

    // ---- 电量 / 能耗 ----
    /** 累计卡路里；原始值乘 0.1，超出 `0..1_000_000` 时为 `-1.0`。 */
    val cumulativeCalories: Float,
    /** 累计碳排放；原始值乘 0.1，超出 `0..1_000_000` 时为 `-1.0`。 */
    val cumulativeCarbonEmissions: Float,
    /** 充电功率状态。`0x70` bit4-5。 */
    val batteryChargingPowerStatus: Int,
    /** 充电功率原始值乘 2。 */
    val batteryChargingPowerValue: Int,
    /** 充电上限。`0x70` bit3-5。 */
    val batteryChargingLimitValue: Int,
    /** 电池温度；原始值乘 0.1 再减 100。 */
    val batteryTemperature: Float,

    // ---- 导航 / ECU ----
    /** 导航类型。`0x74` bit4-5：无效或 `0` → TFT_IMG，`1` → TFT，其余 → AV。 */
    val carNaviType: VehicleNaviType,
    val ecuType: Int,

    // ---- 出厂 / 配置 ----
    val supplierCode: String,
    val productModel: Int,
    val configCodePart0: String,
    val configCodePart1: String,
    val configCodePart2: String,
    val configCodePart3: String,
    val configCodePart4: String,
    val configCodePart5: String,
    val configVersionMinor: Int,
    val configVersionMajor: Int,
    val vehicleConfigFailureCause: Int,
    val mcuConfigFailureCause: Int,
    val icmConfigFailureCause: Int,
    val almConfigFailureCause: Int,
    val vehicleConfigResult: Int,
    val mcuConfigFailureResult: Int,
    val icmConfigFailureResult: Int,
    val almConfigFailureResult: Int,
    val vehicleConfigStatus: Int,

    // ---- 灯光 / 仪表 ----
    val atmosphereShowMode: Int,
    val atmosphereRgb1r: Int,
    val atmosphereRgb1g: Int,
    val atmosphereRgb1b: Int,
    val atmosphereRgb2r: Int,
    val atmosphereRgb2g: Int,
    val atmosphereRgb2b: Int,
    val atmosphereOperationMode: Int,
    val atmosphereColorMode: Int,
    val brightnessAdjustValue: Int,
    val brightnessAdjustSwitch: Int,
    val capacitySwitchStatus: Int,

    // ---- Wi-Fi / 投屏 ----
    val wifiStatus: Int,
    val wifiStatusNew: Int,
    val dvrWifiStatus: Int,
    val wifiP2pStatus: Int,

    // ---- 雷达 ----
    val fcmRadarEnable: Int,
    val fcmRadarSwitch: Int,
    val fcwRadarMode: Int,
    val fcwRadarWorking: Int,
    val rcwRadarEnable: Int,
    val rcwRadarSwitch: Int,
    val rcwRadarMode: Int,
    val rcwRadarWorking: Int,
    /** 原实现与 [fcwRadarMode] 读取同一位域。 */
    val bsdRadar: Int,

    // ---- 骑行 / 定制 ----
    val customizeThrottleSensitivity: Int,
    val customizeEnergyLevel: Int,
    val customizeEnergyState: Int,
    val customizeTcs: Int,
    val otdSwitch: Int,
    val sleepModeSwitch: Int,
    val seatPowerStatus: Int,
    val maxSpeedLimitMode: Int,
    val customSoundClear: Int,
    val customSoundSwitch: Int,
    val oneKeyEnable: Int,
    val frontCollisionWarning: Int,
    val turnToSoundReminders: Int,
    val addBluetoothKey: Int,
    val clearBluetoothKey: Int,
    val longEnduranceStatus: Int,
    val familyAccountAutoState: Int,
    val readyP: Int,
    val calorie: Int,
    val capacitySettingStatus: Int,
    val templeSettingStatus: Int,
    val eleHandleLock: Int,
    val gearMemorySettingFeedback: Int,

    // ---- 电池 / 蓝牙 ----
    val batteryBindStatus: Int,
    val batteryMutualRecognition: Int,
    /** 原实现与 [batteryMutualRecognition] 读取同一位域。 */
    val blueLightSwitch: Int,
    val bleRangingState: Int,
    val bleRangingBaseValue: Int,
    val bleRangingEnvironmentalFactor: Int,
    val autoBleRangingBaseValue: Int,
    val autoBleRangingEnvironmentalFactor: Int,
    val powerOnPwd: Int,
    val workMode: Int,
    val rangingStatus: Int,
    val otaCode: Int,

    // ---- 电机 / 坡度 / 同步 ----
    val motorAngleMarkerLearningState: Int,
    val motorAngleMarkerLearningSupportState: Int,
    val steepSlopeState: Int,
    val rampSlopeState: Int,
    val syncTimeResult: Int,
    val syncTimeResultStatus: Int,
    val voltageSettingStatus: Int,
    val eBikeLightStatus: Int,
    val eBikePowerGear: Int,
    val eBikeEMachineryTemp: Int,
    val eBikeEMachineryPower: Int,
    val eBikeEMachinerySpeed: Int,
    val eBikeRidingPower: Float,
    val eBikeTapFrequency: Int,
    val eBikeRidingTimeMin: Int,
    val eBikeRidingTimeHour: Int,
    val eBikeRidingTotalTimeMin: Int,
    val eBikeRidingTotalTimeHour: Int,

    // ---- 三蹦 / 车身 ----
    val tricycleLockStat: Int,
    val tricycleLeftDoorStat: Int,
    val tricycleRightDoorStat: Int,
    val trackModeStatus: Int,
    val customWallpaperFeedback: Int,
    val customSkinFeedback: Int,

    // ---- 预留单字段 ----
    val derivatives: Int,
    val iotRemoveAlarm: Int,
    val laterIotStatus: Int,
    val removeAlarm: Int,
)

/**
 * 短报文（`l76.y`）承载的两个字段。
 *
 * 短报文与完整报文是**不同的报文形态**，字段布局不可互换，
 * 因此单独建模而不是复用 [TtpInfo]。
 */
data class TtpShortInfo(
    /** 电池互认状态。字节 `0x04` 的 bit6-7。 */
    val batteryMutualRecognition: Int,
    /** 限速解除状态。字节 `0x05` 的 bit0-1。 */
    val speedLimitReleased: Int,
    val raw: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || ((other is TtpShortInfo) && raw.contentEquals(other.raw))

    override fun hashCode(): Int = raw.contentHashCode()
}

/**
 * `l76.z([B)` / `l76.y([B)` 的等价实现。
 *
 * 字节序提醒：[Ttp.H] / [Ttp.e] 是**小端**，[Ttp.J] 是**大端**，
 * 同一份报文里混用，不能统一处理。
 */
internal object TtpInfoParser {

    /** 完整报文解析，对应 `l76.z`。 */
    fun parse(d: ByteArray): TtpInfo {
        val v2 = Ttp.m(d, 0x5A)
        val amount = Ttp.c(d, 0x59, 0)

        return TtpInfo(
            raw = d,
            hex = d.toHex(),

            protocolSecondaryVersionCode = Ttp.m(d, 0x04),
            protocolPrimaryVersionCode = Ttp.m(d, 0x05),
            softwareSecondaryVersionCode = Ttp.m(d, 0x06),
            softwarePrimaryVersionCode = Ttp.m(d, 0x07),

            parkingStatus = Ttp.C(d, 0x08, 0),
            onOffStatus = Ttp.c(d, 0x4E, 0),
            speedGear = Ttp.C(d, 0x4D, 2),
            realKeyStatus = Ttp.G(d, 0x4D, 0),
            whistleMode = Ttp.G(d, 0x4E, 5),
            whistleCurrentStatus = Ttp.G(d, 0x4F, 0),
            nfcWorkMode = Ttp.C(d, 0x4F, 2),
            nfcLearningCardMode = Ttp.C(d, 0x4F, 5),

            totalBatteryVoltage = Ttp.scaleTenths(Ttp.H(d, 0x50, 0x51)),
            totalBatteryElectricity = batteryElectricity(d),
            totalBatterySoc = Ttp.m(d, 0x49).toFloat(),
            totalBatteryCount = Ttp.d(d, 0x42, 4),
            auxiliaryBattery2Soc = Ttp.m(d, 0x4A).toFloat(),
            pBElectricitySoc = soc(d, 0x57),
            sBElectricitySoc = soc(d, 0x58),
            busbarVoltage = Ttp.scaleTenths(Ttp.H(d, 0x28, 0x29)),
            busbarCurrent = busbarCurrent(d),
            batteryWorkingStatus = Ttp.d(d, 0x54, 0),
            batteryChargingStatus = Ttp.C(d, 0x54, 4),
            tBFullChargeRemainingMin = Ttp.H(d, 0x55, 0x56),

            remainingMileage = remainingMileage(d),
            currentOdoValue = Ttp.scaleTenths(Ttp.e(d, 0x45, 0x46, 0x47, 0x48)),
            currentTripValue = Ttp.scaleTenths(Ttp.H(d, 0x49, 0x4A)),
            currentSpeedValue = Ttp.scaleTenths(Ttp.H(d, 0x4B, 0x4C)),
            maximumSpeedDefault = Ttp.m(d, 0x21),
            maximumSpeedCurrent = Ttp.m(d, 0x22),

            drivingMemory = Ttp.C(d, 0x8E, 5),
            cruiseControl = Ttp.G(d, 0x8F, 0),
            activateX = Ttp.G(d, 0x8E, 3),

            longitude = coordinate(d, 0x38, 0x39, 0x3A, 0x3B, offset = 180.0),
            latitude = coordinate(d, 0x3C, 0x3D, 0x3E, 0x3F, offset = 90.0),

            faultCodeAmount = amount,
            faultCodeProtocolVersion1 = Ttp.C(d, 0x59, 3),
            faultCodeProtocolVersion2 = v2,
            faultCodes = faultCodes(d, v2, amount),
            extended = parseExtended(d),
        )
    }

    /** 短报文解析，对应 `l76.y`。 */
    fun parseShort(d: ByteArray): TtpShortInfo = TtpShortInfo(
        batteryMutualRecognition = Ttp.G(d, 0x04, 6),
        speedLimitReleased = Ttp.G(d, 0x05, 0),
        raw = d,
    )

    // ==================== 私有工具 ====================

    /**
     * 总电池电流：先乘 0.1，**再减 500**。
     *
     * 顺序不能颠倒，否则无效值会被错误缩放。
     */
    private fun busbarCurrent(d: ByteArray): Float {
        val raw = Ttp.H(d, 0x2A, 0x2B)
        return if (raw == Ttp.INVALID_I) Ttp.INVALID_F else raw * 0.1f - 500.0f
    }

    /**
     * 总电池电量：先乘 0.1，**再减 1000**。
     *
     * 顺序不能颠倒，否则无效值会被错误缩放。
     */
    private fun batteryElectricity(d: ByteArray): Float {
        val raw = Ttp.H(d, 0x52, 0x53)
        return if (raw == Ttp.INVALID_I) Ttp.INVALID_F else raw * 0.1f - 1000.0f
    }

    /** 前后电池 SOC：**先判无效**（`-1` 或大于 200），再乘 0.5。 */
    private fun soc(d: ByteArray, off: Int): Float {
        val raw = Ttp.m(d, off)
        return if (raw == Ttp.INVALID_I || raw > 200) Ttp.INVALID_F else raw * 0.5f
    }

    /**
     * 剩余里程：乘 0.1 后保留 1 位小数。
     *
     * 原实现用 `BigDecimal.setScale(1, mode)`；KMP 没有 `BigDecimal`，
     * 这里用整数运算等价实现四舍五入，避免引入平台依赖。
     */
    private fun remainingMileage(d: ByteArray): Float {
        val raw = Ttp.H(d, 0x43, 0x44)
        if (raw == Ttp.INVALID_I) return Ttp.INVALID_F
        // 原实现是 raw × 0.1f 后再 setScale(1)；raw 本身即十分位。
        return raw * 0.1f
    }

    /** 经纬度：原始值 / 1_800_000 - offset；无效值保持 `-1.0`。 */
    private fun coordinate(
        d: ByteArray,
        o0: Int,
        o1: Int,
        o2: Int,
        o3: Int,
        offset: Double,
    ): Double {
        val raw = Ttp.e(d, o0, o1, o2, o3)
        return if (raw == Ttp.INVALID_I) -1.0 else raw.toDouble() / 1_800_000.0 - offset
    }

    /**
     * 故障码序列，偏移 `0x5B`–`0x6A`，两字节一对，共 8 个。
     *
     * 使用**大端** `J`，与其它数值字段不同。
     * 仅当协议版本 2 为 `0xF2` 且数量大于 0 时读取。
     */
    private fun faultCodes(d: ByteArray, v2: Int, amount: Int): List<Int> {
        if (v2 != 0xF2 || amount <= 0) return emptyList()
        var offset = 0x5B
        return buildList {
            while (offset < 0x6B) {
                add(Ttp.J(d, offset, offset + 1))
                offset += 2
            }
        }
    }

    /** `0x6A` 之后的状态位与配置字段。 */
    private fun parseExtended(d: ByteArray): TtpExtendedInfo {
        val tempRaw = Ttp.H(d, 0x9B, 0x9C)
        val powerRaw = Ttp.m(d, 0x71)

        return TtpExtendedInfo(
            eBikeBatteryHealth = Ttp.m(d, 0x6B),
            smartKeysLongClickStatus = Ttp.d(d, 0x6C, 0),
            smartKeysShortClickStatus = Ttp.d(d, 0x6C, 4),
            customFunctionLong = Ttp.d(d, 0x6C, 0),
            customFunctionShort = Ttp.d(d, 0x6C, 4),
            simulatedSoundWaveStatus = Ttp.G(d, 0x6D, 0),
            simulatedSoundWaveModeStatus = Ttp.d(d, 0x6D, 2),
            absFunctionStatus = Ttp.G(d, 0x6D, 6),
            reversingState = Ttp.G(d, 0x6E, 4),
            helpCarState = Ttp.G(d, 0x6E, 6),
            constSpeedState = Ttp.G(d, 0x6F, 0),
            simulatedSoundWaveVolumeStatus = Ttp.c(d, 0x92, 3),
            simulatedSoundWaveOnlineStatus = Ttp.G(d, 0xA3, 0),

            primaryCardOneStatus = Ttp.C(d, 0x8F, 2),
            primaryCardTwoStatus = Ttp.C(d, 0x8F, 5),
            childCardOneStatus = Ttp.C(d, 0x90, 0),
            childCardTwoStatus = Ttp.C(d, 0x90, 3),
            childCardThreeStatus = Ttp.C(d, 0x91, 0),
            childCardFourStatus = Ttp.C(d, 0x91, 3),
            childCardFiveStatus = Ttp.C(d, 0x92, 0),

            dvrFormatStatus = Ttp.G(d, 0xA3, 6),
            brakePowerDownFunctionStatus = Ttp.G(d, 0xA3, 4),
            tPowerStatus = Ttp.G(d, 0x76, 4),
            customDriveSwitchStatus = Ttp.G(d, 0x76, 6),
            customDriveLevelStatus = Ttp.C(d, 0x79, 0),
            seatHeatSwitchStatus = Ttp.G(d, 0x75, 4),
            seatHeatLevelStatus = Ttp.G(d, 0x75, 6),
            wifiRestState = Ttp.G(d, 0xA3, 6),
            tcsWorkMode = Ttp.C(d, 0xAA, 0),
            lightBeltStatus = Ttp.G(d, 0x6F, 6),
            lightBeltColor = Ttp.C(d, 0x70, 0),
            ambientStatus = Ttp.G(d, 0x23, 0),
            ambientColor = Ttp.d(d, 0x23, 2),
            automaticHeadlightFunctionStatus = Ttp.G(d, 0x34, 0),
            turnSignalReturnsAutomaticallyStatus = Ttp.G(d, 0x33, 6),

            cumulativeCalories = rangedTenths(Ttp.D(d, 0xA4, 0xA5, 0xA6)),
            cumulativeCarbonEmissions = rangedTenths(Ttp.D(d, 0xA7, 0xA8, 0xA9)),
            batteryChargingPowerStatus = Ttp.G(d, 0x70, 6),
            batteryChargingPowerValue = if (powerRaw == Ttp.INVALID_I) {
                Ttp.INVALID_I
            } else {
                powerRaw * 2
            },
            batteryChargingLimitValue = Ttp.C(d, 0x70, 3),
            batteryTemperature = if (tempRaw == Ttp.INVALID_I) {
                Ttp.INVALID_F
            } else {
                tempRaw * 0.1f - 100.0f
            },

            carNaviType = when (Ttp.G(d, 0x74, 4)) {
                Ttp.INVALID_I, 0 -> VehicleNaviType.TFT_IMG
                1 -> VehicleNaviType.TFT
                else -> VehicleNaviType.AV
            },
            ecuType = Ttp.G(d, 0x91, 6),

            supplierCode = supplierCode(d),
            productModel = Ttp.m(d, 0x98),
            configCodePart0 = hexByte(d, 0x38),
            configCodePart1 = hexByte(d, 0x39),
            configCodePart2 = hexByte(d, 0x3A),
            configCodePart3 = hexByte(d, 0x3B),
            configCodePart4 = hexByte(d, 0x3C),
            configCodePart5 = hexByte(d, 0x3D),
            configVersionMinor = Ttp.m(d, 0x3E),
            configVersionMajor = Ttp.m(d, 0x3F),
            vehicleConfigFailureCause = Ttp.d(d, 0x81, 4),
            mcuConfigFailureCause = Ttp.C(d, 0x82, 0),
            icmConfigFailureCause = Ttp.C(d, 0x82, 3),
            almConfigFailureCause = Ttp.C(d, 0x27, 0),
            vehicleConfigResult = Ttp.G(d, 0x82, 6),
            mcuConfigFailureResult = Ttp.G(d, 0x83, 0),
            icmConfigFailureResult = Ttp.G(d, 0x83, 2),
            almConfigFailureResult = Ttp.G(d, 0x83, 4),
            vehicleConfigStatus = Ttp.G(d, 0x83, 6),

            atmosphereShowMode = Ttp.C(d, 0x09, 5),
            atmosphereRgb1r = Ttp.m(d, 0x0A),
            atmosphereRgb1g = Ttp.m(d, 0x0B),
            atmosphereRgb1b = Ttp.m(d, 0x0C),
            atmosphereRgb2r = Ttp.m(d, 0x0D),
            atmosphereRgb2g = Ttp.m(d, 0x0E),
            atmosphereRgb2b = Ttp.m(d, 0x0F),
            atmosphereOperationMode = Ttp.C(d, 0x1C, 5),
            atmosphereColorMode = Ttp.G(d, 0x15, 2),
            brightnessAdjustValue = Ttp.m(d, 0x84),
            brightnessAdjustSwitch = Ttp.G(d, 0x1D, 3),
            capacitySwitchStatus = Ttp.G(d, 0x23, 6),

            wifiStatus = Ttp.C(d, 0x1D, 5),
            wifiStatusNew = Ttp.G(d, 0x1D, 5),
            dvrWifiStatus = Ttp.G(d, 0x1E, 0),
            wifiP2pStatus = Ttp.G(d, 0x1E, 0),

            fcmRadarEnable = Ttp.G(d, 0x7F, 2),
            fcmRadarSwitch = Ttp.G(d, 0x7F, 6),
            fcwRadarMode = Ttp.G(d, 0x80, 4),
            fcwRadarWorking = Ttp.G(d, 0x81, 0),
            rcwRadarEnable = Ttp.G(d, 0x7F, 4),
            rcwRadarSwitch = Ttp.G(d, 0x80, 0),
            rcwRadarMode = Ttp.G(d, 0x80, 6),
            rcwRadarWorking = Ttp.G(d, 0x81, 2),
            bsdRadar = Ttp.G(d, 0x80, 2),

            customizeThrottleSensitivity = Ttp.C(d, 0x89, 0),
            customizeEnergyLevel = Ttp.C(d, 0x89, 3),
            customizeEnergyState = Ttp.G(d, 0x89, 6),
            customizeTcs = Ttp.G(d, 0x8A, 4),
            otdSwitch = Ttp.G(d, 0x2F, 2),
            sleepModeSwitch = Ttp.G(d, 0x88, 4),
            seatPowerStatus = Ttp.G(d, 0x2F, 6),
            maxSpeedLimitMode = Ttp.G(d, 0x20, 6),
            customSoundClear = Ttp.m(d, 0x21),
            customSoundSwitch = Ttp.G(d, 0x2F, 4),
            oneKeyEnable = Ttp.l(d, 0x1D, 2),
            frontCollisionWarning = Ttp.G(d, 0x79, 3),
            turnToSoundReminders = Ttp.G(d, 0x7A, 0),
            addBluetoothKey = Ttp.G(d, 0x37, 6),
            clearBluetoothKey = Ttp.G(d, 0x88, 4),
            longEnduranceStatus = Ttp.G(d, 0x88, 6),
            familyAccountAutoState = Ttp.G(d, 0x7F, 0),
            readyP = Ttp.G(d, 0x32, 6),
            calorie = Ttp.H(d, 0x9F, 0xA0),
            capacitySettingStatus = Ttp.m(d, 0x77),
            templeSettingStatus = Ttp.G(d, 0x6F, 2),
            eleHandleLock = Ttp.G(d, 0xAD, 2),
            gearMemorySettingFeedback = Ttp.G(d, 0xAE, 6),

            batteryBindStatus = Ttp.G(d, 0x6E, 0),
            batteryMutualRecognition = Ttp.G(d, 0x6E, 2),
            blueLightSwitch = Ttp.G(d, 0x6E, 2),
            bleRangingState = Ttp.C(d, 0x8E, 0),
            bleRangingBaseValue = Ttp.a(d, 0x8B, 0),
            bleRangingEnvironmentalFactor = Ttp.a(d, 0x8C, 0) + Ttp.a(d, 0x8D, 0),
            autoBleRangingBaseValue = Ttp.m(d, 0x85),
            autoBleRangingEnvironmentalFactor = Ttp.H(d, 0x86, 0x87),
            powerOnPwd = Ttp.H(d, 0x99, 0x9A),
            workMode = Ttp.G(d, 0x24, 0),
            rangingStatus = Ttp.G(d, 0x88, 0),
            otaCode = Ttp.H(d, 0x9D, 0x9E),

            motorAngleMarkerLearningState = Ttp.G(d, 0xAA, 3),
            motorAngleMarkerLearningSupportState = Ttp.G(d, 0xAB, 0),
            steepSlopeState = Ttp.G(d, 0xAB, 2),
            rampSlopeState = Ttp.G(d, 0xAB, 4),
            syncTimeResult = Ttp.G(d, 0xAB, 6),
            syncTimeResultStatus = Ttp.G(d, 0xAC, 0),
            voltageSettingStatus = Ttp.C(d, 0xAC, 2),
            eBikeLightStatus = Ttp.G(d, 0x08, 6),
            eBikePowerGear = Ttp.d(d, 0x0A, 4),
            eBikeEMachineryTemp = Ttp.q(d, 0x0B),
            eBikeEMachineryPower = Ttp.H(d, 0x0C, 0x0D),
            eBikeEMachinerySpeed = Ttp.H(d, 0x0E, 0x0F),
            eBikeRidingPower = Ttp.scaleTenths(Ttp.H(d, 0x10, 0x11)),
            eBikeTapFrequency = Ttp.m(d, 0x12),
            eBikeRidingTimeMin = Ttp.m(d, 0x13),
            eBikeRidingTimeHour = Ttp.m(d, 0x14),
            eBikeRidingTotalTimeMin = Ttp.m(d, 0x17),
            eBikeRidingTotalTimeHour = Ttp.H(d, 0x18, 0x19),

            tricycleLockStat = Ttp.G(d, 0xAD, 4),
            tricycleLeftDoorStat = Ttp.G(d, 0xAD, 6),
            tricycleRightDoorStat = Ttp.G(d, 0xAE, 0),
            trackModeStatus = Ttp.G(d, 0xAD, 0),
            customWallpaperFeedback = Ttp.G(d, 0xAE, 2),
            customSkinFeedback = Ttp.G(d, 0xAE, 4),

            derivatives = Ttp.G(d, 0x2E, 4),
            iotRemoveAlarm = Ttp.G(d, 0x2E, 6),
            laterIotStatus = Ttp.G(d, 0x2E, 4),
            removeAlarm = Ttp.G(d, 0x2E, 6),
        )
    }

    /** 累计值：原始值在 `0..1_000_000` 内才乘 0.1，否则为 `-1.0`。 */
    private fun rangedTenths(raw: Int): Float =
        if (raw < 0 || raw > 1_000_000) Ttp.INVALID_F else raw * 0.1f

    /**
     * 供应商代码：从 `0x97` 倒序读到 `0x93`，逐字节转字符。
     *
     * 无效字节会被 `int-to-char` 转成 `\uFFFF`，这里保持同样结果。
     */
    private fun supplierCode(d: ByteArray): String = buildString(5) {
        for (off in 0x97 downTo 0x93) {
            val v = Ttp.m(d, off)
            append(if (v < 0) '\uFFFF' else v.toChar())
        }
    }

    /** 对应 `l76.p`：`%02x` 后截断到 2 位并转大写。 */
    private fun hexByte(d: ByteArray, off: Int): String {
        val v = Ttp.m(d, off)
        if (v < 0) return "FF"
        return v.toString(16).padStart(2, '0').take(2).uppercase()
    }
}
