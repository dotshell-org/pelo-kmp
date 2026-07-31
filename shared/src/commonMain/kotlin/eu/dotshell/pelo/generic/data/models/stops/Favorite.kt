package eu.dotshell.pelo.generic.data.models.stops

import kotlinx.serialization.Serializable

/**
 * Represents a user-created favorite with a name, icon, and associated stop
 */
@Serializable
data class Favorite(
    val id: String, // Unique identifier
    val name: String, // User-defined name for the favorite
    val iconName: String, // Name of the icon resource
    val stopName: String, // Name of the associated stop
)
