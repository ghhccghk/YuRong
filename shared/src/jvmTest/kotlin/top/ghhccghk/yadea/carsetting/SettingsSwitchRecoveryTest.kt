package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.settingSwitchTarget
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** Exercises the same target callback as both native Switch branches, with in-memory writers. */
class SettingsSwitchRecoveryTest {
    private fun value(checked: Boolean) = CarSettingValueState(
        configuredValue = CarSettingKind.Toggle(checked),
    )

    @Test fun nativeSwitchTargetIsNotInvertedAgain() {
        for (current in listOf(false, true)) {
            assertEquals(!current, settingSwitchTarget(value(current), !current)!!.checked)
            assertEquals(current, (value(current).configuredValue as CarSettingKind.Toggle).checked)
        }
    }

    @Test fun unknownNeverUsesFalseFallbackToComputeTarget() {
        assertNull(CarSettingValueState().displayKind())
        assertNull(settingSwitchTarget(CarSettingValueState(), true))
        assertNull(settingSwitchTarget(CarSettingValueState(), false))
    }

    @Test fun localCacheCanDriveDirectSwitchWithoutCreatingObservation() {
        val state = CarSettingValueState(cachedValue = VehicleSettingValue.Known(
            CarSettingKind.Toggle(true), "A", 1, SettingValueSource.CACHED_TTP, live = false,
        ))
        assertFalse(settingSwitchTarget(state, false)!!.checked)
        assertEquals(VehicleSettingValue.Unknown, state.vehicleValue)
    }

    @Test fun submittedTargetCanBeReversedWithoutUsingStaleCloudValue() {
        val state = value(false).copy(submittedValue = CarSettingKind.Toggle(true),
            writeState = SettingWriteState.CLOUD_ACCEPTED,
            confirmationState = SettingConfirmationState.WAITING_READBACK)
        assertTrue((state.displayKind() as CarSettingKind.Toggle).checked)
        assertFalse(settingSwitchTarget(state, false)!!.checked)
        assertEquals(VehicleSettingValue.Unknown, state.vehicleValue)
    }

    @Test fun allCloudToggleShapesKeepTypeAndUseOneExplicitTarget() {
        val cloud = CloudCarSettings((1..95).map { CloudBikeConfig(it.toString()) } +
            listOf("001", "002", "003", "1005").map { CloudBikeConfig(it) }, emptyMap())
        for (item in CloudCarSettingMapper.toItems(cloud).filter { it.kind is CarSettingKind.Toggle }) {
            val state = value(true)
            val target = settingSwitchTarget(state, false)!!
            assertFalse(item.id, target.checked)
            assertTrue(item.id, item.copy(kind = target).kind is CarSettingKind.Toggle)
        }
    }

    @Test fun bothAdaptiveVariantsKeepTenthRoundCommandPolarity() {
        for ((id, parameter) in listOf("64" to 1, "89" to 2)) {
            val item = CloudCarSettingMapper.toItems(CloudCarSettings(listOf(CloudBikeConfig(id)), emptyMap()))
                .single { it.id == "cloud:$id:adaptive" }
            val on = VehicleSettingCommands.forItem(item.copy(kind = settingSwitchTarget(value(false), true)!!))!!
            val off = VehicleSettingCommands.forItem(item.copy(kind = settingSwitchTarget(value(true), false)!!))!!
            assertEquals(0xD0, on.commandId)
            assertEquals(parameter, on.parameter)
            assertEquals(0, off.parameter)
        }
    }

    @Test fun oneClickInvokesWriterOnceAndFailurePreservesNewObservation() = runBlocking {
        val response = CompletableDeferred<Boolean>()
        var calls = 0
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean {
                calls++
                return response.await()
            }
        }
        val cloud = CloudCarSettings(listOf(CloudBikeConfig("13"), CloudBikeConfig("19")), mapOf("alarmSoundEnable" to "0"))
        val c = CarSettingsController(writer = writer, gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = cloud
        }, vinProvider = { "A" }, debounceMs = 0)
        c.refresh()
        val target = settingSwitchTarget(c.state.value.valueState("cloud:13"), true)!!
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:13") { target }) }
        assertEquals(1, calls)
        assertFalse(c.update("cloud:13") { target })
        val status = TtpInfoParser.parse(ByteArray(256) { -1 }).let {
            it.copy(extended = it.extended.copy(automaticHeadlightFunctionStatus = 1))
        }
        c.updateVehicleStatus("A", status)
        response.complete(false)
        job.join()
        assertFalse((c.state.value.valueState("cloud:13").displayKind() as CarSettingKind.Toggle).checked)
        assertTrue((c.state.value.valueState("cloud:19").displayKind() as CarSettingKind.Toggle).checked)
        assertEquals(SettingWriteState.FAILED, c.state.value.valueState("cloud:13").writeState)
        assertEquals(1, calls)
    }
}
