package top.ghhccghk.yadea.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator

@Composable
fun AppPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    if (AppDesign.style == UiThemeStyle.Material3) {
        Column(modifier = modifier.fillMaxWidth()) {
            AppText(
                text = title,
                style = AppDesign.typography.pageTitle,
                color = AppDesign.colors.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                AppText(
                    text = subtitle,
                    style = AppDesign.typography.summary,
                    color = AppDesign.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDesign.dimensions.compactSpacing),
                )
            }
        }
    }
}

@Composable
fun AppSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            AppText(
                text = title,
                style = AppDesign.typography.sectionTitle,
                color = AppDesign.colors.primary,
                modifier = Modifier.padding(
                    start = AppDesign.dimensions.itemHorizontalPadding,
                    end = AppDesign.dimensions.itemHorizontalPadding,
                    bottom = AppDesign.dimensions.compactSpacing,
                ),
            )
        }
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    }
}

@Composable
fun AppListRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }
    Column {
        Row(
            modifier = rowModifier.fillMaxWidth().padding(
                horizontal = AppDesign.dimensions.itemHorizontalPadding,
                vertical = AppDesign.dimensions.itemVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    style = AppDesign.typography.itemTitle,
                    color = if (enabled) AppDesign.colors.onSurface else AppDesign.colors.onSurfaceVariant,
                )
                if (!summary.isNullOrBlank()) {
                    AppText(
                        text = summary,
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
            }
            if (value != null) {
                AppText(
                    text = value,
                    style = AppDesign.typography.value,
                    color = AppDesign.colors.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = AppDesign.dimensions.itemHorizontalPadding,
                    end = AppDesign.dimensions.itemHorizontalPadding,
                ),
                color = AppDesign.colors.divider,
            )
        }
    }
}

@Composable
fun AppEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = message,
        style = AppDesign.typography.summary,
        color = AppDesign.colors.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(
            horizontal = AppDesign.dimensions.itemHorizontalPadding,
            vertical = AppDesign.dimensions.itemVerticalPadding,
        ),
    )
}

@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    AppStyleContent(
        material = { CircularProgressIndicator(modifier = modifier) },
        miuix = { MiuixCircularProgressIndicator(modifier = modifier) },
    )
}
