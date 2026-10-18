package top.ghhccghk.yadea.auth

/** 登录表单校验。正则来自原实现，边界不可放宽。 */
object AuthValidation {
    private val chinaMobile = Regex("^1(3|4|5|6|7|8|9)\\d{9}$")
    private val overseasMobile = Regex("^[0-9]{7,11}$")
    private val email = Regex("[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}")
    private val password = Regex("^[\"',./?!~_\\-<>&*^%$@a-zA-Z0-9]{6,18}$")
    private val smsCode = Regex("^[0-9]{4}$")

    fun isChinaMobile(value: String): Boolean = chinaMobile.matches(value)
    fun isOverseasMobile(value: String): Boolean = overseasMobile.matches(value)
    fun isMobile(value: String): Boolean = isChinaMobile(value) || isOverseasMobile(value)
    fun isEmail(value: String): Boolean = email.matches(value)
    fun isPassword(value: String): Boolean = password.matches(value)
    fun isSmsCode(value: String): Boolean = smsCode.matches(value)

    fun accountTypeOf(account: String): AuthAccountType? = when {
        isMobile(account) -> AuthAccountType.Mobile
        isEmail(account) -> AuthAccountType.Email
        else -> null
    }

    /** 返回可直接展示的错误文案；`null` 表示通过。 */
    fun validatePasswordLogin(account: String, password: String): String? = when {
        account.isBlank() -> "请输入手机号或邮箱"
        accountTypeOf(account) == null -> "请输入正确的手机号或邮箱"
        password.isBlank() -> "请输入密码"
        !isPassword(password) -> "密码需为 6-18 位，且只能包含允许的字符"
        else -> null
    }

    fun validateSmsLogin(mobile: String, code: String): String? = when {
        mobile.isBlank() -> "请输入手机号"
        !isMobile(mobile) -> "请输入正确的手机号"
        code.isBlank() -> "请输入验证码"
        !isSmsCode(code) -> "验证码必须为 4 位数字"
        else -> null
    }
}
