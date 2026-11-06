package app.pixelbureau.logic

import kotlin.math.pow
import kotlin.random.Random

/**
 * Loop math for the startup cutscene (office canvas). Beats: CITY (the
 * aerial plate, flying in) -> APPROACH (zoom toward the towers) -> CLEAR
 * (flash, clouds part, the office settles in) -> DONE (plain office).
 *
 * CITY and APPROACH run on fixed timers; CLEAR waits for spritesReady.
 * Taps skip forward one beat (CLEAR still waits for loading).
 */

/** CITY beat: soaring through the streets, in ms. */
const val IntroCityMs = 2_200L

/** APPROACH beat: flying at the bureau tower, in ms. */
const val IntroApproachMs = 1_200L

/** CLEAR beat duration in ms. */
const val IntroRevealMs = 900L

/** Cutscene beats in play order. */
enum class IntroBeat { CITY, APPROACH, CLEAR, DONE }

/** CITY progress 0..1 from ms since the cutscene started. */
fun introCityT(cityMs: Long): Float =
    (cityMs.toFloat() / IntroCityMs.toFloat()).coerceIn(0f, 1f)

/** APPROACH progress 0..1 from ms since the approach started. */
fun introApproachT(approachMs: Long): Float =
    (approachMs.toFloat() / IntroApproachMs.toFloat()).coerceIn(0f, 1f)

/** CLEAR progress 0..1 from ms since the reveal started. */
fun introRevealT(revealElapsedMs: Long): Float =
    (revealElapsedMs.toFloat() / IntroRevealMs.toFloat()).coerceIn(0f, 1f)

/** Office zoom during CLEAR: 1.14 settling to 1.0 with an ease-out cubic. */
fun introOfficeZoom(revealT: Float): Float {
    val t = revealT.coerceIn(0f, 1f)
    return 1f + 0.14f * (1f - t).pow(3)
}

/**
 * White veil during CLEAR: a strong flash covering the tower-to-office cut,
 * then a soft swell peaking mid-beat as the clouds part.
 */
fun introVeilAlpha(revealT: Float): Float {
    val t = revealT.coerceIn(0f, 1f)
    val flash = (1f - t / 0.18f).coerceIn(0f, 1f) * 0.75f
    val swell = 0.45f * (1f - (2f * t - 1f) * (2f * t - 1f))
    return maxOf(flash, swell).coerceIn(0f, 1f)
}

/** Caption chip alpha: full until CLEAR, gone in its first third. */
fun introCaptionAlpha(beat: IntroBeat, revealT: Float): Float {
    if (beat != IntroBeat.CLEAR) return 1f
    return (1f - revealT.coerceIn(0f, 1f) / 0.33f).coerceIn(0f, 1f)
}

/** Caption line per beat (ellipsis dots appended by the caller). */
fun introCaptionText(beat: IntroBeat): String = when (beat) {
    IntroBeat.CITY -> "Flying to Pixel Bureau"
    IntroBeat.APPROACH -> "Arriving at the bureau"
    IntroBeat.CLEAR, IntroBeat.DONE -> "Opening the office"
}

/** Animated ellipsis dots 0..3, stepping every 400ms. */
fun introDots(elapsedMs: Long): Int =
    ((elapsedMs / 400L) % 4L).toInt()

/**
 * Street scroll in screen heights: fast linear rush during CITY, then an
 * ease-out crawl during APPROACH. [cityMs] must freeze once APPROACH starts
 * so the handoff is continuous.
 */
fun introScrollFrac(cityMs: Long, approachMs: Long?): Float {
    val city = cityMs.coerceAtLeast(0L).toFloat() / 1100f
    if (approachMs == null) return city
    val t = (approachMs.coerceAtLeast(0L).toFloat() / IntroApproachMs.toFloat()).coerceIn(0f, 1f)
    return city + 0.25f * (1f - (1f - t).pow(3))
}

/** Wrap a 0..1 lane position into -0.15..1.15 spawn/exit space. */
fun introWrapY(yFrac: Float, scrollFrac: Float, speed: Float): Float {
    var y = (yFrac + scrollFrac * speed) % 1.3f
    if (y < 0f) y += 1.3f
    return y - 0.15f
}

/** One rooftop row of the city sprawl below, 0 far .. 1 near. */
data class GridRow(val yFrac: Float, val depth: Float)

/**
 * Rooftop grid rows across the ground band (0.36..1.08 of view height),
 * scrolling toward the viewer with [scrollFrac] and wrapping seamlessly.
 */
fun introGridRows(scrollFrac: Float, rows: Int = 5): List<GridRow> {
    val top = 0.36f
    val span = 0.72f
    return List(rows) { r ->
        var y = (r.toFloat() / rows * span + scrollFrac * 0.5f) % span
        if (y < 0f) y += span
        GridRow(top + y, (y / span).coerceIn(0f, 1f))
    }
}

/** Rooftop column X (fraction of view width) with diagonal sideways drift. */
fun introGridColX(col: Int, scrollFrac: Float, cols: Int = 7): Float {
    val span = 1.4f
    val spacing = span / cols
    var x = (col * spacing + scrollFrac * 0.22f) % span
    if (x < 0f) x += span
    return x - 0.2f
}

/** Deterministic rooftop look: tone 0..3 (3 = park green). */
fun introBlockTone(col: Int, row: Int): Int = absInt(col * 73 + row * 151) % 4

/** Deterministic rooftop detail: 0 plain, 1 roof box, 2 antenna. */
fun introBlockDetail(col: Int, row: Int): Int = absInt(col * 37 + row * 91) % 3

private fun absInt(v: Int): Int = if (v < 0) -v else v

/**
 * How the aerial plate is framed. [zoom] multiplies the cover fit (1 fills
 * the screen exactly). [focusX] and [focusY] are 0..1 in the image, where
 * the camera looks. CITY flies over the bay toward the towers (the crop
 * window travels across the plate); APPROACH dives at the bureau tower.
 */
data class IntroPlate(val zoom: Float, val focusX: Float, val focusY: Float)

/** Plate focus where CITY hands off to APPROACH. */
const val IntroCityEndX = 0.46f

/** Plate focus where CITY hands off to APPROACH. */
const val IntroCityEndY = 0.47f

/** Gentle push-in by the end of CITY. */
const val IntroCityEndZoom = 1.08f

/** Bureau tower dive target, plate fractions. */
const val IntroTowerX = 0.39f

/** Bureau tower dive target, plate fractions. */
const val IntroTowerY = 0.40f

/** Dive zoom filling the screen with the tower. */
const val IntroDiveZoom = 2.05f

fun introCityPlate(cityT: Float, approachT: Float?): IntroPlate {
    val city = cityT.coerceIn(0f, 1f)
    if (approachT == null) {
        return IntroPlate(
            1f + (IntroCityEndZoom - 1f) * city,
            0.62f + (IntroCityEndX - 0.62f) * city,
            0.44f + (IntroCityEndY - 0.44f) * city,
        )
    }
    val dive = approachT.coerceIn(0f, 1f).let { it * it }
    return IntroPlate(
        IntroCityEndZoom + (IntroDiveZoom - IntroCityEndZoom) * dive,
        IntroCityEndX + (IntroTowerX - IntroCityEndX) * dive,
        IntroCityEndY + (IntroTowerY - IntroCityEndY) * dive,
    )
}

/** Cover-fit placement of the plate on the canvas: never any bars. */
data class IntroFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** Plate px -> screen px. */
    val scale: Float,
)

/**
 * Cover-fit [frame] so the cutscene always fills the screen: the plate is
 * scaled to cover the canvas (times [frame]'s zoom) and the focus point is
 * centered unless it would expose an edge. Plate-space elements (ships,
 * wheel, sparkles) share this one transform.
 */
fun introCoverFrame(
    canvasW: Float,
    canvasH: Float,
    plateW: Float,
    plateH: Float,
    frame: IntroPlate,
): IntroFrame {
    val zoom = frame.zoom.coerceAtLeast(1f)
    val scale = maxOf(canvasW / plateW, canvasH / plateH) * zoom
    val dw = plateW * scale
    val dh = plateH * scale
    // dw >= canvasW and dh >= canvasH up to float dust; pin the range.
    val left = (canvasW / 2f - frame.focusX * dw).coerceIn(minOf(canvasW - dw, 0f), 0f)
    val top = (canvasH / 2f - frame.focusY * dh).coerceIn(minOf(canvasH - dh, 0f), 0f)
    return IntroFrame(left, top, dw, dh, scale)
}

/** City zoom while descending onto the tower during APPROACH. */
fun introCityZoom(approachT: Float?): Float {
    if (approachT == null) return 1f
    val t = approachT.coerceIn(0f, 1f)
    return 1f + 1.1f * t * t
}

/** One diagonal drift cloud for the airplane POV. */
data class DriftCloud(
    val x0: Float,
    val y0: Float,
    /** Loop speed; Y drifts at 0.35x for the diagonal. */
    val speed: Float,
    val size: Float,
    val layer: Int,
)

/** Deterministic drift fleet: far, mid and near whoosh layers. */
fun introDriftSeeds(): List<DriftCloud> {
    val rng = Random(11)
    return List(12) {
        val layer = it % 3
        DriftCloud(
            x0 = rng.nextFloat(),
            y0 = rng.nextFloat() * 0.9f,
            speed = 0.25f + layer * 0.22f + rng.nextFloat() * 0.08f,
            size = 0.45f + layer * 0.35f + rng.nextFloat() * 0.15f,
            layer = layer,
        )
    }
}

/** Diagonal drift position (fractions) streaming left to right, downward. */
fun introDriftAt(cloud: DriftCloud, scrollFrac: Float): Pair<Float, Float> =
    Pair(
        introWrapY(cloud.x0, scrollFrac, cloud.speed),
        introWrapY(cloud.y0, scrollFrac, cloud.speed * 0.35f),
    )

/**
 * Puff scroll: the plate flight distance plus an extra rush once the dive
 * starts, so foreground clouds stream past instead of crawling.
 */
fun introPuffScroll(cityMs: Long, approachMs: Long?, approachT: Float?): Float {
    val base = introScrollFrac(cityMs, approachMs)
    if (approachT == null) return base
    return base + approachT.coerceIn(0f, 1f) * 0.6f
}

/**
 * Edge fade 0..1 hiding the drift wrap teleport: puffs dissolve as they
 * near the -0.15/1.15 spawn band edges and are solid inside 0..1.
 */
fun introDriftEdgeFade(x: Float, y: Float): Float {
    val fx = ((x + 0.15f) / 0.15f).coerceIn(0f, 1f) * ((1.15f - x) / 0.15f).coerceIn(0f, 1f)
    val fy = ((y + 0.15f) / 0.15f).coerceIn(0f, 1f) * ((1.15f - y) / 0.15f).coerceIn(0f, 1f)
    return minOf(fx, fy)
}

/** One vessel's sail from its plate berth, plate px. */
data class IntroShipLane(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
)

/**
 * The three sailing ships (cruise liner, ferry A, ferry B). Starts match
 * the extracted hull centers exactly so frame one is seamless.
 */
val IntroShipLanes = listOf(
    IntroShipLane(1133f, 618f, 1058f, 618f),
    IntroShipLane(1029.5f, 513f, 968f, 515f),
    IntroShipLane(963f, 563.5f, 905f, 559f),
)

/** Ms over which the lanes play: the whole CITY + APPROACH flight. */
const val IntroSailMs = IntroCityMs + IntroApproachMs

/** Hull center in plate px at [elapsedMs]: a steady sail, clamped. */
fun introShipAt(lane: IntroShipLane, elapsedMs: Long): Pair<Float, Float> {
    val t = (elapsedMs.coerceAtLeast(0L).toFloat() / IntroSailMs.toFloat()).coerceIn(0f, 1f)
    return Pair(lane.x0 + (lane.x1 - lane.x0) * t, lane.y0 + (lane.y1 - lane.y0) * t)
}

/** Ferris wheel hub, plate px (measured rim-circle fit). */
const val IntroWheelHubX = 1078f

/** Ferris wheel hub, plate px (measured rim-circle fit). */
const val IntroWheelHubY = 313f

/** Rim spin, degrees per second. */
const val IntroWheelDegPerSec = 25f

/** Rim rotation 0..360 at [elapsedMs]. */
fun introWheelAngle(elapsedMs: Long): Float =
    (elapsedMs.coerceAtLeast(0L).toFloat() / 1000f * IntroWheelDegPerSec) % 360f

/** Open-water region for sun glitter, plate fractions. */
data class IntroSparkQuad(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** Right bay, lower bay, far bay, center bay. */
val IntroSparkQuads = listOf(
    IntroSparkQuad(0.76f, 0.40f, 0.88f, 0.52f),
    IntroSparkQuad(0.57f, 0.60f, 0.70f, 0.69f),
    IntroSparkQuad(0.30f, 0.22f, 0.55f, 0.30f),
    IntroSparkQuad(0.49f, 0.355f, 0.55f, 0.40f),
)

/** One sun-glitter fleck: plate fractions plus twinkle timing. */
data class IntroSparkle(
    val x: Float,
    val y: Float,
    val phase: Float,
    /** Twinkle cycles per second. */
    val speed: Float,
    /** Base size multiplier 0.6..1.4. */
    val size: Float,
)

/** Deterministic glitter field across the quads. */
fun introSparkles(perQuad: Int = 14): List<IntroSparkle> {
    val rng = Random(21)
    return IntroSparkQuads.flatMap { q ->
        List(perQuad) {
            IntroSparkle(
                x = q.x0 + rng.nextFloat() * (q.x1 - q.x0),
                y = q.y0 + rng.nextFloat() * (q.y1 - q.y0),
                phase = rng.nextFloat() * 6.2831855f,
                speed = 0.8f + rng.nextFloat() * 1.6f,
                size = 0.6f + rng.nextFloat() * 0.8f,
            )
        }
    }
}

/** Glitter twinkle 0..1: sharp bright peaks, soft dark troughs. */
fun introSparkleAlpha(sparkle: IntroSparkle, elapsedMs: Long): Float {
    val t = elapsedMs.coerceAtLeast(0L).toFloat() / 1000f
    val s = kotlin.math.sin(t * sparkle.speed * 6.2831855f + sparkle.phase) * 0.5f + 0.5f
    return s * s
}

/** One gull: plate-fraction berth plus flight timing. */
data class IntroBird(val x0: Float, val y0: Float, val speed: Float, val phase: Float)

/** Three gulls over the bay. */
fun introBirds(): List<IntroBird> = listOf(
    IntroBird(0.50f, 0.10f, 0.008f, 0f),
    IntroBird(0.56f, 0.13f, 0.011f, 2.1f),
    IntroBird(0.62f, 0.09f, 0.007f, 4.2f),
)

/** Gull flight at [elapsedMs]: plate fractions plus wing flap -1..1. */
data class IntroBirdDraw(val x: Float, val y: Float, val flap: Float)

fun introBirdAt(bird: IntroBird, elapsedMs: Long): IntroBirdDraw {
    val t = elapsedMs.coerceAtLeast(0L).toFloat() / 1000f
    val x = bird.x0 + t * bird.speed
    val y = bird.y0 + kotlin.math.sin(t * 1.7f + bird.phase) * 0.006f
    val flap = kotlin.math.sin(t * 9f + bird.phase * 2f)
    return IntroBirdDraw(x, y, flap)
}

/** Cloud-bank scroll 0..1 looping every 9s across the bottom of the sky. */
fun introBankOffset(elapsedMs: Long): Float =
    (elapsedMs.coerceAtLeast(0L).toFloat() / 9000f) % 1f

/** The bank dissolves as the camera dives under it during APPROACH. */
fun introBankAlpha(approachT: Float?): Float {
    if (approachT == null) return 1f
    return (1f - approachT.coerceIn(0f, 1f) * 1.4f).coerceIn(0f, 1f)
}

/**
 * Bureau tower scale: a speck on the horizon through CITY, then diving
 * at the viewer with an accelerating ease through APPROACH.
 */
fun introTargetScale(cityT: Float, approachT: Float?): Float {
    if (approachT == null) return 0.10f + 0.06f * cityT.coerceIn(0f, 1f)
    val t = approachT.coerceIn(0f, 1f)
    return 0.16f + 1.19f * t * t
}

/**
 * Tower baseline Y (fraction of view height): horizon haze during CITY,
 * then swooping down to the entrance as the camera arrives.
 */
fun introTargetYFrac(cityT: Float, approachT: Float?): Float {
    if (approachT == null) return 0.40f + 0.02f * cityT.coerceIn(0f, 1f)
    return 0.42f + 0.60f * approachT.coerceIn(0f, 1f)
}

/** Zoom-through cloud count for the CLEAR beat. */
const val IntroCloudCount = 14

/** One zoom-through cloud: fixed flight path, seeded for determinism. */
data class IntroCloud(
    /** Flight direction from screen center, radians. */
    val angleRad: Float,
    /** Distance travelled per ms (0..1 loop space). */
    val speedPerMs: Float,
    /** Loop offset 0..1 so clouds start spread out. */
    val phase: Float,
    /** Base size multiplier 0.7..1.3. */
    val size: Float,
)

/** Deterministic cloud fleet; same order and paths every launch. */
fun introCloudSeeds(count: Int = IntroCloudCount): List<IntroCloud> {
    val rng = Random(7)
    return List(count) {
        IntroCloud(
            angleRad = rng.nextFloat() * 6.2831855f,
            // Full center-to-edge traverse in ~2.5-4.5s per lane.
            speedPerMs = 0.00022f + rng.nextFloat() * 0.00018f,
            phase = rng.nextFloat(),
            size = 0.7f + rng.nextFloat() * 0.6f,
        )
    }
}

/** Per-frame cloud placement in 0..1 loop space plus draw scale/alpha. */
data class CloudDraw(val dist: Float, val scale: Float, val alpha: Float)

/**
 * Cloud placement at [loopMs]: distance wraps 0..1 (respawn at center),
 * scale grows with approach, alpha fades in at spawn and out at exit.
 */
fun introCloudAt(cloud: IntroCloud, loopMs: Long): CloudDraw {
    var dist = (cloud.phase + loopMs.toFloat() * cloud.speedPerMs) % 1f
    if (dist < 0f) dist += 1f
    val scale = cloud.size * (0.35f + dist * 2.0f)
    val fadeIn = (dist / 0.08f).coerceIn(0f, 1f)
    val fadeOut = ((1f - dist) / 0.18f).coerceIn(0f, 1f)
    return CloudDraw(dist, scale, minOf(fadeIn, fadeOut))
}

/**
 * CLEAR-beat adjustment: clouds rush outward and fade with [revealT].
 * Returns null once fully gone so the caller can stop drawing.
 */
fun introClearCloud(draw: CloudDraw, revealT: Float): CloudDraw? {
    val t = revealT.coerceIn(0f, 1f)
    if (t >= 1f) return null
    val alpha = draw.alpha * (1f - t)
    if (alpha <= 0.01f) return null
    return CloudDraw(draw.dist + t * 0.55f, draw.scale, alpha)
}
