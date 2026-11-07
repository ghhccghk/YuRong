package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSettingWriterAccessTest {

    @Test
    fun mappedWriterRejectsReadOnlyItemBeforeSink() = runBlocking {
        val sink = RecordingSink()
        val writer = MappedCarSettingWriter(sink)

        val result = writer.writeWithResult(
            CarSettingItem(
                id = "cloud:2",
                configId = "2",
                title = "边撑感应",
                summary = "",
                classification = CarSettingClassification.POWER,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(true),
                writable = false,
            ),
        )

        assertFalse(result.success)
        assertEquals(0, sink.calls)
    }

    @Test
    fun cloudWriterRejectsReadOnlyItemBeforeBleOrCloud() = runBlocking {
        val gateway = RecordingGateway()
        val ble = RecordingWriter()
        val writer = CloudCarSettingWriter(
            gateway = gateway,
            vinProvider = { "VIN" },
            bleFallback = ble,
            bleAvailableProvider = { true },
        )

        val result = writer.writeWithResult(
            CarSettingItem(
                id = "cloud:9",
                configId = "9",
                title = "信息提醒",
                summary = "",
                classification = CarSettingClassification.SECURITY,
                channel = CarSettingChannel.BOTH,
                kind = CarSettingKind.Toggle(true),
                writable = false,
            ),
        )

        assertFalse(result.success)
        assertEquals(0, ble.calls)
        assertEquals(0, gateway.updateCalls)
    }


    @Test
    fun splitEntryRequiresStableIdAndConfirmedWriteSupport() {
        val unknownSplit = CarSettingItem(
            id = "cloud:47:unknown",
            configId = "47",
            title = "充电功率",
            summary = "",
            classification = CarSettingClassification.BATTERY,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(true),
        )
        val confirmedSplit = unknownSplit.copy(id = "cloud:47:enable")
        val unsupported = unknownSplit.copy(
            id = "cloud:2:unknown",
            configId = "2",
            kind = CarSettingKind.Info("只读"),
        )

        assertFalse(CarSettingItemAccess.canWrite(unknownSplit))
        assertTrue(CarSettingItemAccess.canWrite(confirmedSplit))
        assertFalse(CarSettingItemAccess.canWrite(unsupported))
    }

    private class RecordingSink : VehicleSettingCommandSink {
        var calls = 0
        override suspend fun send(command: VehicleSettingCommand): Boolean {
            calls += 1
            return true
        }
    }

    private class RecordingWriter : CarSettingWriter {
        var calls = 0
        override suspend fun write(item: CarSettingItem): Boolean =
            writeWithResult(item).success

        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            calls += 1
            return CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
        }
    }

    private class RecordingGateway : CarSettingsGateway {
        var updateCalls = 0
        override suspend fun load(vin: String): CloudCarSettings? = null
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
            updateCalls += 1
            return true
        }
    }
}
