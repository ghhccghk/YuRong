package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** All IO stays in memory: neither Gateway nor CommandSink contacts a real service. */
class VehicleSettingsSecondPhaseTest {
    private val ids = listOf("5", "7", "11", "39")
    private val keys = listOf("cloud:5", "cloud:7", "cloud:7:time", "cloud:11", "cloud:11:distance", "cloud:39", "cloud:39:time")
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })

    private class FakeGateway(var cloud: CloudCarSettings) : CarSettingsGateway {
        var requests = 0
        var fail = false
        override suspend fun load(vin: String): CloudCarSettings? {
            requests++
            return if (fail) null else cloud
        }
    }

    private class FakeWriter(
        val action: suspend (CarSettingItem) -> CarSettingWriteResult = { CarSettingWriteResult(true, CarSettingWriteChannel.BLE) },
    ) : CarSettingWriter {
        val writes = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem) = writeWithResult(item).success
        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            writes += item
            return action(item)
        }
    }

    private fun cloud(values: Map<String, String> = emptyMap()) = CloudCarSettings(
        configs = ids.map { CloudBikeConfig(confId = it) }, values = values,
    )

    private fun status(sound: Int = -1, lock: Int = -1, time: Int = -1, power: Int = -1, distance: Int = -1, brace: Int = -1): TtpInfo =
        unknown.copy(extended = unknown.extended.copy(
            alarmRingThemeCurrent = sound, leaveVehicleAutoLockStatus = lock,
            leaveVehicleAutoLockTimeStatus = time, bleAutoTurnOnOffStatus = power,
            bleAutoTurnOnOffGearStatus = distance, sideBraceStatus = brace,
        ))

    private fun controller(
        gateway: CarSettingsGateway = FakeGateway(cloud()),
        writer: CarSettingWriter = FakeWriter(),
        vin: () -> String = { "VIN-A" },
    ) = CarSettingsController(writer = writer, gateway = gateway, vinProvider = vin,
        debounceMs = 0, statusPollAttempts = 0)

    private fun observe(c: CarSettingsController, info: TtpInfo, sequence: Long, vin: String = "VIN-A",
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) =
        c.updateVehicleObservation(VehicleStatusObservation(vin, info, origin, sequence))

    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == id }
    private fun observed(c: CarSettingsController, id: String) =
        (c.state.value.valueState(id).vehicleValue as VehicleSettingValue.Known).kind

    @Test
    fun refreshedCloudListHasNoFabricatedValuesOrImplicitDrafts() = runBlocking {
        val gateway = FakeGateway(cloud())
        val c = controller(gateway)
        assertTrue(c.state.value.items.isEmpty())
        assertTrue(c.refresh())
        assertEquals(1, gateway.requests)
        for (id in keys) {
            val state = c.state.value.valueState(id)
            assertNull(state.configuredValue)
            assertNull(state.displayKind())
            assertNull(state.draftValue)
            assertFalse(c.submitDraft(id))
            when (val kind = item(c, id).kind) {
                is CarSettingKind.Choice -> assertFalse(kind.valueKnown)
                is CarSettingKind.Toggle -> assertFalse(kind.valueKnown)
                else -> fail("Unexpected kind")
            }
        }
    }

    @Test
    fun dtoFieldsWithoutPageBindingEvidenceNeverBecomeConfiguredOrVehicleValues() = runBlocking {
        for (raw in listOf("0", "1", "2", "1.5", "-1", "255", "null", "")) {
            val c = controller(FakeGateway(cloud(mapOf(
                "bleApproachPowerOnEnable" to raw, "bleApproachPowerOnDistance" to raw,
                "bleLeavePowerOffEnable" to raw, "bleLeavePowerOffDistance" to raw,
                "alarmRingThemeCurrent" to raw,
            ))))
            assertTrue(c.refresh())
            for (id in keys) {
                assertNull(c.state.value.valueState(id).configuredValue)
                assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
            }
        }
    }

    @Test
    fun rawCloudListThroughControllerAndActualWritersUsesOnlyVerifiedBleCommands() = runBlocking {
        val requests = mutableListOf<HttpRequest>()
        val gateway = YadeaCarSettingsGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    requests += request
                    return HttpResponse(200, """{"success":true,"data":{
                        "bleApproachPowerOnEnable":0,"bleApproachPowerOnDistance":2,
                        "bleLeavePowerOffEnable":0,"bleLeavePowerOffDistance":15,
                        "bikeConfigVos":[{"confId":"5"},{"confId":"7"},{"confId":"11"},{"confId":"39"}]
                    }}""")
                }
            },
            tokenProvider = { "fake-token" }, timestampProvider = { 1_700_000_000_000L },
            baseUrl = "https://example.invalid/",
        )
        val commands = mutableListOf<VehicleSettingCommand>()
        val ble = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean {
                commands += command
                return true
            }
        })
        val c = controller(gateway, CloudCarSettingWriter(gateway, { "VIN-A" }, ble, { true }))
        assertTrue(c.refresh())
        for (id in keys) {
            assertNull(c.state.value.valueState(id).configuredValue)
            assertNull(c.state.value.valueState(id).displayKind())
        }
        observe(c, status(sound = 1, lock = 1, time = 3, power = 1, distance = 2, brace = 1), 1)
        val expected = listOf(0x2E to 1, 0x53 to -1, 0x55 to 3, 0x5C to -1,
            0x5E to 2, 0x53 to -1, 0x55 to 3)
        for ((index, id) in keys.withIndex()) {
            assertTrue(c.update(id) { it })
            assertEquals(expected[index], commands.last().let { it.commandId to it.parameter })
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState(id).confirmationState)
        }
        for (configId in listOf("7", "11", "39")) {
            assertTrue(c.update("cloud:$configId") { (it as CarSettingKind.Toggle).copy(checked = false) })
            assertEquals(if (configId == "11") 0x5D else 0x54, commands.last().commandId)
        }
        // The cloud transport supplied metadata once; no guessed cloud update was sent.
        assertEquals(1, requests.size)
        assertTrue(requests.single().url.endsWith(YadeaCarSettingsGateway.PATH))
        assertEquals(keys.size + 3, commands.size)
    }

    @Test
    fun originalBitOffsetsDecodeThroughRealParserWithoutInventingMissingBytes() {
        for (sound in 0..1) for (time in 0..3) {
            val bytes = ByteArray(256) { 0xFF.toByte() }
            bytes[0x32] = (0xFC or sound).toByte()
            bytes[0x24] = ((0xFF and 0x83) or (1 shl 2) or (time shl 4)).toByte()
            bytes[0x25] = 0xFD.toByte() // sideBraceStatus = 1
            bytes[0x1A] = 0x7F.toByte() // bleAutoTurnOnOffStatus = 1
            bytes[0x1B] = 0xFA.toByte() // gear = 2
            val parsed = TtpInfoParser.parse(bytes).extended
            assertEquals(sound, parsed.alarmRingThemeCurrent)
            assertEquals(1, parsed.leaveVehicleAutoLockStatus)
            assertEquals(time, parsed.leaveVehicleAutoLockTimeStatus)
            assertEquals(1, parsed.sideBraceStatus)
            assertEquals(1, parsed.bleAutoTurnOnOffStatus)
            assertEquals(2, parsed.bleAutoTurnOnOffGearStatus)
        }
        for (info in listOf(unknown, TtpInfoParser.parse(ByteArray(8) { 0xFF.toByte() }))) {
            assertEquals(-1, info.extended.alarmRingThemeCurrent)
            assertEquals(-1, info.extended.leaveVehicleAutoLockStatus)
            assertEquals(-1, info.extended.leaveVehicleAutoLockTimeStatus)
        }
    }

    @Test
    fun id5BothThemesReadAndWriteTheirExactChoiceIndex() = runBlocking {
        val commands = mutableListOf<VehicleSettingCommand>()
        val c = controller(writer = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
        }))
        assertTrue(c.refresh())
        for (index in 0..1) {
            observe(c, status(sound = index), index.toLong() + 1)
            val kind = observed(c, "cloud:5") as CarSettingKind.Choice
            assertEquals(index, kind.selectedIndex)
            assertEquals(listOf("主题 1", "主题 2")[index], kind.options[kind.selectedIndex])
            assertTrue(c.update("cloud:5") { kind.copy(selectedIndex = 1 - index) })
            assertEquals(0x2E, commands.last().commandId)
            assertEquals(1 - index, commands.last().parameter)
            assertEquals(index, (observed(c, "cloud:5") as CarSettingKind.Choice).selectedIndex)
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:5").confirmationState)
        }
    }

    @Test
    fun unknownSoundRequiresExplicitChoiceAndNeverSubmitsItsPlaceholder() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        assertFalse(c.update("cloud:5") { it })
        assertTrue(writer.writes.isEmpty())
        assertTrue(c.editDraft("cloud:5") { (it as CarSettingKind.Choice).copy(selectedIndex = 1, valueKnown = true) })
        assertTrue(c.submitDraft("cloud:5"))
        assertEquals(1, VehicleSettingCommands.forItem(writer.writes.single())!!.parameter)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:5").vehicleValue)
    }

    @Test
    fun id7And39ShareTimeReadbackButSendEnumRatherThanSecondsOrProgress() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        for (index in 0..3) {
            observe(c, status(lock = 1, time = index, brace = 1), index.toLong() + 1)
            for (id in listOf("cloud:7:time", "cloud:39:time")) {
                val kind = observed(c, id) as CarSettingKind.Choice
                assertEquals(index, kind.selectedIndex)
                assertEquals(listOf("3 秒", "5 秒", "10 秒", "15 秒")[index], kind.options[kind.selectedIndex])
                assertTrue(c.update(id) { kind })
                val command = VehicleSettingCommands.forItem(writer.writes.last())!!
                assertEquals(0x55, command.commandId)
                assertEquals(index, command.parameter)
            }
        }
    }

    @Test
    fun autoLockSwitchIsKnownOnlyWhenBothOriginalModelBranchesAgree() = runBlocking {
        for ((raw, brace, expected) in listOf(Triple(0, -1, false), Triple(0, 0, false), Triple(1, 1, true),
            Triple(1, 0, null), Triple(1, -1, null), Triple(2, 1, null), Triple(-1, 1, null))) {
            val c = controller()
            assertTrue(c.refresh())
            observe(c, status(lock = raw, brace = brace), 1)
            for (id in listOf("cloud:7", "cloud:39")) {
                if (expected == null) assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
                else assertEquals(expected, (observed(c, id) as CarSettingKind.Toggle).checked)
            }
        }
    }

    @Test
    fun invalidEnumsDoNotCreateAnyObservationOrKnownDefault() = runBlocking {
        for (invalid in listOf(-1, 3, 7, 255)) {
            val c = controller()
            assertTrue(c.refresh())
            observe(c, status(sound = invalid, lock = invalid, time = if (invalid == 3) 4 else invalid,
                power = invalid, distance = invalid), 1)
            for (id in keys) assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
        }
        val c = controller()
        assertTrue(c.refresh())
        observe(c, status(sound = 2, lock = 2, time = 4, power = 2, distance = 3), 1)
        for (id in keys) assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
    }

    @Test
    fun id11SwitchAndDistanceHaveSeparateObservationsAndSeparateCommands() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        observe(c, status(power = 1, distance = 2), 1)
        assertTrue((observed(c, "cloud:11") as CarSettingKind.Toggle).checked)
        assertEquals(2, (observed(c, "cloud:11:distance") as CarSettingKind.Choice).selectedIndex)
        for (index in 0..2) {
            assertTrue(c.update("cloud:11:distance") { (it as CarSettingKind.Choice).copy(selectedIndex = index) })
            val command = VehicleSettingCommands.forItem(writer.writes.last())!!
            assertEquals(0x5E, command.commandId)
            assertEquals(index, command.parameter)
        }
        assertTrue(c.update("cloud:11") { (it as CarSettingKind.Toggle).copy(checked = false) })
        assertEquals(0x5D, VehicleSettingCommands.forItem(writer.writes.last())!!.commandId)
        assertEquals(2, (observed(c, "cloud:11:distance") as CarSettingKind.Choice).selectedIndex)
    }

    @Test
    fun independentWritesNeverRequireOrFillUnknownSiblings() = runBlocking {
        for (configId in listOf("7", "11", "39")) {
            val writer = FakeWriter()
            val c = controller(writer = writer)
            assertTrue(c.refresh())
            // Explicit switch command needs no fabricated time/distance.
            assertTrue(c.update("cloud:$configId") { (it as CarSettingKind.Toggle).copy(checked = true, valueKnown = true) })
            val suffix = if (configId == "11") "distance" else "time"
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:$configId:$suffix").vehicleValue)
            assertTrue(writer.writes.single().kind is CarSettingKind.Toggle)
            // Switch receipt is not an observation. Cancel implicit placeholder submission.
            assertFalse(c.update("cloud:$configId:$suffix") { it })
        }
    }

    @Test
    fun timeAndDistanceEditingRequiresAnEnabledParentWithoutFakingItsState() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        for (id in listOf("cloud:7:time", "cloud:39:time", "cloud:11:distance")) {
            assertFalse(c.editDraft(id) { (it as CarSettingKind.Choice).copy(selectedIndex = 1, valueKnown = true) })
            assertFalse(c.update(id) { (it as CarSettingKind.Choice).copy(selectedIndex = 1, valueKnown = true) })
        }
        observe(c, status(lock = 0, time = 1, power = 0, distance = 1), 1)
        for (id in listOf("cloud:7:time", "cloud:39:time", "cloud:11:distance")) assertFalse(c.update(id) { it })
        observe(c, status(lock = 1, brace = 1, power = 1), 2)
        for (id in listOf("cloud:7:time", "cloud:39:time", "cloud:11:distance")) {
            // Current time may be unknown: a deliberately selected independent target is safe.
            assertTrue(c.editDraft(id) { (it as CarSettingKind.Choice).copy(selectedIndex = 1, valueKnown = true) })
            assertTrue(c.submitDraft(id))
        }
    }

    @Test
    fun directMapperAndWritersRejectUnknownOutOfRangeAndForgedChoices() = runBlocking {
        val sends = mutableListOf<VehicleSettingCommand>()
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sends += command; return true }
        })
        var cloudWrites = 0
        val cloudWriter = CloudCarSettingWriter(object : CarSettingsGateway {
            override suspend fun load(vin: String): CloudCarSettings? = null
            override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean { cloudWrites++; return true }
        }, { "VIN-A" }, mapped, { true })
        for (entry in CloudCarSettingMapper.toItems(cloud()).filter { it.configId in ids }) {
            val bad = when (val kind = entry.kind) {
                is CarSettingKind.Toggle -> listOf(kind)
                is CarSettingKind.Choice -> listOf(kind, kind.copy(valueKnown = true, selectedIndex = -1),
                    kind.copy(valueKnown = true, selectedIndex = kind.options.size),
                    kind.copy(valueKnown = true, options = listOf("forged")))
                else -> error("Unexpected kind")
            }
            for (kind in bad) {
                assertNull(VehicleSettingCommands.forConfigId(entry.configId, kind))
                assertFalse(mapped.write(entry.copy(kind = kind)))
                assertFalse(cloudWriter.write(entry.copy(kind = kind)))
            }
            val target = when (val kind = entry.kind) {
                is CarSettingKind.Toggle -> kind.copy(valueKnown = true)
                is CarSettingKind.Choice -> kind.copy(valueKnown = true)
            }
            assertNull(VehicleSettingCommands.forItem(entry.copy(id = "cloud:001", kind = target)))
        }
        assertTrue(sends.isEmpty())
        assertEquals(0, cloudWrites)
    }

    @Test
    fun directCommandBoundaryRejectsInvalidParametersBeforeExistingEncoder() {
        for ((id, type, limit) in listOf(Triple(0x55, "LEAVE_THE_CAR_AUTO_LOCK_TIME", 3),
            Triple(0x5E, "AUTO_TURN_ON_OFF", 2), Triple(0x2E, "ALARM_THEME", 2))) {
            for (invalid in listOf(-1, limit + 1, 255)) {
                try { VehicleSettingCommand(type, id, invalid); fail("Invalid command reached encoder") }
                catch (_: IllegalArgumentException) { }
            }
            for (value in 0..limit) assertNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand(type, id, value)))
        }
    }

    @Test
    fun controllerRejectsInvalidTargetsBeforeCreatingDraftsOrCallingWriter() = runBlocking {
        val writer = FakeWriter()
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        observe(c, status(sound = 0, lock = 1, time = 0, power = 1, distance = 0, brace = 1), 1)
        for (id in keys) {
            val known = observed(c, id)
            val invalid = when (known) {
                is CarSettingKind.Toggle -> listOf(known.copy(valueKnown = false))
                is CarSettingKind.Choice -> listOf(known.copy(valueKnown = false),
                    known.copy(selectedIndex = -1), known.copy(selectedIndex = known.options.size),
                    known.copy(options = listOf("forged")))
                else -> error("Unexpected kind")
            }
            for (target in invalid) {
                assertFalse(c.editDraft(id) { target })
                assertFalse(c.update(id) { target })
                assertNull(c.state.value.valueState(id).draftValue)
                assertNull(c.state.value.valueState(id).submittedValue)
                assertEquals(SettingWriteState.IDLE, c.state.value.valueState(id).writeState)
            }
        }
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun noCloudWriteIsInventedAndBleAvailabilityControlsActualWriterRoute() = runBlocking {
        val fallback = FakeWriter()
        var cloudWrites = 0
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = cloud()
            override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean { cloudWrites++; return true }
        }
        var ble = false
        val writer = CloudCarSettingWriter(gateway, { "VIN-A" }, fallback, { ble })
        val entry = CloudCarSettingMapper.toItems(cloud()).single { it.id == "cloud:5" }
        val target = entry.copy(kind = (entry.kind as CarSettingKind.Choice).copy(selectedIndex = 1, valueKnown = true))
        for (id in ids) {
            assertNull(CloudCarSettingUpdateBuilder.build(id, CarSettingKind.Toggle(true), "VIN-A"))
            assertNull(CloudCarSettingUpdateBuilder.build(id, CarSettingKind.Choice(1, SecondPhaseSettings.timeOptions), "VIN-A"))
        }
        assertFalse(writer.write(target))
        assertTrue(fallback.writes.isEmpty())
        ble = true
        assertTrue(writer.write(target))
        assertEquals(1, fallback.writes.size)
        assertEquals(0, cloudWrites)
    }

    @Test
    fun onlyNewLiveReadbackCanConfirmEachSetting() = runBlocking {
        for (id in keys) {
            val c = controller()
            assertTrue(c.refresh())
            val before = status(sound = 0, lock = 1, time = 0, power = 1, distance = 0, brace = 1)
            observe(c, before, 10)
            assertTrue(c.update(id) { kind -> when (kind) {
                is CarSettingKind.Toggle -> kind.copy(checked = false)
                is CarSettingKind.Choice -> kind.copy(selectedIndex = 1)
                else -> error("Unexpected kind")
            } })
            val target = status(sound = 1, lock = 0, time = 1, power = 0, distance = 1, brace = 1)
            observe(c, target, 10) // Same sequence predates write.
            observe(c, target, 11, origin = VehicleStatusOrigin.CACHED_TTP)
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState(id).confirmationState)
            assertFalse(c.state.value.valueState(id).cachedValue!!.live)
            observe(c, target, 12)
            assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState(id).confirmationState)
            assertNull(c.state.value.valueState(id).submittedValue)
        }
    }

    @Test
    fun missingFieldsInvalidateFreshnessAndDoNotConfirmOrEraseDrafts() = runBlocking {
        val c = controller()
        assertTrue(c.refresh())
        observe(c, status(sound = 0, lock = 1, time = 0, power = 1, distance = 0, brace = 1), 1)
        assertTrue(c.editDraft("cloud:7:time") { (it as CarSettingKind.Choice).copy(selectedIndex = 3) })
        val draft = c.state.value.valueState("cloud:7:time").draftValue
        assertTrue(c.update("cloud:5") { (it as CarSettingKind.Choice).copy(selectedIndex = 1) })
        observe(c, unknown, 2)
        for (id in keys) {
            val state = c.state.value.valueState(id)
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, state.readbackState)
            assertFalse((state.vehicleValue as VehicleSettingValue.Known).live)
        }
        assertEquals(draft, c.state.value.valueState("cloud:7:time").draftValue)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:5").confirmationState)
    }

    @Test
    fun failureKeepsObservedValuesAndClearsTargetsForAllFourSettings() = runBlocking {
        for (id in keys) {
            val c = controller(writer = FakeWriter { CarSettingWriteResult(false, CarSettingWriteChannel.BLE) })
            assertTrue(c.refresh())
            observe(c, status(sound = 0, lock = 1, time = 0, power = 1, distance = 0, brace = 1), 1)
            val before = c.state.value.valueState(id).vehicleValue
            assertFalse(c.update(id) { kind -> when (kind) {
                is CarSettingKind.Toggle -> kind.copy(checked = false)
                is CarSettingKind.Choice -> kind.copy(selectedIndex = 1)
                else -> error("Unexpected kind")
            } })
            val after = c.state.value.valueState(id)
            assertEquals(before, after.vehicleValue)
            assertEquals(SettingWriteState.FAILED, after.writeState)
            assertNull(after.submittedValue)
            assertNotNull(after.error)
        }
    }

    @Test
    fun cancellationPropagatesAndCannotBeMisreportedAsSuccess() = runBlocking {
        for (id in keys) {
            val c = controller(writer = FakeWriter { throw CancellationException("fake") })
            assertTrue(c.refresh())
            observe(c, status(sound = 0, lock = 1, time = 0, power = 1, distance = 0, brace = 1), 1)
            try { c.update(id) { it }; fail("Cancellation was swallowed") }
            catch (_: CancellationException) {
                assertEquals(SettingWriteState.CANCELLED, c.state.value.valueState(id).writeState)
                assertNull(c.state.value.valueState(id).submittedValue)
                assertTrue(c.state.value.pendingIds.isEmpty())
            }
        }
    }

    @Test
    fun vinSwitchDropsDraftAndRejectsOldObservationsAndWriteReceipts() = runBlocking {
        var vin = "VIN-A"
        val receipt = CompletableDeferred<CarSettingWriteResult>()
        val writer = FakeWriter { receipt.await() }
        val c = controller(writer = writer, vin = { vin })
        assertTrue(c.refresh())
        observe(c, status(sound = 0, lock = 1, time = 0, power = 1, distance = 0, brace = 1), 10)
        assertTrue(c.editDraft("cloud:11:distance") { (it as CarSettingKind.Choice).copy(selectedIndex = 2) })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:39") { it }) }
        vin = "VIN-B"
        c.onVinChanged(vin)
        assertTrue(c.refresh())
        assertFalse(c.submitDraft("cloud:11:distance"))
        observe(c, status(sound = 1, lock = 1, power = 1, brace = 1), 100, vin = "VIN-A")
        receipt.complete(CarSettingWriteResult(true, CarSettingWriteChannel.BLE))
        job.join()
        for (id in keys) {
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
            assertEquals(SettingWriteState.IDLE, c.state.value.valueState(id).writeState)
            assertNull(c.state.value.valueState(id).draftValue)
        }
    }

    @Test
    fun lateCloudResponseCannotPopulateAnotherVinOrAnEarlierVisitToSameVin() = runBlocking {
        for (switchBack in listOf(false, true)) {
            var vin = "VIN-A"
            val response = CompletableDeferred<CloudCarSettings>()
            var request = 0
            val c = controller(gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String): CloudCarSettings =
                    if (++request == 1) response.await() else cloud().copy(configs = listOf(CloudBikeConfig("5", confName = "new")))
            }, vin = { vin })
            val load = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.refresh()) }
            vin = "VIN-B"; c.onVinChanged(vin)
            if (switchBack) { vin = "VIN-A"; c.onVinChanged(vin) }
            assertTrue(c.refresh())
            response.complete(cloud())
            load.join()
            assertEquals(vin, c.state.value.vin)
            assertEquals("new", item(c, "cloud:5").title)
            assertFalse(c.state.value.items.any { it.configId == "7" })
        }
    }

    @Test
    fun oldSequenceAndWrongVinCannotOverrideNewObservation() = runBlocking {
        val c = controller()
        assertTrue(c.refresh())
        observe(c, status(sound = 1, lock = 1, time = 3, power = 1, distance = 2, brace = 1), 20)
        val before = keys.associateWith { c.state.value.valueState(it).vehicleValue }
        observe(c, status(sound = 0, lock = 0, time = 0, power = 0, distance = 0), 19)
        observe(c, status(sound = 0, lock = 0, time = 0, power = 0, distance = 0), 30, vin = "VIN-B")
        for (id in keys) assertEquals(before[id], c.state.value.valueState(id).vehicleValue)
    }

    @Test
    fun refreshAndFailurePreserveDraftsAndDoNotOverwriteVehicleObservationWithCloud() = runBlocking {
        val gateway = FakeGateway(cloud())
        val c = controller(gateway)
        assertTrue(c.refresh())
        observe(c, status(sound = 0, lock = 1, time = 0, power = 1, distance = 0, brace = 1), 1)
        for (id in listOf("cloud:5", "cloud:7:time", "cloud:11:distance", "cloud:39:time")) {
            assertTrue(c.editDraft(id) { (it as CarSettingKind.Choice).copy(selectedIndex = 1) })
        }
        val drafts = keys.associateWith { c.state.value.valueState(it).draftValue }
        gateway.cloud = cloud(mapOf("bleApproachPowerOnEnable" to "0", "bleLeavePowerOffEnable" to "0"))
        assertTrue(c.refresh())
        gateway.fail = true
        assertFalse(c.refresh())
        for (id in keys) {
            assertEquals(drafts[id], c.state.value.valueState(id).draftValue)
            assertTrue((c.state.value.valueState(id).vehicleValue as VehicleSettingValue.Known).live)
        }
        assertTrue((observed(c, "cloud:11") as CarSettingKind.Toggle).checked)
    }

    @Test
    fun submittingAliasCannotBeDuplicatedOrInvalidatedByRefresh() = runBlocking {
        val receipt = CompletableDeferred<CarSettingWriteResult>()
        val writer = FakeWriter { receipt.await() }
        val c = controller(writer = writer)
        assertTrue(c.refresh())
        observe(c, status(lock = 1, time = 0, brace = 1), 1)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.update("cloud:7:time") { it } }
        assertFalse(c.refresh())
        assertFalse(c.update("cloud:39:time") { it })
        assertFalse(c.editDraft("cloud:39:time") { it })
        assertEquals(1, writer.writes.size)
        receipt.complete(CarSettingWriteResult(true, CarSettingWriteChannel.BLE))
        job.join()
        assertEquals(SettingWriteState.LOCAL_UPDATED, c.state.value.valueState("cloud:7:time").writeState)
    }

    @Test
    fun cancelledRefreshAndDraftCancellationNeverGenerateAWrite() = runBlocking {
        val writer = FakeWriter()
        var wait = false
        val c = controller(writer = writer, gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String): CloudCarSettings {
                if (wait) CompletableDeferred<Unit>().await()
                return cloud()
            }
        })
        assertTrue(c.refresh())
        assertTrue(c.editDraft("cloud:5") { (it as CarSettingKind.Choice).copy(selectedIndex = 1, valueKnown = true) })
        c.cancelDraft("cloud:5")
        assertFalse(c.submitDraft("cloud:5"))
        wait = true
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.refresh() }
        job.cancelAndJoin()
        assertFalse(c.state.value.loading)
        assertTrue(writer.writes.isEmpty())
    }
}
