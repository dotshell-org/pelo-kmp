package eu.dotshell.pelo.generic.utils.location

import kotlin.math.abs

/**
 * Pure angular helpers for the compass heading (degrees clockwise from north). Kept free of any
 * sensor/platform type so they can be unit-tested and reused by every actual `HeadingProvider`.
 */

/** Wraps any angle into [0, 360). */
fun normalizeDegrees(deg: Float): Float {
    val m = deg % 360f
    return if (m < 0f) m + 360f else m
}

/** Signed shortest rotation from [from] to [to], in (-180, 180]. */
fun shortestAngleDelta(from: Float, to: Float): Float {
    val diff = (to - from + 540f) % 360f - 180f
    // (x % 360) can be -180 for exact opposite; normalize that to +180 so the range is (-180, 180].
    return if (diff <= -180f) diff + 360f else diff
}

/** Absolute shortest angular distance between two headings, in [0, 180]. */
fun angularDistance(a: Float, b: Float): Float = abs(shortestAngleDelta(a, b))

/**
 * Angular low-pass filter. Moves [previous] a fraction [alpha] of the way toward [next] along the
 * shortest arc (so 350° + 20° smooths toward ~355°, never sweeping the long way through 180°).
 * [alpha] is clamped to [0, 1]; a null [previous] (first sample) returns [next] normalized.
 */
fun smoothHeading(previous: Float?, next: Float, alpha: Float): Float {
    val target = normalizeDegrees(next)
    if (previous == null) return target
    val a = alpha.coerceIn(0f, 1f)
    return normalizeDegrees(previous + a * shortestAngleDelta(previous, target))
}
