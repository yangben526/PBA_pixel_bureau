package app.pixelbureau.sprites

import kotlin.math.min

/**
 * Blade disk vs the square housing, as a fraction of the housing half-side.
 * Corner screws sit outside this circle. Wind streaks are not part of the housing.
 */
internal const val ServerFanRotorFraction = 0.80f

internal class ServerFanSplit(
    val frame: IntArray,
    val rotor: IntArray,
    val width: Int,
    val height: Int,
    /** Housing center in pixels. The blades spin around this, not the bitmap center. */
    val centerX: Float,
    val centerY: Float,
)

/**
 * Split a keyed fan sprite into a still frame and a blade disk of the same size.
 * The frame keeps the square housing and screws. Wind outside the housing is dropped.
 * The rotor is the disk inside the opening.
 */
internal fun splitServerFan(pixels: IntArray, width: Int, height: Int): ServerFanSplit {
    val count = width * height
    val frame = IntArray(count)
    val rotor = IntArray(count)
    if (width <= 1 || height <= 1 || pixels.size < count) {
        return ServerFanSplit(frame, rotor, width, height, width / 2f, height / 2f)
    }
    val housing = BooleanArray(count)
    val seed = fanSeed(pixels, width, height)
    if (seed < 0) {
        return ServerFanSplit(frame, rotor, width, height, width / 2f, height / 2f)
    }
    val queue = IntArray(count)
    var head = 0
    var tail = 0
    queue[tail++] = seed
    housing[seed] = true
    var minX = width
    var minY = height
    var maxX = 0
    var maxY = 0
    while (head < tail) {
        val index = queue[head++]
        val x = index % width
        val y = index / width
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
        if (x > 0) enqueueFan(pixels, housing, queue, index - 1, tail).also { tail = it }
        if (x + 1 < width) enqueueFan(pixels, housing, queue, index + 1, tail).also { tail = it }
        if (y > 0) enqueueFan(pixels, housing, queue, index - width, tail).also { tail = it }
        if (y + 1 < height) enqueueFan(pixels, housing, queue, index + width, tail).also { tail = it }
    }
    val centerX = (minX + maxX) / 2f
    val centerY = (minY + maxY) / 2f
    val half = min(maxX - minX, maxY - minY) / 2f
    val radius = half * ServerFanRotorFraction
    val radiusSq = radius * radius
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val color = pixels[index]
            if (fanEmpty(color) || !housing[index]) continue
            val dx = x - centerX
            val dy = y - centerY
            val opaque = (0xFF shl 24) or (color and 0x00FFFFFF)
            if (dx * dx + dy * dy <= radiusSq) {
                rotor[index] = opaque
            } else {
                frame[index] = opaque
            }
        }
    }
    return ServerFanSplit(frame, rotor, width, height, centerX, centerY)
}

private fun fanEmpty(color: Int): Boolean {
    return (color ushr 24) == 0 || isSpriteKey(color)
}

private fun fanSeed(pixels: IntArray, width: Int, height: Int): Int {
    val cx = width / 2
    val cy = height / 2
    val limit = maxOf(width, height)
    var radius = 0
    while (radius < limit) {
        val y0 = (cy - radius).coerceAtLeast(0)
        val y1 = (cy + radius).coerceAtMost(height - 1)
        val x0 = (cx - radius).coerceAtLeast(0)
        val x1 = (cx + radius).coerceAtMost(width - 1)
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (radius > 0 && x != x0 && x != x1 && y != y0 && y != y1) continue
                val index = y * width + x
                if (!fanEmpty(pixels[index])) return index
            }
        }
        radius++
    }
    return -1
}

private fun enqueueFan(
    pixels: IntArray,
    housing: BooleanArray,
    queue: IntArray,
    index: Int,
    tail: Int,
): Int {
    if (housing[index] || fanEmpty(pixels[index])) return tail
    housing[index] = true
    queue[tail] = index
    return tail + 1
}
