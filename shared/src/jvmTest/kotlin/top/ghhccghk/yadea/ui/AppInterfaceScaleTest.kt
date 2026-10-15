package top.ghhccghk.yadea.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 界面缩放的档位换算测试：覆盖默认值、边界、四舍五入与损坏值回退。 */
class AppInterfaceScaleTest {

    @Test
    fun defaultScaleIsOne() {
        assertEquals(1f, AppInterfaceScale.snap(AppInterfaceScale.DEFAULT_SCALE))
        assertEquals(100, AppInterfaceScale.percentOf(AppInterfaceScale.DEFAULT_SCALE))
        assertEquals("100%", AppInterfaceScale.label(AppInterfaceScale.DEFAULT_SCALE))
    }

    @Test
    fun rangeMatchesPercentBounds() {
        assertEquals(0.8f, AppInterfaceScale.range.start)
        assertEquals(1.3f, AppInterfaceScale.range.endInclusive)
        assertEquals(4, AppInterfaceScale.STEPS)
    }

    @Test
    fun scaleSnapsToNearestStep() {
        // 0.94 距离 0.9 更近，0.96 距离 1.0 更近。
        assertEquals(0.9f, AppInterfaceScale.snap(0.94f))
        assertEquals(1f, AppInterfaceScale.snap(0.96f))
        assertEquals(1.1f, AppInterfaceScale.snap(1.1f))
        assertEquals("120%", AppInterfaceScale.label(1.16f))
    }

    @Test
    fun outOfRangeScaleIsClamped() {
        assertEquals(0.8f, AppInterfaceScale.snap(0.1f))
        assertEquals(1.3f, AppInterfaceScale.snap(9f))
        assertEquals(1f, AppInterfaceScale.snap(Float.NaN))
    }

    @Test
    fun percentRoundTripsThroughStorage() {
        listOf(80, 90, 100, 110, 120, 130).forEach { percent ->
            val scale = AppInterfaceScale.percentToScale(percent)
            assertEquals(percent, AppInterfaceScale.percentOf(scale))
        }
    }

    @Test
    fun brokenPercentFallsBackToNearestStep() {
        // 0 表示历史数据缺失或损坏，收敛到最小档位而不是崩溃。
        assertEquals(0.8f, AppInterfaceScale.percentToScale(0))
        assertEquals(1.3f, AppInterfaceScale.percentToScale(1000))
        assertEquals(1.2f, AppInterfaceScale.percentToScale(116))
    }
}
