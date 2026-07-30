package eu.dotshell.pelo.generic.data.dataset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The subset of the pipeline's `dataset.json` the app actually consumes.
 *
 * The file carries much more (per-file checksums, per-period stats, tool version).
 * Only the fields declared here are read; the rest is ignored by the lenient parser
 * so the pipeline can keep evolving the format without breaking older apps.
 */
@Serializable
data class DatasetInfo(
    val validity: DatasetValidity = DatasetValidity(),
    val createdAt: String? = null,
    @SerialName("schema_version") val schemaVersion: Int? = null
)

/**
 * How long the bundled timetables may be trusted.
 *
 * [source] says where the window came from: `feed_info` is the operator's own
 * commitment, `calendar` is a weaker fallback inferred from service calendars, and
 * `none` means the feed told us nothing. Only [SOURCE_FEED_INFO] is firm enough to
 * phrase as a promise to the user.
 */
@Serializable
data class DatasetValidity(
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val source: String = SOURCE_NONE,
    @SerialName("feed_version") val feedVersion: String? = null,
    @SerialName("feed_publisher") val feedPublisher: String? = null
) {
    companion object {
        const val SOURCE_FEED_INFO = "feed_info"
        const val SOURCE_CALENDAR = "calendar"
        const val SOURCE_NONE = "none"
    }
}
