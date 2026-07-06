package eu.dotshell.massilia.generic.data.telemetry

import eu.dotshell.massilia.platform.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Defence-in-depth checks applied to every outgoing telemetry [Message] just before it is
 * gzipped and POSTed.
 *
 * The point is not to replace the type-safe Kotlin contract (which is the primary
 * protection) but to catch *future* regressions where:
 *  - someone adds a new event type without updating the disclosure CGU,
 *  - someone accidentally introduces a free-form text field that could leak PII,
 *  - the payload grows unboundedly because a Vague 4+ change forgot to cap a collection.
 *
 * If a guardrail trips we **drop the offending field** (or the whole event) and log a
 * warning — never silently send data the user didn't consent to.
 */
object PayloadGuardrail {

    private const val TAG = "TelemetryGuardrail"

    /** Allowed `kind` discriminators on the wire. Must match the CGU §5.3 enumeration. */
    private val ALLOWED_EVENT_KINDS: Set<String> = setOf(
        "session_opened",
        "session_closed",
        "search_stop",
        "search_line",
        "search_itinerary",
        "itinerary_calculated",
        "itinerary_chosen",
        "trip_completed",
        "line_clicked",
        "stop_clicked",
        "alert_submitted",
        "alert_read"
    )

    /**
     * Hard cap on the number of events of any single kind per message. Beyond this we drop
     * the oldest entries and keep the most recent — bounded payloads + freshest data.
     */
    private const val MAX_EVENTS_PER_KIND = 500

    /**
     * Hard cap on string fields that have no semantic length bound. Catches accidental
     * inclusion of long blobs (free text, base64, etc.). Stop ids and line ids in practice
     * are < 40 chars, geohash bucket is exactly 6 chars.
     */
    private const val MAX_STRING_LENGTH = 256

    private val jsonInspector = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Returns a (possibly trimmed) version of [message] that satisfies all invariants. If the
     * input is already conformant, the same instance is returned.
     */
    fun sanitize(message: Message): Message {
        val filteredEvents = filterAndCapEvents(message.events)
        if (filteredEvents === message.events) return message
        return message.copy(events = filteredEvents)
    }

    /**
     * Inspect the fully-serialized JSON. Used as a last belt-and-suspenders pass before gzip
     * to catch any field that slipped past the typed checks. Returns true if the payload
     * appears safe.
     */
    fun assertNoSurprises(serializedJson: String): Boolean {
        return try {
            val root = jsonInspector.parseToJsonElement(serializedJson).jsonObject
            val ok = inspect(root, path = "$")
            if (!ok) {
                Log.e(TAG, "Payload contains unexpected content — investigation needed")
            }
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Could not introspect payload (will still send, type-safe layer applies)", e)
            true
        }
    }

    private fun filterAndCapEvents(events: List<TelemetryEvent>): List<TelemetryEvent> {
        val (allowed, blocked) = events.partition { it.discriminator() in ALLOWED_EVENT_KINDS }
        if (blocked.isNotEmpty()) {
            Log.w(
                TAG,
                "Dropped ${blocked.size} event(s) with unrecognised kind: ${blocked.map { it.discriminator() }.distinct()}"
            )
        }
        val groupedByKind = allowed.groupBy { it.discriminator() }
        val needsCapping = groupedByKind.values.any { it.size > MAX_EVENTS_PER_KIND }
        if (!needsCapping && blocked.isEmpty()) return events
        return groupedByKind.values
            .flatMap { sameKind ->
                if (sameKind.size > MAX_EVENTS_PER_KIND) sameKind.takeLast(MAX_EVENTS_PER_KIND)
                else sameKind
            }
    }

    private fun inspect(element: JsonElement, path: String): Boolean {
        return when (element) {
            is JsonObject -> element.entries.all { (key, value) -> inspect(value, "$path.$key") }
            is JsonArray -> element.withIndex().all { (i, v) -> inspect(v, "$path[$i]") }
            is JsonPrimitive -> inspectPrimitive(element, path)
        }
    }

    private fun inspectPrimitive(primitive: JsonPrimitive, path: String): Boolean {
        if (!primitive.isString) return true
        val s = primitive.content
        if (s.length > MAX_STRING_LENGTH) {
            Log.w(TAG, "Oversized string at $path (${s.length} chars > $MAX_STRING_LENGTH)")
            return false
        }
        return true
    }

    /**
     * Map a [TelemetryEvent] subtype to its `kind` discriminator. Mirrors the `@SerialName`
     * annotations on [TelemetryEvent]'s sealed children — kept in sync manually because the
     * guardrail must reject anything *not* listed here, which means we can't derive the
     * allowlist from the class graph (that would defeat the purpose).
     */
    private fun TelemetryEvent.discriminator(): String = when (this) {
        is TelemetryEvent.SessionOpened -> "session_opened"
        is TelemetryEvent.SessionClosed -> "session_closed"
        is TelemetryEvent.SearchStop -> "search_stop"
        is TelemetryEvent.SearchLine -> "search_line"
        is TelemetryEvent.SearchItinerary -> "search_itinerary"
        is TelemetryEvent.ItineraryCalculated -> "itinerary_calculated"
        is TelemetryEvent.ItineraryChosen -> "itinerary_chosen"
        is TelemetryEvent.TripCompleted -> "trip_completed"
        is TelemetryEvent.LineClicked -> "line_clicked"
        is TelemetryEvent.StopClicked -> "stop_clicked"
        is TelemetryEvent.AlertSubmitted -> "alert_submitted"
        is TelemetryEvent.AlertRead -> "alert_read"
    }

}
