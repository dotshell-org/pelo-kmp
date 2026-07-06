package eu.dotshell.massilia.generic.data.models.navigation

data class NavigationKeyStopDeadline(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val deadlineSeconds: Int,
    val type: NavigationKeyStopType
)
