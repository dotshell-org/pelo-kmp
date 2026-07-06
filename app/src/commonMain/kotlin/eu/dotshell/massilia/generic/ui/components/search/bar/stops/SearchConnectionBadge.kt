package eu.dotshell.massilia.generic.ui.components.search.bar.stops

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.dotshell.massilia.generic.utils.graphics.LineIconResolver
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider

@Composable
fun SearchConnectionBadge(lineName: String, sizeDp: Int = 30) {
    val drawableName = LineIconResolver.getDrawableNameForLineName(lineName)
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val stringProvider = StringProvider(LocalPlatformContext.current)

    if (drawableName.isNotBlank() && drawableProvider.hasDrawable(drawableName)) {
        Image(
            painter = drawableProvider.getPainter(drawableName),
            contentDescription = stringProvider["line_icon"].replace("%s", lineName),
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}
