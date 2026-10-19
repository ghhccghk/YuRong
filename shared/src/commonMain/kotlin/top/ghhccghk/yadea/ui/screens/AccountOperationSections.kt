package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import top.ghhccghk.yadea.auth.AuthController
import top.ghhccghk.yadea.auth.AuthUser

private enum class RecoveryAction(val label: String) {
    Register("注册账号"),
    ResetPassword("重置密码"),
}

/** 未登录状态下的手机号/邮箱注册和验证码改密。 */
@Composable
internal fun AccountRecoverySection(
    controller: AuthController,
    busy: Boolean,
    countdown: Int,
    agreed: Boolean,
    onNeedAgreement: () -> Unit,
) {
    val dimensions = AppDesign.dimensions
    var action by remember { mutableStateOf(RecoveryAction.Register) }
    var useEmail by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AppSection(title = "注册与找回密码") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = dimensions.itemHorizontalPadding,
                vertical = dimensions.itemVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
        ) {
            AppSegmentedControl(
                options = RecoveryAction.entries.map { it.label },
                selectedIndex = RecoveryAction.entries.indexOf(action),
                onSelected = {
                    action = RecoveryAction.entries[it]
                    controller.clearFeedback()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AppSegmentedControl(
                options = listOf("手机号", "邮箱"),
                selectedIndex = if (useEmail) 1 else 0,
                onSelected = {
                    useEmail = it == 1
                    controller.clearFeedback()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = account,
                onValueChange = { account = it },
                label = if (useEmail) "邮箱" else "手机号",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (useEmail) KeyboardType.Email else KeyboardType.Phone,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                AppTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(4) },
                    label = "4 位验证码",
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AppOutlinedButton(
                    onClick = {
                        val type = if (action == RecoveryAction.ResetPassword) 4 else 1
                        if (useEmail) {
                            controller.sendEmailCode(account, type)
                        } else {
                            controller.sendSmsCode(account, type)
                        }
                    },
                    enabled = !busy && countdown <= 0,
                ) {
                    AppText(if (countdown > 0) "${countdown}s" else "获取验证码")
                }
            }
            AppTextField(
                value = password,
                onValueChange = { password = it },
                label = "密码（6-18 位）",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            AppButton(
                onClick = {
                    if (!agreed) {
                        onNeedAgreement()
                        return@AppButton
                    }
                    when (action) {
                        RecoveryAction.Register -> if (useEmail) {
                            controller.registerByEmail(account, code, password)
                        } else {
                            controller.registerBySms(account, code, password)
                        }
                        RecoveryAction.ResetPassword ->
                            controller.changePassword(account, code, password)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(if (busy) "处理中…" else action.label)
            }
        }
    }
}

private enum class SecurityTarget(val label: String) {
    Mobile("手机号"),
    Email("邮箱"),
}

/** 已登录账号的手机号/邮箱绑定与换绑。 */
@Composable
internal fun AccountSecuritySection(
    controller: AuthController,
    user: AuthUser,
    busy: Boolean,
    countdown: Int,
) {
    val dimensions = AppDesign.dimensions
    var target by remember { mutableStateOf(SecurityTarget.Mobile) }
    var contact by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val useEmail = target == SecurityTarget.Email
    val changing = if (useEmail) user.email.isNotBlank() else user.mobile.isNotBlank()

    AppSection(title = "账号安全") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = dimensions.itemHorizontalPadding,
                vertical = dimensions.itemVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
        ) {
            AppSegmentedControl(
                options = SecurityTarget.entries.map { it.label },
                selectedIndex = SecurityTarget.entries.indexOf(target),
                onSelected = {
                    target = SecurityTarget.entries[it]
                    contact = ""
                    code = ""
                    controller.clearFeedback()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AppListRow(
                title = if (useEmail) "当前邮箱" else "当前手机号",
                value = (if (useEmail) user.email else user.mobile).ifBlank { "未绑定" },
            )
            AppTextField(
                value = contact,
                onValueChange = { contact = it },
                label = if (useEmail) "新邮箱" else "新手机号",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (useEmail) KeyboardType.Email else KeyboardType.Phone,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                AppTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(4) },
                    label = "4 位验证码",
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AppOutlinedButton(
                    onClick = {
                        if (useEmail) {
                            controller.sendEmailCode(contact, if (changing) 11 else 3)
                        } else {
                            // smali: BindTypeActivity.getSmsCode
                            // 换绑发送 11，首次绑定发送 3。
                            controller.sendSmsCode(contact, if (changing) 11 else 3)
                        }
                    },
                    enabled = !busy && countdown <= 0,
                ) {
                    AppText(if (countdown > 0) "${countdown}s" else "获取验证码")
                }
            }
            AppButton(
                onClick = {
                    if (useEmail) {
                        controller.bindOrChangeEmail(contact, code, changing)
                    } else {
                        controller.bindOrChangeMobile(contact, code, changing)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(
                    if (busy) {
                        "处理中…"
                    } else if (changing) {
                        "更换${target.label}"
                    } else {
                        "绑定${target.label}"
                    },
                )
            }
        }
    }
}
