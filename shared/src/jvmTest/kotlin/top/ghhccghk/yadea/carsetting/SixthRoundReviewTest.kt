package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.PasswordEnableState
import top.ghhccghk.yadea.vehicle.PasswordFlow
import top.ghhccghk.yadea.vehicle.PasswordRequestResult
import top.ghhccghk.yadea.vehicle.PasswordSmsVerifyResult
import top.ghhccghk.yadea.vehicle.PasswordVerifyResult
import top.ghhccghk.yadea.vehicle.PasswordWriteState
import top.ghhccghk.yadea.vehicle.VehiclePasswordController
import top.ghhccghk.yadea.vehicle.VehiclePasswordGateway

class SixthRoundReviewTest {

    private class FakePasswordGateway : VehiclePasswordGateway {
        override suspend fun getEnableState(vin: String) = PasswordEnableState(status = 1, hasPassword = true)
        override suspend fun exists(vin: String) = true
        override suspend fun set(vin: String, password: String) = PasswordRequestResult(true)
        override suspend fun reset(vin: String, password: String, validCode: String) = PasswordRequestResult(true)
        override suspend fun verify(vin: String, password: String) = PasswordVerifyResult(true)
        override suspend fun sendSmsCode(vin: String, mobile: String) = PasswordRequestResult(true)
        override suspend fun verifySmsCode(vin: String, mobile: String, smsCode: String) =
            PasswordSmsVerifyResult(true, validCode = "valid")
        override suspend fun updateEnable(vin: String, enabled: Boolean) = PasswordRequestResult(true)
    }

    private class RecordingSink : VehicleSettingCommandSink {
        var sent = false
        override suspend fun send(command: VehicleSettingCommand): Boolean {
            sent = true
            return true
        }
    }

    @Test
    fun passwordStatesSeparateCloudBleAndVehicleConfirmation(): Unit = runBlocking {
        val sink = RecordingSink()
        val controller = VehiclePasswordController(
            gateway = FakePasswordGateway(),
            vinProvider = { "VIN-A" },
            mobileProvider = { "13800138000" },
            commandSink = sink,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        controller.onVinChanged("VIN-A")
        controller.begin(PasswordFlow.Set)
        controller.submitPassword("1357")

        val accepted = controller.state.value
        assertTrue(accepted.cloudAccepted)
        assertTrue(accepted.bleSent)
        assertEquals(PasswordWriteState.LOCAL_UPDATED, accepted.writeState)

        controller.onVehiclePasswordResult(0)
        assertEquals(PasswordWriteState.VEHICLE_CONFIRMED, controller.state.value.writeState)
    }

    @Test
    fun passwordVinChangeClearsOldState(): Unit = runBlocking {
        var vin = "VIN-A"
        val controller = VehiclePasswordController(
            gateway = FakePasswordGateway(),
            vinProvider = { vin },
            mobileProvider = { "13800138000" },
            commandSink = RecordingSink(),
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        controller.onVinChanged(vin)
        controller.begin(PasswordFlow.Forget)
        controller.verifySmsCode("123456")

        vin = "VIN-B"
        controller.onVinChanged(vin)
        assertEquals(null, controller.state.value.flow)
        assertFalse(controller.state.value.smsVerified)
        assertEquals(PasswordWriteState.UNKNOWN, controller.state.value.writeState)
    }

    @Test
    fun atmosphereLocalSelectionDoesNotClaimVehicleReadback(): Unit = runBlocking {
        val item = CarSettingItem(
            id = "cloud:73:config",
            configId = "73",
            title = "",
            summary = "",
            classification = CarSettingClassification.LIGHT,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Atmosphere(),
        )
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = object : CarSettingWriter {
                override suspend fun write(item: CarSettingItem) = true
                override suspend fun writeWithResult(item: CarSettingItem) =
                    CarSettingWriteResult(true, CarSettingWriteChannel.BLE, CarSettingConfirmationStatus.LOCAL_UPDATED)
            },
            vinProvider = { "VIN" },
            debounceMs = 0L,
        )
        controller.update(item.id) {
            CarSettingKind.Atmosphere(showMode = 2, valueKnown = false)
        }
        val saved = controller.state.value.items.single().kind as CarSettingKind.Atmosphere
        assertFalse(saved.valueKnown)
    }

    @Test
    fun missingX1ConfigsAreReadOnly() {
        val missing = CloudCarSettingMapper.toMissingCloudItems(
            CloudCarSettings(configs = emptyList(), values = emptyMap()),
        ).filter { it.configId in setOf("001", "002", "003") }
        assertTrue(missing.isNotEmpty())
        assertTrue(missing.none { it.writable })
    }
}
