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

/** Two-copy layout that tiles [tileWidth] across [dstW] with no gap/overlap. */
data class HeroTileLayout(val firstX: Int, val firstW: Int, val secondX: Int, val secondW: Int)

/**
 * Display-pixel layout for a wrapped tile: the first copy's width derives
 * from the rounded offsets so both copies abut exactly (independent rounding
 * would flicker a 1px gap/overlap line at the seam while scrolling).
 */
fun heroTileLayout(shiftPx: Int, tileWidth: Int, dstW: Int): HeroTileLayout {
    val w = tileWidth.coerceAtLeast(1)
    val scale = dstW / w.toFloat()
    val firstX = ((shiftPx - w) * scale).roundToInt()
    val secondX = (shiftPx * scale).roundToInt()
    return HeroTileLayout(firstX, secondX - firstX, secondX, dstW)
}
