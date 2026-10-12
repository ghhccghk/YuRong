package top.ghhccghk.yadea

import androidx.compose.ui.window.ComposeUIViewController
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.NSUserDefaultsPreferenceStore

fun MainViewController() = ComposeUIViewController { App(preferences = AppPreferences(NSUserDefaultsPreferenceStore())) }
