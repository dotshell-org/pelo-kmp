package eu.dotshell.pelo.generic.data.models.realtime.alerts.community

/**
 * API response containing all stop alerts
 * Structure: { "stopId1": { "karma_below_threshold": [...], "karma_at_or_above_threshold": [...] }, ... }
 */
typealias UserStopAlertsResponse = Map<String, StopAlertsStatus>
