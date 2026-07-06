package eu.dotshell.massilia.generic.data.models.itinerary

/**
 * Enum for time selection mode: departure or arrival
 */
enum class TimeMode {
    DEPARTURE, // Search by departure time (default)
    ARRIVAL // Search by arrival time ("I need to be there by...")
}
