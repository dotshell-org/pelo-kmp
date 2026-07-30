package eu.dotshell.pelo

import eu.dotshell.pelo.generic.data.dataset.DatasetInstaller
import eu.dotshell.pelo.generic.data.dataset.DatasetStorage
import eu.dotshell.pelo.generic.data.dataset.RemoteDatasetFile
import eu.dotshell.pelo.generic.data.dataset.RemoteDatasetManifest
import eu.dotshell.pelo.generic.data.dataset.StageResult
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DatasetInstallerTest {

    private lateinit var root: String
    private lateinit var storage: DatasetStorage
    private lateinit var installer: DatasetInstaller

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "ds-${System.nanoTime()}").absolutePath
        storage = DatasetStorage(root)
        installer = DatasetInstaller(storage)
    }

    @After
    fun tearDown() {
        File(root).deleteRecursively()
    }

    private fun sha(bytes: ByteArray) = bytes.toByteString().sha256().hex()

    /** Writes files into staging and returns a manifest describing them. */
    private fun stageFiles(version: String, files: Map<String, ByteArray>): RemoteDatasetManifest {
        files.forEach { (path, bytes) -> storage.writeBytes("${storage.stagingDir}/$path", bytes) }
        return RemoteDatasetManifest(
            version = version,
            path = version,
            createdAt = "2026-07-20T07:00:00Z",
            files = files.map { (path, bytes) -> RemoteDatasetFile(path, sha(bytes), bytes.size.toLong()) }
        )
    }

    private val goodFiles get() = mapOf(
        "dataset.json" to """{"schema_version":2}""".encodeToByteArray(),
        "raptor/stops_saturday.bin" to byteArrayOf(1, 2, 3, 4)
    )

    @Test
    fun `a verified download stages, promotes, and becomes the active version`() {
        val manifest = stageFiles("v1", goodFiles)

        assertEquals(StageResult.Installed, installer.stageDownloaded(manifest))
        assertEquals("v1", installer.pendingVersion())
        assertNull("nothing is active until promotion", installer.activeVersion())

        assertEquals("v1", installer.promotePending())
        assertEquals("v1", installer.activeVersion())
        assertNull("pending is consumed by promotion", installer.pendingVersion())
        assertTrue(storage.hasDataset(storage.currentDir))
    }

    @Test
    fun `a wrong checksum rejects the whole download and clears staging`() {
        val manifest = stageFiles("v1", goodFiles).let {
            it.copy(files = it.files.map { f ->
                if (f.path == "raptor/stops_saturday.bin") f.copy(sha256 = "deadbeef") else f
            })
        }

        val result = installer.stageDownloaded(manifest)
        assertTrue(result is StageResult.Rejected)
        assertNull(installer.pendingVersion())
        assertFalse("staging is wiped on rejection", storage.exists(storage.stagingDir))
    }

    @Test
    fun `a truncated file rejects on size before hashing`() {
        val manifest = stageFiles("v1", goodFiles).let {
            it.copy(files = it.files.map { f ->
                if (f.path.endsWith(".bin")) f.copy(bytes = f.bytes + 100) else f
            })
        }
        assertTrue(installer.stageDownloaded(manifest) is StageResult.Rejected)
    }

    @Test
    fun `a download missing the sentinel is rejected`() {
        val manifest = stageFiles("v1", mapOf("raptor/stops_saturday.bin" to byteArrayOf(9)))
        assertTrue(installer.stageDownloaded(manifest) is StageResult.Rejected)
    }

    @Test
    fun `promotion replaces an older current in place`() {
        installer.stageDownloaded(stageFiles("v1", goodFiles))
        installer.promotePending()

        val newBin = byteArrayOf(5, 6, 7, 8, 9)
        installer.stageDownloaded(stageFiles("v2", goodFiles + ("raptor/stops_saturday.bin" to newBin)))
        assertEquals("v2", installer.promotePending())

        assertEquals("v2", installer.activeVersion())
        assertEquals(newBin.size.toLong(), storage.sizeOf("${storage.currentDir}/raptor/stops_saturday.bin"))
    }

    @Test
    fun `promotePending is a no-op when nothing is staged`() {
        assertNull(installer.promotePending())
        assertNull(installer.activeVersion())
    }

    @Test
    fun `recover restores current from an interrupted swap`() {
        installer.stageDownloaded(stageFiles("v1", goodFiles))
        installer.promotePending()

        // Simulate a crash mid-swap: current moved aside to trash, new one not yet in.
        File("${storage.currentDir}").renameTo(File("$root/current.trash"))
        assertFalse(storage.hasDataset(storage.currentDir))

        installer.recover()
        assertTrue("current is restored from trash", storage.hasDataset(storage.currentDir))
        assertEquals("v1", installer.activeVersion())
    }
}
