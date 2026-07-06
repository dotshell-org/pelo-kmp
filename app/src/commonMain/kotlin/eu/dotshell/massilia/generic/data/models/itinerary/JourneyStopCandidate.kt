package eu.dotshell.massilia.generic.data.models.itinerary

data class JourneyStopCandidate(
    val legIndex: Int,
    val isLegEnd: Boolean,
    val lat: Double,
    val lon: Double
)
