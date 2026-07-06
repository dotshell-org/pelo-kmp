package eu.dotshell.massilia.generic.data.telemetry

import eu.dotshell.massilia.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import eu.dotshell.massilia.platform.randomId
import kotlinx.datetime.Clock
import kotlin.concurrent.Volatile

/**
 * In-memory authoritative view of the current daily report, with debounced persistence
 * to [TelemetryStorage].
 *
 * Responsibilities:
 *  - Append events to the cumulative [DailyReportState] and the [PendingDelta].
 *  - Open / close sessions (kept in the state's `sessions` list).
 *  - Provide a snapshot of pending events for the [TelemetryUploader].
 *  - Mark events as sent after a successful upload (removes them from the pending set,
 *    keeps them in the cumulative state for the local profile computation).
 *  - Rotate cleanly when the daily_id changes: a closing snapshot is returned to the
 *    caller for a final flush with the previous id, then internal state resets.
 *
 * Concurrency: a single [Mutex] guards all mutations. The debounced flush runs in the
 * provided [scope].
 *
 * Persistence model: writes are debounced ([DEBOUNCE_MS]) to coalesce bursts of events,
 * but [forceFlush] is called before every upload to guarantee the on-disk view matches
 * the in-memory view.
 */
class DailyReportRepository(
    private val storage: TelemetryStorage,
    private val scope: CoroutineScope,
    private val networkCode: String,
    private val appVersion: String,
    private val schemaVersion: Int
) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<DailyReportState?>(null)
    val state: StateFlow<DailyReportState?> = _state.asStateFlow()

    private var pendingEventIds: MutableList<String> = mutableListOf()
    private var pendingSessionIds: MutableList<String> = mutableListOf()

    @Volatile
    private var flushJob: Job? = null

    /**
     * Load any persisted state from disk for [dailyId]. If the persisted state belongs to a
     * different daily_id (rotation happened while app was killed), the previous state is
     * dropped — the [TelemetryUploader] is expected to flush it via [snapshotPendingForUpload]
     * before this call.
     */
    suspend fun initFor(dailyId: String, day: String) = mutex.withLock {
        val persistedState = storage.readState()
        val persistedPending = storage.readPending()

        if (persistedState != null && persistedState.dailyId == dailyId) {
            _state.value = persistedState
            pendingEventIds = persistedPending?.eventIds?.toMutableList() ?: mutableListOf()
            pendingSessionIds = persistedPending?.sessionIds?.toMutableList() ?: mutableListOf()
        } else {
            _state.value = DailyReportState(
                dailyId = dailyId,
                day = day,
                networkCode = networkCode,
                appVersion = appVersion,
                schemaVersion = schemaVersion,
                sessions = emptyList(),
                events = emptyList(),
                lastModifiedAt = nowIso()
            )
            pendingEventIds = mutableListOf()
            pendingSessionIds = mutableListOf()
            forceFlush()
        }
    }

    suspend fun appendEvent(event: TelemetryEvent) = mutex.withLock {
        val current = _state.value ?: run {
            Log.w(TAG, "appendEvent called before initFor — dropping ${event::class.simpleName}")
            return@withLock
        }
        _state.value = current.copy(
            events = current.events + event,
            lastModifiedAt = nowIso()
        )
        pendingEventIds.add(event.eventId)
        scheduleFlush()
    }

    suspend fun openSession(sessionId: String, openedAt: String) = mutex.withLock {
        val current = _state.value ?: return@withLock
        val newSession = SessionRecord(sessionId = sessionId, openedAt = openedAt, closedAt = null)
        _state.value = current.copy(
            sessions = current.sessions + newSession,
            lastModifiedAt = nowIso()
        )
        pendingSessionIds.add(sessionId)
        scheduleFlush()
    }

    suspend fun closeSession(sessionId: String, closedAt: String) = mutex.withLock {
        val current = _state.value ?: return@withLock
        val updatedSessions = current.sessions.map {
            if (it.sessionId == sessionId && it.closedAt == null) it.copy(closedAt = closedAt) else it
        }
        _state.value = current.copy(sessions = updatedSessions, lastModifiedAt = nowIso())
        if (sessionId !in pendingSessionIds) pendingSessionIds.add(sessionId)
        scheduleFlush()
    }

    /**
     * Atomically capture the events and sessions that should ship in the next upload, plus
     * the cumulative state needed to build a [Message]. Does **not** clear the pending lists —
     * call [markSent] only after the network call succeeds.
     */
    suspend fun snapshotPendingForUpload(): UploadSnapshot? = mutex.withLock {
        val current = _state.value ?: return@withLock null
        forceFlush()

        // Hoist the sets out of the filter lambdas — otherwise toSet() rebuilt them per element.
        val pendingEventIdSet = pendingEventIds.toSet()
        val pendingSessionIdSet = pendingSessionIds.toSet()
        val pendingEvents = current.events.filter { it.eventId in pendingEventIdSet }
        val pendingSessions = current.sessions.filter { it.sessionId in pendingSessionIdSet }
        if (pendingEvents.isEmpty() && pendingSessions.isEmpty()) return@withLock null

        UploadSnapshot(
            dailyId = current.dailyId,
            day = current.day,
            networkCode = current.networkCode,
            appVersion = current.appVersion,
            schemaVersion = current.schemaVersion,
            sessions = pendingSessions,
            events = pendingEvents,
            eventIds = pendingEvents.map { it.eventId },
            sessionIds = pendingSessions.map { it.sessionId }
        )
    }

    /**
     * Acknowledge a successful upload — the snapshot's event_ids and session_ids leave the
     * pending set.
     */
    suspend fun markSent(eventIds: List<String>, sessionIds: List<String>) = mutex.withLock {
        val eventSet = eventIds.toSet()
        val sessionSet = sessionIds.toSet()
        pendingEventIds.removeAll(eventSet)
        pendingSessionIds.removeAll(sessionSet)
        forceFlush()
    }

    /**
     * Reset state for a new daily_id. Caller must have already shipped (or persisted) the
     * previous day's pending data — this method does not preserve it.
     */
    suspend fun resetForNewDay(newDailyId: String, newDay: String) = mutex.withLock {
        _state.value = DailyReportState(
            dailyId = newDailyId,
            day = newDay,
            networkCode = networkCode,
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            sessions = emptyList(),
            events = emptyList(),
            lastModifiedAt = nowIso()
        )
        pendingEventIds = mutableListOf()
        pendingSessionIds = mutableListOf()
        forceFlush()
    }

    private fun scheduleFlush() {
        // Coalesce bursts of events into a single disk write.
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(DEBOUNCE_MS)
            mutex.withLock { forceFlush() }
        }
    }

    private suspend fun forceFlush() {
        val current = _state.value ?: return
        storage.writeState(current)
        storage.writePending(
            PendingDelta(
                dailyId = current.dailyId,
                eventIds = pendingEventIds.toList(),
                sessionIds = pendingSessionIds.toList()
            )
        )
    }

    private fun nowIso(): String = Clock.System.now().toString()

    companion object {
        private const val TAG = "TelemetryRepo"
        private const val DEBOUNCE_MS = 1_000L
    }
}

/**
 * Bundle returned by [DailyReportRepository.snapshotPendingForUpload] containing everything
 * the [TelemetryUploader] needs to build and post a [Message].
 */
data class UploadSnapshot(
    val dailyId: String,
    val day: String,
    val networkCode: String,
    val appVersion: String,
    val schemaVersion: Int,
    val sessions: List<SessionRecord>,
    val events: List<TelemetryEvent>,
    val eventIds: List<String>,
    val sessionIds: List<String>
)
