package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** No network transport or platform adapter is constructed. */
class BatterySettingsSafetyTest {
    private val ids = listOf("47", "48", "60", "62")
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })
    private fun cloud(values: Map<String, String> = emptyMap()) = CloudCarSettings(
        configs = ids.map { CloudBikeConfig(it) }, values = values,
    )
    private class FakeGateway(var data: CloudCarSettings) : CarSettingsGateway {
        val updates = mutableListOf<Pair<String, CloudCarSettingUpdate>>()
        var accepted = true
        override suspend fun load(vin: String) = data
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
            updates += vin to update
            return accepted
        }
    }
    private class FakeWriter(
        val action: suspend (CarSettingItem) -> CarSettingWriteResult = {
            CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
        },
    ) : CarSettingWriter {
        val writes = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem) = writeWithResult(item).success
        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            writes += item
            return action(item)
        }
    }
    private fun controller(g: CarSettingsGateway = FakeGateway(cloud()), w: CarSettingWriter = FakeWriter(),
                           vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == id }
    private fun observe(c: CarSettingsController, info: TtpInfo, seq: Long = 1,
                        vin: String = "VIN-A", origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) =
        c.updateVehicleObservation(VehicleStatusObservation(vin, info, origin, seq))
    private fun status(power: Int = -1, limit: Int = -1, heat: Int = -1, capacity: Int = -1,
                       enabled: Int = -1) = unknown.copy(extended = unknown.extended.copy(
        batteryChargingPowerValue = power, batteryChargingLimitValue = limit, seatHeatLevelStatus = heat,
        capacitySettingStatus = capacity, batteryChargingPowerStatus = enabled,
        capacitySwitchStatus = enabled, seatHeatSwitchStatus = enabled,
    ))
    private fun known(kind: CarSettingKind): Boolean = when (kind) {
        is CarSettingKind.Toggle -> kind.valueKnown
        is CarSettingKind.Choice -> kind.valueKnown
        is CarSettingKind.Slider -> kind.valueKnown
        else -> false
    }

    @Test fun absentValuesCannotBeSubmittedThroughControllerBuilderOrWriters() = runBlocking {
        val g = FakeGateway(cloud()); val w = FakeWriter(); val c = controller(g, w)
        assertTrue(c.refresh())
        val sink = mutableListOf<VehicleSettingCommand>()
        val ble = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sink += command; return true }
        })
        val writer = CloudCarSettingWriter(g, { "VIN-A" }, ble, { true })
        for (i in c.state.value.items.filter { BatterySettings.supports(it.configId, it.kind) }) {
            assertFalse(known(i.kind))
            assertNull(c.state.value.valueState(i.id).displayKind())
            assertNull(c.state.value.valueState(i.id).draftValue)
            assertFalse(c.update(i.id) { it })
            assertNull(VehicleSettingCommands.forItem(i))
            assertNull(CloudCarSettingUpdateBuilder.build(i.configId, i.kind, "VIN-A"))
            assertFalse(ble.write(i)); assertFalse(writer.write(i))
            assertTrue(VehicleSettingCommands.supportsItem(i))
            assertTrue(CloudCarSettingUpdateBuilder.supports(i.configId, i.kind))
        }
        assertTrue(w.writes.isEmpty()); assertTrue(g.updates.isEmpty()); assertTrue(sink.isEmpty())
    }

    @Test fun malformedCloudValuesNeverBecomeKnownOrClamped() = runBlocking {
        for (raw in listOf("", "null", "NaN", "Infinity", "-1", "9999", "1.5", "22.1")) {
            val c = controller(FakeGateway(cloud(mapOf("chargeWattVal" to raw, "chargeUlVal" to raw,
                "seatHeatingLevel" to raw, "capacity" to raw))))
            assertTrue(c.refresh())
            for (id in listOf("cloud:47", "cloud:48", "cloud:62:level", "cloud:60:level")) {
                assertFalse(known(item(c, id).kind))
                assertNull(c.state.value.valueState(id).configuredValue)
            }
        }
    }

    @Test fun exactConfiguredValuesAndZeroBasedHeatAreRetained() = runBlocking {
        for (level in 0..2) {
            val c = controller(FakeGateway(cloud(mapOf("seatHeatingLevel" to "$level", "chargeWattVal" to "351",
                "chargeUlVal" to "76", "capacity" to "22", "chargeWattSwitch" to "1"))))
            assertTrue(c.refresh())
            assertNull(c.state.value.valueState("cloud:62:level").configuredValue)
            assertEquals(351f, (c.state.value.valueState("cloud:47").configuredValue as CarSettingKind.Slider).value)
            assertEquals(76f, (c.state.value.valueState("cloud:48").configuredValue as CarSettingKind.Slider).value)
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:47").vehicleValue)
        }
    }

    @Test fun unverifiedCloudSeatHeatingFieldsNeverBecomeConfiguredValues() = runBlocking {
        val c = controller(FakeGateway(cloud(mapOf("seatHeating" to "1", "seatHeatingLevel" to "2"))))
        assertTrue(c.refresh())
        for (id in listOf("cloud:62", "cloud:62:level")) {
            assertNull(c.state.value.valueState(id).configuredValue)
            assertNull(c.state.value.valueState(id).displayKind())
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
        }
    }

    @Test fun unknownSeatHeatStateCannotBeEditedOrSubmittedUntilTtpReadback() = runBlocking {
        val writer = FakeWriter()
        val c = controller(w = writer)
        assertTrue(c.refresh())
        val toggle = item(c, "cloud:62")
        assertFalse(c.editDraft(toggle.id) { (it as CarSettingKind.Toggle).copy(checked = true, valueKnown = true) })
        assertFalse(c.update(toggle.id) { (it as CarSettingKind.Toggle).copy(checked = true, valueKnown = true) })
        assertTrue(writer.writes.isEmpty())

        observe(c, status(heat = 2, enabled = 1))
        assertTrue(c.update(toggle.id) { (it as CarSettingKind.Toggle).copy(checked = false, valueKnown = true) })
        assertEquals(false, (writer.writes.single().kind as CarSettingKind.Toggle).checked)
    }

    @Test fun everyCapacityAndHeatChoiceUsesItsExactRawParameter() {
        for ((index, value) in BatteryCapacityOptions.valuesAh.withIndex()) {
            val k = CarSettingKind.Choice(index, BatteryCapacityOptions.labels)
            assertEquals(value, VehicleSettingCommands.forConfigId("60", k)!!.parameter)
            val u = CloudCarSettingUpdateBuilder.build("60", k, "VIN-A")!!
            assertEquals(value, u.fields["value"]); assertEquals("BATT_V_A_CAP_MODEL", u.fields["id"])
        }
        for (index in 0..2) {
            val k = CarSettingKind.Choice(index, BatterySettings.heatOptions)
            assertEquals(index, VehicleSettingCommands.forConfigId("62", k)!!.parameter)
            val u = CloudCarSettingUpdateBuilder.build("62", k, "VIN-A")!!
            assertEquals(index, u.fields["seatHeatingLevel"]); assertEquals(index, u.fields["value"])
            assertFalse(u.fields.containsKey("seatHeating"))
        }
    }

    @Test fun slidersRejectFractionalNonFiniteOutOfRangeAndUnknownTargets() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (id in listOf("47", "48")) {
            val template = item(c, "cloud:$id").kind as CarSettingKind.Slider
            val invalid = listOf(-1f, 0f, 1001f, 350.5f, Float.NaN, Float.POSITIVE_INFINITY) +
                if (id == "47") listOf(199f, 351f) else listOf(70f, 74f, 101f)
            for (v in invalid) {
                val target = template.copy(value = v, valueKnown = true)
                assertFalse(c.update("cloud:$id") { target })
                assertNull(CloudCarSettingUpdateBuilder.build(id, target, "VIN-A"))
                assertNull(VehicleSettingCommands.forConfigId(id, target))
            }
            assertFalse(c.update("cloud:$id") { template })
        }
    }

    @Test fun invalidChoiceShapeIndexAndUnknownCannotReachAWriter() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (id in listOf("60", "62")) {
            val template = item(c, "cloud:$id:level").kind as CarSettingKind.Choice
            for (target in listOf(template, template.copy(selectedIndex = -1, valueKnown = true),
                template.copy(selectedIndex = 99, valueKnown = true),
                template.copy(selectedIndex = 0, options = listOf("invented"), valueKnown = true))) {
                assertFalse(c.update("cloud:$id:level") { target })
                assertNull(VehicleSettingCommands.forConfigId(id, target))
                assertNull(CloudCarSettingUpdateBuilder.build(id, target, "VIN-A"))
            }
        }
    }

    @Test fun splitIdsCannotBypassValidationOrSelectAnotherCommand() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (id in listOf("47", "48", "60", "62")) {
            val i = item(c, if (id in listOf("60", "62")) "cloud:$id:level" else "cloud:$id")
            val target = when (val k = i.kind) {
                is CarSettingKind.Choice -> k.copy(selectedIndex = 0, valueKnown = true)
                is CarSettingKind.Slider -> k.copy(value = if (id == "47") 350f else 80f, valueKnown = true)
                else -> error("type")
            }
            for (wrongId in listOf("cloud:60:enable", "cloud:73:config", "cloud:002:level", "cloud:$id:unknown")) {
                assertNull(VehicleSettingCommands.forItem(i.copy(id = wrongId, kind = target)))
            }
        }
    }

    @Test fun directCommandConstructionRejectsInvalidParametersBeforeEncoding() {
        for ((id, invalid) in mapOf(0xCA to listOf(-1, 199, 351, 1001), 0xCB to listOf(-1, 71, 101),
            0xCD to listOf(-1, 0, 14, 39), 0xD3 to listOf(-1, 3, 255),
            0xC9 to listOf(-1, 2), 0xD2 to listOf(-1, 2), 0xD4 to listOf(-1, 2))) {
            for (p in invalid) {
                try { VehicleSettingCommand("direct", id, p); fail("command $id accepted $p") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test fun cloudAndBleWritesNeverSupplyUnknownSiblingFields() = runBlocking {
        val g = FakeGateway(cloud()); val commands = mutableListOf<VehicleSettingCommand>()
        val ble = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
        })
        val c = controller(g, CloudCarSettingWriter(g, { "VIN-A" }, ble, { false })); assertTrue(c.refresh())
        assertFalse(c.update("cloud:47") { (it as CarSettingKind.Slider).copy(value = 400f, valueKnown = true) })
        assertEquals("CHARGE_WATT_VAL", g.updates.single().second.fields["id"])
        assertFalse(g.updates.single().second.fields.containsKey("chargeWattSwitch"))
        assertFalse(c.update("cloud:62:level") { (it as CarSettingKind.Choice).copy(selectedIndex = 0, valueKnown = true) })
        assertFalse(g.updates.last().second.fields.containsKey("seatHeating"))
        val i = item(c, "cloud:62").copy(kind = CarSettingKind.Toggle(true))
        assertTrue(ble.write(i)); assertEquals(0xD2, commands.single().commandId)
        assertEquals(1, commands.single().parameter)
    }

    @Test fun nonFivePercentCloudValueIsNotSilentlyChangedForBle() = runBlocking {
        val g = FakeGateway(cloud()); val commands = mutableListOf<VehicleSettingCommand>()
        val ble = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
        })
        val writer = CloudCarSettingWriter(g, { "VIN-A" }, ble, { true })
        val c = controller(g); assertTrue(c.refresh())
        val i = item(c, "cloud:48").let { it.copy(kind = (it.kind as CarSettingKind.Slider).copy(value = 76f, valueKnown = true)) }
        val result = writer.writeWithResult(i)
        assertTrue(result.success); assertEquals(CarSettingWriteChannel.CLOUD, result.channel)
        assertTrue(commands.isEmpty()); assertEquals(76, g.updates.single().second.fields["value"])
        assertTrue(writer.write(i.copy(kind = (i.kind as CarSettingKind.Slider).copy(value = 80f))))
        assertEquals(80, commands.single().parameter)
    }

    @Test fun freshTtpFillsExactPowerHeatCapacityAndOriginalLimitMapping() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (raw in 0..6) {
            observe(c, status(power = 352, limit = raw, heat = raw % 3, capacity = 22, enabled = 1), raw + 1L)
            fun value(id: String) = (c.state.value.valueState(id).vehicleValue as VehicleSettingValue.Known).kind
            assertEquals(352f, (value("cloud:47") as CarSettingKind.Slider).value)
            assertEquals(if (raw <= 1) 75f else (70 + raw * 5).toFloat(), (value("cloud:48") as CarSettingKind.Slider).value)
            assertEquals(raw % 3, (value("cloud:62:level") as CarSettingKind.Choice).selectedIndex)
            assertEquals(2, (value("cloud:60:level") as CarSettingKind.Choice).selectedIndex)
        }
    }

    @Test fun invalidTtpIsNeverLiveOrAValidConfirmation() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        observe(c, status(power = 350, limit = 6, heat = 2, capacity = 22, enabled = 1))
        observe(c, status(power = 1001, limit = 7, heat = 3, capacity = 14, enabled = 2), 2)
        for (i in c.state.value.items.filter { BatterySettings.supports(it.configId, it.kind) }) {
            val s = c.state.value.valueState(i.id)
            assertFalse((s.vehicleValue as VehicleSettingValue.Known).live)
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, s.readbackState)
        }
    }

    @Test fun cachedOldAndWrongVinObservationsDoNotReplaceLiveState() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        observe(c, status(power = 350, limit = 6, heat = 2), 10, origin = VehicleStatusOrigin.CACHED_TTP)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:47").vehicleValue)
        assertEquals(SettingValueSource.CACHED_TTP, c.state.value.valueState("cloud:47").cachedValue!!.source)
        observe(c, status(power = 400, limit = 5, heat = 1), 11)
        val before = c.state.value.valueStates
        observe(c, status(power = 500, limit = 4, heat = 0), 9)
        observe(c, status(power = 500), 12, vin = "VIN-B")
        assertEquals(before, c.state.value.valueStates)
    }

    @Test fun cloudRefreshPreservesDraftAndSources() = runBlocking {
        val g = FakeGateway(cloud(mapOf("chargeWattVal" to "350"))); val c = controller(g)
        assertTrue(c.refresh()); observe(c, status(power = 400))
        assertTrue(c.editDraft("cloud:47") { (it as CarSettingKind.Slider).copy(value = 500f, valueKnown = true) })
        g.data = cloud(mapOf("chargeWattVal" to "600")); assertTrue(c.refresh())
        val s = c.state.value.valueState("cloud:47")
        assertEquals(500f, (s.draftValue as CarSettingKind.Slider).value)
        assertEquals(600f, (s.configuredValue as CarSettingKind.Slider).value)
        assertEquals(400f, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
    }

    @Test fun failureAndCancellationNeverReplaceVehicleValue() = runBlocking {
        for (cancel in listOf(false, true)) {
            val c = controller(w = FakeWriter { if (cancel) throw CancellationException("cancel") else CarSettingWriteResult(false) })
            assertTrue(c.refresh()); observe(c, status(power = 350))
            try {
                assertFalse(c.update("cloud:47") { (it as CarSettingKind.Slider).copy(value = 500f) })
                assertFalse(cancel)
            } catch (_: CancellationException) { assertTrue(cancel) }
            val s = c.state.value.valueState("cloud:47")
            assertEquals(350f, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
            assertNull(s.submittedValue); assertNotEquals(SettingConfirmationState.READBACK_MATCHED, s.confirmationState)
        }
    }

    @Test fun writerAcceptanceNeedsNewerMatchingVehicleReadback() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, status(power = 350), 10)
        assertTrue(c.update("cloud:47") { (it as CarSettingKind.Slider).copy(value = 500f) })
        assertEquals(SettingWriteState.LOCAL_UPDATED, c.state.value.valueState("cloud:47").writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:47").confirmationState)
        observe(c, status(power = 500), 9)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:47").confirmationState)
        observe(c, status(power = 500), 11)
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:47").confirmationState)
    }

    @Test fun vinChangeDropsOldDraftAndLateReceipt() = runBlocking {
        var vin = "VIN-A"; val receipt = CompletableDeferred<CarSettingWriteResult>()
        val c = controller(w = FakeWriter { receipt.await() }, vin = { vin })
        assertTrue(c.refresh()); observe(c, status(power = 350))
        assertTrue(c.editDraft("cloud:47") { (it as CarSettingKind.Slider).copy(value = 500f) })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.submitDraft("cloud:47")) }
        assertFalse(c.refresh())
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh())
        assertFalse(c.submitDraft("cloud:47"))
        receipt.complete(CarSettingWriteResult(true, CarSettingWriteChannel.BLE)); job.join()
        val s = c.state.value.valueState("cloud:47")
        assertNull(s.draftValue); assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        assertEquals(SettingWriteState.IDLE, s.writeState)
    }
}
