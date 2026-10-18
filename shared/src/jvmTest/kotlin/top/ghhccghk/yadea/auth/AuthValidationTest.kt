package top.ghhccghk.yadea.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun mobileRules_matchOriginalRegex() {
        assertTrue(AuthValidation.isChinaMobile("13800138000"))
        assertTrue(AuthValidation.isChinaMobile("19912345678"))
        assertFalse(AuthValidation.isChinaMobile("12012345678"))
        assertFalse(AuthValidation.isChinaMobile("1380013800"))
        assertTrue(AuthValidation.isOverseasMobile("1234567"))
        assertTrue(AuthValidation.isOverseasMobile("12345678901"))
        assertFalse(AuthValidation.isOverseasMobile("123456"))
    }

    @Test
    fun emailAndPasswordRules_matchOriginalRegex() {
        assertTrue(AuthValidation.isEmail("user.name+tag@example.com"))
        assertFalse(AuthValidation.isEmail("missing-at.example.com"))
        assertTrue(AuthValidation.isPassword("Abc12345"))
        assertTrue(AuthValidation.isPassword("a_b-c.d/!?~"))
        assertFalse(AuthValidation.isPassword("12345"))
        assertFalse(AuthValidation.isPassword("密码123456"))
        assertFalse(AuthValidation.isPassword("a".repeat(19)))
    }

    @Test
    fun smsCode_isExactlyFourDigits() {
        assertTrue(AuthValidation.isSmsCode("0000"))
        assertTrue(AuthValidation.isSmsCode("1234"))
        assertFalse(AuthValidation.isSmsCode("123"))
        assertFalse(AuthValidation.isSmsCode("12345"))
        assertFalse(AuthValidation.isSmsCode("12a4"))
    }

    @Test
    fun accountType_isInferredForLoginRequests() {
        assertEquals(AuthAccountType.Mobile, AuthValidation.accountTypeOf("13800138000"))
        assertEquals(AuthAccountType.Email, AuthValidation.accountTypeOf("a@b.com"))
        assertNull(AuthValidation.accountTypeOf("not-an-account"))
    }

    @Test
    fun loginValidation_returnsActionableMessages() {
        assertEquals(
            "密码需为 6-18 位，且只能包含允许的字符",
            AuthValidation.validatePasswordLogin("13800138000", "123"),
        )
        assertNull(AuthValidation.validatePasswordLogin("13800138000", "Abc12345"))
        assertEquals(
            "验证码必须为 4 位数字",
            AuthValidation.validateSmsLogin("13800138000", "12"),
        )
        assertNull(AuthValidation.validateSmsLogin("13800138000", "1234"))
    }
}
