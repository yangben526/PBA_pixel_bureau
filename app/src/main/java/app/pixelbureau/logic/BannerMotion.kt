package app.pixelbureau.logic

import kotlin.math.cos

/**
 * Loop math for the AgentHire banner "Hire" animation (PixelHire page).
 * Scale-only, drawn anchored at the slice's bottom-center. Amplitude measured
 * against the 2000x667 art to clear "Agent" beside it, the tagline below,
 * and the profile card.
 */

/** Scale added at the loop peak; logo scale ranges 1.0..1.012. */
const val AgentHireTitleBreathe = 0.006f

/** Logo scale at [phase] radians; 1.0 at rest, peaking mid-loop. */
fun agentHireTitleScale(phase: Float): Float =
    1f + AgentHireTitleBreathe * (1f - cos(phase))
