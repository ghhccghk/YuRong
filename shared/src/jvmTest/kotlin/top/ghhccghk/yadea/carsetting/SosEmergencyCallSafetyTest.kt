package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.EmergencyContacts
import top.ghhccghk.yadea.vehicle.hasCompleteSosContactInfo

class SosEmergencyCallSafetyTest {
    private class FakeWriter : CarSettingWriter {
        val writes = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean {
            writes += item
            return true
        }
    }

    private fun contacts(
        vin: String = "VIN-A",
        ownerName: String = "Owner",
        ownerMobile: String = "13800000000",
        emergencyName: String = "Contact",
        emergencyMobile: String = "13900000000",
    ) = EmergencyContacts(vin, ownerName, ownerMobile, emergencyName, emergencyMobile)

    @Test
    fun sosRequiresCompleteContactsForTheSameVin() {
        assertTrue(hasCompleteSosContactInfo(contacts(), " VIN-A "))
        assertFalse(hasCompleteSosContactInfo(null, "VIN-A"))
        assertFalse(hasCompleteSosContactInfo(contacts(vin = "VIN-B"), "VIN-A"))
        assertFalse(hasCompleteSosContactInfo(contacts(ownerName = " "), "VIN-A"))
        assertFalse(hasCompleteSosContactInfo(contacts(ownerMobile = ""), "VIN-A"))
        assertFalse(hasCompleteSosContactInfo(contacts(emergencyName = ""), "VIN-A"))
        assertFalse(hasCompleteSosContactInfo(contacts(emergencyMobile = " "), "VIN-A"))
    }

    @Test
    fun directControllerEnableIsBlockedWithoutCurrentVinContacts() = runBlocking {
        val writer = FakeWriter()
        val controller = CarSettingsController(
            initialItems = listOf(
                CarSettingItem(
                    id = "cloud:91",
                    configId = "91",
                    title = "SOS",
                    summary = "",
                    classification = CarSettingClassification.SECURITY,
                    channel = CarSettingChannel.IOT,
                    kind = CarSettingKind.Toggle(false),
                ),
            ),
            writer = writer,
            vinProvider = { "VIN-A" },
            debounceMs = 0L,
            statusPollAttempts = 0,
            sosContactReadyProvider = { false },
        )

        assertFalse(controller.update("cloud:91") { CarSettingKind.Toggle(true) })
        assertTrue(writer.writes.isEmpty())
        assertEquals("请先补齐当前车辆的车主和紧急联系人信息", controller.state.value.message)
    }

    @Test
    fun currentVinContactEvidenceAllowsEnableAndMissingContactsDoNotBlockDisable() = runBlocking {
        val writer = FakeWriter()
        val item = CarSettingItem(
            id = "cloud:91",
            configId = "91",
            title = "SOS",
            summary = "",
            classification = CarSettingClassification.SECURITY,
            channel = CarSettingChannel.IOT,
            kind = CarSettingKind.Toggle(false),
        )
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = writer,
            vinProvider = { "VIN-A" },
            debounceMs = 0L,
            statusPollAttempts = 0,
            sosContactReadyProvider = { vin -> hasCompleteSosContactInfo(contacts(), vin) },
        )

        assertTrue(controller.update("cloud:91") { CarSettingKind.Toggle(true) })
        assertEquals(true, (writer.writes.single().kind as CarSettingKind.Toggle).checked)

        val disableController = CarSettingsController(
            initialItems = listOf(item.copy(kind = CarSettingKind.Toggle(true))),
            writer = FakeWriter(),
            vinProvider = { "VIN-A" },
            debounceMs = 0L,
            statusPollAttempts = 0,
            sosContactReadyProvider = { false },
        )
        assertTrue(disableController.update("cloud:91") { CarSettingKind.Toggle(false) })
    }
}
