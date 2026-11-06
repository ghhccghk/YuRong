package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EmergencyContactValidationTest {

    @Test
    fun validationMatchesContactPopupRules() {
        assertEquals(
            "请输入您的姓名",
            emergencyContactValidationError(
                ContactType.Owner,
                name = "",
                mobile = "13800138000",
                otherMobile = "13900139000",
            ),
        )
        assertEquals(
            "请输入紧急联系人的姓名",
            emergencyContactValidationError(
                ContactType.Emergency,
                name = "",
                mobile = "13800138000",
                otherMobile = "13900139000",
            ),
        )
        assertEquals(
            "请输入手机号",
            emergencyContactValidationError(
                ContactType.Owner,
                name = "车主",
                mobile = "",
                otherMobile = "13900139000",
            ),
        )
        assertEquals(
            "请输入正确的手机号",
            emergencyContactValidationError(
                ContactType.Owner,
                name = "车主",
                mobile = "23800138000",
                otherMobile = "13900139000",
            ),
        )
        assertEquals(
            "请输入正确的手机号",
            emergencyContactValidationError(
                ContactType.Emergency,
                name = "联系人",
                mobile = "1380013800",
                otherMobile = "13900139000",
            ),
        )
        assertEquals(
            "手机号不能与紧急联系人手机号相同",
            emergencyContactValidationError(
                ContactType.Owner,
                name = "车主",
                mobile = "13900139000",
                otherMobile = "13900139000",
            ),
        )
        assertEquals(
            "手机号不能与车主手机号相同",
            emergencyContactValidationError(
                ContactType.Emergency,
                name = "联系人",
                mobile = "13800138000",
                otherMobile = "13800138000",
            ),
        )
        assertNull(
            emergencyContactValidationError(
                ContactType.Owner,
                name = "车主",
                mobile = "13800138000",
                otherMobile = "13900139000",
            ),
        )
    }

    @Test
    fun controllerRejectsInvalidContactBeforeGatewayCall() {
        val gateway = RecordingEmergencyContactGateway()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = EmergencyContactController(
            gateway = gateway,
            vinProvider = { "VIN123" },
            scope = scope,
        )

        controller.save(
            type = ContactType.Owner,
            name = "车主",
            mobile = "23800138000",
        )

        assertEquals("请输入正确的手机号", controller.state.value.message)
        assertFalse(controller.state.value.success)
        assertEquals(0, gateway.saveCalls)
        controller.dispose()
        scope.cancel()
    }

    @Test
    fun loadUsesOwnerMobileFallbackOnlyWhenCloudValueIsEmpty() = runBlocking {
        val gateway = RecordingEmergencyContactGateway().apply {
            contacts = EmergencyContacts(vin = "VIN123", ownerMobile = "")
        }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = EmergencyContactController(
            gateway = gateway,
            vinProvider = { "VIN123" },
            ownerMobileFallbackProvider = { "13800138000" },
            scope = scope,
        )

        controller.load()
        val state = withTimeout(5_000) {
            controller.state.first { it.contacts != null }
        }

        assertEquals("13800138000", state.contacts?.ownerMobile)
        controller.dispose()
        scope.cancel()
    }

    @Test
    fun loadKeepsCloudOwnerMobileOverFallback() = runBlocking {
        val gateway = RecordingEmergencyContactGateway().apply {
            contacts = EmergencyContacts(vin = "VIN123", ownerMobile = "13900139000")
        }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controller = EmergencyContactController(
            gateway = gateway,
            vinProvider = { "VIN123" },
            ownerMobileFallbackProvider = { "13800138000" },
            scope = scope,
        )

        controller.load()
        val state = withTimeout(5_000) {
            controller.state.first { it.contacts != null }
        }

        assertEquals("13900139000", state.contacts?.ownerMobile)
        controller.dispose()
        scope.cancel()
    }

    @Test
    fun contactNameInputMatchesConfirmedEmojiRanges() {
        assertEquals(
            "车主",
            sanitizeContactNameInput(
                "车主😀\u2600\uFE0F🤖🚀\u2700\uD83C\uDF00\uD83E\uDD10\uD83E\uDE70",
            ),
        )
        assertEquals(
            "\uD83C\uDEFF\uD83D\uDE50\uD83D\uDF00\u27C0\uFE10\uD83E\uDE00\uD83E\uDF00",
            sanitizeContactNameInput(
                "\uD83C\uDEFF\uD83C\uDF00\uD83D\uDFFF\uD83D\uDE00\uD83D\uDE4F\uD83D\uDE50" +
                    "\uD83D\uDE80\uD83D\uDEFF\uD83D\uDF00\u2600\u27BF\u27C0" +
                    "\uFE00\uFE0F\uFE10\uD83E\uDD00\uD83E\uDDFF\uD83E\uDE00" +
                    "\uD83E\uDE70\uD83E\uDEFF\uD83E\uDF00",
            ),
        )
    }

    @Test
    fun contactNameInputLimitsUtf16LengthToThirty() {
        val input = "12345678901234567890123456789012345"

        assertEquals(input.take(30), sanitizeContactNameInput(input))
    }

}

private class RecordingEmergencyContactGateway : EmergencyContactGateway {
    var saveCalls = 0

    var contacts: EmergencyContacts? = null

    override suspend fun load(vin: String): EmergencyContacts? =
        contacts ?: EmergencyContacts(vin = vin)

    override suspend fun save(
        vin: String,
        type: ContactType,
        name: String,
        mobile: String,
    ): Boolean {
        saveCalls += 1
        return true
    }
}
