package com.spendobserver.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeOutcomeTest {

    private val width = 900f
    private val fling = 2000f
    private val farEnough = width * SwipeCommitFraction + 1

    private fun outcome(drag: Float, velocity: Float = 0f) = swipeOutcome(drag, velocity, width, fling)

    @Test
    fun farEnoughAndReleasedSlowly_goesThroughEitherWay() {
        assertEquals(SwipeDirection.Left, outcome(-farEnough))
        assertEquals(SwipeDirection.Right, outcome(farEnough))
    }

    @Test
    fun shortAndSlow_staysPut() {
        assertNull(outcome(-100f, velocity = -500f))
        assertNull(outcome(100f, velocity = 500f))
    }

    @Test
    fun shortButFlickedOnward_goesThrough() {
        assertEquals(SwipeDirection.Left, outcome(-50f, velocity = -fling))
        assertEquals(SwipeDirection.Right, outcome(50f, velocity = fling))
    }

    @Test
    fun farEnoughButFlungBack_staysPut() {
        assertNull(outcome(-farEnough, velocity = fling))
        assertNull(outcome(farEnough, velocity = -fling))
    }

    @Test
    fun shortAndFlickedTheOtherWay_staysPut() {
        assertNull(outcome(50f, velocity = -fling))
    }

    @Test
    fun noDragOrNoWidth_staysPut() {
        assertNull(outcome(0f, velocity = fling))
        assertNull(swipeOutcome(-farEnough, 0f, widthPx = 0f, flingVelocityPx = fling))
    }
}
