package app.pixelbureau

import app.pixelbureau.logic.IntroApproachMs
import app.pixelbureau.logic.IntroBeat
import app.pixelbureau.logic.IntroCityMs
import app.pixelbureau.logic.IntroCloudCount
import app.pixelbureau.logic.IntroPlate
import app.pixelbureau.logic.IntroRevealMs
import app.pixelbureau.logic.IntroSailMs
import app.pixelbureau.logic.IntroShipLanes
import app.pixelbureau.logic.IntroSparkQuads
import app.pixelbureau.logic.introApproachT
import app.pixelbureau.logic.introBankAlpha
import app.pixelbureau.logic.introBankOffset
import app.pixelbureau.logic.introBirdAt
import app.pixelbureau.logic.introBirds
import app.pixelbureau.logic.introBlockDetail
import app.pixelbureau.logic.introBlockTone
import app.pixelbureau.logic.introCaptionAlpha
import app.pixelbureau.logic.introCaptionText
import app.pixelbureau.logic.introCityPlate
import app.pixelbureau.logic.introCityT
import app.pixelbureau.logic.introCityZoom
import app.pixelbureau.logic.introClearCloud
import app.pixelbureau.logic.introCloudAt
import app.pixelbureau.logic.introCloudSeeds
import app.pixelbureau.logic.introCoverFrame
import app.pixelbureau.logic.introDots
import app.pixelbureau.logic.introDriftAt
import app.pixelbureau.logic.introDriftEdgeFade
import app.pixelbureau.logic.introDriftSeeds
import app.pixelbureau.logic.introGridColX
import app.pixelbureau.logic.introGridRows
import app.pixelbureau.logic.introOfficeZoom
import app.pixelbureau.logic.introPuffScroll
import app.pixelbureau.logic.introRevealT
import app.pixelbureau.logic.introScrollFrac
import app.pixelbureau.logic.introShipAt
import app.pixelbureau.logic.introSparkleAlpha
import app.pixelbureau.logic.introSparkles
import app.pixelbureau.logic.introTargetScale
import app.pixelbureau.logic.introTargetYFrac
import app.pixelbureau.logic.introVeilAlpha
import app.pixelbureau.logic.introWheelAngle
import app.pixelbureau.logic.introWrapY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroCutsceneTest {

    @Test
    fun beatProgressClamps() {
        assertEquals(0f, introCityT(-10), 0f)
        assertEquals(0.5f, introCityT(IntroCityMs / 2), 1e-6f)
        assertEquals(1f, introCityT(IntroCityMs + 9999), 0f)
        assertEquals(0f, introApproachT(0), 0f)
        assertEquals(1f, introApproachT(IntroApproachMs), 0f)
        assertEquals(0.5f, introRevealT(IntroRevealMs / 2), 1e-6f)
        assertEquals(1f, introRevealT(IntroRevealMs + 9999), 0f)
    }

    @Test
    fun officeZoomSettlesFromSlightPushToOne() {
        assertEquals(1.14f, introOfficeZoom(0f), 1e-6f)
        assertEquals(1f, introOfficeZoom(1f), 1e-6f)
        val mid = introOfficeZoom(0.5f)
        assertTrue(mid > 1f && mid < 1.14f)
        // Ease-out: most of the settle happens early.
        assertTrue(mid < 1.07f)
    }

    @Test
    fun veilFlashesOnCutThenSwellsMidClear() {
        assertEquals(0.75f, introVeilAlpha(0f), 1e-6f)
        assertEquals(0f, introVeilAlpha(1f), 1e-6f)
        // Mid-beat swell still visible above the decayed flash.
        assertTrue(introVeilAlpha(0.5f) > 0.3f)
    }

    @Test
    fun captionShowsPerBeatAndFadesEarlyInClear() {
        assertEquals("Flying to Pixel Bureau", introCaptionText(IntroBeat.CITY))
        assertEquals("Arriving at the bureau", introCaptionText(IntroBeat.APPROACH))
        assertEquals("Opening the office", introCaptionText(IntroBeat.CLEAR))
        assertEquals(1f, introCaptionAlpha(IntroBeat.CITY, 1f), 0f)
        assertEquals(1f, introCaptionAlpha(IntroBeat.APPROACH, 1f), 0f)
        assertEquals(1f, introCaptionAlpha(IntroBeat.CLEAR, 0f), 0f)
        assertEquals(0f, introCaptionAlpha(IntroBeat.CLEAR, 0.34f), 0f)
    }

    @Test
    fun dotsCycleEveryFourHundredMs() {
        assertEquals(0, introDots(0))
        assertEquals(1, introDots(400))
        assertEquals(3, introDots(1200))
        assertEquals(0, introDots(1600))
    }

    @Test
    fun scrollGrowsThenCrawlsContinuously() {
        val cityOnly = introScrollFrac(1100, null)
        assertEquals(1f, cityOnly, 1e-6f)
        // Approach handoff adds nothing at t=0 (caller freezes cityMs).
        assertEquals(cityOnly, introScrollFrac(1100, 0), 1e-6f)
        val mid = introScrollFrac(1100, IntroApproachMs / 2)
        val end = introScrollFrac(1100, IntroApproachMs)
        assertTrue(mid > cityOnly && end > mid)
        assertEquals(0.25f, end - cityOnly, 1e-5f)
    }

    @Test
    fun wrapStaysInSpawnBand() {
        var s = -2f
        while (s <= 4f) {
            val y = introWrapY(0.35f, s, 1f)
            assertTrue(y >= -0.15f && y <= 1.15f)
            s += 0.07f
        }
    }

    @Test
    fun gridRowsStayInGroundBand() {
        var s = -2f
        while (s <= 6f) {
            val rows = introGridRows(s)
            assertEquals(5, rows.size)
            for (row in rows) {
                assertTrue(row.yFrac >= 0.36f && row.yFrac <= 1.08f)
                assertTrue(row.depth in 0f..1f)
            }
            s += 0.11f
        }
    }

    @Test
    fun gridColumnsWrapWithDrift() {
        var s = -2f
        while (s <= 6f) {
            for (c in 0 until 7) {
                val x = introGridColX(c, s)
                assertTrue(x >= -0.2f && x <= 1.2f)
            }
            s += 0.13f
        }
        // Columns actually move with scroll.
        assertTrue(introGridColX(0, 0f) != introGridColX(0, 1f))
    }

    @Test
    fun blockLooksAreBounded() {
        for (c in 0 until 7) {
            for (r in 0 until 5) {
                assertTrue(introBlockTone(c, r) in 0..3)
                assertTrue(introBlockDetail(c, r) in 0..2)
            }
        }
    }

    @Test
    fun cityFliesOverTheBayThenDivesAtTheTower() {
        // CITY opens over the bay and pans toward the towers.
        val wide = introCityPlate(0f, null)
        assertEquals(1f, wide.zoom, 1e-6f)
        assertEquals(0.62f, wide.focusX, 1e-6f)
        assertEquals(0.44f, wide.focusY, 1e-6f)
        val arrived = introCityPlate(1f, null)
        assertEquals(1.08f, arrived.zoom, 1e-6f)
        assertEquals(0.46f, arrived.focusX, 1e-6f)
        assertEquals(0.47f, arrived.focusY, 1e-6f)
        // APPROACH handoff is continuous, then dives at the bureau tower.
        val diveStart = introCityPlate(1f, 0f)
        assertEquals(arrived.zoom, diveStart.zoom, 1e-6f)
        assertEquals(arrived.focusX, diveStart.focusX, 1e-6f)
        assertEquals(arrived.focusY, diveStart.focusY, 1e-6f)
        val close = introCityPlate(1f, 1f)
        assertEquals(2.05f, close.zoom, 1e-6f)
        assertEquals(0.39f, close.focusX, 1e-6f)
        assertEquals(0.40f, close.focusY, 1e-6f)
        assertTrue(introCityPlate(1f, 0.5f).zoom in arrived.zoom..close.zoom)
        assertEquals(1f, introCityZoom(null), 0f)
        assertEquals(1f, introCityZoom(0f), 0f)
        assertEquals(2.1f, introCityZoom(1f), 1e-6f)
        assertTrue(introCityZoom(0.5f) < 1.6f)
    }

    @Test
    fun coverFrameAlwaysFillsTheScreen() {
        // Portrait phone, landscape tablet, square: never any bars.
        val shapes = listOf(
            Triple(1080f, 2400f, "portrait"),
            Triple(2400f, 1080f, "landscape"),
            Triple(1000f, 1000f, "square"),
        )
        val plates = listOf(
            introCityPlate(0f, null),
            introCityPlate(1f, null),
            introCityPlate(1f, 0.5f),
            introCityPlate(1f, 1f),
        )
        for ((cw, ch, shape) in shapes) {
            for (plate in plates) {
                val f = introCoverFrame(cw, ch, 1672f, 941f, plate)
                assertTrue("$shape covers width", f.width >= cw - 1e-3f)
                assertTrue("$shape covers height", f.height >= ch - 1e-3f)
                assertTrue("$shape left inside", f.left <= 0f && f.left + f.width >= cw - 1e-3f)
                assertTrue("$shape top inside", f.top <= 0f && f.top + f.height >= ch - 1e-3f)
                assertTrue(f.scale > 0f)
            }
        }
        // Centered focus lands the plate point at the canvas center.
        val f = introCoverFrame(1080f, 2400f, 1672f, 941f, IntroPlate(1f, 0.5f, 0.5f))
        assertEquals(540f, f.left + 0.5f * f.width, 1f)
        assertEquals(1200f, f.top + 0.5f * f.height, 1f)
    }

    @Test
    fun shipsSailTheirLanesSteadily() {
        assertEquals(3, IntroShipLanes.size)
        for (lane in IntroShipLanes) {
            val (sx, sy) = introShipAt(lane, 0)
            assertEquals(lane.x0, sx, 1e-6f)
            assertEquals(lane.y0, sy, 1e-6f)
            val (ex, ey) = introShipAt(lane, IntroSailMs)
            assertEquals(lane.x1, ex, 1e-4f)
            assertEquals(lane.y1, ey, 1e-4f)
            // Halfway through the sail, halfway down the lane.
            val (mx, my) = introShipAt(lane, IntroSailMs / 2)
            assertEquals((lane.x0 + lane.x1) / 2f, mx, 1e-4f)
            assertEquals((lane.y0 + lane.y1) / 2f, my, 1e-4f)
            // Clamped past the end.
            assertEquals(ex, introShipAt(lane, IntroSailMs + 99999).first, 1e-6f)
        }
    }

    @Test
    fun wheelSpinsSteadily() {
        assertEquals(0f, introWheelAngle(0), 0f)
        assertEquals(25f, introWheelAngle(1000), 1e-4f)
        assertEquals(85f, introWheelAngle(3400), 1e-3f)
        assertTrue(introWheelAngle(20_000) in 0f..360f)
    }

    @Test
    fun sparklesAreDeterministicAndTwinkle() {
        val a = introSparkles()
        val b = introSparkles()
        assertEquals(56, a.size)
        assertEquals(a, b)
        for (s in a) {
            assertTrue(s.x in 0f..1f && s.y in 0f..1f)
            assertTrue(s.size in 0.6f..1.4f)
            var t = 0L
            while (t <= 5_000L) {
                assertTrue(introSparkleAlpha(s, t) in 0f..1f)
                t += 173L
            }
        }
        // Every quad holds flecks.
        for (q in IntroSparkQuads) {
            assertTrue(a.any { it.x >= q.x0 && it.x <= q.x1 && it.y >= q.y0 && it.y <= q.y1 })
        }
        // It actually twinkles instead of sitting still.
        val s = a.first()
        assertTrue(introSparkleAlpha(s, 0) != introSparkleAlpha(s, 500))
    }

    @Test
    fun birdsFlyAndFlap() {
        val birds = introBirds()
        assertEquals(3, birds.size)
        for (b in birds) {
            val d0 = introBirdAt(b, 0)
            val d1 = introBirdAt(b, 2000)
            assertTrue(d1.x > d0.x)
            assertTrue(d0.flap in -1f..1f)
            assertTrue(introBirdAt(b, 300).flap != introBirdAt(b, 900).flap)
        }
    }

    @Test
    fun bankScrollsAndDissolvesOnTheDive() {
        assertEquals(0f, introBankOffset(0), 0f)
        assertTrue(introBankOffset(4500) > 0.4f && introBankOffset(4500) < 0.6f)
        assertEquals(0f, introBankOffset(9000), 1e-6f)
        assertEquals(1f, introBankAlpha(null), 0f)
        assertEquals(1f, introBankAlpha(0f), 1e-6f)
        assertEquals(0f, introBankAlpha(1f), 1e-6f)
        assertTrue(introBankAlpha(0.5f) in 0f..1f)
    }

    @Test
    fun puffScrollRushesOnApproach() {
        val cityOnly = introPuffScroll(2200, null, null)
        assertEquals(introScrollFrac(2200, null), cityOnly, 1e-6f)
        // Dive start adds nothing; the full dive adds the whole rush.
        assertEquals(cityOnly, introPuffScroll(2200, 0, 0f), 1e-6f)
        assertEquals(0.6f, introPuffScroll(2200, 1200, 1f) - introScrollFrac(2200, 1200), 1e-6f)
    }

    @Test
    fun driftEdgeFadeHidesWrapTeleports() {
        assertEquals(0f, introDriftEdgeFade(-0.15f, 0.5f), 0f)
        assertEquals(0f, introDriftEdgeFade(1.15f, 0.5f), 0f)
        assertEquals(0f, introDriftEdgeFade(0.5f, -0.15f), 0f)
        assertEquals(0f, introDriftEdgeFade(0.5f, 1.15f), 0f)
        assertEquals(1f, introDriftEdgeFade(0.5f, 0.5f), 1e-6f)
        assertEquals(1f, introDriftEdgeFade(0f, 0f), 1e-6f)
        assertEquals(1f, introDriftEdgeFade(1f, 1f), 1e-6f)
        assertTrue(introDriftEdgeFade(-0.05f, 0.5f) in 0f..1f)
    }

    @Test
    fun driftFleetIsDeterministic() {
        val a = introDriftSeeds()
        val b = introDriftSeeds()
        assertEquals(12, a.size)
        assertEquals(a, b)
        assertTrue(a.any { it.layer == 0 } && a.any { it.layer == 2 })
    }

    @Test
    fun driftStaysInWrapBand() {
        for (cloud in introDriftSeeds()) {
            var s = -2f
            while (s <= 6f) {
                val (x, y) = introDriftAt(cloud, s)
                assertTrue(x >= -0.15f && x <= 1.15f)
                assertTrue(y >= -0.15f && y <= 1.15f)
                s += 0.23f
            }
        }
    }

    @Test
    fun towerGrowsFromSpeckToFilling() {
        assertEquals(0.10f, introTargetScale(0f, null), 1e-6f)
        assertEquals(0.16f, introTargetScale(1f, null), 1e-6f)
        assertEquals(0.16f, introTargetScale(1f, 0f), 1e-6f)
        assertEquals(1.35f, introTargetScale(1f, 1f), 1e-6f)
        // Accelerating ease-in: well under half grown at halfway.
        assertTrue(introTargetScale(1f, 0.5f) < 1.0f)
        val y0 = introTargetYFrac(0f, null)
        val y1 = introTargetYFrac(1f, 1f)
        assertTrue(y0 < 0.5f && y1 > 0.9f)
    }

    @Test
    fun cloudFleetIsDeterministic() {
        val a = introCloudSeeds()
        val b = introCloudSeeds()
        assertEquals(IntroCloudCount, a.size)
        assertEquals(a, b)
        assertTrue(a.all { it.speedPerMs > 0f && it.size in 0.7f..1.3f })
    }

    @Test
    fun cloudLoopsStayInBounds() {
        for (cloud in introCloudSeeds()) {
            var t = 0L
            while (t <= 20_000L) {
                val d = introCloudAt(cloud, t)
                assertTrue(d.dist in 0f..1f)
                assertTrue(d.scale > 0f)
                assertTrue(d.alpha in 0f..1f)
                t += 333L
            }
        }
    }

    @Test
    fun clearCloudsRushOutAndVanish() {
        val cloud = introCloudSeeds().first()
        val base = introCloudAt(cloud, 1000L)
        val mid = introClearCloud(base, 0.5f)!!
        assertTrue(mid.dist > base.dist)
        assertTrue(mid.alpha < base.alpha)
        assertNull(introClearCloud(base, 1f))
    }
}
