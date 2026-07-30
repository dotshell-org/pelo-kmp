package eu.dotshell.pelo.generic.ui.screens.plan.itinerary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small "walk X min" chip shown in journey summary rows for the access/egress/pure walks
 * (the walks at the start or end of a journey — mid-journey transfers stay hidden).
 */
@Composable
fun WalkDurationChip(
    minutes: Int,
    tint: Color,
    textColor: Color,
    iconSize: Dp = 20.dp,
    fontSize: TextUnit = 12.sp
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = "${minutes.coerceAtLeast(1)} min",
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}
