package eu.dotshell.pelo.generic.data.network.mapstyle

/**
 * Data class representing a map style
 */
data class MapStyleData(
    val key: String,
    val displayName: String,
    val styleUrl: String,
    val category: MapStyleCategory
)
