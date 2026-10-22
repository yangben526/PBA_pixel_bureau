package app.pixelbureau

import app.pixelbureau.logic.HeroCityLoopMs
import app.pixelbureau.logic.HeroCloudLoopMs
import app.pixelbureau.logic.heroTileLayout
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

    @Test
    fun tileCopiesAbutExactlyAndCoverCanvas() {
        for (dstW in intArrayOf(1086, 1000, 999, 2172)) {
            var shift = 0
            while (shift < 2172) {
                val layout = heroTileLayout(shift, 2172, dstW)
                assertEquals(layout.secondX, layout.firstX + layout.firstW)
                assertTrue(layout.firstX <= 0)
                assertTrue(layout.secondX + layout.secondW >= dstW)
                assertTrue(layout.firstW > 0)
                shift += 7
            }
        }
    }

    @Test
    fun tileLayoutAtRestStartsFlush() {
        val layout = heroTileLayout(0, 2172, 1086)
        assertEquals(0, layout.secondX)
        assertEquals(-1086, layout.firstX)
    }
}
