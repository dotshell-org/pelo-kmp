package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.DatasetFormat
import eu.dotshell.pelo.generic.data.dataset.DatasetUpdatePolicy
import eu.dotshell.pelo.generic.data.dataset.DatasetUrls
import eu.dotshell.pelo.generic.data.dataset.RemoteDatasetFile
import eu.dotshell.pelo.generic.data.dataset.RemoteDatasetManifest
import eu.dotshell.pelo.generic.data.dataset.UpdateDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetUpdatePolicyTest {

    private fun remote(
        version: String = "20260720-abcdef01",
        createdAt: String? = "2026-07-20T07:00:00Z",
        schema: Int = DatasetFormat.SCHEMA_VERSION,
        stopsSchema: Int = DatasetFormat.STOPS_SCHEMA_VERSION,
        files: List<RemoteDatasetFile> = listOf(RemoteDatasetFile("raptor/stops_saturday.bin", "aa", 10))
    ) = RemoteDatasetManifest(
        epoch = "epoch-$schema-$stopsSchema",
        version = version,
        path = version,
        schemaVersion = schema,
        stopsSchemaVersion = stopsSchema,
        createdAt = createdAt,
        files = files
    )

    @Test
    fun `newer remote is an update`() {
        val d = DatasetUpdatePolicy.decide(
            activeCreatedAt = "2026-06-15T07:00:00Z",
            remote = remote(createdAt = "2026-07-20T07:00:00Z")
        )
        assertTrue(d is UpdateDecision.Update)
        assertEquals("20260720-abcdef01", (d as UpdateDecision.Update).manifest.version)
    }

    @Test
    fun `same-age remote is up to date`() {
        val ts = "2026-07-20T07:00:00Z"
        assertEquals(
            UpdateDecision.UpToDate,
            DatasetUpdatePolicy.decide(activeCreatedAt = ts, remote = remote(createdAt = ts))
        )
    }

    @Test
    fun `older remote is not pushed onto a newer active dataset`() {
        // Guards against a rollback if the server ever serves a stale pointer.
        val d = DatasetUpdatePolicy.decide(
            activeCreatedAt = "2026-07-20T07:00:00Z",
            remote = remote(createdAt = "2026-07-01T07:00:00Z")
        )
        assertEquals(UpdateDecision.UpToDate, d)
    }

    @Test
    fun `a bundle with no timestamp accepts any compatible remote`() {
        val d = DatasetUpdatePolicy.decide(activeCreatedAt = null, remote = remote())
        assertTrue(d is UpdateDecision.Update)
    }

    @Test
    fun `a mismatched stops schema is incompatible, never downloaded`() {
        val d = DatasetUpdatePolicy.decide(
            activeCreatedAt = null,
            remote = remote(stopsSchema = DatasetFormat.STOPS_SCHEMA_VERSION + 1)
        )
        assertTrue(d is UpdateDecision.Incompatible)
    }

    @Test
    fun `an empty file list is nothing to do`() {
        assertEquals(
            UpdateDecision.UpToDate,
            DatasetUpdatePolicy.decide(activeCreatedAt = null, remote = remote(files = emptyList()))
        )
    }

    @Test
    fun `a malformed remote timestamp never counts as newer`() {
        val d = DatasetUpdatePolicy.decide(
            activeCreatedAt = "2026-07-20T07:00:00Z",
            remote = remote(createdAt = "not-a-date")
        )
        assertEquals(UpdateDecision.UpToDate, d)
    }

    @Test
    fun `urls follow the epoch layout and tolerate a trailing slash in the base`() {
        assertEquals(
            "https://dotshell.eu/lyon/epoch-2-3/latest.json",
            DatasetUrls.latest("https://dotshell.eu/", "lyon")
        )
        assertEquals(
            "https://dotshell.eu/lyon/epoch-2-3/20260720-abcdef01/raptor/stops_saturday.bin",
            DatasetUrls.file("https://dotshell.eu", "lyon", "20260720-abcdef01", "raptor/stops_saturday.bin")
        )
    }
}
