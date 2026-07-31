package eu.dotshell.pelo.generic.ui.viewmodel

import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.applicationContextOf
import kotlin.concurrent.Volatile

/**
 * Hands out the one [TransportViewModel] the app uses.
 *
 * It used to be built by hand inside a `LaunchedEffect` in `App` and parked in a `remember`, which
 * meant it was attached to no ViewModelStoreOwner at all: `onCleared` never ran, `viewModelScope`
 * was never cancelled, and it retained whichever Activity was current when it was built. Every
 * Activity recreation then built a second one — and a system dark-mode switch is enough to trigger
 * that, since the manifest does not list `uiMode` in `configChanges` — while the first kept its
 * coroutines, its SSE subscription and its caches alive against an Activity that no longer existed.
 *
 * So it is scoped to the process instead, exactly like `RaptorRepository`, `SchedulesRepository`
 * and `JourneyCache` already are, and built on the application context so no Activity can be
 * pinned through it.
 *
 * Deliberately never disposed from composition: tearing the view model down on every rotation is
 * the failure this replaces. [TransportViewModel.dispose] exists for tests and for any future
 * owner that genuinely scopes it.
 */
object TransportViewModelHolder {

    @Volatile
    private var instance: TransportViewModel? = null

    /**
     * No `synchronized` in commonMain — a @Volatile double-check is enough for a startup
     * singleton, matching the repositories named above. A lost race only builds one extra
     * instance, which is discarded immediately.
     */
    fun getOrCreate(context: PlatformContext): TransportViewModel =
        instance ?: TransportViewModel(applicationContextOf(context)).also { instance = it }
}
