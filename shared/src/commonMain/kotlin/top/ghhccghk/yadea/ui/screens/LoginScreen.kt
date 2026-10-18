package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import top.ghhccghk.yadea.auth.AuthController

private enum class LoginMode(val label: String) {
    Password("密码登录"),
    Sms("验证码登录"),
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
                AppSection(title = if (mode == LoginMode.Password) "账号密码" else "手机验证码") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                    ) {
                        if (mode == LoginMode.Password) {
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
                        } else {
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
                                if (mode == LoginMode.Password) {
                                    controller.loginByPassword(account, password)
                                } else {
                                    controller.loginBySms(mobile, code)
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
