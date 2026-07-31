package eu.dotshell.pelo.generic.ui.components.search.bar.stops

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

@Composable
fun SearchConnectionBadge(lineName: String, sizeDp: Int = 30) {
    // One of these per connection per search result, so allocating the providers and re-resolving
    // the icon name on every recomposition happened across the whole visible list at once.
    val context = LocalPlatformContext.current
    val drawableProvider = remember(context) { DrawableProvider(context) }
    val stringProvider = remember(context) { StringProvider(context) }
    val drawableName = remember(lineName) { LineIconResolver.getDrawableNameForLineName(lineName) }

    if (drawableName.isNotBlank() && drawableProvider.hasDrawable(drawableName)) {
        Image(
            painter = drawableProvider.getPainter(drawableName),
            contentDescription = stringProvider["line_icon"].replace("%s", lineName),
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}
