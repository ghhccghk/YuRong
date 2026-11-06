package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
}

private class RecordingEmergencyContactGateway : EmergencyContactGateway {
    var saveCalls = 0

    override suspend fun load(vin: String): EmergencyContacts? = EmergencyContacts(vin = vin)

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