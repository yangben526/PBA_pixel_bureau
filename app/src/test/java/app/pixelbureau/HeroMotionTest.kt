package app.pixelbureau

import app.pixelbureau.logic.HeroCityLoopMs
import app.pixelbureau.logic.HeroCloudLoopMs
import app.pixelbureau.logic.heroTileShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroMotionTest {
    @Test
    fun shiftStartsAtZeroAndWrapsAtLoopEnd() {
        assertEquals(0, heroTileShift(0f, 2172))
        assertEquals(0, heroTileShift(1f, 2172))
    }

    @Test
    fun shiftStaysWithinTile() {
        var steps = 0
        var peak = 0
        while (steps <= 100) {
            val shift = heroTileShift(steps / 100f, 2172)
            assertTrue(shift in 0 until 2172)
            peak = maxOf(peak, shift)
            steps += 1
        }
        assertTrue(peak > 2000)
    }

    @Test
    fun cloudsLoopFasterThanCity() {
        assertTrue(HeroCloudLoopMs < HeroCityLoopMs)
    }
}
