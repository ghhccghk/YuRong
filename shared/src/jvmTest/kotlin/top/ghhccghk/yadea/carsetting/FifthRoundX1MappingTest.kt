package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.hexToBytes

class FifthRoundX1MappingTest {

    @Test
    fun x1ConfigIdsRemainDistinctAndSplitStably() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "001", confName = "X1 距离单位"),
                CloudBikeConfig(confId = "002", confName = "X1 感应自动开关机"),
                CloudBikeConfig(confId = "003", confName = "X1 制动能量回收"),
            ),
            values = emptyMap(),
        )
        val items = CloudCarSettingMapper.toItems(cloud)
        assertTrue(items.any { it.id == "cloud:001" && it.kind is CarSettingKind.Choice })
        assertFalse(items.any { it.id == "cloud:002" })
        assertFalse(items.any { it.id == "cloud:003" })
        assertTrue(items.any { it.id == "cloud:002:enable" })
        assertTrue(items.any { it.id == "cloud:002:level" })
        assertTrue(items.any { it.id == "cloud:003:enable" })
        assertTrue(items.any { it.id == "cloud:003:level" })
        listOf("cloud:002:level", "cloud:003:level").forEach { id ->
            val slider = items.single { it.id == id }.kind as CarSettingKind.Slider
            assertEquals(0f..100f, slider.range)
            assertEquals("%", slider.unit)
            assertFalse(slider.valueKnown)
        }
    }

    @Test
    fun x1CommandsMatchX1VehicleSettingViewModel() {
        val items = listOf(
            CarSettingItem(
                id = "cloud:001",
                configId = "001",
                title = "",
                summary = "",
                classification = CarSettingClassification.METER,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(1, listOf("km/h", "mph")),
            ),
            CarSettingItem(
                id = "cloud:002:enable",
                configId = "002",
                title = "",
                summary = "",
                classification = CarSettingClassification.POWER,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(true),
            ),
            CarSettingItem(
                id = "cloud:002:level",
                configId = "002",
                title = "",
                summary = "",
                classification = CarSettingClassification.POWER,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(100f, 0f..100f, steps = 1, unit = "%"),
            ),
            CarSettingItem(
                id = "cloud:003:enable",
                configId = "003",
                title = "",
                summary = "",
                classification = CarSettingClassification.RIDE_CONTROL,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(false),
            ),
            CarSettingItem(
                id = "cloud:003:level",
                configId = "003",
                title = "",
                summary = "",
                classification = CarSettingClassification.RIDE_CONTROL,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(100f, 0f..100f, steps = 1, unit = "%"),
            ),
        )
        val commands = items.map { requireNotNull(VehicleSettingCommands.forItem(it)) }
        assertEquals(listOf(0x8A, 0x5C, 0x5E, 0x16, 0x3D), commands.map { it.commandId })
        assertEquals(2, commands[2].parameter)
        assertEquals(3, commands[4].parameter)
        commands.forEach { assertNotNull(VehicleSettingFrameEncoder.encode(it)) }

        listOf(0f to 0, 50f to 1, 100f to 2).forEach { (progress, raw) ->
            val autoCommand = VehicleSettingCommands.forItem(
                items[2].copy(kind = CarSettingKind.Slider(progress, 0f..100f, steps = 1, unit = "%")),
            )
            val recoveryCommand = VehicleSettingCommands.forItem(
                items[4].copy(kind = CarSettingKind.Slider(progress, 0f..100f, steps = 1, unit = "%")),
            )
            assertEquals(raw, autoCommand?.parameter)
            assertEquals(raw + 1, recoveryCommand?.parameter)
        }
        assertNull(VehicleSettingCommands.forItem(
            items[2].copy(kind = CarSettingKind.Slider(25f, 0f..100f, steps = 1, unit = "%")),
        ))
        assertNull(VehicleSettingCommands.forItem(
            items[4].copy(kind = CarSettingKind.Slider(50f, 0f..100f, steps = 1, unit = "%", valueKnown = false)),
        ))
    }

    @Test
    fun x1RawLevelsAndUiTicksMapExactlyAndRejectUnknownTicks() {
        assertEquals(listOf(0f, 50f, 100f), (0..2).map { X1GearMapping.autoUnlockProgress(it) })
        assertEquals(listOf(0f, 50f, 100f), (1..3).map { X1GearMapping.energyRecoveryProgress(it) })
        assertEquals(listOf(0, 1, 2), listOf(0f, 50f, 100f).map { X1GearMapping.autoUnlockLevel(it) })
        assertEquals(listOf(1, 2, 3), listOf(0f, 50f, 100f).map { X1GearMapping.energyRecoveryLevel(it) })
        assertNull(X1GearMapping.autoUnlockProgress(-1))
        assertNull(X1GearMapping.autoUnlockProgress(3))
        assertNull(X1GearMapping.energyRecoveryProgress(-1))
        assertNull(X1GearMapping.energyRecoveryProgress(4))
        assertNull(X1GearMapping.autoUnlockLevel(25f))
        assertNull(X1GearMapping.energyRecoveryLevel(75f))
    }

    @Test
    fun x1TtpFieldsMatchSmaliBitRanges() {
        val bytes = ByteArray(0xB0) { 0xFF.toByte() }
        bytes[0x1A] = ((bytes[0x1A].toInt() and 0x3F) or (1 shl 6)).toByte()
        bytes[0x1B] = ((bytes[0x1B].toInt() and 0xF8) or 2).toByte()
        val status = TtpInfoParser.parse(bytes).extended
        assertEquals(1, status.bleAutoTurnOnOffStatus)
        assertEquals(2, status.bleAutoTurnOnOffGearStatus)
    }

    @Test
    fun x1TtpValuesBackfillTheirStableItemsWithoutBecomingConfiguredValues() = runBlocking {
        val ids = listOf("001", "002", "003")
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(
                configs = ids.map { CloudBikeConfig(confId = it) }, values = emptyMap(),
            )
        }
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
        }
        val controller = CarSettingsController(
            writer = writer,
            gateway = gateway,
            vinProvider = { "VIN-X1" },
            debounceMs = 0,
        )
        assertTrue(controller.refresh())

        val base = TtpInfoParser.parse(ByteArray(0xB0) { 0xFF.toByte() })
        val status = base.copy(
            extended = base.extended.copy(
                metricSystemStatus = 1,
                bleAutoTurnOnOffStatus = 1,
                bleAutoTurnOnOffGearStatus = 2,
                brakeModePowerRecoverFunctionStatus = 1,
                brakeModePowerRecoverGearStatus = 3,
            ),
        )
        controller.updateVehicleStatus("VIN-X1", status)

        listOf("cloud:001", "cloud:002:enable", "cloud:002:level", "cloud:003:enable", "cloud:003:level")
            .forEach { id ->
            val value = controller.state.value.valueState(id)
            val observed = value.vehicleValue as VehicleSettingValue.Known
            assertEquals("VIN-X1", observed.vin)
            assertEquals(SettingValueSource.LIVE_TTP, observed.source)
            assertEquals(SettingReadbackState.VALID, value.readbackState)
            assertNull(value.configuredValue)
        }
        assertEquals(1, ((controller.state.value.valueState("cloud:001").vehicleValue as VehicleSettingValue.Known)
            .kind as CarSettingKind.Choice).selectedIndex)
        assertEquals(true, ((controller.state.value.valueState("cloud:002:enable").vehicleValue as VehicleSettingValue.Known)
            .kind as CarSettingKind.Toggle).checked)
        assertEquals(100f, ((controller.state.value.valueState("cloud:002:level").vehicleValue as VehicleSettingValue.Known)
            .kind as CarSettingKind.Slider).value)
        assertEquals(true, ((controller.state.value.valueState("cloud:003:enable").vehicleValue as VehicleSettingValue.Known)
            .kind as CarSettingKind.Toggle).checked)
        assertEquals(100f, ((controller.state.value.valueState("cloud:003:level").vehicleValue as VehicleSettingValue.Known)
            .kind as CarSettingKind.Slider).value)
    }

    @Test
    fun autoUnlockLevelRequiresEnabledSameVinLiveTtpEvenThroughControllerEntryPoints() = runBlocking {
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(
                configs = listOf(CloudBikeConfig(confId = "002")), values = emptyMap(),
            )
        }
        val controller = CarSettingsController(
            writer = object : CarSettingWriter { override suspend fun write(item: CarSettingItem) = true },
            gateway = gateway,
            vinProvider = { "VIN-X1" },
            debounceMs = 0,
        )
        assertTrue(controller.refresh())
        val editToMiddle = { kind: CarSettingKind ->
            (kind as CarSettingKind.Slider).copy(value = 50f, valueKnown = true)
        }
        assertFalse(controller.editDraft("cloud:002:level", editToMiddle))
        assertFalse(controller.update("cloud:002:level", editToMiddle))

        val base = TtpInfoParser.parse(ByteArray(0xB0) { 0xFF.toByte() })
        controller.updateVehicleStatus("VIN-X1", base.copy(extended = base.extended.copy(
            bleAutoTurnOnOffStatus = 0,
            bleAutoTurnOnOffGearStatus = 0,
        )))
        assertFalse(controller.editDraft("cloud:002:level", editToMiddle))
        assertFalse(controller.update("cloud:002:level", editToMiddle))

        controller.updateVehicleStatus("VIN-X1", base.copy(extended = base.extended.copy(
            bleAutoTurnOnOffStatus = 1,
            bleAutoTurnOnOffGearStatus = 0,
        )))
        assertTrue(controller.editDraft("cloud:002:level", editToMiddle))
        controller.cancelDraft("cloud:002:level")
    }

    @Test
    fun invalidX1TtpFieldsRemainUnknownInsteadOfUsingControlPlaceholders() = runBlocking {
        val ids = listOf("001", "002", "003")
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(
                configs = ids.map { CloudBikeConfig(confId = it) }, values = emptyMap(),
            )
        }
        val controller = CarSettingsController(
            writer = object : CarSettingWriter { override suspend fun write(item: CarSettingItem) = true },
            gateway = gateway,
            vinProvider = { "VIN-X1" },
            debounceMs = 0,
        )
        assertTrue(controller.refresh())
        val unknown = TtpInfoParser.parse(ByteArray(0xB0) { 0xFF.toByte() })
        val invalid = unknown.copy(extended = unknown.extended.copy(
            metricSystemStatus = 2,
            bleAutoTurnOnOffStatus = -1,
            bleAutoTurnOnOffGearStatus = 3,
            brakeModePowerRecoverFunctionStatus = -1,
            brakeModePowerRecoverGearStatus = 0,
        ))
        controller.updateVehicleStatus("VIN-X1", invalid)

        listOf("cloud:001", "cloud:002:enable", "cloud:002:level", "cloud:003:enable", "cloud:003:level")
            .forEach { id -> assertEquals(id, VehicleSettingValue.Unknown, controller.state.value.valueState(id).vehicleValue) }
    }
}
