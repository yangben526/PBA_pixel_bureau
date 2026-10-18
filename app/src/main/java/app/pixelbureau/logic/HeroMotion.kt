package app.pixelbureau.logic

import kotlin.math.roundToInt

/** Full-width loop durations for the Pixegle hero parallax layers. */
const val HeroCloudLoopMs = 12_000
const val HeroCityLoopMs = 30_000

/**
 * Endless rightward tile shift in bitmap pixels for loop [progress] 0..1.
 * Progress 1 wraps to 0 (identical frame: the tile repeats exactly).
 */
fun heroTileShift(progress: Float, tileWidth: Int): Int {
    val w = tileWidth.coerceAtLeast(1)
    return ((progress.coerceIn(0f, 1f) * w).roundToInt() % w)
}
