package eu.dotshell.pelo.generic.ui.screens.plan

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import eu.dotshell.pelo.generic.utils.graphics.LineIconResolver
import eu.dotshell.pelo.platform.DrawableProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.DrawableResource

/**
 * One line, ready to draw: everything the chip needs is resolved up front so composition does no
 * lookup work. [icon] is null when the line has no dedicated drawable (coloured fallback badge),
 * and [alertKey] is the pre-uppercased key used against the traffic-alert map.
 */
@Immutable
data class LineEntry(
    val name: String,
    val icon: DrawableResource?,
    val alertKey: String
)

/** A titled group of lines, in display order. [id] is the raw category key, localised by the UI. */
data class LineCategory(
    val id: String,
    val lines: List<LineEntry>
)

/**
 * Categorised line list backing [LinesBottomSheet].
 *
 * Building it costs a drawable lookup per line plus a natural sort per category — tens of
 * milliseconds, and it used to run inside the sheet's composition, i.e. on the main thread while
 * the sheet was animating open. It is now built on [Dispatchers.Default] and memoised, so the
 * app can warm it while the user is still on the map and the sheet composes from a ready list.
 *
 * The cache is read and written on the main thread only (the result is published *after*
 * [withContext] hands control back to the caller's dispatcher), so it needs no locking.
 */
object LinesCatalog {

    private var cachedSource: List<String>? = null
    private var cachedCategories: List<LineCategory>? = null

    /** Returns the memoised categories for [allLines], or null if they still have to be built. */
    fun peek(allLines: List<String>): List<LineCategory>? =
        cachedCategories?.takeIf { cachedSource == allLines }

    /** Memoised categories for [allLines], computed off the main thread on the first call. */
    suspend fun get(allLines: List<String>): List<LineCategory> {
        peek(allLines)?.let { return it }
        if (allLines.isEmpty()) return emptyList()

        val categories = withContext(Dispatchers.Default) { categorizeLines(allLines) }
        cachedSource = allLines
        cachedCategories = categories
        return categories
    }
}

/**
 * Categories for [allLines], from the cache when possible. Returns an empty list for the frame or
 * two it takes to build them when the cache is cold (a warmed cache renders on the first frame).
 */
@Composable
fun rememberLineCategories(allLines: List<String>): List<LineCategory> {
    var categories by remember(allLines) {
        mutableStateOf(LinesCatalog.peek(allLines) ?: emptyList())
    }
    LaunchedEffect(allLines) {
        if (categories.isEmpty()) categories = LinesCatalog.get(allLines)
    }
    return categories
}

/** Icons decoded per frame by [LineIconWarmup]. Four keeps a warm-up frame well inside budget. */
private const val WARMUP_ICONS_PER_FRAME = 4

/** Roughly what the lines sheet shows before any scrolling: five rows of six chips, plus slack. */
const val LINES_SHEET_FIRST_SCREEN_ICONS = 36

/**
 * Decodes line icons ahead of time, a few per frame.
 *
 * Compose Multiplatform's `painterResource()` is *blocking* on Android and iOS: it reads the
 * drawable file and parses its vector XML with `runBlocking` inside the composition. It also
 * memoises the decoded image globally and forever, so composing an icon once makes every later
 * use of it free. This walks the catalog and composes the icons nobody has scrolled to yet,
 * [WARMUP_ICONS_PER_FRAME] per frame, so the cost is paid in slack time instead of in the middle
 * of a fling. Nothing is drawn: only the decode-and-cache side effect matters.
 *
 * @param limit caps how many icons are warmed — used to warm just the first screenful while the
 *   user is still on the map, leaving the rest to the sheet itself.
 * @param pauseWhileScrolling list to stand aside for: warming stops while it is being scrolled,
 *   so the two never compete for the same frame. Taken as a state rather than a boolean so that
 *   the scroll read invalidates this composable alone, not the caller.
 */
@Composable
fun LineIconWarmup(
    allLines: List<String>,
    limit: Int = Int.MAX_VALUE,
    pauseWhileScrolling: ScrollableState? = null
) {
    val enabled = pauseWhileScrolling?.isScrollInProgress != true
    val platformContext = LocalPlatformContext.current
    val drawableProvider = remember(platformContext) { DrawableProvider(platformContext) }
    // Also what builds the catalog itself, off the main thread, on the first call.
    val categories = rememberLineCategories(allLines)

    val icons = remember(categories, limit) {
        categories
            .asSequence()
            .flatMap { category -> category.lines.asSequence() }
            .mapNotNull { it.icon }
            .take(limit)
            .toList()
    }

    var warmedCount by remember(icons) { mutableStateOf(0) }

    LaunchedEffect(icons, enabled) {
        if (!enabled) return@LaunchedEffect
        while (warmedCount < icons.size) {
            // Gives the window composed below its frame to decode before moving on.
            withFrameNanos { }
            warmedCount += WARMUP_ICONS_PER_FRAME
        }
    }

    // Only the current window stays composed: the decoded images live on in the resource cache,
    // so holding on to the painters afterwards would cost memory for nothing.
    val window = remember(icons, warmedCount) {
        if (warmedCount >= icons.size) {
            emptyList()
        } else {
            icons.subList(warmedCount, (warmedCount + WARMUP_ICONS_PER_FRAME).coerceAtMost(icons.size))
        }
    }
    window.forEach { icon ->
        key(icon) {
            @Suppress("UNUSED_EXPRESSION")
            drawableProvider.getPainter(icon)
        }
    }
}

/** Splits a name into digit/non-digit runs, so "C3" sorts before "C10". Hoisted: compiling this
 *  inside the comparator meant one Regex allocation per comparison, ~2000 per sheet opening. */
private val NUMBER_BOUNDARY = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")

private val naturalPartsComparator = Comparator<List<String>> { partsA, partsB ->
    val maxParts = maxOf(partsA.size, partsB.size)

    for (i in 0 until maxParts) {
        val partA = partsA.getOrNull(i)
        val partB = partsB.getOrNull(i)

        if (partA == null) return@Comparator -1 // a est plus court
        if (partB == null) return@Comparator 1  // b est plus court

        val numA = partA.toIntOrNull()
        val numB = partB.toIntOrNull()

        if (numA != null && numB != null) {
            val numCompare = numA.compareTo(numB)
            if (numCompare != 0) return@Comparator numCompare
        } else {
            val strCompare = partA.compareTo(partB)
            if (strCompare != 0) return@Comparator strCompare
        }
    }
    return@Comparator 0
}

/**
 * Organises lines by category and filters those which haven't icon.
 */
private fun categorizeLines(lines: List<String>): List<LineCategory> {
    // Keep lines with icons, and keep NAVI* even without a dedicated icon file.
    val linesWithIcon = lines.mapNotNull { line ->
        val upperLine = line.uppercase()
        val icon = DrawableProvider.find(LineIconResolver.getDrawableNameForLineName(line))
        if (icon != null || upperLine.startsWith("NAVI")) {
            LineEntry(name = line, icon = icon, alertKey = upperLine)
        } else {
            null
        }
    }

    val metros = mutableListOf<LineEntry>()
    val trams = mutableListOf<LineEntry>()
    val funiculaires = mutableListOf<LineEntry>()
    val chrono = mutableListOf<LineEntry>()
    val pleineLune = mutableListOf<LineEntry>()
    val jd = mutableListOf<LineEntry>()
    val navigone = mutableListOf<LineEntry>()
    val gareExpress = mutableListOf<LineEntry>()
    val soyeuses = mutableListOf<LineEntry>()
    val navettes = mutableListOf<LineEntry>()
    val zi = mutableListOf<LineEntry>()
    val carsDuRhone = mutableListOf<LineEntry>()
    val bus = mutableListOf<LineEntry>()

    linesWithIcon.forEach { entry ->
        val upperLine = entry.alertKey
        when {
            upperLine in setOf("A", "B", "C", "D") -> metros.add(entry)
            upperLine.startsWith("F") && (upperLine == "F1" || upperLine == "F2") -> funiculaires.add(
                entry
            )

            upperLine.startsWith("TB") || upperLine == "RX" || upperLine.contains("RHON") -> trams.add(
                entry
            )

            upperLine.startsWith("T") && upperLine.length == 2 -> trams.add(entry)
            upperLine.startsWith("C") && upperLine.length >= 2 -> chrono.add(entry)
            upperLine.startsWith("PL") -> pleineLune.add(entry)
            upperLine.startsWith("JD") -> jd.add(entry)
            upperLine.startsWith("NAVI") -> navigone.add(entry)
            upperLine.startsWith("GE") -> gareExpress.add(entry)
            upperLine.startsWith("S") -> soyeuses.add(entry)
            upperLine.startsWith("ZI") -> zi.add(entry)
            upperLine.startsWith("N") -> navettes.add(entry)
            upperLine.length >= 3 && upperLine != "128" && upperLine.all { it.isDigit() } -> carsDuRhone.add(
                entry
            )

            else -> bus.add(entry)
        }
    }

    // Natural sort that correctly handles numbers in strings. The split key is computed once per
    // line instead of twice per comparison.
    fun naturalSort(entries: List<LineEntry>): List<LineEntry> =
        entries
            .map { it to it.name.split(NUMBER_BOUNDARY) }
            .sortedWith(compareBy(naturalPartsComparator) { it.second })
            .map { it.first }

    return buildList {
        fun addCategory(id: String, entries: List<LineEntry>) {
            if (entries.isNotEmpty()) add(LineCategory(id, naturalSort(entries)))
        }

        addCategory("Métro", metros)
        addCategory("Funiculaire", funiculaires)
        addCategory("Tramway", trams)
        addCategory("Navigone", navigone)
        addCategory("Chrono", chrono)
        addCategory("Pleine Lune", pleineLune)
        addCategory("Gare Express", gareExpress)
        addCategory("Navette", navettes)
        addCategory("Soyeuse", soyeuses)
        addCategory("Zone Industrielle", zi)
        addCategory("Bus", bus)
        addCategory("Cars du Rhône TCL unifié", carsDuRhone)
        addCategory("Junior Direct", jd)
    }
}
