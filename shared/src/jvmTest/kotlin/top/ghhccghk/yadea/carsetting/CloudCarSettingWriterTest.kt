package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCarSettingWriterTest {

    private class FakeGateway(
        private val updateResult: Boolean,
    ) : CarSettingsGateway {
        var updateCalls = 0
        override suspend fun load(vin: String): CloudCarSettings? = null
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
            updateCalls += 1
            return updateResult
        }
    }

    private class RecordingWriter(
        private val result: Boolean,
    ) : CarSettingWriter {
        var calls = 0
        override suspend fun write(item: CarSettingItem): Boolean =
            writeWithResult(item).success

        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            calls += 1
            return CarSettingWriteResult(
                success = result,
                channel = CarSettingWriteChannel.BLE,
            )
        }
    }

    private val alarmItem = CarSettingItem(
        id = "cloud:9",
        configId = "9",
        title = "信息提醒",
        summary = "",
        classification = CarSettingClassification.SECURITY,
        channel = CarSettingChannel.BOTH,
        kind = CarSettingKind.LowPowerInfo(true, 20, CarSettingKind.LowPowerInfoEdit.ALARM_SWITCH),
    )

    @Test
    fun bleConnectedWritesBleWithoutCloud() = runBlocking {
        val ble = RecordingWriter(true)
        val gateway = FakeGateway(updateResult = true)
        val writer = CloudCarSettingWriter(
            gateway = gateway,
            vinProvider = { "VIN" },
            bleFallback = ble,
            bleAvailableProvider = { true },
        )

        val result = writer.writeWithResult(alarmItem)
        assertTrue(result.success)
        assertEquals(CarSettingWriteChannel.BLE, result.channel)
        assertEquals(CarSettingConfirmationStatus.LOCAL_UPDATED, result.confirmationStatus)
        assertEquals(1, ble.calls)
        assertEquals(0, gateway.updateCalls)
    }

    @Test
    fun bleDisconnectedUsesCloud() = runBlocking {
        val ble = RecordingWriter(false)
        val gateway = FakeGateway(updateResult = true)
        val writer = CloudCarSettingWriter(
            gateway = gateway,
            vinProvider = { "VIN" },
            bleFallback = ble,
            bleAvailableProvider = { false },
        )

        val result = writer.writeWithResult(alarmItem)
        assertTrue(result.success)
        assertEquals(CarSettingWriteChannel.CLOUD, result.channel)
        assertEquals(CarSettingConfirmationStatus.CLOUD_ACCEPTED, result.confirmationStatus)
        assertEquals(0, ble.calls)
        assertEquals(1, gateway.updateCalls)
    }

    @Test
    fun bleFailureFallsBackToCloud() = runBlocking {
        val ble = RecordingWriter(false)
        val gateway = FakeGateway(updateResult = true)
        val writer = CloudCarSettingWriter(
            gateway = gateway,
            vinProvider = { "VIN" },
            bleFallback = ble,
            bleAvailableProvider = { true },
        )

        val result = writer.writeWithResult(alarmItem)
        assertTrue(result.success)
        assertEquals(CarSettingWriteChannel.CLOUD, result.channel)
        assertEquals(CarSettingConfirmationStatus.CLOUD_ACCEPTED, result.confirmationStatus)
        assertEquals(1, ble.calls)
        assertEquals(1, gateway.updateCalls)
    }

    @Test
    fun bleAndCloudFailReturnsFalse() = runBlocking {
        val ble = RecordingWriter(false)
        val gateway = FakeGateway(updateResult = false)
        val writer = CloudCarSettingWriter(
            gateway = gateway,
            vinProvider = { "VIN" },
            bleFallback = ble,
            bleAvailableProvider = { true },
        )

        val result = writer.writeWithResult(alarmItem)
        assertFalse(result.success)
        assertEquals(CarSettingConfirmationStatus.FAILED, result.confirmationStatus)
        assertEquals(1, ble.calls)
        assertEquals(1, gateway.updateCalls)
    }
}
