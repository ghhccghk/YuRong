package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.settingSwitchTarget
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** In-memory gateway/sink only; never opens an account, network or BLE connection. */
class AlarmAtmosphereCommandDirectionTest {
    private val cloud = CloudCarSettings(listOf(CloudBikeConfig("13"), CloudBikeConfig("73")), emptyMap())
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })
    private val items = CloudCarSettingMapper.toItems(cloud)
    private fun item(id: String) = items.single { it.id == "cloud:$id" }

    @Test fun directUiTargetsReachFakeSinkWithCorrectedCommandDirection() = runBlocking {
        val commands = mutableListOf<VehicleSettingCommand>()
        val writer = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean {
                commands += command
                return true
            }
        })
        for (id in listOf("13", "73")) for (checked in listOf(false, true)) {
            val state = CarSettingValueState(configuredValue = CarSettingKind.Toggle(!checked))
            val target = requireNotNull(settingSwitchTarget(state, checked))
            assertEquals(checked, target.checked)
            val receipt = writer.writeWithResult(item(id).copy(kind = target))
            assertTrue(receipt.success)
            assertEquals(CarSettingConfirmationStatus.LOCAL_UPDATED, receipt.confirmationStatus)
            val expected = if (id == "13") {
                VehicleSettingCommand(if (checked) "WARING_SOUND_CLOSE" else "WARING_SOUND_OPEN",
                    if (checked) 0x63 else 0x62)
            } else VehicleSettingCommand("COMMAND_FEEL_LIGHT_SETTING_STATE", 0xC3, if (checked) 1 else 0)
            assertEquals(expected, commands.last())
            assertEquals(expected, VehicleSettingCommands.forConfigId(id, target))
            assertNotNull(VehicleSettingFrameEncoder.encode(commands.last()))
        }
        assertEquals(4, commands.size)
    }

    @Test fun unknownUiStateAndUnknownTargetsStayUnwritable() = runBlocking {
        var sends = 0
        val writer = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sends++; return true }
        })
        for (checked in listOf(false, true)) {
            assertNull(settingSwitchTarget(CarSettingValueState(), checked))
            for (id in listOf("13", "73")) {
                val target = CarSettingKind.Toggle(checked, valueKnown = false)
                assertNull(VehicleSettingCommands.forConfigId(id, target))
                assertFalse(writer.write(item(id).copy(kind = target)))
            }
        }
        assertEquals(0, sends)
    }

    @Test fun receiptNeedsFreshReadbackAndLeavesOriginalObservationUntouched() = runBlocking {
        for (id in listOf("13", "73")) for (checked in listOf(false, true)) {
            val commands = mutableListOf<VehicleSettingCommand>()
            val writer = MappedCarSettingWriter(object : VehicleSettingCommandSink {
                override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
            })
            val controller = CarSettingsController(writer = writer, gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String) = cloud
            }, vinProvider = { "TEST-VIN" }, debounceMs = 0)
            assertTrue(controller.refresh())
            fun observe(value: Boolean, sequence: Long) {
                val info = unknown.copy(extended = unknown.extended.copy(
                    alarmSoundSwitchAfterStatus = if (value) 0 else 1,
                    ambientStatus = if (value) 1 else 0,
                ))
                controller.updateVehicleObservation(VehicleStatusObservation(
                    "TEST-VIN", info, VehicleStatusOrigin.LIVE_TTP, sequence))
            }
            observe(!checked, 1)
            val before = controller.state.value.valueState("cloud:$id")
            assertEquals(!checked, (before.displayKind() as CarSettingKind.Toggle).checked)
            val target = requireNotNull(settingSwitchTarget(before, checked))
            assertTrue(controller.update("cloud:$id") { target })
            val submitted = controller.state.value.valueState("cloud:$id")
            assertEquals(before.vehicleValue, submitted.vehicleValue)
            assertEquals(checked, (submitted.displayKind() as CarSettingKind.Toggle).checked)
            assertEquals(SettingConfirmationState.WAITING_READBACK, submitted.confirmationState)
            observe(checked, 2)
            assertEquals(SettingConfirmationState.READBACK_MATCHED,
                controller.state.value.valueState("cloud:$id").confirmationState)
            assertEquals(1, commands.size)
        }
    }

    @Test fun legacyAlarmEntryUsesSameDirection() {
        val legacy = CarSettingsRegistry.defaultItems().single { it.id == "light.find_sound" }
        for (checked in listOf(false, true)) {
            val target = CarSettingKind.Toggle(checked)
            assertEquals(VehicleSettingCommands.forConfigId("13", target),
                VehicleSettingCommands.forItem(legacy.copy(kind = target)))
        }
    }
}
