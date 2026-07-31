package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.platform.FileSystem

/**
 * The cold-start half of the downloaded-dataset feature: repair, promote, and guard —
 * all local, no network. Wired into RaptorLibrary initialization so a downloaded
 * dataset takes effect exactly once, at the next launch, and a dataset that breaks
 * loading is rolled back to the bundle before it can brick the app.
 *
 * Ordering matters: [prepareForLoad] and [beginLoad] MUST both run before anything
 * reads `current` (i.e. before the first [DatasetStore] read), so the active tree is
 * settled — promoted or reverted — for the whole session.
 */
class DatasetLifecycle(val storage: DatasetStorage) {

    /** App entry point: roots the dataset tree under the app-private files dir. */
    constructor(fileSystem: FileSystem) : this(DatasetStorage(root = "${fileSystem.filesDir()}/datasets"))

    val installer = DatasetInstaller(storage)
    val guard = DatasetHealthGuard(storage)

    /** Repairs an interrupted swap and promotes a pending download into `current`. */
    fun prepareForLoad() {
        installer.recover()
        installer.promotePending()
    }

    /**
     * Decides whether the now-active dataset can be trusted to load. Reverts to the
     * bundle (and quarantines the version) when it has already failed repeatedly.
     * Returns the version about to be loaded, or null once on the bundle.
     */
    fun beginLoad(): String? {
        val active = installer.activeVersion() ?: return null
        return when (guard.beforeInit(active)) {
            InitPlan.REVERT -> {
                installer.discardCurrent()
                guard.quarantine(active)
                null
            }
            InitPlan.PROCEED -> active
        }
    }

    /** Call after a successful load, so the active version's failure count is cleared. */
    fun loadSucceeded() {
        guard.recordSuccess(installer.activeVersion())
    }
}
