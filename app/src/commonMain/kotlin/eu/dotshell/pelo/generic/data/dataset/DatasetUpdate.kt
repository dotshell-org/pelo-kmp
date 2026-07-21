package eu.dotshell.pelo.generic.data.dataset

import kotlinx.datetime.Instant

/**
 * The binary formats this build can parse, and therefore the epoch it asks the
 * server for. These MUST track the raptor-kmp reader the app links against
 * (routes/index/lines container = RRT2 → 2; stops container = RST3 → 3), NOT
 * whatever a given dataset happens to bundle: the epoch expresses what the CODE can
 * read. When raptor-kmp gains a new binary format, bump these in the same change.
 */
object DatasetFormat {
    const val SCHEMA_VERSION = 2
    const val STOPS_SCHEMA_VERSION = 3

    /** The epoch path segment the app requests, e.g. `epoch-2-3`. */
    fun epoch(): String = "epoch-$SCHEMA_VERSION-$STOPS_SCHEMA_VERSION"
}

/**
 * Builds the dataset server URLs. Pure: the base URL and city are passed in so the
 * source of that configuration stays out of the URL logic (and out of tests).
 *
 * [baseUrl] is the origin, e.g. `https://api.dotshell.eu`; [city] the path segment, e.g.
 * `lyon`. Layout served by `deploy/nginx.conf`:
 *
 *   {base}/{city}/{epoch}/latest.json
 *   {base}/{city}/{epoch}/{version}/{file path}
 */
object DatasetUrls {
    fun latest(baseUrl: String, city: String): String =
        "${trim(baseUrl)}/$city/${DatasetFormat.epoch()}/latest.json"

    fun file(baseUrl: String, city: String, version: String, filePath: String): String =
        "${trim(baseUrl)}/$city/${DatasetFormat.epoch()}/$version/$filePath"

    private fun trim(baseUrl: String): String = baseUrl.trimEnd('/')
}

/** The outcome of comparing the active dataset against the server's latest. */
sealed interface UpdateDecision {
    /** The active dataset is at least as fresh as the server's. */
    data object UpToDate : UpdateDecision

    /**
     * The server published a format this build cannot read. Should not happen given
     * the epoch-scoped URL, but if a misrouted or hand-edited manifest slips through,
     * refusing it is the whole reason the epoch exists.
     */
    data class Incompatible(val remoteSchema: Int, val remoteStopsSchema: Int) : UpdateDecision

    /** A newer, compatible dataset is available and should be downloaded. */
    data class Update(val manifest: RemoteDatasetManifest) : UpdateDecision
}

object DatasetUpdatePolicy {

    /**
     * Decides whether [remote] should replace the active dataset.
     *
     * Freshness is compared on `createdAt` (the pipeline run time), which both the
     * bundled `dataset.json` and the server manifest carry — unlike the version id,
     * which the bundle predates. Both sides are parsed as instants and compared as
     * instants: lexical comparison would rank a malformed value like `not-a-date`
     * above a real timestamp. An unparseable REMOTE timestamp is refused (we will not
     * act on data we cannot date); a null or unparseable ACTIVE timestamp (a bundle
     * built before this metadata existed) means we cannot prove we are current, so any
     * compatible remote wins.
     */
    fun decide(
        activeCreatedAt: String?,
        remote: RemoteDatasetManifest
    ): UpdateDecision {
        if (remote.schemaVersion != DatasetFormat.SCHEMA_VERSION ||
            remote.stopsSchemaVersion != DatasetFormat.STOPS_SCHEMA_VERSION
        ) {
            return UpdateDecision.Incompatible(remote.schemaVersion, remote.stopsSchemaVersion)
        }
        // A remote with no files is nothing to act on.
        if (remote.files.isEmpty() || remote.version.isBlank()) return UpdateDecision.UpToDate

        val remoteInstant = parseInstant(remote.createdAt) ?: return UpdateDecision.UpToDate
        val activeInstant = parseInstant(activeCreatedAt)
        val newer = activeInstant == null || remoteInstant > activeInstant
        return if (newer) UpdateDecision.Update(remote) else UpdateDecision.UpToDate
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }
}
