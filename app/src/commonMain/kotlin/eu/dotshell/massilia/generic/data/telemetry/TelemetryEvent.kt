package eu.dotshell.massilia.generic.data.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * All telemetry events that may be appended to the [DailyReportState] and flushed
 * to the backend through the [PendingDelta].
 *
 * Each event carries:
 * - a unique [eventId] (UUID v4) so the backend can dedup if a message is retried,
 * - an ISO-8601 [at] timestamp captured at the moment the event was emitted.
 *
 * The discriminator is the `kind` field (kotlinx.serialization defaults to the
 * class type, but we make it explicit for stability when refactoring class names).
 */
@Serializable
sealed class TelemetryEvent {
    abstract val eventId: String
    abstract val at: String

    // ---------- Sessions ----------

    @Serializable
    @SerialName("session_opened")
    data class SessionOpened(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("session_id") val sessionId: String
    ) : TelemetryEvent()

    @Serializable
    @SerialName("session_closed")
    data class SessionClosed(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("session_id") val sessionId: String,
        @SerialName("opened_at") val openedAt: String,
        @SerialName("closed_at") val closedAt: String
    ) : TelemetryEvent()

    // ---------- Searches ----------

    @Serializable
    @SerialName("search_stop")
    data class SearchStop(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("stop_id") val stopId: String
    ) : TelemetryEvent()

    @Serializable
    @SerialName("search_line")
    data class SearchLine(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("line_id") val lineId: String
    ) : TelemetryEvent()

    @Serializable
    @SerialName("search_itinerary")
    data class SearchItinerary(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("origin_ref") val originRef: PlaceRef,
        @SerialName("dest_ref") val destRef: PlaceRef
    ) : TelemetryEvent()

    // ---------- Itineraries ----------

    @Serializable
    @SerialName("itinerary_calculated")
    data class ItineraryCalculated(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("calc_id") val calcId: String,
        val origin: PlaceRef,
        val dest: PlaceRef,
        @SerialName("requested_at") val requestedAt: String,
        @SerialName("departure_at") val departureAt: String,
        val options: List<ItineraryOption>
    ) : TelemetryEvent()

    @Serializable
    @SerialName("itinerary_chosen")
    data class ItineraryChosen(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("calc_id") val calcId: String,
        @SerialName("option_index") val optionIndex: Int
    ) : TelemetryEvent()

    // ---------- Trips ----------

    @Serializable
    @SerialName("trip_completed")
    data class TripCompleted(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("started_at") val startedAt: String,
        @SerialName("ended_at") val endedAt: String,
        @SerialName("stops_passed") val stopsPassed: List<String>
    ) : TelemetryEvent()

    // ---------- Clicks ----------

    @Serializable
    @SerialName("line_clicked")
    data class LineClicked(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("line_id") val lineId: String,
        val context: String
    ) : TelemetryEvent()

    @Serializable
    @SerialName("stop_clicked")
    data class StopClicked(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("stop_id") val stopId: String,
        val context: String
    ) : TelemetryEvent()

    // ---------- Alerts ----------

    @Serializable
    @SerialName("alert_submitted")
    data class AlertSubmitted(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        val kind: String,
        @SerialName("stop_id") val stopId: String? = null,
        @SerialName("line_id") val lineId: String? = null
    ) : TelemetryEvent()

    @Serializable
    @SerialName("alert_read")
    data class AlertRead(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("alert_id") val alertId: String,
        @SerialName("read_at") val readAt: String
    ) : TelemetryEvent()
}

/**
 * Reference to a place — either a stop id from GTFS or a hashed H3 cell for free-text addresses.
 *
 * Exactly one of [stopId] / [h3] is populated. We use nullable fields to keep
 * the wire format flat and easy to read (the backend can branch on which is present).
 */
@Serializable
data class PlaceRef(
    @SerialName("stop_id") val stopId: String? = null,
    val h3: String? = null
) {
    init {
        require((stopId != null) xor (h3 != null)) {
            "Exactly one of stopId / h3 must be set"
        }
    }
}

/**
 * One of the options proposed by the routing engine for a given itinerary calculation.
 * Captured at calculation time to enable downstream analysis of user preferences
 * (fastest vs. fewest transfers, etc.).
 */
@Serializable
data class ItineraryOption(
    val index: Int,
    @SerialName("duration_min") val durationMin: Int,
    val transfers: Int,
    val lines: List<String>
)
