package eu.dotshell.pelo.generic.data.network

import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.UserStopAlertsResponse

/**
 * API surface for community (karma-based) user stop alerts.
 *
 * Implemented by transport clients whose network has a backend for it; clients of
 * fully-local networks simply don't implement it and the feature degrades to "no alerts".
 */
interface UserStopAlertsApi {
    suspend fun getUserStopAlerts(stopIds: List<String>): UserStopAlertsResponse
}
