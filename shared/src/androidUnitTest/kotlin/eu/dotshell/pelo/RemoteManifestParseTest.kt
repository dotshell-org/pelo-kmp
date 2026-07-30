package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.DatasetValidity
import eu.dotshell.pelo.generic.data.dataset.RemoteDatasetManifest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the manifest model against an actual `latest.json` captured from the live
 * server (https://dotshell.eu/lyon/epoch-2-3/latest.json). A field rename on either
 * side — the model or deploy/publish.sh — would surface here instead of silently
 * decoding to defaults at runtime.
 */
class RemoteManifestParseTest {

    private val liveSample = """
        {
          "epoch": "epoch-2-3",
          "version": "20260720-35173250",
          "path": "20260720-35173250",
          "schema_version": 2,
          "stops_schema_version": 3,
          "tool_version": "0.3.0",
          "created_at": "2026-07-20T14:41:30.336971077Z",
          "input_sha256": "3517325043cadb654aa64feb19a1b8966fe5b7c7489271192e7ea63ff2a3672c",
          "validity": {
            "start_date": "2026-07-20",
            "end_date": "2026-11-17",
            "source": "feed_info",
            "feed_publisher": "TCL"
          },
          "total_bytes": 19006687,
          "files": [
            { "path": "dataset.json", "sha256": "aa", "bytes": 3411 },
            { "path": "raptor/stops_saturday.bin", "sha256": "bb", "bytes": 814315 }
          ]
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `the model decodes a real server manifest`() {
        val m = json.decodeFromString(RemoteDatasetManifest.serializer(), liveSample)

        assertEquals("epoch-2-3", m.epoch)
        assertEquals("20260720-35173250", m.version)
        assertEquals(2, m.schemaVersion)
        assertEquals(3, m.stopsSchemaVersion)
        assertEquals("2026-07-20T14:41:30.336971077Z", m.createdAt)
        assertEquals(19006687L, m.totalBytes)
        assertEquals(2, m.files.size)
        assertEquals("raptor/stops_saturday.bin", m.files[1].path)
        assertEquals(814315L, m.files[1].bytes)

        assertEquals("2026-11-17", m.validity.endDate)
        assertEquals(DatasetValidity.SOURCE_FEED_INFO, m.validity.source)
        assertEquals("TCL", m.validity.feedPublisher)
    }
}
