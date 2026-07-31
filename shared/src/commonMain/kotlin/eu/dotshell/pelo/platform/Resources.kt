package eu.dotshell.pelo.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import eu.dotshell.pelo.resources.Res
import eu.dotshell.pelo.resources.allDrawableResources
import eu.dotshell.pelo.resources.allStringResources
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Cross-platform drawable access backed by Compose Multiplatform resources
 * (`composeResources/drawable`). Resources are resolved dynamically by name via
 * the generated [Res.allDrawableResources] registry, so the same name-based API
 * used across the UI works unchanged on every platform (was Android `getIdentifier`).
 *
 * Carries no state — see [equals]. The [context] parameter is vestigial, left from the days when
 * lookup went through Android's `getIdentifier`; removing it would touch every call site, so it
 * stays for now.
 */
@Immutable
@OptIn(ExperimentalResourceApi::class)
class DrawableProvider(@Suppress("unused") private val context: PlatformContext) {

    @Composable
    fun getPainter(name: String): Painter =
        painterResource(Res.allDrawableResources.getValue(name))

    /** Same as [getPainter], for a resource already resolved through [find]. */
    @Composable
    fun getPainter(resource: DrawableResource): Painter =
        painterResource(resource)

    fun hasDrawable(name: String): Boolean =
        Res.allDrawableResources.containsKey(name)

    /**
     * All instances are interchangeable: every lookup goes to the same generated registry and the
     * context is unused.
     *
     * This is what actually restores skipping. @Immutable alone would not: Compose still asks
     * `equals` whether a parameter changed, so with identity equality a freshly built provider
     * always read as "changed" — and one is passed down into PlanContent, which meant that entire
     * subtree could never skip a recomposition.
     */
    override fun equals(other: Any?): Boolean = other is DrawableProvider

    override fun hashCode(): Int = DRAWABLE_PROVIDER_HASH

    companion object {
        private const val DRAWABLE_PROVIDER_HASH = 0x11D0C0DE

        /**
         * Name lookup that needs neither a context nor a composition, so screens can resolve
         * their icons once on a background thread instead of once per chip per recomposition.
         */
        fun find(name: String): DrawableResource? =
            Res.allDrawableResources[name]
    }
}

/**
 * Cross-platform string access backed by Compose Multiplatform resources
 * (`composeResources/values`), resolved dynamically by name.
 *
 * Stateless and interchangeable, for the same reasons as [DrawableProvider].
 */
@Immutable
@OptIn(ExperimentalResourceApi::class)
class StringProvider(@Suppress("unused") private val context: PlatformContext) {

    override fun equals(other: Any?): Boolean = other is StringProvider

    override fun hashCode(): Int = STRING_PROVIDER_HASH

    private companion object {
        private const val STRING_PROVIDER_HASH = 0x5710C0DE
    }

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
