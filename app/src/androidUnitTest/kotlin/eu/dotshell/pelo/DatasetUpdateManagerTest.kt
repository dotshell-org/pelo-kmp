package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.CheckResult
import eu.dotshell.pelo.generic.data.dataset.DatasetFormat
import eu.dotshell.pelo.generic.data.dataset.DatasetLifecycle
import eu.dotshell.pelo.generic.data.dataset.DatasetStorage
import eu.dotshell.pelo.generic.data.dataset.DatasetTransport
import eu.dotshell.pelo.generic.data.dataset.DatasetUpdateManager
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DatasetUpdateManagerTest {

    private lateinit var root: String
    private lateinit var lifecycle: DatasetLifecycle

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "dm-${System.nanoTime()}").absolutePath
        lifecycle = DatasetLifecycle(DatasetStorage(root))
    }

    @After
    fun tearDown() {
        File(root).deleteRecursively()
    }

    private val datasetJson = """{"schema_version":2}""".encodeToByteArray()
    private val binBytes = byteArrayOf(1, 2, 3, 4)
    private fun sha(b: ByteArray) = b.toByteString().sha256().hex()

    private fun manifestJson(
        version: String = "20260720-aaaa1111",
        createdAt: String = "2026-07-20T07:00:00Z",
        schema: Int = DatasetFormat.SCHEMA_VERSION,
        stopsSchema: Int = DatasetFormat.STOPS_SCHEMA_VERSION
    ) = """
        {
          "epoch": "epoch-$schema-$stopsSchema",
          "version": "$version",
          "path": "$version",
          "schema_version": $schema,
          "stops_schema_version": $stopsSchema,
          "created_at": "$createdAt",
          "total_bytes": ${datasetJson.size + binBytes.size},
          "files": [
            { "path": "dataset.json", "sha256": "${sha(datasetJson)}", "bytes": ${datasetJson.size} },
            { "path": "raptor/stops_saturday.bin", "sha256": "${sha(binBytes)}", "bytes": ${binBytes.size} }
          ]
        }
    """.trimIndent()

    /** Serves a fixed manifest and the two matching files. */
    private inner class FakeTransport(
        private val manifest: String?,
        private val corruptBin: Boolean = false
    ) : DatasetTransport {
        var manifestHits = 0
        override suspend fun getText(url: String): String? {
            manifestHits++
            return manifest
        }
        override suspend fun getBytes(url: String): ByteArray? = when {
            url.endsWith("dataset.json") -> datasetJson
            url.endsWith(".bin") -> if (corruptBin) byteArrayOf(9, 9) else binBytes
            else -> null
        }
    }

    private fun manager(
        transport: DatasetTransport,
        activeCreatedAt: String? = "2026-06-01T00:00:00Z"
    ) = DatasetUpdateManager(
        lifecycle = lifecycle,
        transport = transport,
        baseUrl = "https://example.test",
        city = "lyon",
        activeCreatedAt = { activeCreatedAt }
    )

    @Test
    fun `a newer dataset downloads, verifies, and stages as pending`() = runBlocking {
        val result = manager(FakeTransport(manifestJson())).checkForUpdate()

        assertTrue(result is CheckResult.Downloaded)
        assertEquals("20260720-aaaa1111", (result as CheckResult.Downloaded).version)
        assertEquals("20260720-aaaa1111", lifecycle.installer.pendingVersion())
    }

    @Test
    fun `an older-or-equal server dataset is up to date`() = runBlocking {
        val r = manager(
            FakeTransport(manifestJson(createdAt = "2026-01-01T00:00:00Z")),
            activeCreatedAt = "2026-07-20T07:00:00Z"
        ).checkForUpdate()
        assertEquals(CheckResult.UpToDate, r)
    }

    @Test
    fun `an incompatible epoch is refused, never downloaded`() = runBlocking {
        val r = manager(FakeTransport(manifestJson(stopsSchema = 99))).checkForUpdate()
        assertTrue(r is CheckResult.Incompatible)
    }

    @Test
    fun `a corrupt file rejects the download and leaves no pending`() = runBlocking {
        val r = manager(FakeTransport(manifestJson(), corruptBin = true)).checkForUpdate()
        assertTrue(r is CheckResult.Failed)
        assertEquals(null, lifecycle.installer.pendingVersion())
    }

    @Test
    fun `an unreachable manifest fails cleanly`() = runBlocking {
        val r = manager(FakeTransport(manifest = null)).checkForUpdate()
        assertTrue(r is CheckResult.Failed)
    }

    @Test
    fun `an already-pending version is not downloaded again`() = runBlocking {
        val transport = FakeTransport(manifestJson())
        manager(transport).checkForUpdate() // first: downloads
        val r = manager(transport).checkForUpdate() // second: already pending
        assertEquals(CheckResult.AlreadyPending, r)
    }

    @Test
    fun `a quarantined version is not downloaded again`() = runBlocking {
        lifecycle.guard.quarantine("20260720-aaaa1111")
        val r = manager(FakeTransport(manifestJson())).checkForUpdate()
        assertEquals(CheckResult.UpToDate, r)
    }
}
