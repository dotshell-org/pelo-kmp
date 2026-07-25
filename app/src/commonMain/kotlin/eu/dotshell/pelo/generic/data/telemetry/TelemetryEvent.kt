package eu.dotshell.pelo.generic.data.telemetry

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
        // Context for the scheduled times below: whether this was a depart-at / arrive-by search,
        // the service date the schedules are read from, and the timetable snapshot in use.
        @SerialName("time_mode") val timeMode: String,          // "departure" | "arrival"
        @SerialName("service_date") val serviceDate: String,    // YYYY-MM-DD
        @SerialName("dataset_version") val datasetVersion: String? = null,
        // Every option shown, with full leg-by-leg detail. Most searches never lead to an
        // itinerary_chosen (users read the summary and leave), so the detail lives here where it
        // is always captured; itinerary_chosen only references the picked option by signature.
        val options: List<ItineraryOptionDetail>
    ) : TelemetryEvent()

    @Serializable
    @SerialName("itinerary_chosen")
    data class ItineraryChosen(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("calc_id") val calcId: String,
        // Which option(s) of the referenced calc the user picked, by
        // [ItineraryOptionDetail.signature] — a reference, no content copy. Usually one.
        @SerialName("chosen_signatures") val chosenSignatures: List<String> = emptyList()
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
 * One proposed itinerary, as a compact leg skeleton — enough to identify the exact path without
 * the bulk of every intermediate stop. Stored on every [TelemetryEvent.ItineraryCalculated]
 * (most searches are never "chosen", so the option list is captured at calculation time). The
 * intermediate stops passed on each leg and their scheduled times are recovered downstream from
 * the timetable (line + boarding stop + departure time + service_date + dataset_version).
 */
@Serializable
data class ItineraryOptionDetail(
    val index: Int,                                         // position as displayed
    // Stable content id (departure/arrival + legs). How itinerary_chosen refers back to an option.
    val signature: String,
    @SerialName("duration_min") val durationMin: Int,
    val transfers: Int,
    val lines: List<String>,                               // summary line list (the "résumé")
    val legs: List<ItineraryLeg>
)

/**
 * One leg of an itinerary: which line, boarded and alighted where and when. Intermediate stops
 * are intentionally omitted (recomputable from the timetable). Stop names on a walk leg to/from a
 * coordinate endpoint are omitted too — they can be the raw address label; the scrubbed geohash
 * on the parent event stays the only endpoint hint.
 */
@Serializable
data class ItineraryLeg(
    val line: String? = null,                              // route name; null on a walk leg
    val walk: Boolean = false,
    @SerialName("from_stop") val fromStop: String? = null, // stop name; null for a coordinate endpoint
    @SerialName("to_stop") val toStop: String? = null,
    @SerialName("dep_seconds") val depSeconds: Int,        // seconds since local midnight
    @SerialName("arr_seconds") val arrSeconds: Int
)
