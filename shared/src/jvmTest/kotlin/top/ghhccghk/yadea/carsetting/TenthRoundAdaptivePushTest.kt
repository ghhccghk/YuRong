package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** Uses fake writers/gateways and synthetic TTP bytes, never a vehicle or transport. */
class TenthRoundAdaptivePushTest {
    private fun cloud(vararg ids: String) = CloudCarSettings(ids.map { CloudBikeConfig(confId = it) }, emptyMap())
    private fun items(vararg ids: String) = CloudCarSettingMapper.toItems(cloud(*ids))
    private fun status(switch: Int = 3, level: Int = 7, main: Int = 3) = TtpInfoParser.parse(
        ByteArray(256) { -1 }.also {
            it[0x76] = ((switch shl 6) or 0x3F).toByte()
            it[0x79] = (0xF8 or level).toByte()
            it[0x6E] = ((main shl 6) or 0x3F).toByte()
        },
    )

    private fun CarSettingsController.value(id: String) = state.value.valueState(id)
    private fun CarSettingsController.checked(id: String) =
        ((value(id).vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Toggle).checked
    private fun CarSettingsController.level(id: String) =
        ((value(id).vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value

    private val bleWriter = object : CarSettingWriter {
        override suspend fun write(item: CarSettingItem) = true
        override suspend fun writeWithResult(item: CarSettingItem) = CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
    }

    @Test fun config64OnlyRawOneEnablesAdaptivePush() {
        assertEquals(false, AdaptivePush64Mapping.checkedFromRaw(0))
        assertEquals(true, AdaptivePush64Mapping.checkedFromRaw(1))
        assertEquals(false, AdaptivePush64Mapping.checkedFromRaw(2))
        val c = CarSettingsController(items("64"), writer = null)
        for (raw in 0..2) {
            c.updateVehicleStatus("VIN-A", status(switch = raw))
            assertEquals(raw == 1, c.checked("cloud:64:adaptive"))
        }
    }

    @Test fun config89OnlyRawTwoEnablesAdaptivePush() {
        assertEquals(false, AdaptivePush89Mapping.checkedFromRaw(0))
        assertEquals(false, AdaptivePush89Mapping.checkedFromRaw(1))
        assertEquals(true, AdaptivePush89Mapping.checkedFromRaw(2))
        val c = CarSettingsController(items("89"), writer = null)
        for (raw in 0..2) {
            c.updateVehicleStatus("VIN-A", status(switch = raw))
            assertEquals(raw == 2, c.checked("cloud:89:adaptive"))
        }
    }

    @Test fun sharedTtpFieldDoesNotMergeTheTwoVariantMeanings() {
        val c = CarSettingsController(items("64", "89"), writer = null)
        for (raw in 0..2) {
            c.updateVehicleStatus("VIN-A", status(switch = raw))
            assertEquals(raw == 1, c.checked("cloud:64:adaptive"))
            assertEquals(raw == 2, c.checked("cloud:89:adaptive"))
        }
    }

    @Test fun config64ParentClickUsesTargetNotOldCheckboxState() {
        val item = items("64").single { it.id == "cloud:64:adaptive" }
        for (currentChecked in listOf(false, true)) {
            val targetChecked = !currentChecked
            val originalParameter = if (currentChecked) 0 else 1
            assertEquals(originalParameter, AdaptivePush64Mapping.parameterForTarget(targetChecked))
            val command = VehicleSettingCommands.forItem(item.copy(kind = CarSettingKind.Toggle(targetChecked)))!!
            assertEquals(0xD0, command.commandId)
            assertEquals("COMMAND_CONST_CUSTOM_DRIVE_SWITCH_SETTING", command.commandType)
            assertEquals(originalParameter, command.parameter)
        }
    }

    @Test fun config89ParentClickUsesTargetNotOldCheckboxState() {
        val item = items("89").single { it.id == "cloud:89:adaptive" }
        for (currentChecked in listOf(false, true)) {
            val targetChecked = !currentChecked
            val originalParameter = if (currentChecked) 0 else 2
            assertEquals(originalParameter, AdaptivePush89Mapping.parameterForTarget(targetChecked))
            val command = VehicleSettingCommands.forItem(item.copy(kind = CarSettingKind.Toggle(targetChecked)))!!
            assertEquals(0xD0, command.commandId)
            assertEquals(originalParameter, command.parameter)
        }
    }

    @Test fun unknownAndInvalidAdaptiveStatesNeverBecomeKnownOff() {
        for (raw in listOf(-1, -2, 3, 4, Int.MAX_VALUE)) {
            assertNull(AdaptivePush64Mapping.checkedFromRaw(raw))
            assertNull(AdaptivePush89Mapping.checkedFromRaw(raw))
        }
        val c = CarSettingsController(items("64", "89"), writer = null)
        c.updateVehicleStatus("VIN-A", status())
        for (id in listOf("cloud:64:adaptive", "cloud:89:adaptive")) {
            assertEquals(VehicleSettingValue.Unknown, c.value(id).vehicleValue)
        }
    }

    @Test fun rawMainSwitchComesFromHelpCarStateIndependentlyOfAdaptive() {
        val c = CarSettingsController(items("64", "89"), writer = null)
        c.updateVehicleStatus("VIN-A", status(switch = 2, main = 1))
        assertTrue(c.checked("cloud:64"))
        assertTrue(c.checked("cloud:89"))
        assertFalse(c.checked("cloud:64:adaptive"))
        assertTrue(c.checked("cloud:89:adaptive"))
        c.updateVehicleStatus("VIN-A", status(switch = 1, main = 0))
        assertFalse(c.checked("cloud:64"))
        assertFalse(c.checked("cloud:89"))
    }

    @Test fun originalThreeTicksRoundTripZeroOneTwoForBothVariants() {
        for (configId in listOf("64", "89")) {
            val c = CarSettingsController(items(configId), writer = null)
            val item = c.state.value.items.single { it.id == "cloud:$configId:level" }
            val slider = item.kind as CarSettingKind.Slider
            assertEquals(0f..2f, slider.range)
            assertEquals(1, slider.steps)
            assertFalse(slider.valueKnown)
            for (tick in 0..2) {
                c.updateVehicleStatus("VIN-A", status(level = tick))
                assertEquals(tick.toFloat(), c.level(item.id))
                val command = VehicleSettingCommands.forItem(item.copy(kind = slider.copy(value = tick.toFloat(), valueKnown = true)))!!
                assertEquals(0xD1, command.commandId)
                assertEquals(tick, command.parameter)
            }
        }
    }

    @Test fun invalidOrMissingLevelCannotBeClampedIntoAnExistingTick() {
        for (configId in listOf("64", "89")) {
            val c = CarSettingsController(items(configId), writer = null)
            val item = c.state.value.items.single { it.id == "cloud:$configId:level" }
            val slider = item.kind as CarSettingKind.Slider
            for (raw in 3..7) {
                c.updateVehicleStatus("VIN-A", status(level = raw))
                assertEquals(VehicleSettingValue.Unknown, c.value(item.id).vehicleValue)
            }
            for (target in listOf(-1f, 0.5f, 1.5f, 3f, Float.NaN, Float.POSITIVE_INFINITY)) {
                assertNull(VehicleSettingCommands.forItem(item.copy(kind = slider.copy(value = target))))
            }
            c.updateVehicleStatus("VIN-A", TtpInfoParser.parse(ByteArray(0)))
            assertEquals(VehicleSettingValue.Unknown, c.value(item.id).vehicleValue)
        }
    }

    @Test fun fieldBitPositionsPreserveInvalidSentinelsAndSiblingBits() {
        val parsed = status(switch = 2, level = 0, main = 1).extended
        assertEquals(2, parsed.customDriveSwitchStatus)
        assertEquals(0, parsed.customDriveLevelStatus)
        assertEquals(1, parsed.helpCarState)
        assertEquals(-1, parsed.tPowerStatus)
        assertEquals(-1, status().extended.customDriveSwitchStatus)
        assertEquals(-1, status().extended.customDriveLevelStatus)
    }

    @Test fun vinSwitchAndOldWriteCannotPolluteTheOtherVariant() = runBlocking {
        var vin = "VIN-A"
        val pending = CompletableDeferred<CarSettingWriteResult>()
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = cloud(if (vin == "VIN-A") "64" else "89")
        }
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
            override suspend fun writeWithResult(item: CarSettingItem) = pending.await()
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { vin }, debounceMs = 0)
        c.refresh()
        c.updateVehicleStatus(vin, status(switch = 1, level = 0))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(c.update("cloud:64:adaptive") { CarSettingKind.Toggle(false) })
        }
        vin = "VIN-B"
        c.onVinChanged(vin)
        c.refresh()
        c.updateVehicleStatus("VIN-A", status(switch = 1, level = 0))
        assertEquals(VehicleSettingValue.Unknown, c.value("cloud:89:adaptive").vehicleValue)
        c.updateVehicleStatus("VIN-B", status(switch = 2, level = 2))
        val newValue = c.value("cloud:89:adaptive")
        pending.complete(CarSettingWriteResult(true, CarSettingWriteChannel.BLE))
        job.join()
        assertEquals(newValue, c.value("cloud:89:adaptive"))
        assertFalse(c.state.value.valueStates.containsKey("cloud:64:adaptive"))
    }

    @Test fun sameVinConfigurationReloadInvalidatesOldVariantOperation() = runBlocking {
        var configId = "64"
        val pending = CompletableDeferred<CarSettingWriteResult>()
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = cloud(configId)
        }
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
            override suspend fun writeWithResult(item: CarSettingItem) = pending.await()
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0)
        c.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.update("cloud:64:adaptive") { CarSettingKind.Toggle(true) } }
        configId = "89"
        c.refresh()
        val before = c.state.value
        pending.complete(CarSettingWriteResult(true, CarSettingWriteChannel.BLE))
        job.join()
        assertEquals(before, c.state.value)
        assertEquals(VehicleSettingValue.Unknown, c.value("cloud:89:adaptive").vehicleValue)
    }

    @Test fun draftCancellationAndBleReceiptDoNotForgeVariantReadback() = runBlocking {
        for ((configId, enabledRaw) in listOf("64" to 1, "89" to 2)) {
            val c = CarSettingsController(items(configId), bleWriter, debounceMs = 0)
            val id = "cloud:$configId:adaptive"
            c.updateVehicleStatus("VIN-A", status(switch = 0))
            c.editDraft(id) { CarSettingKind.Toggle(true) }
            assertFalse(c.checked(id))
            c.cancelDraft(id)
            assertNull(c.value(id).draftValue)
            c.update(id) { CarSettingKind.Toggle(true) }
            assertFalse(c.checked(id))
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.value(id).confirmationState)
            c.updateVehicleStatus("VIN-A", status(switch = enabledRaw))
            assertTrue(c.checked(id))
            assertEquals(SettingConfirmationState.READBACK_MATCHED, c.value(id).confirmationState)
        }
    }
}
