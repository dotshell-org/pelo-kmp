package eu.dotshell.pelo.generic.data.network

import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.CommunityAlert
import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.CommunityAlertsResponse

/**
 * API surface for community (karma-based) disruption alerts.
 *
 * Implemented by transport clients whose network has a backend for it; clients of
 * fully-local networks simply don't implement it and the feature degrades to "no alerts".
 */
interface UserStopAlertsApi {
    suspend fun getStopAlerts(stopIds: List<String>): CommunityAlertsResponse

    /**
     * Alerts attached to whole lines, including those reported at one of their stops — trouble on
     * a low-traffic line shows up here long before any single stop of it accumulates enough
     * reports to stand out on its own.
     */
    suspend fun getLineAlerts(lineIds: List<String>): CommunityAlertsResponse

    /**
     * Confirms or refutes an alert. Returns the updated alert, or null when the call failed.
     *
     * [deviceId] is the caller's daily-rotating alert identifier, passed in rather than read here
     * so the network client stays free of storage concerns.
     */
    suspend fun voteOnAlert(alertId: String, confirm: Boolean, deviceId: String): CommunityAlert?
}
