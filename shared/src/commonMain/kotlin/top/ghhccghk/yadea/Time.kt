package top.ghhccghk.yadea

import kotlin.time.TimeSource

private val appTimeStart = TimeSource.Monotonic.markNow()

internal fun currentMonotonicMillis(): Long =
    appTimeStart.elapsedNow().inWholeMilliseconds
