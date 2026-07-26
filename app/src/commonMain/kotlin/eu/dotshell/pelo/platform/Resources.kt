package eu.dotshell.pelo.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import eu.dotshell.pelo.resources.Res
import eu.dotshell.pelo.resources.allDrawableResources
import eu.dotshell.pelo.resources.allStringResources
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Cross-platform drawable access backed by Compose Multiplatform resources
 * (`composeResources/drawable`). Resources are resolved dynamically by name via
 * the generated [Res.allDrawableResources] registry, so the same name-based API
 * used across the UI works unchanged on every platform (was Android `getIdentifier`).
 */
@OptIn(ExperimentalResourceApi::class)
class DrawableProvider(@Suppress("unused") private val context: PlatformContext) {

    @Composable
    fun getPainter(name: String): Painter =
        painterResource(Res.allDrawableResources.getValue(name))

    fun hasDrawable(name: String): Boolean =
        Res.allDrawableResources.containsKey(name)
}

/**
 * Cross-platform string access backed by Compose Multiplatform resources
 * (`composeResources/values`), resolved dynamically by name.
 */
@OptIn(ExperimentalResourceApi::class)
class StringProvider(@Suppress("unused") private val context: PlatformContext) {

    @Composable
    operator fun get(name: String): String =
        stringResource(Res.allStringResources.getValue(name))

    /** Same lookup as [get], filling the resource's positional placeholders with [args]. */
    @Composable
    fun format(name: String, vararg args: Any): String =
        stringResource(Res.allStringResources.getValue(name), *args)

    /**
     * Picks between a singular and a plural key by [count]. Compose's plural resources are not
     * exposed through the by-name registry this provider is built on, and fr/en both need only
     * the one/other split.
     */
    @Composable
    fun plural(singularName: String, pluralName: String, count: Int, vararg args: Any): String =
        format(if (count == 1) singularName else pluralName, *args)
}
