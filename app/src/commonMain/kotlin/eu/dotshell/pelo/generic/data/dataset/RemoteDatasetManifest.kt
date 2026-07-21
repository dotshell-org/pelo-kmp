package eu.dotshell.pelo.generic.data.dataset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The server's `latest.json` — the pointer to the newest published dataset for a
 * city and epoch. Mirrors what `deploy/publish.sh` writes.
 *
 * Only the fields the app acts on are declared; the lenient parser ignores the rest
 * so the server can add fields without breaking older apps.
 */
@Serializable
data class RemoteDatasetManifest(
    val epoch: String = "",
    /** Opaque version id, e.g. `20260720-35173250`; also the path segment holding the files. */
    val version: String = "",
    val path: String = "",
    @SerialName("schema_version") val schemaVersion: Int = -1,
    @SerialName("stops_schema_version") val stopsSchemaVersion: Int = -1,
    @SerialName("created_at") val createdAt: String? = null,
    val validity: DatasetValidity = DatasetValidity(),
    @SerialName("total_bytes") val totalBytes: Long = 0,
    val files: List<RemoteDatasetFile> = emptyList()
)

/** One downloadable file, with the digest and size a client verifies it against. */
@Serializable
data class RemoteDatasetFile(
    /** Path relative to the version directory, e.g. `raptor/stops_saturday.bin`. */
    val path: String = "",
    val sha256: String = "",
    val bytes: Long = 0
)
