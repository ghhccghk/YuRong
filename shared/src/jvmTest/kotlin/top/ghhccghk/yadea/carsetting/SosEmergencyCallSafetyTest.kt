package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.EmergencyContacts
import top.ghhccghk.yadea.vehicle.ContactType
import top.ghhccghk.yadea.vehicle.EmergencyContactController
import top.ghhccghk.yadea.vehicle.EmergencyContactGateway
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
    fun currentVinContactEvidenceAllowsEnableAndMissingContactsBlockDisable() = runBlocking {
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

        val disableWriter = FakeWriter()
        val disableController = CarSettingsController(
            initialItems = listOf(item.copy(kind = CarSettingKind.Toggle(true))),
            writer = disableWriter,
            vinProvider = { "VIN-A" },
            debounceMs = 0L,
            statusPollAttempts = 0,
            sosContactReadyProvider = { false },
        )
        assertFalse(disableController.update("cloud:91") { CarSettingKind.Toggle(false) })
        assertTrue(disableWriter.writes.isEmpty())
        assertEquals(true, (disableController.state.value.items.single().kind as CarSettingKind.Toggle).checked)
        assertEquals("请先补齐当前车辆的车主和紧急联系人信息", disableController.state.value.message)

        assertTrue(controller.update("cloud:91") { CarSettingKind.Toggle(false) })
        assertEquals(listOf(true, false), writer.writes.map { (it.kind as CarSettingKind.Toggle).checked })
    }

    @Test
    fun loadingSharedContactControllerEnablesSosOnlyForItsCurrentVin() = runBlocking {
        var vin = "VIN-A"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gateway = object : EmergencyContactGateway {
            override suspend fun load(vin: String) = contacts(vin = vin)
            override suspend fun save(vin: String, type: ContactType, name: String, mobile: String) = false
        }
        val contactController = EmergencyContactController(gateway, { vin }, scope = scope)
        val writer = FakeWriter()
        val controller = CarSettingsController(
            initialItems = listOf(CarSettingItem(
                id = "cloud:91", configId = "91", title = "SOS", summary = "",
                classification = CarSettingClassification.SECURITY,
                channel = CarSettingChannel.IOT, kind = CarSettingKind.Toggle(false),
            )),
            writer = writer, vinProvider = { vin }, debounceMs = 0L, statusPollAttempts = 0,
            sosContactReadyProvider = { currentVin ->
                hasCompleteSosContactInfo(contactController.state.value.contacts, currentVin)
            },
        )
        try {
            assertFalse(controller.update("cloud:91") { CarSettingKind.Toggle(true) })
            assertTrue(writer.writes.isEmpty())

            contactController.load()
            assertTrue(controller.update("cloud:91") { CarSettingKind.Toggle(true) })
            assertEquals(1, writer.writes.size)
            assertEquals("VIN-A", contactController.state.value.contacts?.vin)

            vin = "VIN-B"
            assertFalse(hasCompleteSosContactInfo(contactController.state.value.contacts, vin))
            assertFalse(controller.update("cloud:91") { CarSettingKind.Toggle(true) })
            assertEquals(1, writer.writes.size)
        } finally {
            contactController.dispose()
            scope.cancel()
        }
    }
}
