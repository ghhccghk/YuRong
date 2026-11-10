package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** In-memory parser, mapper and controller only; no transport or platform adapter. */
class TenthRoundAlarmSensitivityTest {
    private fun items() = CloudCarSettingMapper.toItems(
        CloudCarSettings(listOf(CloudBikeConfig(confId = "14"), CloudBikeConfig(confId = "20")), emptyMap()),
    )

    private fun status(raw: Int, warningMode: Int = 6) = TtpInfoParser.parse(
        ByteArray(256) { -1 }.also {
            it[0x1C] = ((0xFF and (7 shl 2).inv()) or (raw shl 2)).toByte()
            it[0x33] = ((4 shl 3) or warningMode).toByte()
        },
    )

    private val writer = object : CarSettingWriter {
        override suspend fun write(item: CarSettingItem) = true
        override suspend fun writeWithResult(item: CarSettingItem) =
            CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
    }

    @Test fun alarmFieldUsesThreeBitsAtOneCTwoAndNotWarningMode() {
        for (raw in 0..7) {
            val extended = status(raw).extended
            assertEquals(if (raw == 7) -1 else raw, extended.alarmSensitivityStatus)
            assertEquals(6, extended.alarmWarningMode)
            assertEquals(4, extended.alarmWarningVolumeLevel)
        }
    }

    @Test fun missingAlarmBytesAndAllOnesKeepUnknown() {
        assertEquals(-1, TtpInfoParser.parse(ByteArray(0)).extended.alarmSensitivityStatus)
        assertEquals(-1, TtpInfoParser.parse(ByteArray(0x1C) { -1 }).extended.alarmSensitivityStatus)
        assertEquals(-1, status(7).extended.alarmSensitivityStatus)
    }

    @Test fun rawLowMiddleHighMapToOriginalProgressAndTick() {
        for ((raw, progress) in listOf(0 to 0f, 1 to 50f, 2 to 100f)) {
            assertEquals(progress, AlarmSensitivityMapping.progressFromRaw(raw))
            assertEquals(raw, AlarmSensitivityMapping.tickFromProgress(progress))
        }
    }

    @Test fun unsupportedRawValuesDoNotBecomeNormalLowProgress() {
        for (raw in listOf(-1, -2, 3, 4, 5, 6, 7, 8, Int.MAX_VALUE)) {
            assertNull(AlarmSensitivityMapping.progressFromRaw(raw))
        }
    }

    @Test fun onlyProvenTickPositionsCanGenerateAlarmCommands() {
        val item = items().single { it.configId == "14" }
        val slider = item.kind as CarSettingKind.Slider
        for ((progress, tick) in listOf(0f to 0, 50f to 1, 100f to 2)) {
            val command = VehicleSettingCommands.forItem(item.copy(kind = slider.copy(value = progress)))!!
            assertEquals("ALARM_SENSITIVITY_GEAR", command.commandType)
            assertEquals(0x5F, command.commandId)
            assertEquals(tick, command.parameter)
            assertEquals(progress, AlarmSensitivityMapping.progressFromRaw(command.parameter))
        }
        for (value in listOf(-1f, 1f, 2f, 25f, 49.9f, 75f, 101f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(VehicleSettingCommands.forItem(item.copy(kind = slider.copy(value = value))))
        }
    }

    @Test fun alarmHasThreeProgressTicksWithoutChangingVehicleVolumeRange() {
        val alarm = items().single { it.configId == "14" }.kind as CarSettingKind.Slider
        val volume = items().single { it.configId == "20" }.kind as CarSettingKind.Slider
        assertEquals(0f..100f, alarm.range)
        assertEquals(1, alarm.steps)
        assertFalse(alarm.valueKnown)
        assertEquals(0f..7f, volume.range)
        assertEquals(6, volume.steps)
        assertEquals(6, VehicleSettingCommands.forConfigId("20", volume.copy(value = 6f))!!.parameter)
    }

    @Test fun oldWarningFieldAndCloudValuesCannotEstablishAlarmReadback() {
        val cloudItems = CloudCarSettingMapper.toItems(CloudCarSettings(
            listOf(CloudBikeConfig(confId = "14")), mapOf("alarmWarningMode" to "1", "alarmSensitivityStatus" to "2"),
        ))
        val c = CarSettingsController(cloudItems, writer = null)
        c.updateVehicleStatus("VIN-A", status(7, warningMode = 1))
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:14").vehicleValue)
        assertFalse((c.state.value.items.single { it.id == "cloud:14" }.kind as CarSettingKind.Slider).valueKnown)
    }

    @Test fun validAlarmReadbackUnlocksUnknownAndInvalidDoesNotOverwriteLastObservation() {
        val c = CarSettingsController(items(), writer = null)
        for ((raw, progress) in listOf(0 to 0f, 1 to 50f, 2 to 100f)) {
            c.updateVehicleStatus("VIN-A", status(raw))
            val value = c.state.value.valueState("cloud:14")
            assertEquals(progress, ((value.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
            assertEquals(SettingReadbackState.VALID, value.readbackState)
        }
        val observed = c.state.value.valueState("cloud:14").vehicleValue
        for (raw in 3..7) {
            c.updateVehicleStatus("VIN-A", status(raw))
            val value = c.state.value.valueState("cloud:14")
            assertEquals(observed, value.vehicleValue)
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, value.readbackState)
        }
    }

    @Test fun draftProgressAndSendingReceiptNeverImpersonateAlarmReadback() = runBlocking {
        val c = CarSettingsController(items(), writer, debounceMs = 0)
        c.updateVehicleStatus("VIN-A", status(0))
        assertTrue(c.editDraft("cloud:14") { (it as CarSettingKind.Slider).copy(value = 100f) })
        assertEquals(0f, ((c.state.value.valueState("cloud:14").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertTrue(c.submitDraft("cloud:14"))
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:14").confirmationState)
        c.updateVehicleStatus("VIN-A", status(7, warningMode = 2))
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:14").confirmationState)
        c.updateVehicleStatus("VIN-A", status(2))
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:14").confirmationState)
    }

    @Test fun differentVinAlarmAndVolumeFieldsCannotReplaceCurrentVehicleState() = runBlocking {
        var vin = "VIN-A"
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) =
                CloudCarSettings(listOf(CloudBikeConfig(confId = "14"), CloudBikeConfig(confId = "20")), emptyMap())
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { vin })
        c.refresh()
        c.updateVehicleStatus(vin, status(2))
        vin = "VIN-B"
        c.onVinChanged(vin)
        c.refresh()
        c.updateVehicleStatus("VIN-A", status(2))
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:14").vehicleValue)
        c.updateVehicleStatus("VIN-B", status(1))
        assertEquals(50f, ((c.state.value.valueState("cloud:14").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertEquals(4f, ((c.state.value.valueState("cloud:20").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
    }
}
