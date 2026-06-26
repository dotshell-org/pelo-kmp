package eu.dotshell.pelo.generic.ui.components

import org.maplibre.compose.map.GestureOptions

actual fun mapGestureOptions(interactive: Boolean): GestureOptions = GestureOptions(
    isRotateEnabled = interactive,
    isScrollEnabled = interactive,
    isTiltEnabled = interactive,
    isZoomEnabled = interactive,
    isDoubleTapEnabled = interactive,
    isQuickZoomEnabled = interactive
)
