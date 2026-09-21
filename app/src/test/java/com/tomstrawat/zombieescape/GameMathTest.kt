package com.tomstrawat.zombieescape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMathTest {

    @Test
    fun clampLimitsValue() {
        assertEquals(0f, clamp(-2f, 0f, 10f))
        assertEquals(7f, clamp(7f, 0f, 10f))
        assertEquals(10f, clamp(20f, 0f, 10f))
    }

    @Test
    fun distanceIsEuclidean() {
        assertEquals(5f, distance(0f, 0f, 3f, 4f), 0.0001f)
    }

    @Test
    fun moveTowardDoesNotOvershoot() {
        assertEquals(10f, moveToward(8f, 10f, 5f))
        assertEquals(0f, moveToward(2f, 0f, 5f))
        assertTrue(moveToward(4f, 9f, 2f) > 4f)
    }
}
