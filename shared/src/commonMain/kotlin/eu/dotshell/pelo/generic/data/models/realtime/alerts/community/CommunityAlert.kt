package eu.dotshell.pelo.generic.data.models.realtime.alerts.community

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How much an alert is allowed to bend an itinerary.
 *
 * Distinct from `official.AlertSeverity`, which classifies the transit agency's own feed by how
 * loud its wording is. This one is the routing contract: it says what the engine does, and the two
 * are never read in the same file.
 */
@Serializable
enum class AlertSeverity {
    /** Displayed, never routed around. A lift out of order does not justify a detour. */
    INFO,

    /** The impacted stop costs extra time, so a better route wins if one exists. */
    WARNING,

    /** The stop is dropped from the graph: the vehicle no longer serves it. */
    BLOCKING
}

/** Whether the alert came from a traveller or from the operator's feed. */
@Serializable
enum class AlertSource {
    USER,
    OFFICIAL
}

/** Where the alert sits in the collaborative validation cycle. */
@Serializable
enum class AlertStatus {
    /** Reported but not yet vouched for — the "possible alert" a passer-by can confirm. */
    UNCONFIRMED,
    CONFIRMED,
    INVALIDATED
}

/**
 * One disruption as the backend reports it, on a stop, a line, or a stop of a given line.
 *
 * [id] is stable for the alert's whole life, which is what makes it votable: it used to be the id
 * of the most recent report in an aggregate, and changed every time somebody else reported the
 * same thing.
 */
@Immutable
@Serializable
data class CommunityAlert(
    val id: String,
    val stopId: String? = null,
    val lineId: String? = null,
    val type: String,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val source: AlertSource = AlertSource.USER,
    val status: AlertStatus = AlertStatus.UNCONFIRMED,
    val karma: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    /** True when the alert should change how a journey is computed at all. */
    val affectsRouting: Boolean
        get() = status == AlertStatus.CONFIRMED && severity != AlertSeverity.INFO
}

/**
 * Alerts for one stop or one line, split by whether the crowd has vouched for them.
 *
 * Replaces the karma-threshold split the app used to compute: the threshold now lives on the
 * server, where the trust weighting that feeds it also lives.
 */
@Immutable
@Serializable
data class AlertsForTarget(
    @SerialName("confirmed")
    val confirmed: List<CommunityAlert> = emptyList(),

    @SerialName("unconfirmed")
    val unconfirmed: List<CommunityAlert> = emptyList()
) {
    fun all(): List<CommunityAlert> = confirmed + unconfirmed
}

/** Response of `/users-alerts/stops` and `/users-alerts/lines`, keyed by the requested id. */
typealias CommunityAlertsResponse = Map<String, AlertsForTarget>
