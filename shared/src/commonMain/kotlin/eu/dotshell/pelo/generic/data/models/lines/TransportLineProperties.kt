package eu.dotshell.pelo.generic.data.models.lines

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents transport line properties
 */
@Immutable
@Serializable
data class TransportLineProperties(
    val lineName: String = "",
    @SerialName("line_code")
    val traceCode: String = "",
    @SerialName("line_id")
    val lineId: String = "",
    @SerialName("trace_type")
    val traceType: String = "",
    @SerialName("trace_name")
    val traceName: String = "",
    val direction: String? = null,
    val origin: String = "",
    val destination: String = "",
    @SerialName("origin_name")
    val originName: String = "",
    @SerialName("destination_name")
    val destinationName: String = "",
    @SerialName("transport_type")
    val transportType: String = "",
    @SerialName("start_date")
    val startDate: String = "",
    @SerialName("end_date")
    val endDate: String? = null,
    @SerialName("line_type_code")
    val lineTypeCode: String = "",
    @SerialName("line_type_name")
    val lineTypeName: String = "",
    @SerialName("sort_code")
    val sortCode: String = "",
    @SerialName("version_name")
    val versionName: String = "",
    @SerialName("last_updated")
    val lastUpdate: String = "",
    @SerialName("last_updated_fme")
    val lastUpdateFme: String = "",
    val gid: Int = 0,
    val color: String? = null
)
