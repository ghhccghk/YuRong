package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.basic.SearchBar as MiuixSearchBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup

/**
 * 搜索框：为设备列表之类的长列表提供即时过滤输入。
 *
 * 业务页面只传入查询字符串与回调，两套风格各自使用原生搜索输入：
 * - Material 3 使用原生 [OutlinedTextField]，带前置搜索图标与按需出现的清除按钮。
 * - Miuix 使用原生 [MiuixSearchBar] + [MiuixInputField]，保持胶囊外形与原生清空图标，
 *   并在搜索展开时接管返回键（返回键会收起搜索并清空关键字）。
 *
 * 过滤规则见 [matchesSearchQuery]，两套风格完全一致。
 *
 * @param query 当前关键字，由调用方持有，便于同一关键字同时驱动列表过滤。
 * @param onQueryChange 关键字变化回调；清除按钮同样通过它写回空字符串。
 * @param label 未输入时显示的占位文案。
 * @param modifier 应用于最外层可布局节点。
 * @param enabled 是否可用；禁用时两套风格都不响应输入。
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Miuix 的搜索输入需要显式维护展开状态：折叠时显示占位文案，展开后由原生控件接管焦点与键盘。
    var expanded by remember { mutableStateOf(false) }
    val miuixExpanded = expanded || query.isNotEmpty()
    val onMiuixExpandedChange: (Boolean) -> Unit = { isExpanded ->
        expanded = isExpanded
        // 收起搜索（返回键）时同时清空关键字，避免列表仍被旧关键字过滤却看不到输入框。
        if (!isExpanded) onQueryChange("")
    }

    AppStyleContent(
        material = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                shape = AppDesign.shapes.field,
                label = { Text(label) },
                leadingIcon = {
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (query.isNotEmpty() && enabled) {
                    {
                        // 只在有内容时提供清除入口，语义与 Miuix 原生搜索框保持一致。
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = MiuixIcons.Basic.SearchCleanup,
                                contentDescription = "清除搜索内容",
                                tint = LocalContentColor.current,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        },
        miuix = {
            MiuixSearchBar(
                inputField = {
                    MiuixInputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        // 过滤是实时的，回车不需要额外动作，保持已输入的关键字不变。
                        onSearch = {},
                        expanded = miuixExpanded,
                        onExpandedChange = onMiuixExpandedChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 失去焦点且没有关键字时收回展开状态，让占位文案重新显示。
                            .onFocusChanged { if (!it.isFocused && query.isEmpty()) expanded = false },
                        label = label,
                        enabled = enabled,
                    )
                },
                // 原生搜索栏在返回键收起时回调这里，与输入框共用同一套展开状态处理。
                onExpandedChange = onMiuixExpandedChange,
                modifier = modifier,
                expanded = miuixExpanded,
                content = {},
            )
        },
    )
}

/**
 * 搜索关键字匹配：设备名称、地址等任一字段包含关键字即算命中。
 *
 * - 关键字去掉首尾空白后按“包含”比较，且忽略大小写，便于用 `YAD` 匹配 `yadea-x1`。
 * - 关键字为空时恒为 `true`，调用方可以直接复用同一段过滤代码，无需额外判空分支。
 * - 字段为 `null`（例如 BLE 设备没有名字）时视为不匹配，不会抛异常。
 */
internal fun matchesSearchQuery(query: String, vararg fields: String?): Boolean {
    val keyword = query.trim()
    if (keyword.isEmpty()) return true
    return fields.any { field -> field?.contains(keyword, ignoreCase = true) == true }
}