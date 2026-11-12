package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleNotify
import top.ghhccghk.yadea.vehicle.VehicleNotifyDispatcher
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin
import top.ghhccghk.yadea.vehicle.hexToBytes

class RadarSettingsSafetyTest {
    private class FakeWriter : CarSettingWriter {
        var last: CarSettingItem? = null
        override suspend fun write(item: CarSettingItem): Boolean = writeWithResult(item).success
        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            last = item
            return CarSettingWriteResult(
                success = true,
                channel = CarSettingWriteChannel.BLE,
                confirmationStatus = CarSettingConfirmationStatus.LOCAL_UPDATED,
            )
        }
    }

    private class FakeSink : VehicleSettingCommandSink {
        val sent = mutableListOf<VehicleSettingCommand>()
        override suspend fun send(command: VehicleSettingCommand): Boolean {
            sent += command
            return true
        }
    }

    private fun cloud(vararg configs: CloudBikeConfig) = CloudCarSettings(
        configs = configs.toList(),
        values = emptyMap(),
    )

    private fun radarItems(cloud: CloudCarSettings) =
        CloudCarSettingMapper.toItems(cloud).filter { it.configId in RadarSettings.configIds }

    private fun radarItem(cloud: CloudCarSettings, id: String) =
        radarItems(cloud).single { it.id == id }

    @Test
    fun eachRadarControlRequiresItsOwnCloudConfigAndPermission() {
        val cases = listOf(
            Triple(CloudBikeConfig("69", isAuth = false, parentId = "security", parentName = "安全", sort = "3"), "cloud:69:fcw", "69"),
            Triple(CloudBikeConfig("78", isAuth = true, parentId = "assist", parentName = "辅助", sort = "8"), "cloud:78:rcw", "78"),
            Triple(CloudBikeConfig("70", parentId = "assist", parentName = "辅助", sort = "5"), "cloud:70:bsd", "70"),
        )

        cases.forEach { (config, expectedId, expectedConfigId) ->
            val items = radarItems(cloud(config))
            assertEquals(listOf(expectedId), items.map { it.id })
            assertEquals(expectedConfigId, items.single().configId)
            assertEquals(config.parentId, items.single().group.id)
            assertEquals(config.isAuth != false, items.single().writable)
            assertEquals(config.isAuth != false, CarSettingItemAccess.canWrite(items.single()))
            val toggle = items.single().kind as CarSettingKind.Toggle
            assertFalse(toggle.valueKnown)
            assertNull(VehicleSettingCommands.forItem(items.single()))
        }
    }

    @Test
    fun onlyExactKnownRadarItemsBuildTheirOriginalCommands() {
        val items = radarItems(
            cloud(CloudBikeConfig("69"), CloudBikeConfig("78"), CloudBikeConfig("70")),
        ).associateBy { it.id }
        val expected = mapOf(
            "cloud:69:fcw" to ("69" to Triple("COMMAND_FRONT_COLLISION", 0x13A, 1)),
            "cloud:78:rcw" to ("78" to Triple("COMMAND_RADAR_RCW", 0x12D, 1)),
            "cloud:70:bsd" to ("70" to Triple("COMMAND_RADAR_BSD", 0x12E, 1)),
        )

        expected.forEach { (id, mapping) ->
            val configId = mapping.first
            val command = mapping.second
            val item = items.getValue(id)
            val enabled = VehicleSettingCommands.forItem(item.copy(kind = CarSettingKind.Toggle(true)))
            assertEquals(command.first, enabled?.commandType)
            assertEquals(command.second, enabled?.commandId)
            assertEquals(command.third, enabled?.parameter)
            assertTrue(VehicleSettingFrameEncoder.encode(enabled!!) != null)

            val disabled = VehicleSettingCommands.forItem(item.copy(kind = CarSettingKind.Toggle(false)))
            assertEquals(0, disabled?.parameter)
            assertNull(VehicleSettingCommands.forConfigId(configId, CarSettingKind.Toggle(true)))
            assertNull(VehicleSettingCommands.forItem(item.copy(configId = "69")))
            assertNull(VehicleSettingCommands.forItem(item.copy(id = "cloud:$configId:forged")))
        }
    }

    @Test
    fun unknownToggleCannotBeEditedOrWrittenAsThePlaceholderOffValue() = runBlocking {
        val item = radarItem(cloud(CloudBikeConfig("69")), "cloud:69:fcw")
        val sink = FakeSink()
        val writer = MappedCarSettingWriter(sink)
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = writer,
            vinProvider = { "VIN-A" },
            debounceMs = 0,
        )

        assertTrue(CarSettingItemAccess.canWrite(item))
        assertFalse(controller.editDraft(item.id) { CarSettingKind.Toggle(true, valueKnown = false) })
        assertFalse(controller.update(item.id) { CarSettingKind.Toggle(false, valueKnown = false) })
        assertFalse(writer.writeWithResult(item).success)
        assertTrue(sink.sent.isEmpty())
        assertEquals(SettingWriteState.IDLE, controller.state.value.valueState(item.id).writeState)
    }

    @Test
    fun ttpFieldsBackfillOnlyTheirMatchingRadarRowsAndInvalidationRemovesLiveClaim() {
        val items = radarItems(
            cloud(CloudBikeConfig("69"), CloudBikeConfig("78"), CloudBikeConfig("70")),
        )
        val controller = CarSettingsController(items, FakeWriter(), vinProvider = { "VIN-A" })
        controller.updateVehicleStatus("VIN-A", radarStatus(front = 1, rcw = 0, bsd = 2))

        assertRadarValue(controller, "cloud:69:fcw", true, live = true)
        assertRadarValue(controller, "cloud:78:rcw", false, live = true)
        assertRadarValue(controller, "cloud:70:bsd", false, live = true)
        assertEquals(SettingReadbackState.VALID, controller.state.value.valueState("cloud:70:bsd").readbackState)

        controller.updateVehicleStatus("VIN-A", null)
        listOf("cloud:69:fcw", "cloud:78:rcw", "cloud:70:bsd").forEach { id ->
            val value = controller.state.value.valueState(id).vehicleValue as VehicleSettingValue.Known
            assertFalse(value.live)
        }
    }

    @Test
    fun allOnesRadarBitFieldsRemainUnknown() {
        val items = radarItems(
            cloud(CloudBikeConfig("69"), CloudBikeConfig("78"), CloudBikeConfig("70")),
        )
        val controller = CarSettingsController(items, FakeWriter(), vinProvider = { "VIN-A" })
        controller.updateVehicleStatus("VIN-A", radarStatus(front = 3, rcw = 3, bsd = 3))

        listOf("cloud:69:fcw", "cloud:78:rcw", "cloud:70:bsd").forEach { id ->
            assertEquals(VehicleSettingValue.Unknown, controller.state.value.valueState(id).vehicleValue)
        }
    }

    @Test
    fun cachedRadarTtpIsNotVehicleConfirmation() = runBlocking {
        val item = radarItem(cloud(CloudBikeConfig("69")), "cloud:69:fcw")
        val writer = FakeWriter()
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = writer,
            vinProvider = { "VIN-A" },
            debounceMs = 0,
        )
        val cached = radarStatus(front = 0, rcw = 0, bsd = 0)
        controller.updateVehicleObservation(
            VehicleStatusObservation("VIN-A", cached, VehicleStatusOrigin.CACHED_TTP, sequence = 1),
        )
        val before = controller.state.value.valueState(item.id)
        assertEquals(VehicleSettingValue.Unknown, before.vehicleValue)
        assertEquals(SettingValueSource.CACHED_TTP, before.cachedValue?.source)
        assertFalse(before.cachedValue!!.live)

        assertTrue(controller.update(item.id) { CarSettingKind.Toggle(true) })
        val accepted = controller.state.value.valueState(item.id)
        assertEquals(SettingWriteState.LOCAL_UPDATED, accepted.writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, accepted.confirmationState)
        assertEquals(VehicleSettingValue.Unknown, accepted.vehicleValue)
    }

    @Test
    fun bleAcceptanceAndFreshMatchingTtpConfirmationRemainSeparate() = runBlocking {
        val item = radarItem(cloud(CloudBikeConfig("69")), "cloud:69:fcw")
        val writer = FakeWriter()
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = writer,
            vinProvider = { "VIN-A" },
            debounceMs = 0,
        )
        controller.updateVehicleStatus("VIN-A", radarStatus(front = 0, rcw = 0, bsd = 0))

        assertTrue(controller.update(item.id) { CarSettingKind.Toggle(true) })
        val accepted = controller.state.value.valueState(item.id)
        assertEquals(SettingWriteState.LOCAL_UPDATED, accepted.writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, accepted.confirmationState)
        assertEquals(false, (accepted.vehicleValue as VehicleSettingValue.Known).kind.let { (it as CarSettingKind.Toggle).checked })
        assertEquals(true, (writer.last?.kind as CarSettingKind.Toggle).checked)

        controller.updateVehicleStatus("VIN-A", radarStatus(front = 1, rcw = 0, bsd = 0))
        val confirmed = controller.state.value.valueState(item.id)
        assertEquals(SettingConfirmationState.READBACK_MATCHED, confirmed.confirmationState)
        assertEquals(true, (confirmed.vehicleValue as VehicleSettingValue.Known).kind.let { (it as CarSettingKind.Toggle).checked })
    }

    private fun assertRadarValue(controller: CarSettingsController, id: String, expected: Boolean, live: Boolean) {
        val value = controller.state.value.valueState(id).vehicleValue as VehicleSettingValue.Known
        assertEquals(expected, (value.kind as CarSettingKind.Toggle).checked)
        assertEquals(live, value.live)
        assertEquals("VIN-A", value.vin)
    }

    private fun radarStatus(front: Int, rcw: Int, bsd: Int): TtpInfo {
        require(front in 0..3 && rcw in 0..3 && bsd in 0..3)
        val bytes = SAMPLE_TTP_HEX.hexToBytes()!!
        val frontByte = bytes[0x79].toInt() and 0xFF
        bytes[0x79] = ((frontByte and 0xE7) or (front shl 3)).toByte()
        val radarByte = bytes[0x80].toInt() and 0xFF
        bytes[0x80] = ((radarByte and 0xF0) or rcw or (bsd shl 2)).toByte()
        return (VehicleNotifyDispatcher.dispatch(bytes) as VehicleNotify.Ttp).info
    }

    private companion object {
        const val SAMPLE_TTP_HEX =
            "59442AAB2F024C06C079FE00FE00B2FEFFFFFFFFFFD5D0FFFFFFCD076BE1FF29" +
                "FFFF003C824DE5F9FFFFFFFFFFFF7FFFFFF7FD57FCFCFFFFFFFFFFFFFFFFFFFF" +
                "FFEF0FFFFF671B00003BFF0000FCEBE3020385F0A19C0177FFFFFFFFFFFFFFFF" +
                "FFFFFFFFFFFFFFFFFFFFFFFF1E7F57F477071E01FFFF5F26FFF8FFFFFFFFFFFC" +
                "FFFFFFFF0BFFFFFFC242DF38FFFFFFFF7FFFFF3830363533FF2E18FFFF4505FF" +
                "FFFFFFFFFFFFFFFFFFFFF8D7EFFFF31B4B4A"
    }
}
