package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem as MiuixDropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

/**
 * 下拉菜单中的一项（业务语义）。
 *
 * 与 [AppPopupPreference] 的区别：那个是「设置项形态的选择器」，本身就占一行；
 * 这里是「按钮展开的动作菜单」，由 [AppDropdownMenu] 的图标按钮触发。
 *
 * @param text 菜单项文案。
 * @param onClick 点击该项后的回调；菜单会在回调前自动收起，与两套原生实现一致。
 * @param enabled 是否可点击；禁用项在两套风格下都不可选中。
 * @param selected 是否处于选中态（用于「当前项」这类单选语义的菜单）。
 * @param summary 次级说明文案；为空时不渲染。
 */
data class AppDropdownMenuItem(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val summary: String? = null,
)

/**
 * 图标按钮 + 下拉动作菜单。
 *
 * Miuix 使用原生 [WindowIconDropdownMenu]（IconButton 形态，弹层是 MIUI 风格的 dropdown popup），
 * Material 3 使用原生 `IconButton` + `DropdownMenu`。
 * 两者语义一致：点击图标展开菜单、点击菜单项先收起再回调、点击外部或返回键只收起不回调。
 *
 * 因为两套原生实现都由各自的「图标按钮」承担触发，这里的 [icon] 只描述视觉内容，
 * 点击行为统一由本组件接管，避免出现「按钮自己处理点击」导致菜单打不开的情况。
 *
 * @param icon 触发按钮的图标，两套风格共用同一个 [ImageVector]。
 * @param contentDescription 触发按钮的无障碍描述，说明这个菜单是做什么的。
 * @param items 菜单项列表；为空时按钮保持可点击，但展开后没有任何菜单项。
 * @param enabled 触发按钮是否可用。
 */
@Composable
fun AppDropdownMenu(
    icon: ImageVector,
    contentDescription: String,
    items: List<AppDropdownMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppStyleContent(
        material = {
            var expanded by remember { mutableStateOf(false) }
            // modifier 应用在最外层可布局节点，弹层需要以该节点作为锚点。
            Box(modifier = modifier) {
                IconButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        // Material 3 的内容色由 LocalContentColor 提供，跟随顶栏等宿主容器。
                        tint = LocalContentColor.current,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = item.text,
                                    color = if (item.selected) {
                                        AppDesign.colors.primary
                                    } else {
                                        AppDesign.colors.onSurface
                                    },
                                )
                            },
                            onClick = {
                                // 先收起再回调：与 Miuix 弹层的 collapseOnSelection 行为保持一致。
                                expanded = false
                                item.onClick()
                            },
                            enabled = item.enabled,
                            // Material 3 没有 Miuix 的勾选图标，用文本勾号保持同一选中语义。
                            leadingIcon = if (item.selected) {
                                { Text(text = "\u2713", color = AppDesign.colors.primary) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
        miuix = {
            // Miuix 侧同样由本组件把业务模型翻译成原生 DropdownEntry；业务页面只认识 AppDropdownMenuItem。
            val entry = DropdownEntry(
                items = items.map { item ->
                    MiuixDropdownItem(
                        text = item.text,
                        enabled = item.enabled,
                        selected = item.selected,
                        summary = item.summary,
                        onClick = item.onClick,
                    )
                },
            )
            WindowIconDropdownMenu(
                entry = entry,
                modifier = modifier,
                enabled = enabled,
            ) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    // Miuix 的内容色是独立的 CompositionLocal，需要单独取值。
                    tint = MiuixLocalContentColor.current,
                )
            }
        },
    )
}