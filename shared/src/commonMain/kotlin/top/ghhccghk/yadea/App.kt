package top.ghhccghk.yadea

import androidx.compose.runtime.Composable
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.ui.MainScreen

/**
 * 应用入口
 */
@Composable
fun App(
    preferences: AppPreferences? = null,
    systemAccentColor: androidx.compose.ui.graphics.Color? = null,
) {
    MainScreen(preferences = preferences, systemAccentColor = systemAccentColor)
}
