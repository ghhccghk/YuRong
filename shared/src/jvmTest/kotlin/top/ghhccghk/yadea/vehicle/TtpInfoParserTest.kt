package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 位域读取与 TtpInfo 解析的向量对拍。
 *
 * 偏移、位宽、无效值与字节序规则见 `docs/vehicle/ttpinfo-fields.md`。
 */
class TtpInfoParserTest {

    /** 构造一条足够长的报文，默认全部填 `0xFF`（即"全部无效"）。 */
    private fun packet(size: Int = 0xB0, fill: Byte = 0xFF.toByte()) = ByteArray(size) { fill }

    // ==================== 位域原语 ====================

    @Test
    fun m_returnsInvalidOnAllOnes() {
        val d = packet(0x10)
        assertEquals(-1, Ttp.m(d, 0x04))
    }

    @Test
    fun m_returnsInvalidWhenOutOfBounds() {
        val d = ByteArray(4)
        assertEquals(-1, Ttp.m(d, 0x04))
        assertEquals(-1, Ttp.m(d, -1))
    }

    @Test
    fun bits3_readsThreeBits() {
        // 0b0000_0101 → 低 3 位 = 5
        val d = packet(0x10, 0x00)
        d[0x08] = 0b0000_0101.toByte()
        assertEquals(5, Ttp.C(d, 0x08, 0))
    }

    @Test
    fun bits3_treatsAllOnesAsInvalid() {
        val d = packet(0x10, 0x00)
        d[0x08] = 0b0000_0111.toByte()
        assertEquals(-1, Ttp.C(d, 0x08, 0))
    }

    @Test
    fun bits2_readsTwoBitsAtShift() {
        // 0b0010_1100 → shift=2 取 0b11 = 3 → 无效
        val d = packet(0x10, 0x00)
        d[0x08] = 0b0010_1100.toByte()
        assertEquals(-1, Ttp.G(d, 0x08, 2))

        // 0b0010_0100 → shift=2 取 0b01 = 1
        d[0x08] = 0b0010_0100.toByte()
        assertEquals(1, Ttp.G(d, 0x08, 2))
    }

    @Test
    fun d_readsFourBits() {
        // 0b1010_0101 → shift=4 取 0b1010 = 10
        val d = packet(0x10, 0x00)
        d[0x0A] = 0b1010_0101.toByte()
        assertEquals(10, Ttp.d(d, 0x0A, 4))
    }

    @Test
    fun c_readsFiveBits() {
        val d = packet(0xB0, 0x00)
        // 0b0001_0011 → 低 5 位 = 0b10011 = 19
        d[0x4E] = 0b0001_0011.toByte()
        assertEquals(19, Ttp.c(d, 0x4E, 0))
    }

    @Test
    fun c_treatsAllOnesAsInvalid() {
        val d = packet(0xB0, 0x00)
        d[0x4E] = 0b0001_1111.toByte()
        assertEquals(-1, Ttp.c(d, 0x4E, 0))
    }

    /**
     * 1 位提取器的行为很特殊：位值为 `1` 时恰好等于掩码，因此被判为无效。
     *
     * 这是原实现的**忠实行为**（`l76.l` 里 `if (v == 1) return -1`），
     * 意味着该提取器只能表达“未置位”（返回 0）与“无效”（返回 -1）两种结果。
     * 移植时保留，不要“修正”成常规布尔语义。
     */
    @Test
    fun l_treatsSetBitAsInvalid() {
        val d = packet(0x20, 0x00)
        d[0x10] = 0b0000_0010.toByte()
        // shift=1：位值为 1 → 无效
        assertEquals(-1, Ttp.l(d, 0x10, 1))
        // shift=0：位值为 0 → 有效，返回 0
        assertEquals(0, Ttp.l(d, 0x10, 0))
    }

    // ==================== 多字节字节序 ====================

    @Test
    fun littleEndian16_decodesLowByteFirst() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x34
        d[0x01] = 0x12
        assertEquals(0x1234, Ttp.H(d, 0x00, 0x01))
    }

    @Test
    fun littleEndian16_treatsAllOnesAsInvalid() {
        val d = packet(0x10, 0xFF.toByte())
        assertEquals(-1, Ttp.H(d, 0x00, 0x01))
    }

    @Test
    fun bigEndian16_decodesHighByteFirst() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x12
        d[0x01] = 0x34
        assertEquals(0x1234, Ttp.J(d, 0x00, 0x01))
    }

    @Test
    fun endianness_differOnSameBytes() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x12
        d[0x01] = 0x34
        // 同一组字节，小端与大端结果不同 —— 误用会让数值"离谱但不崩溃"
        assertEquals(0x3412, Ttp.H(d, 0x00, 0x01))
        assertEquals(0x1234, Ttp.J(d, 0x00, 0x01))
    }

    @Test
    fun e_isLittleEndian32() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x78
        d[0x01] = 0x56
        d[0x02] = 0x34
        d[0x03] = 0x12
        assertEquals(0x12345678, Ttp.e(d, 0x00, 0x01, 0x02, 0x03))
    }

    // ==================== 缩放 ====================

    @Test
    fun scaleTenths_multiplies() {
        assertEquals(25.0f, Ttp.scaleTenths(250), 0.0001f)
    }

    @Test
    fun scaleTenths_skipsInvalid() {
        // 无效值必须原样保留，不能变成 -0.1f
        assertEquals(-1.0f, Ttp.scaleTenths(Ttp.INVALID_I), 0.0001f)
    }

    // ==================== 解析器 ====================

    @Test
    fun parse_allInvalidPacket() {
        val info = TtpInfoParser.parse(packet())
        assertEquals(-1, info.parkingStatus)
        assertEquals(-1, info.onOffStatus)
        assertEquals(-1.0f, info.currentSpeedValue, 0.0001f)
        assertEquals(-1.0f, info.totalBatteryVoltage, 0.0001f)
        assertTrue(info.faultCodes.isEmpty())
    }

    @Test
    fun parse_parkingStatus_fromByteEightLowBits() {
        val d = packet(0xB0, 0x00)
        d[0x08] = 0b0000_0001.toByte()
        val info = TtpInfoParser.parse(d)
        assertEquals(1, info.parkingStatus)
    }

    @Test
    fun parse_onOffStatus_fromByte4E() {
        val d = packet(0xB0, 0x00)
        d[0x4E] = 0b0000_1011.toByte()
        val info = TtpInfoParser.parse(d)
        assertEquals(11, info.onOffStatus)
    }

    @Test
    fun parse_currentSpeedValue_scalesByTenth() {
        val d = packet(0xB0, 0x00)
        // 250 → 25.0 km/h，小端
        d[0x4B] = 0xFA.toByte()
        d[0x4C] = 0x00
        val info = TtpInfoParser.parse(d)
        assertEquals(25.0f, info.currentSpeedValue, 0.0001f)
    }

    @Test
    fun parse_currentSpeedValue_invalidStaysNegativeOne() {
        val d = packet(0xB0, 0xFF.toByte())
        val info = TtpInfoParser.parse(d)
        assertEquals(-1.0f, info.currentSpeedValue, 0.0001f)
    }

    @Test
    fun parse_totalBatteryElectricity_subtractsThousand() {
        val d = packet(0xB0, 0x00)
        // 原始 12000 → 1200.0 - 1000.0 = 200.0
        d[0x52] = 0xE0.toByte()
        d[0x53] = 0x2E
        val info = TtpInfoParser.parse(d)
        assertEquals(200.0f, info.totalBatteryElectricity, 0.01f)
    }

    @Test
    fun parse_soc_invalidAboveTwoHundred() {
        val d = packet(0xB0, 0x00)
        d[0x57] = 0xC9.toByte() // 201
        val info = TtpInfoParser.parse(d)
        assertEquals(-1.0f, info.pBElectricitySoc, 0.0001f)
    }

    @Test
    fun parse_soc_scalesByHalf() {
        val d = packet(0xB0, 0x00)
        d[0x57] = 0x64 // 100 → 50.0
        val info = TtpInfoParser.parse(d)
        assertEquals(50.0f, info.pBElectricitySoc, 0.0001f)
    }

    @Test
    fun parse_drivingMemory_and_cruiseControl() {
        val d = packet(0xB0, 0x00)
        // drivingMemory 在 0x8E bit5-7，activateX 在 0x8E bit3-4。
        d[0x8E] = 0b0100_0000.toByte()
        d[0x8F] = 0b0000_0001.toByte()
        val info = TtpInfoParser.parse(d)
        assertEquals(2, info.drivingMemory)
        assertEquals(1, info.cruiseControl)
        assertEquals(0, info.activateX)
    }

    @Test
    fun parse_faultCodes_onlyWhenProtocolF2AndAmountPositive() {
        val d = packet(0xB0, 0x00)
        d[0x5A] = 0xF2.toByte()
        d[0x59] = 0x01 // 数量 = 1
        // 故障码用大端：0x01,0x02 → 0x0102
        d[0x5B] = 0x01
        d[0x5C] = 0x02
        val info = TtpInfoParser.parse(d)
        assertEquals(0x0102, info.faultCodes.first())
        assertEquals(8, info.faultCodes.size)
    }

    @Test
    fun parse_faultCodes_emptyWhenAmountZero() {
        val d = packet(0xB0, 0x00)
        d[0x5A] = 0xF2.toByte()
        d[0x59] = 0x00
        assertTrue(TtpInfoParser.parse(d).faultCodes.isEmpty())
    }

    // ==================== 短报文 ====================

    @Test
    fun parseShort_readsItsOwnFields() {
        val d = packet(0x10, 0x00)
        d[0x04] = 0b0100_0000.toByte() // bit6-7 = 1
        d[0x05] = 0b0000_0010.toByte() // bit0-1 = 2
        val info = TtpInfoParser.parseShort(d)
        assertEquals(1, info.batteryMutualRecognition)
        assertEquals(2, info.speedLimitReleased)
    }

    @Test
    fun parseShort_invalidOnAllOnes() {
        val info = TtpInfoParser.parseShort(packet(0x10))
        assertEquals(-1, info.batteryMutualRecognition)
        assertEquals(-1, info.speedLimitReleased)
    }

    // ==================== 0x6A 之后的状态位 ====================

    @Test
    fun parse_extendedKeysSensors_0x6B_to_0x6F() {
        val d = packet(0xB0, 0x00)
        d[0x6B] = 85
        d[0x6C] = 0b0101_0011.toByte()
        d[0x6D] = 0b0100_1010.toByte()
        d[0x6E] = 0b0110_0110.toByte()
        d[0x6F] = 0b0100_1001.toByte()

        val e = TtpInfoParser.parse(d).extended
        assertEquals(85, e.eBikeBatteryHealth)
        assertEquals(3, e.smartKeysLongClickStatus)
        assertEquals(5, e.smartKeysShortClickStatus)
        assertEquals(3, e.customFunctionLong)
        assertEquals(5, e.customFunctionShort)
        assertEquals(2, e.simulatedSoundWaveStatus)
        assertEquals(2, e.simulatedSoundWaveModeStatus)
        assertEquals(1, e.absFunctionStatus)
        assertEquals(2, e.batteryBindStatus)
        assertEquals(1, e.batteryMutualRecognition)
        assertEquals(1, e.blueLightSwitch)
        assertEquals(2, e.reversingState)
        assertEquals(1, e.helpCarState)
        assertEquals(1, e.constSpeedState)
        assertEquals(2, e.templeSettingStatus)
        assertEquals(1, e.lightBeltStatus)
    }

    @Test
    fun parse_chargingDriveAndNavi_0x70_to_0x7A() {
        val d = packet(0xB0, 0x00)
        d[0x70] = 0b1001_1010.toByte()
        d[0x71] = 10
        d[0x74] = 0b0010_0000.toByte()
        d[0x75] = 0b0110_1000.toByte()
        d[0x76] = 0b0110_0000.toByte()
        d[0x77] = 50
        d[0x79] = 0b0000_1101.toByte()
        d[0x7A] = 2

        val e = TtpInfoParser.parse(d).extended
        assertEquals(2, e.lightBeltColor)
        assertEquals(3, e.batteryChargingLimitValue)
        assertEquals(2, e.batteryChargingPowerStatus)
        assertEquals(20, e.batteryChargingPowerValue)
        assertEquals(VehicleNaviType.AV, e.carNaviType)
        assertEquals(2, e.seatHeatSwitchStatus)
        assertEquals(1, e.seatHeatLevelStatus)
        assertEquals(2, e.tPowerStatus)
        assertEquals(1, e.customDriveSwitchStatus)
        assertEquals(50, e.capacitySettingStatus)
        assertEquals(5, e.customDriveLevelStatus)
        assertEquals(1, e.frontCollisionWarning)
        assertEquals(2, e.turnToSoundReminders)
    }

    @Test
    fun parse_radarAndConfig_0x7F_to_0x87() {
        val d = packet(0xB0, 0x00)
        d[0x7F] = 0b0010_0100.toByte()
        d[0x80] = 0b0110_1001.toByte()
        d[0x81] = 0b0010_0110.toByte()
        d[0x82] = 0b1000_1001.toByte()
        d[0x83] = 0b0110_1001.toByte()
        d[0x84] = 77
        d[0x85] = 88
        d[0x86] = 0x34
        d[0x87] = 0x12

        val e = TtpInfoParser.parse(d).extended
        assertEquals(1, e.fcmRadarEnable)
        assertEquals(0, e.fcmRadarSwitch)
        assertEquals(2, e.rcwRadarEnable)
        assertEquals(1, e.rcwRadarSwitch)
        assertEquals(2, e.fcwRadarMode)
        assertEquals(2, e.bsdRadar)
        assertEquals(1, e.rcwRadarMode)
        assertEquals(2, e.fcwRadarWorking)
        assertEquals(1, e.rcwRadarWorking)
        assertEquals(2, e.vehicleConfigFailureCause)
        assertEquals(1, e.mcuConfigFailureCause)
        assertEquals(1, e.icmConfigFailureCause)
        assertEquals(2, e.vehicleConfigResult)
        assertEquals(1, e.mcuConfigFailureResult)
        assertEquals(2, e.icmConfigFailureResult)
        assertEquals(2, e.almConfigFailureResult)
        assertEquals(1, e.vehicleConfigStatus)
        assertEquals(77, e.brightnessAdjustValue)
        assertEquals(88, e.autoBleRangingBaseValue)
        assertEquals(0x1234, e.autoBleRangingEnvironmentalFactor)
    }

    @Test
    fun parse_cardsRangingAndDriveModes_0x89_to_0x92() {
        val d = packet(0xB0, 0x00)
        d[0x89] = 0b0100_1001.toByte()
        d[0x8A] = 0b0001_0000.toByte()
        d[0x8B] = 12
        d[0x8C] = 3
        d[0x8D] = 4
        d[0x8E] = 0b0100_0001.toByte()
        d[0x8F] = 0b0100_0100.toByte()
        d[0x90] = 0b0110_0100.toByte()
        d[0x91] = 0b0110_0100.toByte()
        d[0x92] = 0b0111_0010.toByte()

        val info = TtpInfoParser.parse(d)
        val e = info.extended
        assertEquals(1, e.customizeThrottleSensitivity)
        assertEquals(1, e.customizeEnergyLevel)
        assertEquals(1, e.customizeEnergyState)
        assertEquals(1, e.customizeTcs)
        assertEquals(12, e.bleRangingBaseValue)
        assertEquals(7, e.bleRangingEnvironmentalFactor)
        assertEquals(1, e.bleRangingState)
        assertEquals(2, info.drivingMemory)
        assertEquals(0, info.activateX)
        assertEquals(0, info.cruiseControl)
        assertEquals(1, e.primaryCardOneStatus)
        assertEquals(2, e.primaryCardTwoStatus)
        assertEquals(4, e.childCardOneStatus)
        assertEquals(4, e.childCardTwoStatus)
        assertEquals(4, e.childCardThreeStatus)
        assertEquals(4, e.childCardFourStatus)
        assertEquals(2, e.childCardFiveStatus)
        assertEquals(14, e.simulatedSoundWaveVolumeStatus)
    }

    @Test
    fun parse_supplierConfigTemperatureAndEnergy() {
        val d = packet(0xC0, 0x00)
        d[0x93] = 'A'.code.toByte()
        d[0x94] = 'B'.code.toByte()
        d[0x95] = 'C'.code.toByte()
        d[0x96] = 'D'.code.toByte()
        d[0x97] = 'E'.code.toByte()
        d[0x98] = 7
        d[0x99] = 0x34
        d[0x9A] = 0x12
        d[0x9B] = 0xE2.toByte()
        d[0x9C] = 0x04
        d[0x9D] = 0x34
        d[0x9E] = 0x12
        d[0x9F] = 0x64
        d[0xA0] = 0x00
        // 3 字节小端：1000 → 100.0；2000 → 200.0
        d[0xA4] = 0xE8.toByte()
        d[0xA5] = 0x03
        d[0xA6] = 0x00
        d[0xA7] = 0xD0.toByte()
        d[0xA8] = 0x07
        d[0xA9] = 0x00

        val e = TtpInfoParser.parse(d).extended
        assertEquals("EDCBA", e.supplierCode)
        assertEquals(7, e.productModel)
        assertEquals(0x1234, e.powerOnPwd)
        assertEquals(25.0f, e.batteryTemperature, 0.0001f)
        assertEquals(0x1234, e.otaCode)
        assertEquals(100, e.calorie)
        assertEquals(100.0f, e.cumulativeCalories, 0.0001f)
        assertEquals(200.0f, e.cumulativeCarbonEmissions, 0.0001f)
    }

    @Test
    fun parse_secondaryStatus_0xA3_to_0xAE() {
        val d = packet(0xB0, 0x00)
        d[0xA3] = 0b1010_1001.toByte()
        d[0xAA] = 0b0000_1010.toByte()
        d[0xAB] = 0b0110_1001.toByte()
        d[0xAC] = 0b0001_1001.toByte()
        d[0xAD] = 0b0110_1001.toByte()
        d[0xAE] = 0b0110_1001.toByte()

        val e = TtpInfoParser.parse(d).extended
        assertEquals(1, e.simulatedSoundWaveOnlineStatus)
        assertEquals(2, e.brakePowerDownFunctionStatus)
        assertEquals(2, e.dvrFormatStatus)
        assertEquals(2, e.wifiRestState)
        assertEquals(2, e.tcsWorkMode)
        assertEquals(1, e.motorAngleMarkerLearningState)
        assertEquals(1, e.motorAngleMarkerLearningSupportState)
        assertEquals(2, e.steepSlopeState)
        assertEquals(2, e.rampSlopeState)
        assertEquals(1, e.syncTimeResult)
        assertEquals(1, e.syncTimeResultStatus)
        assertEquals(6, e.voltageSettingStatus)
        assertEquals(1, e.trackModeStatus)
        assertEquals(2, e.eleHandleLock)
        assertEquals(2, e.tricycleLockStat)
        assertEquals(1, e.tricycleLeftDoorStat)
        assertEquals(1, e.tricycleRightDoorStat)
        assertEquals(2, e.customWallpaperFeedback)
        assertEquals(2, e.customSkinFeedback)
        assertEquals(1, e.gearMemorySettingFeedback)
    }

    @Test
    fun parse_wifiMiscAndDuplicateAliases() {
        val d = packet(0xB0, 0x00)
        d[0x1D] = 0b0100_1000.toByte()
        d[0x1E] = 2
        d[0x20] = 0b1000_0000.toByte()
        d[0x21] = 25
        d[0x22] = 30
        d[0x23] = 0b0110_1001.toByte()
        d[0x24] = 1
        d[0x2E] = 0b0101_0000.toByte()
        d[0x2F] = 0b0101_0100.toByte()
        d[0x32] = 0b0100_0000.toByte()
        d[0x37] = 0b0100_0000.toByte()
        d[0x88] = 0b0101_0100.toByte()

        val info = TtpInfoParser.parse(d)
        val e = info.extended
        assertEquals(2, e.wifiStatus)
        assertEquals(2, e.wifiStatusNew)
        assertEquals(1, e.brightnessAdjustSwitch)
        assertEquals(2, e.dvrWifiStatus)
        assertEquals(2, e.wifiP2pStatus)
        assertEquals(2, e.maxSpeedLimitMode)
        assertEquals(25, info.maximumSpeedDefault)
        assertEquals(25, e.customSoundClear)
        assertEquals(30, info.maximumSpeedCurrent)
        assertEquals(1, e.ambientStatus)
        assertEquals(10, e.ambientColor)
        assertEquals(1, e.capacitySwitchStatus)
        assertEquals(1, e.workMode)
        assertEquals(1, e.derivatives)
        assertEquals(1, e.laterIotStatus)
        assertEquals(1, e.iotRemoveAlarm)
        assertEquals(1, e.removeAlarm)
        assertEquals(1, e.otdSwitch)
        assertEquals(1, e.customSoundSwitch)
        assertEquals(1, e.seatPowerStatus)
        assertEquals(1, e.readyP)
        assertEquals(1, e.addBluetoothKey)
        assertEquals(0, e.rangingStatus)
        assertEquals(1, e.sleepModeSwitch)
        assertEquals(1, e.clearBluetoothKey)
        assertEquals(1, e.longEnduranceStatus)
    }

    @Test
    fun parse_extended_allOnes_isInvalid() {
        val e = TtpInfoParser.parse(packet(0xB0)).extended
        assertEquals(-1, e.eBikeBatteryHealth)
        assertEquals(-1, e.smartKeysLongClickStatus)
        assertEquals(-1, e.simulatedSoundWaveStatus)
        assertEquals(-1, e.batteryMutualRecognition)
        assertEquals(-1, e.primaryCardOneStatus)
        assertEquals(-1, e.tcsWorkMode)
        assertEquals(-1, e.cumulativeCalories.toInt())
        assertEquals(-1, e.batteryTemperature.toInt())
        assertEquals(VehicleNaviType.TFT_IMG, e.carNaviType)
        assertEquals(5, e.supplierCode.length)
        assertTrue(e.supplierCode.all { it == '\uFFFF' })
        assertEquals("FF", e.configCodePart0)
    }
}
