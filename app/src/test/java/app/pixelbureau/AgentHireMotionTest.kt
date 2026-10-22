package app.pixelbureau

import app.pixelbureau.logic.AgentHireAgentRight
import app.pixelbureau.logic.AgentHireBobPx
import app.pixelbureau.logic.AgentHireCloudArtW
import app.pixelbureau.logic.AgentHireCloudLoopX
import app.pixelbureau.logic.AgentHireHireBottom
import app.pixelbureau.logic.AgentHireHireLeft
import app.pixelbureau.logic.AgentHireLogoPeak
import app.pixelbureau.logic.AgentHireSteamDriftPx
import app.pixelbureau.logic.AgentHireSteamPeak
import app.pixelbureau.logic.AgentHireSteamRisePx
import app.pixelbureau.logic.AgentHireSteamSwayPx
import app.pixelbureau.logic.AgentHireTaglineTop
import app.pixelbureau.logic.AgentHireTitleBreathe
import app.pixelbureau.logic.HireHandPose
import app.pixelbureau.logic.agentHireBlink
import app.pixelbureau.logic.agentHireBob
import app.pixelbureau.logic.agentHireCloudWrap
import app.pixelbureau.logic.agentHireHandPose
import app.pixelbureau.logic.agentHireLogo
import app.pixelbureau.logic.agentHireSteam
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

    @Test
    fun bobRestsAtZeroAndPeaksMidLoop() {
        assertEquals(0f, agentHireBob(0f), 1e-6f)
        assertEquals(AgentHireBobPx, agentHireBob(PI.toFloat() / 2f), 1e-6f)
        assertEquals(-AgentHireBobPx, agentHireBob(3f * PI.toFloat() / 2f), 1e-6f)
        assertEquals(0f, agentHireBob(twoPi), 1e-5f)
    }

    @Test
    fun cloudWrapRestsAtLoopPointAndClosesLoop() {
        assertEquals(AgentHireCloudLoopX, agentHireCloudWrap(0f))
        assertEquals(AgentHireCloudLoopX, agentHireCloudWrap(1f))
        assertEquals(535, agentHireCloudWrap(0.5f))
    }

    @Test
    fun cloudWrapStaysInTileAndScrollsLeft() {
        var steps = 0
        var prev = agentHireCloudWrap(0f)
        while (steps <= 200) {
            val o = agentHireCloudWrap(steps / 200f)
            assertTrue("offset $o out of range", o >= 0 && o < AgentHireCloudArtW)
            if (steps in 1..150) assertTrue("offset increased $prev->$o", o <= prev)
            prev = o
            steps += 1
        }
        assertEquals(15, agentHireCloudWrap(0.76f))
        assertEquals(1975, agentHireCloudWrap(0.78f))
    }

    @Test
    fun hireSliceClearsAgentAndTagline() {
        assertTrue("hire touches Agent", AgentHireHireLeft - AgentHireAgentRight >= 2)
        assertTrue("hire touches tagline", AgentHireTaglineTop - AgentHireHireBottom >= 2)
    }

    @Test
    fun blinkCoversAtWindowsAndRestsElsewhere() {
        assertEquals(0f, agentHireBlink(0f), 1e-6f)
        assertEquals(0f, agentHireBlink(0.5f), 1e-6f)
        assertEquals(0f, agentHireBlink(1f), 1e-6f)
        assertEquals(1f, agentHireBlink(0.09f), 1e-6f)
        assertEquals(1f, agentHireBlink(0.43f), 1e-6f)
        assertEquals(1f, agentHireBlink(0.76f), 1e-6f)
    }

    @Test
    fun handHoldsPresentAndTapsWhileSpeaking() {
        assertEquals(HireHandPose(0, 0), agentHireHandPose(0f))
        assertEquals(HireHandPose(0, 0), agentHireHandPose(0.4f))
        assertEquals(HireHandPose(0, 0), agentHireHandPose(1f))
        assertEquals(HireHandPose(0, -5), agentHireHandPose(0.58f))
        val taps = mutableSetOf<HireHandPose>()
        var steps = 0
        while (steps <= 120) {
            val pose = agentHireHandPose(0.80f + steps / 120f * 0.15f)
            assertTrue("pose $pose invalid", pose.y == 0 && pose.x in -3..3)
            taps.add(pose)
            steps += 1
        }
        assertEquals(
            setOf(HireHandPose(-3, 0), HireHandPose(0, 0), HireHandPose(3, 0)),
            taps,
        )
    }

    @Test
    fun logoRestsDarkAndPeaksMidLoop() {
        assertEquals(0f, agentHireLogo(0f), 1e-6f)
        assertEquals(AgentHireLogoPeak, agentHireLogo(PI.toFloat()), 1e-6f)
        assertEquals(0f, agentHireLogo(twoPi), 1e-5f)
    }

    @Test
    fun steamWispsRiseFadeAndStayBounded() {
        for (i in 0..2) {
            var steps = 0
            while (steps <= 48) {
                val w = agentHireSteam(steps / 48f, i)
                assertTrue("alpha ${w.alpha}", w.alpha >= 0f && w.alpha <= AgentHireSteamPeak + 1e-6f)
                assertTrue("dy ${w.dy}", w.dy >= -AgentHireSteamRisePx && w.dy <= 0f)
                assertTrue(
                    "dx ${w.dx}",
                    w.dx >= -AgentHireSteamSwayPx &&
                        w.dx <= AgentHireSteamSwayPx + AgentHireSteamDriftPx,
                )
                assertTrue("scale ${w.scale}", w.scale >= 0.7f && w.scale <= 1.6f + 1e-6f)
                steps += 1
            }
        }
        val first = agentHireSteam(0f, 0)
        assertEquals(0f, first.dy, 1e-6f)
        assertEquals(0f, first.alpha, 1e-6f)
    }

}
