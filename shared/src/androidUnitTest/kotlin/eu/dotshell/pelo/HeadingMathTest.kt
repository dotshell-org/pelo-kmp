package eu.dotshell.pelo

import eu.dotshell.pelo.generic.utils.location.angularDistance
import eu.dotshell.pelo.generic.utils.location.normalizeDegrees
import eu.dotshell.pelo.generic.utils.location.shortestAngleDelta
import eu.dotshell.pelo.generic.utils.location.smoothHeading
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingMathTest {

    private val eps = 1e-3f

    @Test fun normalizeWrapsIntoZeroTo360() {
        assertEquals(270f, normalizeDegrees(-90f), eps)
        assertEquals(90f, normalizeDegrees(450f), eps)
        assertEquals(0f, normalizeDegrees(360f), eps)
        assertEquals(0f, normalizeDegrees(0f), eps)
        assertEquals(359.5f, normalizeDegrees(359.5f), eps)
    }

    @Test fun shortestDeltaTakesTheShortArc() {
        assertEquals(30f, shortestAngleDelta(350f, 20f), eps)   // forward across north, not -330
        assertEquals(-30f, shortestAngleDelta(20f, 350f), eps)  // backward across north
        assertEquals(180f, shortestAngleDelta(0f, 180f), eps)   // exact opposite → +180
        assertEquals(-179f, shortestAngleDelta(0f, 181f), eps)
    }

    @Test fun angularDistanceIsAbsShortestArc() {
        assertEquals(30f, angularDistance(350f, 20f), eps)
        assertEquals(20f, angularDistance(10f, 350f), eps)
        assertEquals(180f, angularDistance(0f, 180f), eps)
    }

    @Test fun smoothHeadingSeedsOnFirstSample() {
        assertEquals(20f, smoothHeading(null, 20f, 0.2f), eps)
        assertEquals(270f, smoothHeading(null, -90f, 0.2f), eps) // normalized
    }

    @Test fun smoothHeadingMovesAlongShortestArcAcrossNorth() {
        // 350 -> 20 is +30 the short way; half of that from 350 lands at 5, never ~185.
        assertEquals(5f, smoothHeading(350f, 20f, 0.5f), eps)
    }

    @Test fun smoothHeadingClampsAlpha() {
        assertEquals(100f, smoothHeading(100f, 200f, 0f), eps)   // alpha 0 → stays put
        assertEquals(200f, smoothHeading(100f, 200f, 1f), eps)   // alpha 1 → jumps to target
        assertEquals(200f, smoothHeading(100f, 200f, 2f), eps)   // alpha clamped to 1
    }
}
