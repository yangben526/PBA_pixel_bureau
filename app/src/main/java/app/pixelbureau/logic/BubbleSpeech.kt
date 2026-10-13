package app.pixelbureau.logic

/** Overhead bubble size on the office map. 1 matches the original drawing. */
const val BubbleSpeechScaleDefault = 1f
const val BubbleSpeechScaleMin = 0.5f
const val BubbleSpeechScaleMax = 2f

fun coerceBubbleSpeechScale(scale: Float): Float {
    if (!scale.isFinite()) return BubbleSpeechScaleDefault
    return scale.coerceIn(BubbleSpeechScaleMin, BubbleSpeechScaleMax)
}

/**
 * Size of one overhead speech bubble.
 * [bottomFromSpriteTop] is negative when the bubble sits above the sprite.
 * It stays put as [scale] changes, so a larger bubble grows upward.
 */
internal data class BubbleSpeechLayout(
    val textSize: Float,
    val minText: Float,
    val maxWidth: Float,
    val height: Float,
    val corner: Float,
    val bottomFromSpriteTop: Float,
)

internal fun bubbleSpeechLayout(nameSize: Float, tileSpan: Float, scale: Float): BubbleSpeechLayout {
    val sized = coerceBubbleSpeechScale(scale)
    val base = nameSize * sized
    return BubbleSpeechLayout(
        textSize = base,
        minText = (12f * sized).coerceAtLeast(8f),
        maxWidth = (tileSpan * 3.4f * sized).coerceAtLeast(base * 4f),
        height = base * 1.35f,
        corner = base * 0.35f,
        bottomFromSpriteTop = -nameSize * 0.95f,
    )
}
