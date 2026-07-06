package eu.dotshell.massilia.generic.data.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Local accumulated state for the current `daily_id`.
 *
 * This is the **authoritative client-side view** of what has happened today.
 * It is persisted as a single JSON file so that:
 * - the [LocalProfileComputer] can read it cheaply at flush time,
 * - if a backend reset is needed (rare), the client can re-emit the full day.
 *
 * Distinct from [PendingDelta]: this is the cumulative state, the delta is
 * only the not-yet-sent slice.
 */
@Serializable
data class DailyReportState(
    @SerialName("daily_id") val dailyId: String,
    val day: String,                                     // YYYY-MM-DD in user local time
    @SerialName("network_code") val networkCode: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("schema_version") val schemaVersion: Int,
    val sessions: List<SessionRecord> = emptyList(),
    val events: List<TelemetryEvent> = emptyList(),
    @SerialName("last_modified_at") val lastModifiedAt: String
)

/**
 * Set of [TelemetryEvent.eventId] that have been appended since the last successful
 * upload. On flush, the [TelemetryUploader] reads this list, builds a [Message]
 * containing only those events, sends it, and on success removes them from the
 * pending set.
 *
 * Persisted alongside [DailyReportState] so that crashes / kills do not lose data.
 */
@Serializable
data class PendingDelta(
    @SerialName("daily_id") val dailyId: String,
    @SerialName("event_ids") val eventIds: List<String> = emptyList(),
    @SerialName("session_ids") val sessionIds: List<String> = emptyList()
)

/**
 * A session as recorded locally. The session_id is generated on open.
 * `closedAt == null` means the session is still active.
 */
@Serializable
data class SessionRecord(
    @SerialName("session_id") val sessionId: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("closed_at") val closedAt: String? = null
)

/**
 * Profile computed locally on every flush from [LocalHistoryStorage].
 * Sent to the backend; the backend overwrites the document's profile field with
 * the latest value seen for a given `daily_id`.
 */
@Serializable
data class Profile(
    @SerialName("usage_status") val usageStatus: String,    // occasional | regular | intensive
    @SerialName("habitual_lines") val habitualLines: List<String>
)

/**
 * DTO sent in the body of POST /v1/telemetry/daily.
 *
 * This is a **delta** message: only events new since the last successful upload
 * are included. The backend upserts by [dailyId] and appends arrays with event_id dedup.
 */
@Serializable
data class Message(
    @SerialName("daily_id") val dailyId: String,
    @SerialName("network_code") val networkCode: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("sent_at") val sentAt: String,
    val trigger: String,                                    // "session_closed"
    val day: String,
    val sessions: List<SessionRecord>,
    val profile: Profile,
    val events: List<TelemetryEvent>
)
