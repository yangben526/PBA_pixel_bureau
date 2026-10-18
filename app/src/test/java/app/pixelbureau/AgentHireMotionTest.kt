package app.pixelbureau

import app.pixelbureau.logic.AgentHireTitleBreathe
import app.pixelbureau.logic.agentHireTitleScale
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentHireMotionTest {
    private val twoPi = (2.0 * PI).toFloat()

    @Test
    fun titleScaleRestsAtOneAndPeaksMidLoop() {
        assertEquals(1f, agentHireTitleScale(0f), 1e-6f)
        assertEquals(1f + 2f * AgentHireTitleBreathe, agentHireTitleScale(PI.toFloat()), 1e-6f)
        assertEquals(1f, agentHireTitleScale(twoPi), 1e-5f)
    }

    @Test
    fun titleScaleStaysWithinMeasuredClearance() {
        var steps = 0
        while (steps <= 64) {
            val scale = agentHireTitleScale(steps / 64f * twoPi)
            assertTrue("scale $scale out of range", scale >= 1f && scale <= 1.012f + 1e-6f)
            steps += 1
        }
    }

}
