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
        val options: List<ItineraryOption>,
        // Everything needed to replay this calculation offline with raptor-kmp. Nullable so the
        // wire format stays backward-compatible. `origin`/`dest` above are the (privacy-scrubbed)
        // endpoints the replay feeds in: a stop is its name (resolved via resolveStopIdsByName,
        // exactly as the app does), free text is a coarse h3 cell (so free-text replays are
        // approximate). Rare fallback branches (nearby-stop, long-walk, next-day) may have used
        // adjusted inputs — for those, trust `options` + the chosen leg fingerprint over replay.
        @SerialName("recompute") val recompute: ItineraryRecomputeSpec? = null
    ) : TelemetryEvent()

    @Serializable
    @SerialName("itinerary_chosen")
    data class ItineraryChosen(
        @SerialName("event_id") override val eventId: String,
        override val at: String,
        @SerialName("calc_id") val calcId: String,
        @SerialName("option_index") val optionIndex: Int,
        // Compact fingerprint of the exact journey the user picked, so the actual choice is
        // always recoverable even if the dataset later changes and a replay would drift.
        val legs: List<ChosenLeg> = emptyList()
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

/**
 * The full set of raptor-kmp inputs that produced an [TelemetryEvent.ItineraryCalculated], so the
 * options can be regenerated offline instead of shipping their leg-by-leg detail. The endpoints
 * live on the parent event ([TelemetryEvent.ItineraryCalculated.origin] / `dest`); everything else
 * that steers the routing engine is captured here.
 */
@Serializable
data class ItineraryRecomputeSpec(
    // Local calendar date the routing ran against — selects the schedule period
    // (Saturday / Sunday / school-on / school-off weekday). Format: YYYY-MM-DD.
    @SerialName("service_date") val serviceDate: String,
    @SerialName("time_mode") val timeMode: String,                       // "departure" | "arrival"
    // Target time in seconds since local midnight (departure time, or arrival time in arrival mode).
    @SerialName("time_seconds") val timeSeconds: Int,
    // Arrive-by search window in minutes; null in departure mode.
    @SerialName("search_window_min") val searchWindowMin: Int? = null,
    // Route names excluded from the search (user line filters: JD family, RX, …).
    @SerialName("blocked_lines") val blockedLines: List<String> = emptyList(),
    @SerialName("walk_speed_mps") val walkSpeedMps: Double,
    @SerialName("walk_detour_factor") val walkDetourFactor: Double,
    @SerialName("walk_max_access_egress_m") val walkMaxAccessEgressM: Double,
    @SerialName("walk_max_direct_m") val walkMaxDirectM: Double,
    // Identity of the timetable snapshot in use (the dataset's build timestamp), so the replay
    // runs against the exact schedules. Null when it couldn't be read.
    @SerialName("dataset_version") val datasetVersion: String? = null
)

/**
 * One leg of the journey the user actually chose, kept compact: enough to pin the exact path
 * (which line, boarded and alighted where, at what time) without the full [JourneyLeg] detail.
 */
@Serializable
data class ChosenLeg(
    val line: String? = null,                               // route name; null on a walking leg
    val walk: Boolean = false,
    @SerialName("from_stop_id") val fromStopId: String? = null,
    @SerialName("to_stop_id") val toStopId: String? = null,
    @SerialName("dep_seconds") val depSeconds: Int? = null, // seconds since local midnight
    @SerialName("arr_seconds") val arrSeconds: Int? = null
)
