package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin
import top.ghhccghk.yadea.ui.speedPresetPosition
import top.ghhccghk.yadea.ui.speedPresetTarget
import top.ghhccghk.yadea.ui.settingValueSourceSummary
import top.ghhccghk.yadea.ui.settingSliderDraftValue

class VehicleSettingsFirstPhaseTest {
    private val unknownTtp = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })

    private class FakeGateway : CarSettingsGateway {
        val requestedVins = mutableListOf<String>()
        override suspend fun load(vin: String): CloudCarSettings {
            requestedVins += vin
            return CloudCarSettings(
                configs = listOf("4", "55", "58", "61", "92").map {
                    CloudBikeConfig(confId = it)
                },
                values = mapOf(
                    "bikeSpeedMax" to "80",
                    "bikeModelTopSpeedMax" to "100",
                    "bikeModelTopSpeedMin" to "15",
                    "bikeSpeedMaxDefaultMin" to "15",
                    "bikeSpeedMaxDefaultMax" to "100",
                    "highestSpeedLimit" to "0",
                ),
            )
        }
    }

    private class FakeWriter(
        private val result: suspend (CarSettingItem) -> CarSettingWriteResult = {
            CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
        },
    ) : CarSettingWriter {
        val writes = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean = writeWithResult(item).success
        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            writes += item
            return result(item)
        }
    }

    private fun status(
        speed: Int,
        throttle: Int = -1,
        energyLevel: Int = -1,
        energyState: Int = -1,
        tcs: Int = -1,
    ) = unknownTtp.copy(
        maximumSpeedCurrent = speed,
        extended = unknownTtp.extended.copy(
            customizeThrottleSensitivity = throttle,
            customizeEnergyLevel = energyLevel,
            customizeEnergyState = energyState,
            customizeTcs = tcs,
        ),
    )

    private fun controller(
        vin: () -> String = { "VIN-A" },
        writer: CarSettingWriter? = FakeWriter(),
        gateway: CarSettingsGateway = FakeGateway(),
    ) = CarSettingsController(
        writer = writer,
        gateway = gateway,
        vinProvider = vin,
        debounceMs = 0,
        statusPollAttempts = 0,
    )

    @Test
    fun compositeConfigurationRequiresAllFourValidTtpFieldsBeforeWrite() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        val item = c.state.value.items.first { it.configId == "55" }
        assertFalse((item.kind as CarSettingKind.Customize).valueKnown)
        assertNull(VehicleSettingCommands.forItem(item))
        assertTrue(VehicleSettingCommands.supports(item.configId, item.kind))
        assertFalse(c.update(item.id) { CarSettingKind.Customize(1, 1, false, false) })
        assertFalse(c.update(item.id) { it })
        assertTrue(writer.writes.isEmpty())

        c.updateVehicleStatus("VIN-A", status(50, throttle = 2, energyLevel = 1, energyState = 0, tcs = -1))
        assertFalse((c.state.value.items.first { it.id == item.id }.kind as CarSettingKind.Customize).valueKnown)
        assertFalse(c.update(item.id) { it })
        assertTrue(writer.writes.isEmpty())

        c.updateVehicleStatus("VIN-A", status(50, throttle = 2, energyLevel = 1, energyState = 0, tcs = 1))
        val updatedItem = c.state.value.items.first { it.id == item.id }
        val known = c.state.value.valueState(item.id).vehicleValue as VehicleSettingValue.Known
        assertTrue((known.kind as CarSettingKind.Customize).valueKnown)
        assertTrue("item kind=$updatedItem", CarSettingItemAccess.canWrite(updatedItem))
        assertTrue("message=${c.state.value.message}", c.update(item.id) {
            (it as CarSettingKind.Customize).copy(throttleLevel = 1)
        })
        assertEquals(
            listOf(1, 1, 0, 1),
            VehicleSettingCommands.forItem(writer.writes.single())?.parameters,
        )
    }

    @Test
    fun bothCompositeIdsRejectUnknownAndOutOfRangeSiblings() {
        for (id in listOf("55", "92")) {
            val item = CarSettingItem(
                id = "cloud:$id", configId = id, title = "", summary = "",
                classification = CarSettingClassification.RIDE_CONTROL,
                channel = CarSettingChannel.BLE, kind = CarSettingKind.Customize(1, 1, false, false),
            )
            assertNull(VehicleSettingCommands.forItem(item.copy(kind = CarSettingKind.Customize())))
            assertNull(VehicleSettingCommands.forItem(item.copy(
                kind = CarSettingKind.Customize(throttleLevel = 3, energyLevel = 1, energyState = false, tcs = false),
            )))
            assertEquals(listOf(1, 1, 0, 0), VehicleSettingCommands.forItem(item)?.parameters)
            for (partial in listOf(
                CarSettingKind.Customize(1, 1, false, null),
                CarSettingKind.Customize(1, null, false, false),
                CarSettingKind.Customize(null, 1, false, false),
            )) assertNull(VehicleSettingCommands.forItem(item.copy(kind = partial)))
        }
    }

    @Test
    fun refreshRetainsDraftButVinChangeDropsItAndRejectsOldTarget() = runBlocking {
        var vin = "VIN-A"
        val c = controller(vin = { vin })
        assertTrue(c.refresh())
        c.updateVehicleStatus("VIN-A", status(50, 1, 2, 1, 0))
        val id = "cloud:55"
        assertTrue(c.editDraft(id) { (it as CarSettingKind.Customize).copy(tcs = true) })
        assertTrue(c.refresh())
        assertEquals(true, (c.state.value.valueState(id).draftValue as CarSettingKind.Customize).tcs)

        vin = "VIN-B"
        c.onVinChanged(vin)
        assertNull(c.state.value.valueState(id).draftValue)
        assertFalse(c.submitDraft(id))
    }

    @Test
    fun cancelledCompositeWritePropagatesAndClearsSubmittedTarget() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val writer = FakeWriter {
            started.complete(Unit)
            CompletableDeferred<CarSettingWriteResult>().await()
        }
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        c.updateVehicleStatus("VIN-A", status(50, 1, 1, 0, 0))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            c.update("cloud:55") { (it as CarSettingKind.Customize).copy(tcs = true) }
        }
        withTimeout(5_000) { started.await() }
        job.cancelAndJoin()
        assertEquals(SettingWriteState.CANCELLED, c.state.value.valueState("cloud:55").writeState)
        assertNull(c.state.value.valueState("cloud:55").submittedValue)
    }

    @Test
    fun failedCompositeWriteRetainsTheLastVehicleObservation() = runBlocking {
        val writer = FakeWriter { CarSettingWriteResult(false, CarSettingWriteChannel.BLE) }
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        c.updateVehicleStatus("VIN-A", status(50, 1, 2, 1, 0))
        val before = c.state.value.valueState("cloud:92").vehicleValue
        assertFalse(c.update("cloud:92") {
            (it as CarSettingKind.Customize).copy(throttleLevel = 2)
        })
        val after = c.state.value.valueState("cloud:92")
        assertEquals(before, after.vehicleValue)
        assertEquals(SettingWriteState.FAILED, after.writeState)
        assertNull(after.submittedValue)
    }

    @Test
    fun speedReadbackAndConfiguredValuesRemainSeparateForIds4And58And61() = runBlocking {
        val c = controller()
        assertTrue(c.refresh())
        for (id in listOf("cloud:4", "cloud:58")) {
            val configured = c.state.value.valueState(id).configuredValue as CarSettingKind.Slider
            assertEquals(80f, configured.value)
        }
        val limit = c.state.value.valueState("cloud:61")
        assertEquals(0f, (limit.configuredValue as CarSettingKind.Slider).value)

        c.updateVehicleStatus("VIN-A", status(0))
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:4").vehicleValue)
        val unlimited58 = c.state.value.valueState("cloud:58").vehicleValue as VehicleSettingValue.Known
        assertEquals(0f, (unlimited58.kind as CarSettingKind.Slider).value)
        val noLimit = c.state.value.valueState("cloud:61").vehicleValue as VehicleSettingValue.Known
        assertEquals(0f, (noLimit.kind as CarSettingKind.Slider).value)
        assertEquals(0f, ((c.state.value.valueState("cloud:61").displayKind()) as CarSettingKind.Slider).value)

        c.updateVehicleStatus("VIN-A", status(45))
        for (id in listOf("cloud:4", "cloud:58", "cloud:61")) {
            val state = c.state.value.valueState(id)
            assertEquals(45f, ((state.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
            assertEquals(45f, (state.displayKind() as CarSettingKind.Slider).value)
        }
        assertEquals(80f, (c.state.value.valueState("cloud:58").configuredValue as CarSettingKind.Slider).value)
        assertEquals(0f, (c.state.value.valueState("cloud:61").configuredValue as CarSettingKind.Slider).value)

        val speedItem = c.state.value.items.first { it.configId == "58" }
        assertEquals(100, VehicleSettingCommands.forItem(speedItem.copy(
            kind = (speedItem.kind as CarSettingKind.Slider).copy(value = 100f),
        ))?.parameter)
        assertNull(VehicleSettingCommands.forItem(speedItem.copy(
            kind = (speedItem.kind as CarSettingKind.Slider).copy(value = 50f),
        )))
        assertNull(VehicleSettingCommands.forItem(speedItem.copy(
            kind = (speedItem.kind as CarSettingKind.Slider).copy(value = 50.5f),
        )))
        val limitItem = c.state.value.items.first { it.configId == "61" }
        val limitSlider = limitItem.kind as CarSettingKind.Slider
        assertEquals(0, VehicleSettingCommands.forItem(limitItem.copy(kind = limitSlider.copy(value = 0f)))?.parameter)
        assertEquals(45, VehicleSettingCommands.forItem(limitItem.copy(kind = limitSlider.copy(value = 45f)))?.parameter)
        assertNull(VehicleSettingCommands.forItem(limitItem.copy(kind = limitSlider.copy(value = 10f))))
    }

    @Test
    fun speedSettingsWithoutExplicitConfigurationRemainUnknownAndCannotBeEdited() = runBlocking {
        val emptyGateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(
                configs = listOf("4", "58", "61").map { CloudBikeConfig(confId = it) },
                values = mapOf(
                    "bikeModelTopSpeedMax" to "100",
                    "bikeSpeedMaxDefaultMin" to "20",
                    "bikeSpeedMaxDefaultMax" to "90",
                ),
            )
        }
        val c = controller(gateway = emptyGateway)
        assertTrue(c.refresh())
        for (id in listOf("4", "58", "61")) {
            val item = c.state.value.items.first { it.configId == id }
            assertFalse((item.kind as CarSettingKind.Slider).valueKnown)
            assertFalse(c.editDraft(item.id) {
                (it as CarSettingKind.Slider).copy(value = 40f, valueKnown = true)
            })
            assertFalse(c.update(item.id) {
                (it as CarSettingKind.Slider).copy(value = 40f, valueKnown = true)
            })
        }
        assertEquals(20f..90f, (c.state.value.items.first { it.configId == "4" }.kind as CarSettingKind.Slider).range)
        assertEquals(0f..100f, (c.state.value.items.first { it.configId == "61" }.kind as CarSettingKind.Slider).range)
    }

    @Test
    fun cachedSpeedIsMarkedCachedNotLiveAndFreshReadbackConfirmsOnlyAfterWrite() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        c.updateVehicleObservation(VehicleStatusObservation(
            vin = "VIN-A", info = status(45), origin = VehicleStatusOrigin.CACHED_TTP, sequence = 1,
        ))
        val cached = c.state.value.valueState("cloud:58").cachedValue!!
        assertFalse(cached.live)
        assertEquals(SettingValueSource.CACHED_TTP, cached.source)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:58").vehicleValue)

        c.updateVehicleStatus("VIN-A", status(45))
        assertTrue(c.update("cloud:58") { (it as CarSettingKind.Slider).copy(value = 100f) })
        assertEquals(SettingWriteState.LOCAL_UPDATED, c.state.value.valueState("cloud:58").writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:58").confirmationState)
        c.updateVehicleStatus("VIN-A", status(100))
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:58").confirmationState)
        assertEquals(100, VehicleSettingCommands.forItem(writer.writes.single())?.parameter)
    }

    @Test
    fun everyMissingOrInvalidCompositeSiblingBlocksControllerAndMappedWriter() = runBlocking {
        for (id in listOf("55", "92")) {
            for (index in 0..3) {
                for (invalid in listOf(-1, 3, 255)) {
                    val fields = mutableListOf(1, 2, 1, 0).also { it[index] = invalid }
                    val writer = FakeWriter()
                    val c = controller(writer = writer)
                    assertTrue(c.refresh())
                    c.updateVehicleStatus("VIN-A", status(45, fields[0], fields[1], fields[2], fields[3]))
                    assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:$id").vehicleValue)
                    assertFalse(c.editDraft("cloud:$id") { CarSettingKind.Customize(2, 2, true, true) })
                    assertFalse(c.update("cloud:$id") { CarSettingKind.Customize(2, 2, true, true) })
                    assertTrue(writer.writes.isEmpty())
                }
            }
            var sends = 0
            val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
                override suspend fun send(command: VehicleSettingCommand): Boolean {
                    sends++
                    return true
                }
            })
            val template = CloudCarSettingMapper.toItems(CloudCarSettings(
                configs = listOf(CloudBikeConfig(confId = id)), values = emptyMap(),
            )).single { it.id == "cloud:$id" }
            val complete = CarSettingKind.Customize(1, 2, true, false)
            val cloudFallback = FakeWriter()
            var cloudUpdates = 0
            val cloudWriter = CloudCarSettingWriter(object : CarSettingsGateway {
                override suspend fun load(vin: String): CloudCarSettings? = null
                override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
                    cloudUpdates++
                    return true
                }
            }, { "VIN-A" }, cloudFallback, { true })
            for (partial in listOf(
                complete.copy(throttleLevel = null), complete.copy(energyLevel = null),
                complete.copy(energyState = null), complete.copy(tcs = null),
                complete.copy(throttleLevel = -1), complete.copy(energyLevel = 3),
            )) {
                assertNull(VehicleSettingCommands.forConfigId(id, partial))
                assertFalse(mapped.write(template.copy(kind = partial)))
                assertFalse(cloudWriter.write(template.copy(kind = partial)))
                assertNull(VehicleSettingCommands.forItem(template.copy(id = "cloud:001", kind = partial)))
            }
            assertEquals(0, sends)
            assertEquals(0, cloudUpdates)
            assertTrue(cloudFallback.writes.isEmpty())
            assertTrue(mapped.write(template.copy(kind = complete)))
            assertEquals(1, sends)
        }
    }

    @Test
    fun rawCompositeConstructionAndCopyCannotBypassValidatedEncodingPayload() {
        val valid = VehicleSettingCommands.forConfigId("55", CarSettingKind.Customize(2, 1, true, false))!!
        assertNotNull(VehicleSettingFrameEncoder.encode(valid))
        assertEquals(listOf(2, 1, 1, 0), valid.parameters)
        for (parameters in listOf(
            emptyList(), listOf(1, 1, 0), listOf(-1, 1, 0, 0), listOf(1, 3, 0, 0),
            listOf(1, 1, 2, 0), listOf(1, 1, 0, 2), listOf(1, 1, 0, 0, 0),
            mutableListOf(1, 1, 0, 0),
        )) {
            assertIllegalArgument {
                VehicleSettingFrameEncoder.encode(VehicleSettingCommand("CUSTOMIZE_SETTING", 0xC8, parameters = parameters))
            }
            assertIllegalArgument { VehicleSettingFrameEncoder.encode(valid.copy(parameters = parameters)) }
        }
        assertNotNull(VehicleSettingFrameEncoder.encode(valid.copy()))
    }

    @Test
    fun compositeEditsRetainSiblingsAndFreshObservationDoesNotOverwriteDraft() = runBlocking {
        for (id in listOf("55", "92")) {
            val writer = FakeWriter()
            val c = controller(writer = writer)
            assertTrue(c.refresh())
            c.updateVehicleStatus("VIN-A", status(45, 2, 1, 1, 0))
            assertNull(c.state.value.valueState("cloud:$id").draftValue)
            assertFalse(c.submitDraft("cloud:$id"))
            assertTrue(c.editDraft("cloud:$id") { (it as CarSettingKind.Customize).copy(tcs = true) })
            val draft = c.state.value.valueState("cloud:$id").draftValue
            c.updateVehicleStatus("VIN-A", status(45, 0, 0, 0, 0))
            assertEquals(draft, c.state.value.valueState("cloud:$id").draftValue)
            assertTrue(c.refresh())
            assertEquals(draft, c.state.value.valueState("cloud:$id").draftValue)
            assertTrue(c.submitDraft("cloud:$id"))
            assertEquals(listOf(2, 1, 1, 1), VehicleSettingCommands.forItem(writer.writes.single())?.parameters)
            val accepted = c.state.value.valueState("cloud:$id")
            assertEquals(SettingWriteState.LOCAL_UPDATED, accepted.writeState)
            assertEquals(SettingConfirmationState.WAITING_READBACK, accepted.confirmationState)
            val alias = if (id == "55") "cloud:92" else "cloud:55"
            assertFalse(c.editDraft(alias) { (it as CarSettingKind.Customize).copy(energyLevel = 2) })
            assertFalse(c.update(alias) { (it as CarSettingKind.Customize).copy(energyLevel = 2) })
            c.updateVehicleStatus("VIN-A", status(45, 2, 1, 1, 1))
            assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:$id").confirmationState)
            assertTrue(c.editDraft(alias) { (it as CarSettingKind.Customize).copy(energyLevel = 2) })
            c.cancelDraft(alias)
        }
    }

    @Test
    fun cancelledWriterExceptionPropagatesWithoutSuccessOrVehicleMutation() = runBlocking {
        val c = controller(writer = FakeWriter { throw CancellationException("fake cancellation") })
        assertTrue(c.refresh())
        c.updateVehicleStatus("VIN-A", status(45, 1, 1, 0, 0))
        val before = c.state.value.valueState("cloud:55").vehicleValue
        try {
            c.update("cloud:55") { (it as CarSettingKind.Customize).copy(tcs = true) }
            fail("Cancellation must reach the caller")
        } catch (_: CancellationException) {
            assertEquals(SettingWriteState.CANCELLED, c.state.value.valueState("cloud:55").writeState)
            assertEquals(before, c.state.value.valueState("cloud:55").vehicleValue)
            assertNull(c.state.value.valueState("cloud:55").submittedValue)
            assertTrue(c.state.value.pendingIds.isEmpty())
        }
    }

    @Test
    fun pendingCompositePreventsDuplicatesAndOldVinReceiptCannotMutateNewVin() = runBlocking {
        var vin = "VIN-A"
        val receipt = CompletableDeferred<CarSettingWriteResult>()
        val writer = FakeWriter { receipt.await() }
        val c = controller(vin = { vin }, writer = writer)
        assertTrue(c.refresh())
        c.updateVehicleStatus(vin, status(45, 1, 1, 0, 0))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(c.update("cloud:55") { (it as CarSettingKind.Customize).copy(tcs = true) })
        }
        assertEquals(1, writer.writes.size)
        assertFalse(c.refresh())
        assertEquals(SettingWriteState.SUBMITTING, c.state.value.valueState("cloud:55").writeState)
        assertFalse(c.editDraft("cloud:55") { (it as CarSettingKind.Customize).copy(energyLevel = 2) })
        assertFalse(c.update("cloud:55") { it })
        assertFalse(c.editDraft("cloud:92") { (it as CarSettingKind.Customize).copy(energyLevel = 2) })
        assertFalse(c.update("cloud:92") { (it as CarSettingKind.Customize).copy(energyLevel = 2) })
        vin = "VIN-B"
        c.onVinChanged(vin)
        assertTrue(c.refresh())
        assertFalse(c.update("cloud:55") { writer.writes.single().kind })
        receipt.complete(CarSettingWriteResult(true, CarSettingWriteChannel.BLE))
        withTimeout(5_000) { job.join() }
        assertEquals(SettingWriteState.IDLE, c.state.value.valueState("cloud:55").writeState)
        assertNull(c.state.value.valueState("cloud:55").submittedValue)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:55").vehicleValue)
        assertEquals(1, writer.writes.size)
    }

    @Test
    fun observationsRejectWrongVinAndOlderSequenceAndCacheCannotConfirmWrites() = runBlocking {
        val c = controller()
        assertTrue(c.refresh())
        fun observe(vin: String, sequence: Long, speed: Int, origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
            c.updateVehicleObservation(VehicleStatusObservation(vin, status(speed, 1, 2, 1, 0), origin, sequence))
        }
        observe("VIN-B", 100, 100)
        for (id in listOf("4", "58", "61")) {
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:$id").vehicleValue)
        }
        observe("VIN-A", 1, 30, VehicleStatusOrigin.CACHED_TTP)
        observe("VIN-A", 10, 45)
        val fresh = c.state.value.valueState("cloud:61").vehicleValue
        observe("VIN-A", 9, 30)
        observe("VIN-A", 8, 15, VehicleStatusOrigin.CACHED_TTP)
        assertEquals(fresh, c.state.value.valueState("cloud:61").vehicleValue)
        assertTrue(c.update("cloud:61") { (it as CarSettingKind.Slider).copy(value = 50f) })
        observe("VIN-A", 10, 50)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:61").confirmationState)
        observe("VIN-A", 11, 50, VehicleStatusOrigin.CACHED_TTP)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:61").confirmationState)
        observe("VIN-A", 12, 50)
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:61").confirmationState)
        val separate = c.state.value.valueState("cloud:61")
        assertEquals(0f, (separate.configuredValue as CarSettingKind.Slider).value)
        assertEquals(SettingValueSource.LIVE_TTP, (separate.vehicleValue as VehicleSettingValue.Known).source)
    }

    @Test
    fun invalidSpeedTargetsAreRejectedByControllerMapperAndCloudBuilder() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        for (id in listOf("4", "58", "61")) {
            val item = c.state.value.items.first { it.configId == id }
            val slider = item.kind as CarSettingKind.Slider
            for (value in listOf(-1f, 1f, 14f, 121f, 200f, 15.5f, Float.NaN, Float.POSITIVE_INFINITY)) {
                val invalid = slider.copy(value = value, valueKnown = true)
                assertNull(VehicleSettingCommands.forItem(item.copy(kind = invalid)))
                assertFalse(c.editDraft(item.id) { invalid })
                assertFalse(c.update(item.id) { invalid })
                if (id == "61") assertNull(CloudCarSettingUpdateBuilder.build(id, invalid, "VIN-A"))
            }
        }
        assertTrue(writer.writes.isEmpty())
        val limit = c.state.value.items.first { it.configId == "61" }.kind as CarSettingKind.Slider
        for (value in listOf(0f, 15f, 100f)) {
            assertEquals(value.toInt(), VehicleSettingCommands.forConfigId("61", limit.copy(value = value))?.parameter)
            assertEquals(value.toInt(), CloudCarSettingUpdateBuilder.build("61", limit.copy(value = value), "VIN-A")?.fields?.get("value"))
        }
    }

    @Test
    fun invalidOrMissingCloudSpeedIsNeverConvertedToKnownDefaultOrDraft() = runBlocking {
        for (raw in listOf(null, "", "null", "abc", "50.5", "NaN", "-1", "999")) {
            val c = controller(gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String) = CloudCarSettings(
                    configs = listOf("4", "58", "61").map { CloudBikeConfig(confId = it) },
                    values = mapOf("bikeModelTopSpeedMax" to "100", "bikeModelTopSpeedMin" to "15") +
                        (raw?.let { mapOf("bikeSpeedMax" to it, "highestSpeedLimit" to it) } ?: emptyMap()),
                )
            })
            assertTrue(c.refresh())
            for (id in listOf("4", "58", "61")) {
                val state = c.state.value.valueState("cloud:$id")
                assertNull(state.configuredValue)
                assertNull(state.displayKind())
                assertNull(state.draftValue)
                assertFalse(c.submitDraft("cloud:$id"))
            }
        }
    }

    @Test
    fun id58ThreeUiPositionsPreserveRawModelMinimumMaximumAndUnlimited() = runBlocking {
        val c = controller()
        assertTrue(c.refresh())
        val slider = c.state.value.items.first { it.configId == "58" }.kind as CarSettingKind.Slider
        assertEquals(listOf(15, 100, 0), slider.speedPresets)
        for ((index, speed) in slider.speedPresets.withIndex()) {
            val target = speedPresetTarget(slider, index.toFloat())!!
            assertEquals(speed.toFloat(), target.value)
            assertEquals(index.toFloat(), speedPresetPosition(slider, speed.toFloat()))
            assertEquals(speed, VehicleSettingCommands.forConfigId("58", target)?.parameter)
        }
        assertNull(speedPresetPosition(slider, 45f))
        assertNull(speedPresetTarget(slider, Float.NaN))
        assertNull(speedPresetTarget(slider, -1f))
        assertNull(VehicleSettingCommands.forConfigId("58", slider.copy(speedPresets = emptyList())))
        assertFalse(c.update("cloud:58") { slider.copy(value = 40f, speedPresets = listOf(40, 100, 0)) })
    }

    @Test
    fun speedDraftAndSourcesSurviveConfigRefreshAndExposeConflictWithoutMerging() = runBlocking {
        val c = controller()
        assertTrue(c.refresh())
        c.updateVehicleStatus("VIN-A", status(45))
        assertTrue(c.editDraft("cloud:61") { (it as CarSettingKind.Slider).copy(value = 60f) })
        assertTrue(c.refresh())
        val state = c.state.value.valueState("cloud:61")
        assertEquals(60f, (state.draftValue as CarSettingKind.Slider).value)
        assertEquals(45f, ((state.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertEquals(0f, (state.configuredValue as CarSettingKind.Slider).value)
        val summary = settingValueSourceSummary(state)
        assertTrue(summary.contains("车辆当前值"))
        assertTrue(summary.contains("45"))
        assertTrue(summary.contains("云端配置"))
        assertTrue(summary.contains("非车辆回读"))
    }

    @Test
    fun unknownModelBoundsRemainReadOnlyEvenWithAnExplicitSpeedOrForgedDraftBounds() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer, gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(
                configs = listOf("4", "58", "61").map { CloudBikeConfig(confId = it) },
                values = mapOf("bikeSpeedMax" to "80", "highestSpeedLimit" to "0"),
            )
        })
        assertTrue(c.refresh())
        for (id in listOf("4", "58", "61")) {
            val item = c.state.value.items.single { it.id == "cloud:$id" }
            val configured = c.state.value.valueState(item.id).configuredValue as CarSettingKind.Slider
            assertTrue(configured.valueKnown)
            assertFalse(configured.rangeKnown)
            val target = configured.copy(value = 30f)
            assertNull(VehicleSettingCommands.forItem(item.copy(kind = target)))
            assertFalse(c.editDraft(item.id) { target.copy(rangeKnown = true) })
            assertFalse(c.update(item.id) { target.copy(rangeKnown = true) })
            if (id == "61") assertNull(CloudCarSettingUpdateBuilder.build(id, target, "VIN-A"))
        }
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun invalidTtpDoesNotCreateOrRefreshACompleteLiveObservationOrConfirmAPendingTarget() = runBlocking {
        val c = controller()
        assertTrue(c.refresh())
        for (speed in listOf(-1, 1, 14, 121, 255)) {
            c.updateVehicleStatus("VIN-A", status(speed))
            for (id in listOf("4", "58", "61", "55", "92")) {
                assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:$id").vehicleValue)
            }
        }
        c.updateVehicleStatus("VIN-A", status(45, 1, 2, 1, 0))
        assertTrue(c.update("cloud:55") { (it as CarSettingKind.Customize).copy(tcs = true) })
        val before = c.state.value.valueState("cloud:55").vehicleValue as VehicleSettingValue.Known
        c.updateVehicleStatus("VIN-A", status(255, 1, 2, 1, -1))
        for (id in listOf("4", "58", "61", "55", "92")) {
            val value = c.state.value.valueState("cloud:$id")
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, value.readbackState)
            assertFalse((value.vehicleValue as VehicleSettingValue.Known).live)
        }
        val incomplete = c.state.value.valueState("cloud:55")
        assertEquals(before.sequence, (incomplete.vehicleValue as VehicleSettingValue.Known).sequence)
        assertEquals(SettingConfirmationState.WAITING_READBACK, incomplete.confirmationState)
        assertEquals(CarSettingKind.Customize(1, 2, true, false), incomplete.vehicleValue.kind)
        c.updateVehicleStatus("VIN-A", status(45, 1, 2, 1, 1))
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:55").confirmationState)
    }

    @Test
    fun cancelledDraftAndUnknownSpeedNeverProduceAnImplicitWrite() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        c.updateVehicleStatus("VIN-A", status(45, 1, 1, 0, 0))
        for (id in listOf("4", "58", "61", "55", "92")) {
            val key = "cloud:$id"
            assertNull(c.state.value.valueState(key).draftValue)
            assertFalse(c.submitDraft(key))
            assertTrue(c.editDraft(key) { kind ->
                when (kind) {
                    is CarSettingKind.Slider -> kind.copy(value = if (id == "58") 100f else 50f)
                    is CarSettingKind.Customize -> kind.copy(tcs = true)
                    else -> error("Unexpected kind")
                }
            })
            c.cancelDraft(key)
            assertNull(c.state.value.valueState(key).draftValue)
            assertFalse(c.submitDraft(key))
            assertEquals(SettingWriteState.IDLE, c.state.value.valueState(key).writeState)
        }
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun speedUiRoundsOnlyUserInputAndNeverRoundsInvalidConfiguredOrSubmittedValues() {
        val items = CloudCarSettingMapper.toItems(CloudCarSettings(
            configs = listOf(CloudBikeConfig(confId = "4"), CloudBikeConfig(confId = "61")),
            values = mapOf("bikeSpeedMaxDefaultMin" to "15", "bikeSpeedMaxDefaultMax" to "100",
                "bikeModelTopSpeedMax" to "100", "bikeSpeedMax" to "45", "highestSpeedLimit" to "0"),
        ))
        for (id in listOf("4", "61")) {
            val item = items.single { it.id == "cloud:$id" }
            assertEquals(46f, settingSliderDraftValue(item, 45.999996f))
            assertNull(settingSliderDraftValue(item, Float.NaN))
            assertNull(settingSliderDraftValue(item, 121f))
            assertNull(settingSliderDraftValue(item.copy(kind = (item.kind as CarSettingKind.Slider).copy(rangeKnown = false)), 45f))
            assertNull(VehicleSettingCommands.forItem(item.copy(kind = item.kind.copy(value = 45.999996f))))
        }
    }

    @Test
    fun cloudAcceptanceAndResultDoNotInventVehicleConfirmation() = runBlocking {
        val fake = FakeGateway()
        var updates = 0
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = fake.load(vin)
            override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
                assertEquals("VIN-A", vin)
                assertEquals("61", update.configId)
                assertEquals(15, update.fields["highestSpeedLimit"])
                updates++
                return true
            }
            override suspend fun checkStatus(configId: String): Boolean = true
        }
        val c = CarSettingsController(writer = CloudCarSettingWriter(gateway, { "VIN-A" }),
            gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0,
            statusPollAttempts = 1, statusPollIntervalMs = 0)
        assertTrue(c.refresh())
        assertTrue(c.update("cloud:61") { (it as CarSettingKind.Slider).copy(value = 15f) })
        val accepted = c.state.value.valueState("cloud:61")
        assertEquals(1, updates)
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, accepted.writeState)
        assertEquals(CarSettingConfirmationStatus.CLOUD_ACCEPTED, accepted.confirmationStatus)
        assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, accepted.confirmationState)
        assertEquals(VehicleSettingValue.Unknown, accepted.vehicleValue)
        assertNull(accepted.cachedValue)
        assertEquals(0f, (accepted.configuredValue as CarSettingKind.Slider).value)
        assertEquals(15f, (accepted.submittedValue as CarSettingKind.Slider).value)
    }

    @Test
    fun metadataRefreshKeepsSpeedDraftButCannotSubmitUsingObsoleteModelBounds() = runBlocking {
        val fake = FakeGateway()
        var cloud = fake.load("VIN-A")
        val writer = FakeWriter()
        val c = controller(writer = writer, gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = cloud
        })
        assertTrue(c.refresh())
        assertTrue(c.editDraft("cloud:4") { (it as CarSettingKind.Slider).copy(value = 60f) })
        val draft = c.state.value.valueState("cloud:4").draftValue
        cloud = cloud.copy(values = cloud.values + ("bikeSpeedMaxDefaultMax" to "90"))
        assertTrue(c.refresh())
        assertEquals(draft, c.state.value.valueState("cloud:4").draftValue)
        assertEquals(80f, (c.state.value.valueState("cloud:4").configuredValue as CarSettingKind.Slider).value)
        assertFalse(c.submitDraft("cloud:4"))
        assertTrue(writer.writes.isEmpty())
        val current = c.state.value.items.single { it.id == "cloud:4" }.kind as CarSettingKind.Slider
        assertTrue(c.editDraft("cloud:4") { current.copy(value = 60f) })
        assertTrue(c.submitDraft("cloud:4"))
        assertEquals(60, VehicleSettingCommands.forItem(writer.writes.single())?.parameter)
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid composite construction to be rejected")
        } catch (_: IllegalArgumentException) {
            // The encoder was never given an unchecked multi-field payload.
        }
    }
}
