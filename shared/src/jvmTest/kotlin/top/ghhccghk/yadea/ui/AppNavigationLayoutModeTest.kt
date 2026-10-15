package top.ghhccghk.yadea.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** 自适应导航断点的行为测试：确认窄屏走底部导航、宽屏走侧边导航栏。 */
class AppNavigationLayoutModeTest {

    @Test
    fun phoneWidth_usesBottomNavigation() {
        assertEquals(AppNavigationLayoutMode.BottomNavigation, appNavigationLayoutMode(360.dp))
    }

    @Test
    fun widthBelowBreakpoint_usesBottomNavigation() {
        assertEquals(AppNavigationLayoutMode.BottomNavigation, appNavigationLayoutMode(599.dp))
    }

    @Test
    fun widthAtBreakpoint_usesNavigationRail() {
        assertEquals(AppNavigationLayoutMode.NavigationRail, appNavigationLayoutMode(600.dp))
    }

    @Test
    fun desktopWidth_usesNavigationRail() {
        assertEquals(AppNavigationLayoutMode.NavigationRail, appNavigationLayoutMode(1280.dp))
    }
}
