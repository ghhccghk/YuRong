package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSettingsStateIsolationTest {

    @Test
    fun refreshClearsPreviousVinBeforeLoading() = runBlocking {
        var currentVin = "VIN-A"
        val controller = CarSettingsController(
            initialItems = listOf(item("cloud:2")),
            writer = null,
            gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String): CloudCarSettings? = null
            },
            vinProvider = { currentVin },
        )

        currentVin = "VIN-B"
        assertFalse(controller.refresh())

        val state = controller.state.value
        assertEquals("VIN-B", state.vin)
        assertTrue(state.items.isEmpty())
        assertTrue(state.missingCloudItems.isEmpty())
        assertFalse(state.loadedFromCloud)
    }

    @Test
    fun staleVinResponseCannotOverwriteCurrentVin() = runBlocking {
        val oldResponse = CompletableDeferred<CloudCarSettings>()
        var currentVin = "VIN-A"
        val controller = CarSettingsController(
            initialItems = listOf(item("cloud:2")),
            writer = null,
            gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String): CloudCarSettings? = when (vin) {
                    "VIN-A" -> oldResponse.await()
                    else -> CloudCarSettings(
                        configs = listOf(
                            CloudBikeConfig(confId = "31", confName = "速度单位"),
                        ),
                        values = emptyMap(),
                    )
                }
            },
            vinProvider = { currentVin },
        )

        var oldResult: Boolean? = null
        val oldRefresh = launch(start = CoroutineStart.UNDISPATCHED) {
            oldResult = controller.refresh()
        }
        currentVin = "VIN-B"
        assertTrue(controller.refresh())
        oldResponse.complete(
            CloudCarSettings(
                configs = listOf(
                    CloudBikeConfig(confId = "2", confName = "旧车设置"),
                ),
                values = emptyMap(),
            ),
        )
        oldRefresh.join()

        val state = controller.state.value
        assertEquals(false, oldResult)
        assertEquals("VIN-B", state.vin)
        assertEquals(listOf("cloud:31"), state.items.map { it.id })
    }

    @Test
    fun failedRefreshDoesNotKeepPreviousVinItems() = runBlocking {
        var currentVin = "VIN-A"
        val controller = CarSettingsController(
            initialItems = listOf(item("cloud:2")),
            writer = null,
            gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String): CloudCarSettings? = null
            },
            vinProvider = { currentVin },
        )

        currentVin = "VIN-B"
        assertFalse(controller.refresh())

        assertEquals("VIN-B", controller.state.value.vin)
        assertTrue(controller.state.value.items.isEmpty())
    }

    private fun item(id: String) = CarSettingItem(
        id = id,
        title = id,
        summary = "",
        classification = CarSettingClassification.SECURITY,
        channel = CarSettingChannel.BLE,
        kind = CarSettingKind.Toggle(false),
    )
}
