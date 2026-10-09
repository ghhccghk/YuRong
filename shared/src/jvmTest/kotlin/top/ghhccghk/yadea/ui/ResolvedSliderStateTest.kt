package top.ghhccghk.yadea.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedSliderStateTest {

    @Test
    fun normalRange_preservesBoundsAndValue() {
        val state = resolveSliderState(value = 0.5f, valueRange = 0.2f..0.8f)

        assertEquals(0.2f, state.rangeStart)
        assertEquals(0.8f, state.rangeEnd)
        assertEquals(0.5f, state.value)
    }

    @Test
    fun outOfRangeValue_isClamped() {
        val state = resolveSliderState(value = 1.2f, valueRange = 0.2f..0.8f)

        assertEquals(0.8f, state.value)
        assertEquals(0.2f, state.submitValue(-1f))
        assertEquals(0.8f, state.submitValue(2f))
    }

    @Test
    fun reversedRange_isNormalized() {
        val state = resolveSliderState(value = 0.5f, valueRange = 1f..0f)

        assertEquals(0f, state.rangeStart)
        assertEquals(1f, state.rangeEnd)
        assertTrue(state.rangeStart < state.rangeEnd)
    }

    @Test
    fun degenerateRange_rendersStrictRangeButSubmitsFixedValue() {
        val state = resolveSliderState(value = 0.75f, valueRange = 0.75f..0.75f)

        assertTrue(state.rangeStart < state.rangeEnd)
        assertEquals(0.75f, state.value)
        assertEquals(0.75f, state.submitValue(0.2f))
        assertEquals(0.75f, state.submitValue(0.9f))
    }
}
