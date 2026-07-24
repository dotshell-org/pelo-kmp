package eu.dotshell.pelo.generic.utils.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.dotshell.pelo.generic.ui.theme.Green500
import eu.dotshell.pelo.generic.ui.theme.Amber500
import eu.dotshell.pelo.generic.ui.theme.AccentColor
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Stateless: an object so callers don't allocate one per list item / per sort comparison.
object DepartureManager {
    fun parseDepartureToMinutes(rawTime: String): Int? {
        val clean = if (rawTime.count { it == ':' } >= 2) rawTime.substringBeforeLast(":") else rawTime
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        return (hour * 60) + minute
    }

    @Composable
    fun formatRelativeDeparture(departureTime: String, strings: eu.dotshell.pelo.platform.StringProvider): String? {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val nowMinutes = now.hour * 60 + now.minute
        val departureMinutes = parseDepartureToMinutes(departureTime) ?: return null
        val diff = departureMinutes - nowMinutes

        if (diff < 0) return null
        if (diff == 0) return strings["time_less_than_minute"]
        if (diff < 60) return strings["time_in_minutes"].replace("%s", diff.toString())

        val hours = diff / 60
        val minutes = diff % 60
        return strings["time_in_hours_minutes"]
            .replace("%1\$s", hours.toString())
            .replace("%2\$s", minutes.toString().padStart(2, '0'))
    }

    fun getDepartureColor(departureTime: String): Color {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val nowMinutes = now.hour * 60 + now.minute
        val departureMinutes = parseDepartureToMinutes(
            departureTime
        ) ?: return Green500
        val diff = departureMinutes - nowMinutes

        if (diff < 0) return Green500

        // Urgency ramp: the charte turns the imminent tier orange, so the middle tier moves to
        // amber to stay distinguishable from it at small text sizes.
        return when (diff) {
            in 0..1 -> AccentColor
            in 2..14 -> Amber500
            else -> Green500
        }
    }

    /**
     * GTFS service-day times exceed 24h for after-midnight runs (e.g. night
     * lines: "25:30" = 01:30). Countdown/color computations need the raw
     * value to stay correct, so only the displayed text is normalized here.
     */
    fun formatDisplayTime(rawTime: String): String {
        val clean = if (rawTime.count { it == ':' } >= 2) rawTime.substringBeforeLast(":") else rawTime
        val parts = clean.split(":")
        if (parts.size < 2) return rawTime
        val hour = parts[0].toIntOrNull() ?: return rawTime
        if (parts[1].toIntOrNull() == null) return rawTime
        if (hour < 24) return clean
        return "${(hour % 24).toString().padStart(2, '0')}:${parts[1]}"
    }

    fun minutesUntilDeparture(rawTime: String): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val nowMinutes = now.hour * 60 + now.minute
        val departureMinutes = parseDepartureToMinutes(rawTime)
            ?: return Int.MAX_VALUE
        return if (departureMinutes >= nowMinutes) {
            departureMinutes - nowMinutes
        } else {
            (24 * 60 - nowMinutes) + departureMinutes
        }
    }
}
