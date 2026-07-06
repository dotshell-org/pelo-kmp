package eu.dotshell.massilia.generic.ui.components.favorites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Train
import androidx.compose.ui.graphics.vector.ImageVector

internal fun favoriteIcon(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "home" -> Icons.Filled.Home
        "work" -> Icons.Filled.BusinessCenter
        "school" -> Icons.Filled.School
        "shopping" -> Icons.Filled.ShoppingCart
        "bus" -> Icons.Filled.DirectionsBus
        "train" -> Icons.Filled.Train
        "star" -> Icons.Filled.Star
        else -> Icons.Filled.Star
    }
}
