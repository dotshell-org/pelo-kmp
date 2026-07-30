package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.DatasetStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetStoreTest {

    private val bundle = mapOf(
        "raptor/stops_saturday.bin" to byteArrayOf(1, 2, 3),
        "dataset.json" to "BUNDLE".encodeToByteArray()
    )

    private fun store(
        override: Map<String, ByteArray>,
        usingOverride: Boolean
    ) = DatasetStore(
        bundledBytes = { bundle[it] ?: error("no bundled $it") },
        bundledText = { bundle[it]?.decodeToString() ?: error("no bundled $it") },
        overrideBytes = { override[it] },
        overrideText = { override[it]?.decodeToString() },
        usingOverride = usingOverride
    )

    @Test
    fun `reads the bundle when no override is active`() {
        val s = store(override = mapOf("dataset.json" to "OVERRIDE".encodeToByteArray()), usingOverride = false)

        assertFalse(s.usingOverride)
        assertEquals("BUNDLE", s.readText("dataset.json"))
        assertArrayEquals(byteArrayOf(1, 2, 3), s.readBytes("raptor/stops_saturday.bin"))
    }

    @Test
    fun `prefers the override when active`() {
        val s = store(
            override = mapOf(
                "dataset.json" to "OVERRIDE".encodeToByteArray(),
                "raptor/stops_saturday.bin" to byteArrayOf(9, 9, 9)
            ),
            usingOverride = true
        )

        assertTrue(s.usingOverride)
        assertEquals("OVERRIDE", s.readText("dataset.json"))
        assertArrayEquals(byteArrayOf(9, 9, 9), s.readBytes("raptor/stops_saturday.bin"))
    }

    @Test
    fun `falls back to the bundle for a file missing from an active override`() {
        // Degrade rather than crash: an active override that lacks one file still
        // serves the rest, and the bundle covers the gap. Step 3 keeps a broken
        // override from ever going active in the first place.
        val s = store(
            override = mapOf("dataset.json" to "OVERRIDE".encodeToByteArray()),
            usingOverride = true
        )

        assertEquals("OVERRIDE", s.readText("dataset.json"))
        assertArrayEquals(byteArrayOf(1, 2, 3), s.readBytes("raptor/stops_saturday.bin"))
    }
}
