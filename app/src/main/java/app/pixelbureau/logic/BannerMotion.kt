package app.pixelbureau.logic

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Loop math for the AgentHire banner (PixelHire page), measured on the
 * 2000x667 art. Ambient loops (card bob, wrap-scrolling clouds, laptop
 * glow, coffee steam, "Hire" breathe) plus a 12s acting timeline for the
 * planted agent: blinks plus stepped integer hand poses.
 */

/** Scale added at the loop peak; logo scale ranges 1.0..1.012. */
const val AgentHireTitleBreathe = 0.006f

/** Logo scale at [phase] radians; 1.0 at rest, peaking mid-loop. */
fun agentHireTitleScale(phase: Float): Float =
    1f + AgentHireTitleBreathe * (1f - cos(phase))

/** Vertical bob amplitude in art pixels for the profile cards. */
const val AgentHireBobPx = 2f

const val AgentHireBobLoopMs = 3_200
const val AgentHireTitleLoopMs = 3_400

/** Bob offset at [phase] radians; 0 at rest, peaking mid-loop. */
fun agentHireBob(phase: Float): Float = AgentHireBobPx * sin(phase)

/** Full right-to-left cloud loop duration; the tile wraps seamlessly. */
const val AgentHireCloudLoopMs = 24_000

/** Cloud layer width and loop point in art pixels (see extractor). */
const val AgentHireCloudArtW = 2000
const val AgentHireCloudLoopX = 1535

/**
 * Wrapped tile offset in art pixels for cloud loop [progress] 0..1.
 * Progress 0 rests at the loop point (original cloud positions); progress
 * 1 wraps to the identical frame. Feed to [heroTileLayout] with two copies.
 */
fun agentHireCloudWrap(progress: Float): Int {
    var p = progress.coerceIn(0f, 1f)
    if (p >= 1f) p = 0f
    var o = AgentHireCloudLoopX - p * AgentHireCloudArtW
    if (o < 0f) o += AgentHireCloudArtW
    return o.roundToInt().coerceIn(0, AgentHireCloudArtW - 1)
}

private const val HireTwoPi = 6.2831855f
private const val HireTapHz = 5f

/** Acting-loop duration in ms; all windows below are fractions of it. */
const val AgentHireActLoopMs = 12_000

/** Smooth 0..1 window over [a, b] with [edge] ramps (loop progress). */
private fun hireWindow(p: Float, a: Float, b: Float, edge: Float = 0.015f): Float {
    if (p <= a || p >= b) return 0f
    val up = ((p - a) / edge).coerceIn(0f, 1f)
    val down = ((b - p) / edge).coerceIn(0f, 1f)
    val e = minOf(up, down)
    return e * e * (3f - 2f * e)
}

/** Lid coverage 0..1 at acting-loop [progress]; three quick blinks. */
fun agentHireBlink(progress: Float): Float = maxOf(
    hireWindow(progress, 0.08f, 0.10f, 0.004f),
    hireWindow(progress, 0.42f, 0.44f, 0.004f),
    hireWindow(progress, 0.75f, 0.77f, 0.004f),
)

/** Integer hand pose in art pixels: stepped taps, no smooth slides. */
data class HireHandPose(val x: Int, val y: Int)

/**
 * Hand pose at acting-loop [progress]: held lift on the present beat,
 * tap L/rest/R/rest cycle while speaking, rest otherwise.
 */
fun agentHireHandPose(progress: Float): HireHandPose {
    if (progress > 0.52f && progress < 0.64f) return HireHandPose(0, -5)
    if (progress > 0.80f && progress < 0.95f) {
        val t = progress * (AgentHireActLoopMs / 1000f)
        return when (floor(t * HireTapHz).toInt() % 4) {
            0 -> HireHandPose(-3, 0)
            2 -> HireHandPose(3, 0)
            else -> HireHandPose(0, 0)
        }
    }
    return HireHandPose(0, 0)
}

/** Slow laptop-logo glow pulse. */
const val AgentHireLogoLoopMs = 4_500
const val AgentHireLogoPeak = 0.45f

/** Logo overlay alpha at [phase] radians; 0 at rest. */
fun agentHireLogo(phase: Float): Float =
    AgentHireLogoPeak * (1f - cos(phase)) / 2f

/** Coffee-steam wisp loop. */
const val AgentHireSteamLoopMs = 3_000
const val AgentHireSteamX = 1165
const val AgentHireSteamTop = 524
const val AgentHireSteamRisePx = 55f
const val AgentHireSteamSwayPx = 5f
const val AgentHireSteamDriftPx = 18f
const val AgentHireSteamPeak = 0.62f

/** Parametric wisp transform: offset in art pixels, alpha, and scale. */
data class HireSteamWisp(val dx: Float, val dy: Float, val alpha: Float, val scale: Float)

/** Wisp [index] (0..2, staggered thirds) at steam-loop [progress] 0..1. */
fun agentHireSteam(progress: Float, index: Int): HireSteamWisp {
    var ph = (progress + index / 3f) % 1f
    if (ph < 0f) ph += 1f
    val dx = AgentHireSteamSwayPx * sin(HireTwoPi * ph + index * 2.1f) +
        AgentHireSteamDriftPx * ph
    val dy = -AgentHireSteamRisePx * ph
    val alpha = sin(HireTwoPi * ph / 2f) * AgentHireSteamPeak
    return HireSteamWisp(dx, dy, alpha, 0.7f + 0.9f * ph)
}

/** Layer origins in art pixels (bitmaps are cropped to these rects). */
const val AgentHireCard1Left = 1185
const val AgentHireCard1Top = 104
const val AgentHireCard2Left = 1386
const val AgentHireCard2Top = 53
const val AgentHireCard3Left = 1606
const val AgentHireCard3Top = 97
const val AgentHireGreeterLeft = 1342
const val AgentHireGreeterTop = 300
const val AgentHireHandLeft = 1480
const val AgentHireHandTop = 502
const val AgentHireLidLLeft = 1399
const val AgentHireLidLTop = 413
const val AgentHireLidRLeft = 1472
const val AgentHireLidRTop = 413
const val AgentHireLogoLeft = 1300
const val AgentHireLogoTop = 515

/** "Hire" slice rect in art pixels. */
const val AgentHireHireLeft = 679
const val AgentHireHireTop = 140
const val AgentHireHireRight = 1096
const val AgentHireHireBottom = 362

/** Measured neighbors the Hire slice must clear. */
const val AgentHireAgentRight = 674
const val AgentHireTaglineTop = 366
