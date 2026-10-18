package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.auth.AuthController

private enum class LoginMode(val label: String) {
    Password("密码登录"),
    Sms("验证码登录"),
    Token("Token 登录"),
    Advanced("参数登录"),
}

private enum class AdvancedLoginTemplate(val label: String) {
    Password("密码"),
    Sms("短信"),
    Token("Token"),
}

private fun advancedTemplatePath(template: AdvancedLoginTemplate): String = when (template) {
    AdvancedLoginTemplate.Password -> "user/loginByPassword"
    AdvancedLoginTemplate.Sms -> "user/loginBySms"
    AdvancedLoginTemplate.Token -> "user/loginByToken"
}

private fun advancedTemplateJson(template: AdvancedLoginTemplate): String = when (template) {
    AdvancedLoginTemplate.Password -> """
        {
          "_gateway": "True",
          "account": "",
          "password": "",
          "accountType": "1",
          "appType": "1",
          "oppoPushId": "",
          "registrationId": ""
        }
    """.trimIndent()
    AdvancedLoginTemplate.Sms -> """
        {
          "_gateway": "True",
          "mobile": "",
          "code": "",
          "loginAppType": "1",
          "oppoPushId": "",
          "registrationId": ""
        }
    """.trimIndent()
    AdvancedLoginTemplate.Token -> """
        {
          "_gateway": "True",
          "loginToken": "",
          "gyuid": "",
          "appName": "YADEA",
          "verificationType": "1",
          "oppoPushId": "",
          "registrationId": ""
        }
    """.trimIndent()
}

/** 账号页：登录、验证码登录与退出登录。 */
@Composable
fun LoginScreen(
    controller: AuthController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val auth by controller.state.collectAsState()
    val dimensions = AppDesign.dimensions
    var mode by remember { mutableStateOf(LoginMode.Password) }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var gyuid by remember { mutableStateOf("") }
    var advancedTemplate by remember { mutableStateOf(AdvancedLoginTemplate.Password) }
    var advancedPath by remember {
        mutableStateOf(advancedTemplatePath(AdvancedLoginTemplate.Password))
    }
    var advancedJson by remember {
        mutableStateOf(advancedTemplateJson(AdvancedLoginTemplate.Password))
    }
    var advancedHeaders by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "账号",
                subtitle = "登录后可使用车辆绑定、云端设置与 OTA 服务",
            )
        }
        if (auth.session.isLoggedIn) {
            item {
                AppSection(title = "当前账号") {
                    AppListRow(
                        title = auth.session.user?.displayName.orEmpty(),
                        summary = auth.session.user?.accountLabel.orEmpty(),
                        value = "已登录",
                        showDivider = true,
                    )
                    AppListRow(
                        title = "设备标识",
                        summary = auth.session.loginId.ifBlank { "首次登录后生成" },
                    )
                }
            }
            item {
                AppButton(
                    onClick = controller::logout,
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.pageHorizontalPadding,
                    ),
                    enabled = !auth.busy,
                ) {
                    AppText("退出登录", style = AppDesign.typography.button)
                }
            }
        } else {
            item {
                AppSection(title = "登录方式") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                    ) {
                        AppSegmentedControl(
                            options = LoginMode.entries.map { it.label },
                            selectedIndex = LoginMode.entries.indexOf(mode),
                            onSelected = {
                                mode = LoginMode.entries[it]
                                localError = null
                                controller.clearFeedback()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                AppSection(
                    title = when (mode) {
                        LoginMode.Password -> "账号密码"
                        LoginMode.Sms -> "手机验证码"
                        LoginMode.Token -> "Token 登录"
                        LoginMode.Advanced -> "登录参数"
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                    ) {
                        when (mode) {
                            LoginMode.Password -> {
                                AppTextField(
                                    value = account,
                                    onValueChange = { account = it },
                                    label = "手机号或邮箱",
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !auth.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                )
                                AppTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "密码",
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !auth.busy,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                )
                            }
                            LoginMode.Sms -> {
                                AppTextField(
                                    value = mobile,
                                    onValueChange = { mobile = it },
                                    label = "手机号",
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !auth.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                                ) {
                                    AppTextField(
                                        value = code,
                                        onValueChange = { code = it.filter(Char::isDigit).take(4) },
                                        label = "验证码",
                                        modifier = Modifier.weight(1f),
                                        enabled = !auth.busy,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                    AppOutlinedButton(
                                        onClick = { controller.sendSmsCode(mobile) },
                                        enabled = auth.canSendCode,
                                    ) {
                                        AppText(
                                            if (auth.smsCountdown > 0) "${auth.smsCountdown}s" else "获取验证码",
                                            style = AppDesign.typography.button,
                                        )
                                    }
                                }
                            }
                            LoginMode.Token -> {
                                AppTextField(
                                    value = token,
                                    onValueChange = { token = it },
                                    label = "登录 Token（loginToken）",
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !auth.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                )
                                AppTextField(
                                    value = gyuid,
                                    onValueChange = { gyuid = it },
                                    label = "gyuid（可选）",
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !auth.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                )
                                AppText(
                                    text = "一键认证 loginToken 直接粘贴；已有 Bearer Token 请以“Bearer ”开头。gyuid 没有可留空。",
                                    style = AppDesign.typography.summary,
                                    color = AppDesign.colors.onSurfaceVariant,
                                )
                            }
                            LoginMode.Advanced -> {
                                AppSegmentedControl(
                                    options = AdvancedLoginTemplate.entries.map { it.label },
                                    selectedIndex = AdvancedLoginTemplate.entries.indexOf(advancedTemplate),
                                    onSelected = { index ->
                                        val selected = AdvancedLoginTemplate.entries[index]
                                        advancedTemplate = selected
                                        advancedPath = advancedTemplatePath(selected)
                                        advancedJson = advancedTemplateJson(selected)
                                        controller.clearFeedback()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AppTextField(
                                    value = advancedPath,
                                    onValueChange = { advancedPath = it },
                                    label = "接口路径",
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !auth.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                )
                                AppTextField(
                                    value = advancedJson,
                                    onValueChange = { advancedJson = it },
                                    label = "业务 JSON（签名前）",
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                                    enabled = !auth.busy,
                                    singleLine = false,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                )
                                AppTextField(
                                    value = advancedHeaders,
                                    onValueChange = { advancedHeaders = it },
                                    label = "请求头覆盖（每行 key=value，可留空）",
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                    enabled = !auth.busy,
                                    singleLine = false,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                )
                                AppText(
                                    text = "填写从原 App 抓到的 f84.b 业务 JSON；不要填 gatewaySign 表单。请求头可只写需要覆盖的字段。",
                                    style = AppDesign.typography.summary,
                                    color = AppDesign.colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                AppSection {
                    AppCheckboxPreference(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        title = "同意用户协议与隐私政策",
                        summary = "登录即表示同意车辆数据用于账号绑定与服务同步",
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.pageHorizontalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    val error = localError ?: auth.error
                    if (error != null) {
                        AppText(
                            text = error,
                            style = AppDesign.typography.summary,
                            color = AppDesign.colors.error,
                        )
                    } else if (auth.notice != null) {
                        AppText(
                            text = auth.notice.orEmpty(),
                            style = AppDesign.typography.summary,
                            color = AppDesign.colors.primary,
                        )
                    }
                    AppButton(
                        onClick = {
                            localError = if (!agreed) {
                                "请先阅读并同意用户协议与隐私政策"
                            } else {
                                null
                            }
                            if (agreed) {
                                when (mode) {
                                    LoginMode.Password -> controller.loginByPassword(account, password)
                                    LoginMode.Sms -> controller.loginBySms(mobile, code)
                                    LoginMode.Token -> controller.loginByToken(token, gyuid)
                                    LoginMode.Advanced -> controller.loginWithParameters(
                                        path = advancedPath,
                                        businessJson = advancedJson,
                                        headersText = advancedHeaders,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !auth.busy,
                    ) {
                        AppText(
                            if (auth.busy) "处理中…" else "登录",
                            style = AppDesign.typography.button,
                        )
                    }
                    if (!auth.available) {
                        AppText(
                            text = "登录网络层尚未注入。界面、校验与本地会话已就绪。",
                            style = AppDesign.typography.summary,
                            color = AppDesign.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
