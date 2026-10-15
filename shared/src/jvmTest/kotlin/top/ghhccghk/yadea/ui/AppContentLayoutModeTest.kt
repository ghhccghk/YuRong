package top.ghhccghk.yadea.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** 内容分栏断点的行为测试：确认窄屏单栏、宽屏（含桌面窗口）双栏。 */
class AppContentLayoutModeTest {

    @Test
    fun phoneWidth_usesSingleColumn() {
        assertEquals(AppContentLayoutMode.SingleColumn, appContentLayoutMode(360.dp))
    }

    @Test
    fun widthBelowBreakpoint_usesSingleColumn() {
        assertEquals(AppContentLayoutMode.SingleColumn, appContentLayoutMode(839.dp))
    }

    @Test
    fun widthAtBreakpoint_usesTwoColumn() {
        assertEquals(AppContentLayoutMode.TwoColumn, appContentLayoutMode(840.dp))
    }

    @Test
    fun desktopWidth_usesTwoColumn() {
        assertEquals(AppContentLayoutMode.TwoColumn, appContentLayoutMode(1280.dp))
    }
}
