package eu.dotshell.pelo.generic.data.repository

import eu.dotshell.pelo.generic.data.alerts.AlertDeviceIdProvider
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.AlertSeverity
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.AlertStatus
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.AlertsForTarget
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.CommunityAlert
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.CommunityAlertsResponse
import eu.dotshell.pelo.generic.data.models.navigation.NavigationAlertPrompt
import eu.dotshell.pelo.generic.data.models.navigation.NavigationAlertPromptKind
import eu.dotshell.pelo.generic.data.network.UserStopAlertsApi
import eu.dotshell.pelo.generic.utils.search.SearchUtils
import eu.dotshell.pelo.platform.Log
import eu.dotshell.pelo.platform.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * What a set of live alerts means for the routing engine.
 *
 * Stops and lines are named, not indexed: alerts are keyed by stop *name* throughout the system,
 * and only RaptorRepository knows how to resolve a name to the timetable's stop ids.
 *
 * [warnedLineNames] carries no stop — the report was made against the line as a whole. The caller
 * turns those into penalties on the stops where the journey actually boards that line, since
 * penalising every stop it serves would punish itineraries that never go near the trouble.
 */
data class RoutingDisruptions(
    val blockedStopNames: Set<String> = emptySet(),
    val blockedLineNames: Set<String> = emptySet(),
    val stopPenaltySeconds: Map<String, Int> = emptyMap(),
    val warnedLineNames: Set<String> = emptySet(),
    /** The confirmed alerts these directives were derived from, for labelling the result. */
    val confirmedAlerts: List<CommunityAlert> = emptyList(),
    /** Alerts on this journey worth putting to the traveller, with the question to ask. */
    val pendingConfirmations: List<NavigationAlertPrompt> = emptyList()
) {
    val isEmpty: Boolean
        get() = blockedStopNames.isEmpty() && blockedLineNames.isEmpty() &&
            stopPenaltySeconds.isEmpty() && warnedLineNames.isEmpty()

    /**
     * The alert most worth naming to the traveller: the one that changed their itinerary the most,
     * and among equals the one most people vouched for.
     */
    fun mostSevereAlert(): CommunityAlert? =
        confirmedAlerts.maxWithOrNull(compareBy({ it.severity.ordinal }, { it.karma }))
}

/**
 * Community alerts: fetching them, turning them into routing directives, and voting on them.
 *
 * Multiplatform: no android.util.Log, uses platform.Log. When the transport client has no
 * [UserStopAlertsApi] backend (null), every query resolves to "no alerts".
 *
 * @param warningPenaltySeconds what one WARNING-level alert costs a stop. Penalties do not stack:
 *        a stop reported both crowded and delayed is not twice as slow, so they combine with max.
 */
class UserStopAlertsRepository(
    private val api: UserStopAlertsApi?,
    private val deviceIdProvider: AlertDeviceIdProvider?,
    private val warningPenaltySeconds: Int,
    private val staleAfterMinutes: Int = DEFAULT_STALE_AFTER_MINUTES
) {
    companion object {
        private const val TAG = "UserStopAlertsRepository"
        private const val API_CHUNK_SIZE = 10

        /**
         * How long a confirmed alert coasts before travellers are asked whether it still holds.
         *
         * Without re-asking, a confirmed alert would keep bending itineraries for its whole TTL on
         * the strength of a crowd that has long since moved on.
         */
        const val DEFAULT_STALE_AFTER_MINUTES = 30
    }

    /**
     * Alerts this device answered in the current session.
     *
     * The server is the authority — it stamps [CommunityAlert.viewerHasVoted] — but a vote and the
     * next fetch are two round trips, and in between the alert would come back unflagged and be
     * asked again. This closes that window.
     */
    private val answeredAlertIds = mutableSetOf<String>()

    suspend fun getStopAlerts(stopIds: List<String>): CommunityAlertsResponse =
        fetchChunked(stopIds) { api?.getStopAlerts(it, deviceIdProvider?.currentOrRotate()) }

    suspend fun getLineAlerts(lineIds: List<String>): CommunityAlertsResponse =
        fetchChunked(lineIds) { api?.getLineAlerts(it, deviceIdProvider?.currentOrRotate()) }

    private suspend fun fetchChunked(
        ids: List<String>,
        fetch: suspend (List<String>) -> CommunityAlertsResponse?
    ): CommunityAlertsResponse = withContext(ioDispatcher) {
        if (api == null || ids.isEmpty()) return@withContext emptyMap()

        val requested = ids.distinct()
        Log.i(TAG, "Fetching community alerts for ${requested.size} targets")

        val merged = linkedMapOf<String, AlertsForTarget>()
        requested.chunked(API_CHUNK_SIZE).forEach { chunk ->
            try {
                fetch(chunk)?.let(merged::putAll)
            } catch (chunkError: Exception) {
                Log.w(TAG, "Chunk request failed, retrying one by one: ${chunkError.message}")
                chunk.forEach { id ->
                    try {
                        fetch(listOf(id))?.let(merged::putAll)
                    } catch (singleError: Exception) {
                        Log.w(TAG, "Single alert request failed for '$id': ${singleError.message}")
                    }
                }
            }
        }
        merged
    }

    /**
     * Resolves the alerts affecting a journey into directives the engine can apply.
     *
     * Only [AlertStatus.CONFIRMED] alerts change routing. An unconfirmed report is one person's
     * word: it is surfaced through [RoutingDisruptions.pendingConfirmations] so a traveller passing
     * by can settle it, and only starts bending itineraries once the crowd agrees.
     */
    suspend fun disruptionsFor(
        stopNames: List<String>,
        lineNames: List<String>
    ): RoutingDisruptions = withContext(Dispatchers.Default) {
        if (api == null || (stopNames.isEmpty() && lineNames.isEmpty())) {
            return@withContext RoutingDisruptions()
        }

        val stopAlerts = getStopAlerts(stopNames)
        val lineAlerts = getLineAlerts(lineNames)
        if (stopAlerts.isEmpty() && lineAlerts.isEmpty()) return@withContext RoutingDisruptions()

        val blockedStops = mutableSetOf<String>()
        val penalties = mutableMapOf<String, Int>()
        val blockedLines = mutableSetOf<String>()
        val warnedLines = mutableSetOf<String>()
        val confirmed = mutableListOf<CommunityAlert>()
        val pending = mutableListOf<NavigationAlertPrompt>()

        // The API key is the stop name as the backend stores it; map it back to the caller's
        // spelling so accents, case and punctuation differences don't turn into silent misses.
        val callerByApiKey = matchApiKeysToCallerNames(stopAlerts.keys, stopNames)
        stopAlerts.forEach { (apiKey, target) ->
            val stopName = callerByApiKey[apiKey] ?: apiKey
            target.confirmed.forEach { alert ->
                when (alert.severity) {
                    AlertSeverity.BLOCKING -> blockedStops.add(stopName)
                    AlertSeverity.WARNING ->
                        penalties[stopName] = maxOf(penalties[stopName] ?: 0, warningPenaltySeconds)
                    AlertSeverity.INFO -> return@forEach
                }
                confirmed.add(alert)
            }
            pending.addAll(promptsFor(target.confirmed, target.unconfirmed))
        }

        lineAlerts.forEach { (lineName, target) ->
            target.confirmed.forEach { alert ->
                when (alert.severity) {
                    AlertSeverity.BLOCKING -> blockedLines.add(lineName)
                    AlertSeverity.WARNING -> warnedLines.add(lineName)
                    AlertSeverity.INFO -> return@forEach
                }
                confirmed.add(alert)
            }
            pending.addAll(promptsFor(target.confirmed, target.unconfirmed))
        }

        RoutingDisruptions(
            blockedStopNames = blockedStops,
            blockedLineNames = blockedLines,
            stopPenaltySeconds = penalties,
            warnedLineNames = warnedLines,
            confirmedAlerts = confirmed.distinctBy { it.id },
            // The same alert comes back from both queries when it names a stop and a line.
            pendingConfirmations = pending.distinctBy { it.alert.id }
        ).also {
            Log.d(
                TAG,
                "Disruptions: blocked stops=${it.blockedStopNames.size}, penalised=${it.stopPenaltySeconds.size}, " +
                    "blocked lines=${it.blockedLineNames.size}, warned lines=${it.warnedLineNames.size}, " +
                    "pending=${it.pendingConfirmations.size}"
            )
        }
    }

    /**
     * Confirms or refutes an alert.
     *
     * A no-op without a device id provider: an anonymous vote could be neither weighted by trust
     * nor deduplicated, which makes it indistinguishable from ballot stuffing.
     */
    suspend fun vote(alertId: String, confirm: Boolean): CommunityAlert? = withContext(ioDispatcher) {
        val deviceId = deviceIdProvider?.currentOrRotate() ?: return@withContext null
        answeredAlertIds.add(alertId)
        api?.voteOnAlert(alertId, confirm, deviceId)
    }

    /**
     * Decides what, if anything, to ask the traveller about the alerts on their route.
     *
     * Unconfirmed reports are always worth a question. Confirmed ones are only re-asked once they
     * have gone quiet for [staleAfterMinutes] — asking about an alert three people just vouched
     * for would train travellers to dismiss the prompt without reading it.
     */
    private fun promptsFor(
        confirmed: List<CommunityAlert>,
        unconfirmed: List<CommunityAlert>
    ): List<NavigationAlertPrompt> {
        val now = Clock.System.now()

        // Never ask twice. An alert this device has voted on is settled as far as it is concerned;
        // re-offering it turns a one-tap question into a loop.
        fun CommunityAlert.isAnswered() = viewerHasVoted || id in answeredAlertIds

        val stale = confirmed.filter { alert ->
            if (alert.isAnswered()) return@filter false
            val updatedAt = runCatching { Instant.parse(alert.updatedAt) }.getOrNull() ?: return@filter false
            (now - updatedAt).inWholeMinutes >= staleAfterMinutes
        }

        return unconfirmed.filterNot { it.isAnswered() }
            .map { NavigationAlertPrompt(it, NavigationAlertPromptKind.LOW_KARMA_CONFIRM) } +
            stale.map { NavigationAlertPrompt(it, NavigationAlertPromptKind.HIGH_KARMA_STILL_THERE) }
    }

    /**
     * Maps each API stop key to the caller's own spelling of that stop.
     *
     * Exact match first, then a normalised one — but only when it is unambiguous: two caller stops
     * normalising to the same key would make either choice a guess.
     */
    private fun matchApiKeysToCallerNames(
        apiKeys: Set<String>,
        callerNames: List<String>
    ): Map<String, String> {
        val callersByNormalized = callerNames.groupBy(SearchUtils::normalizeStopKey)
        val result = linkedMapOf<String, String>()

        apiKeys.forEach { apiKey ->
            if (apiKey in callerNames) {
                result[apiKey] = apiKey
                return@forEach
            }
            val candidates = callersByNormalized[SearchUtils.normalizeStopKey(apiKey)].orEmpty()
            if (candidates.size == 1) result[apiKey] = candidates.first()
        }
        return result
    }
}
