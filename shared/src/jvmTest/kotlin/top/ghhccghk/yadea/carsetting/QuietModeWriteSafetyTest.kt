package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** Order-39 control is not implemented by the ordinary order-3E setting writer. */
class QuietModeWriteSafetyTest {
    private class Gateway(var present: Boolean = true) : CarSettingsGateway {
        var writes = 0
        override suspend fun load(vin: String) = CloudCarSettings(
            if (present) listOf(CloudBikeConfig("93")) else emptyList(), mapOf("quietMode" to "1"))
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean { writes++; return true }
    }
    private fun target(kind: CarSettingKind = CarSettingKind.Toggle(true)) = CarSettingItem(
        id = "cloud:93", configId = "93", title = "静音模式", summary = "", kind = kind, writable = true,
        classification = CarSettingClassification.LIGHT, channel = CarSettingChannel.BLE)

    @Test fun directoryKeepsTheEntryReadOnlyAndNeverInventsCurrentState() = runBlocking {
        val c = CarSettingsController(writer = null, gateway = Gateway(), vinProvider = { "VIN-A" })
        assertTrue(c.refresh()); val item = c.state.value.items.single { it.id == "cloud:93" }
        assertEquals("cloud:93", item.id); assertFalse(item.writable)
        assertNull(c.state.value.valueState(item.id).displayKind())
        assertNull(c.state.value.valueState(item.id).configuredValue)
        c.updateVehicleStatus("VIN-A", TtpInfoParser.parse(ByteArray(256) { 0 }))
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(item.id).vehicleValue)
        assertNull(c.state.value.valueState(item.id).displayKind())
        assertTrue(CloudCarSettingMapper.toItems(CloudCarSettings(emptyList(), emptyMap())).none { it.configId == "93" })
    }

    @Test fun knownUnknownAndForgedTargetsNeverReachEitherActualWriterOrCloud() = runBlocking {
        val g = Gateway(); var sends = 0; var fallbackCalls = 0
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sends++; return true }
        })
        val fallback = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean { fallbackCalls++; return true }
        }
        val cloud = CloudCarSettingWriter(g, { "VIN-A" }, fallback, { true })
        for (kind in listOf(CarSettingKind.Toggle(true), CarSettingKind.Toggle(false),
            CarSettingKind.Toggle(false, false), CarSettingKind.Choice(0, listOf("开启", "关闭")))) {
            for (id in listOf("cloud:93", "missing:93", "cloud:002:enable", "forged")) {
                val item = target(kind).copy(id = id)
                assertFalse(CarSettingItemAccess.canWrite(item)); assertNull(VehicleSettingCommands.forItem(item))
                assertFalse(mapped.write(item)); assertFalse(cloud.write(item))
            }
            assertNull(VehicleSettingCommands.forConfigId("93", kind))
            assertFalse(CloudCarSettingUpdateBuilder.supports("93", kind))
            assertFalse(VehicleSettingCommands.supports("93", kind))
        }
        assertEquals(0, sends); assertEquals(0, fallbackCalls); assertEquals(0, g.writes)
    }

    @Test fun controllerCannotBypassTheBlockEvenWithAnExplicitWritableInitialItem() = runBlocking {
        var writes = 0
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean { writes++; return true }
        }
        val c = CarSettingsController(listOf(target()), writer, vinProvider = { "VIN-A" }, debounceMs = 0)
        for (checked in listOf(true, false)) {
            assertFalse(c.editDraft("cloud:93") { CarSettingKind.Toggle(checked) })
            assertFalse(c.update("cloud:93") { CarSettingKind.Toggle(checked) })
        }
        assertFalse(c.submitDraft("cloud:93")); assertEquals(0, writes)
        assertNull(c.state.value.valueState("cloud:93").submittedValue)
        assertEquals(SettingConfirmationState.UNKNOWN, c.state.value.valueState("cloud:93").confirmationState)
    }

    @Test fun rawCommandAndCopyCannotReachTheIncompatibleEncoder() {
        for (params in listOf(emptyList(), listOf(1, 2), listOf(0, 1), listOf(-1, 2), mutableListOf(1, 2))) {
            try {
                VehicleSettingCommand("COMMAND_DISABLE_OVERSPEED_ALERT", 0x141, parameters = params)
                fail("ordinary settings must not encode order-39 control")
            } catch (_: IllegalArgumentException) { }
        }
        for ((name, id) in listOf("forged" to 0x141, "COMMAND_DISABLE_OVERSPEED_ALERT" to 0x95)) {
            try { VehicleSettingCommand(name, id); fail("id/type alias bypass") }
            catch (_: IllegalArgumentException) { }
        }
        val valid = VehicleSettingCommand("SIMULATED_SOUND_WAVE_OPEN", 0x95)
        try { valid.copy(commandId = 0x141); fail("copy bypass") }
        catch (_: IllegalArgumentException) { }
        assertNotNull(VehicleSettingFrameEncoder.encode(valid))
    }

    @Test fun vinSwitchAndRefreshCannotReenableOrConfirmTheBlockedEntry() = runBlocking {
        var vin = "VIN-A"; var writes = 0
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean { writes++; return true }
        }
        val c = CarSettingsController(writer = writer, gateway = Gateway(), vinProvider = { vin }, debounceMs = 0)
        assertTrue(c.refresh()); vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh())
        assertFalse(c.update("cloud:93") { CarSettingKind.Toggle(true) })
        assertEquals(0, writes); assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:93").vehicleValue)
        assertEquals(SettingConfirmationState.UNKNOWN, c.state.value.valueState("cloud:93").confirmationState)
    }
}
