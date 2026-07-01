package eu.dotshell.pelo.generic.utils.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.dotshell.pelo.generic.ui.theme.AccentColor
import eu.dotshell.pelo.generic.ui.theme.Green500
import eu.dotshell.pelo.generic.ui.theme.Orange500
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

        return when (diff) {
            in 0..1 -> AccentColor
            in 2..14 -> Orange500
            else -> Green500
        }
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
