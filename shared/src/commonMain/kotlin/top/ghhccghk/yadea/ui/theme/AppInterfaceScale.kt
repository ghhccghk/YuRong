package top.ghhccghk.yadea.ui

import kotlin.math.roundToInt

/**
 * 界面缩放的取值规则。
 *
 * 语义上是“倍率”，但持久化时按百分比整数保存，避免浮点数在
 * SharedPreferences / Java Preferences / localStorage / NSUserDefaults 之间出现精度漂移。
 * 这里集中放纯函数与常量，滑条档位、存储换算和边界收敛都能被单元测试直接覆盖。
 */
object AppInterfaceScale {
    /** 最小缩放百分比。 */
    const val MIN_PERCENT = 80

    /** 最大缩放百分比。 */
    const val MAX_PERCENT = 130

    /** 相邻档位之间的百分比步进。 */
    const val STEP_PERCENT = 10

    /**
     * 端点之间的档位数，与 Material 3 / Miuix Slider 的 `steps` 语义一致：
     * 80% ~ 130%、步进 10% 时中间有 4 个档位。
     */
    const val STEPS = 4

    /** 默认缩放倍率，保持原有界面尺寸。 */
    const val DEFAULT_SCALE = 1f

    /** 滑条使用的倍率区间。 */
    val range: ClosedFloatingPointRange<Float> = MIN_PERCENT / 100f..MAX_PERCENT / 100f

    /** 把任意倍率收敛到合法区间；NaN 视为默认值。 */
    fun coerce(scale: Float): Float =
        if (scale.isNaN()) DEFAULT_SCALE else scale.coerceIn(range.start, range.endInclusive)

    /** 把任意倍率吸附到最近的档位（倍率表示）。 */
    fun snap(scale: Float): Float = percentToScale(percentOf(scale))

    /** 把任意倍率换算成吸附到最近档位的百分比。 */
    fun percentOf(scale: Float): Int = snapPercent((coerce(scale) * 100).roundToInt())

    /** 把百分比吸附到最近的档位，并收敛到合法区间。 */
    fun snapPercent(percent: Int): Int {
        val clamped = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        // 全部使用整数运算，避免 0.8f + 0.1f 这类浮点误差影响档位判断。
        val stepsFromMin = (clamped - MIN_PERCENT + STEP_PERCENT / 2) / STEP_PERCENT
        return (MIN_PERCENT + stepsFromMin * STEP_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    /** 百分比 -> 倍率；损坏值回退到最近的合法档位。 */
    fun percentToScale(percent: Int): Float = snapPercent(percent) / 100f

    /** 界面展示用的百分比文本，例如 “110%”。 */
    fun label(scale: Float): String = "${percentOf(scale)}%"
}
