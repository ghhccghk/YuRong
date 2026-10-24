package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleControlControllerTest {

    @Test
    fun cloudLockRequiresOnlineVehicleAndUsesVin() {
        val calls = mutableListOf<Pair<String, VehicleControlCommand>>()
        val controller = VehicleControlController(
            sink = null,
            connectedProvider = { false },
            scope = CoroutineScope(Dispatchers.Unconfined),
            cloudSink = object : VehicleCloudControlSink {
                override suspend fun send(
                    vin: String,
                    command: VehicleControlCommand,
                ): VehicleCloudControlResult {
                    calls += vin to command
                    return VehicleCloudControlResult(true)
                }
            },
            cloudOnlineProvider = { true },
            vinProvider = { "VIN1" },
        )

        controller.send(VehicleControlCommand.LOCK, VehicleControlChannel.CLOUD)

        assertEquals(listOf("VIN1" to VehicleControlCommand.LOCK), calls)
        assertTrue(controller.state.value.message.orEmpty().contains("车联网"))
    }

    @Test
    fun cloudRejectsUnsupportedCommandAndOfflineVehicle() {
        var calls = 0
        val controller = VehicleControlController(
            sink = null,
            connectedProvider = { false },
            scope = CoroutineScope(Dispatchers.Unconfined),
            cloudSink = object : VehicleCloudControlSink {
                override suspend fun send(
                    vin: String,
                    command: VehicleControlCommand,
                ): VehicleCloudControlResult {
                    calls += 1
                    return VehicleCloudControlResult(true)
                }
            },
            cloudOnlineProvider = { false },
            vinProvider = { "VIN1" },
        )

        controller.send(VehicleControlCommand.POWER_ON, VehicleControlChannel.CLOUD)
        controller.send(VehicleControlCommand.LOCK, VehicleControlChannel.CLOUD)

        assertEquals(0, calls)
        assertTrue(controller.state.value.message.orEmpty().contains("不在线"))
    }
}
