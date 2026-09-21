package com.tomstrawat.zombieescape

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal fun clamp(value: Float, minValue: Float, maxValue: Float): Float =
    max(minValue, min(maxValue, value))

internal fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float =
    hypot(ax - bx, ay - by)

internal fun moveToward(current: Float, target: Float, amount: Float): Float {
    if (current < target) return min(current + amount, target)
    if (current > target) return max(current - amount, target)
    return current
}
