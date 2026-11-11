package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** A local clock time, with no date, timezone or implicit default selection. */
data class AppTimeOfDay(val hour: Int, val minute: Int) {
    init { require(hour in 0..23 && minute in 0..59) }

    fun format(): String = hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

    companion object {
        fun parse(value: String?): AppTimeOfDay? {
            if (value == null || !Regex("[0-9]{2}:[0-9]{2}").matches(value)) return null
            val hour = value.substring(0, 2).toInt()
            val minute = value.substring(3).toInt()
            return if (hour in 0..23 && minute in 0..59) AppTimeOfDay(hour, minute) else null
        }
    }
}

/** Shared, controlled time input using each theme's native AppPopupPreference. */
@Composable
fun AppTimePicker(
    title: String,
    value: AppTimeOfDay?,
    onValueChange: (AppTimeOfDay) -> Unit,
    modifier: Modifier = Modifier,
    minuteStep: Int = 1,
    enabled: Boolean = true,
) {
    require(minuteStep in 1..60 && 60 % minuteStep == 0)
    var hour by remember(value) { mutableStateOf(value?.hour) }
    var minute by remember(value, minuteStep) { mutableStateOf(value?.minute) }
    val minutes = remember(minuteStep) { (0 until 60 step minuteStep).toList() }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
        AppPopupPreference(
            title = "$title · 时",
            summary = if (hour == null) "未选择" else null,
            options = (0..23).map { it.toString().padStart(2, '0') },
            selectedIndex = hour ?: -1,
            onSelectedIndexChange = { selected ->
                hour = selected
                minute?.takeIf { it in minutes }?.let { onValueChange(AppTimeOfDay(selected, it)) }
            },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        AppPopupPreference(
            title = "$title · 分",
            summary = if (minute == null || minute !in minutes) "未选择" else null,
            options = minutes.map { it.toString().padStart(2, '0') },
            selectedIndex = minutes.indexOf(minute),
            onSelectedIndexChange = { selected ->
                val nextMinute = minutes[selected]
                minute = nextMinute
                hour?.let { onValueChange(AppTimeOfDay(it, nextMinute)) }
            },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}
