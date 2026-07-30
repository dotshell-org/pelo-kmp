package eu.dotshell.pelo.generic.data.cache.journey

/**
 * Wrapper for cached journey with timestamp
 */
data class CachedJourney(
    val journeys: List<SerializableJourneyResult>,
    val timestamp: Long
)
