package eu.dotshell.pelo.generic.ui.components

import org.maplibre.compose.map.GestureOptions

/**
 * Builds the map gesture configuration. [GestureOptions] has platform-specific constructors,
 * so this can't be expressed once in commonMain — each platform mirrors the legacy
 * `MapLibreView.applyInteractionSettings`: rotation is always off; scroll/zoom/tilt follow
 * [interactive].
 */
expect fun mapGestureOptions(interactive: Boolean): GestureOptions
