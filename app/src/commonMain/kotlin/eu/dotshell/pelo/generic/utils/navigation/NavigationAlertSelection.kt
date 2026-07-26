package eu.dotshell.pelo.generic.utils.navigation

import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.AlertSeverity
import eu.dotshell.pelo.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.pelo.generic.data.repository.itinerary.itinerary.JourneyResult

/**
 * The one traffic alert worth interrupting the guidance for, or null.
 *
 * Only lines still ahead count: an alert on a line already left behind is history, and the point of
 * showing it mid-journey is to let the traveller change their mind while they still can.
 *
 * Informational alerts are left out. They are the bulk of the feed — works finishing next month,
 * a lift out of service at a station nobody on this journey passes — and putting them in front of
 * someone following turn-by-turn guidance would teach them to ignore the banner entirely.
 *
 * [alertsForLine] is the lookup by line name, injected so this stays testable without a ViewModel.
 */
fun selectNavigationAlert(
    journey: JourneyResult?,
    fromLegIndex: Int,
    alertsForLine: (String) -> List<TrafficAlert>,
): TrafficAlert? {
    if (journey == null) return null

    val linesAhead = journey.legs
        .drop(fromLegIndex.coerceAtLeast(0))
        .filterNot { it.isWalking }
        .mapNotNull { it.routeName?.takeIf { name -> name.isNotBlank() } }
        .distinct()
    if (linesAhead.isEmpty()) return null

    return linesAhead
        .flatMap(alertsForLine)
        .distinctBy { it.alertNumber }
        .filter { it.isWorthInterrupting() }
        // Lower level means more severe in this feed; alertNumber only breaks ties, so the same
        // situation always surfaces the same alert instead of flickering between equals.
        .minWithOrNull(compareBy({ it.severityLevel }, { it.alertNumber }))
}

private fun TrafficAlert.isWorthInterrupting(): Boolean =
    when (AlertSeverity.fromSeverityType(severityType, severityLevel)) {
        AlertSeverity.SIGNIFICANT_DELAYS, AlertSeverity.OTHER_EFFECT -> true
        AlertSeverity.INFORMATION, AlertSeverity.UNKNOWN -> false
    }
