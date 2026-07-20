package eu.dotshell.pelo.generic.data.dataset

import eu.dotshell.pelo.generic.data.config.AppConfigLoader
import eu.dotshell.pelo.platform.FileSystem
import eu.dotshell.pelo.platform.PlatformContext
import eu.dotshell.pelo.platform.isUnmeteredNetwork
import kotlinx.datetime.Clock

/**
 * Assembles the over-the-air update machinery from app config and platform services.
 * The single place the download feature is wired together, so both the startup check
 * and the settings "check now" action build it the same way.
 */
object DatasetUpdates {

    /**
     * Returns a scheduler wired to this app's dataset server, or null when the feature
     * is not configured (no `datasetUpdate` block) — in which case the app simply keeps
     * using its bundled data.
     */
    fun forApp(context: PlatformContext): DatasetUpdateScheduler? {
        val config = runCatching { AppConfigLoader.getConfig() }.getOrNull() ?: return null
        val update = config.datasetUpdate ?: return null

        val fileSystem = FileSystem(context)
        val lifecycle = DatasetLifecycle(fileSystem)
        val manager = DatasetUpdateManager(
            lifecycle = lifecycle,
            transport = KtorDatasetTransport(),
            baseUrl = update.baseUrl,
            city = update.city,
            activeCreatedAt = { DatasetInfoLoader.load(fileSystem)?.createdAt }
        )
        return DatasetUpdateScheduler(
            manager = manager,
            storage = lifecycle.storage,
            isUnmetered = { isUnmeteredNetwork(context) },
            now = { Clock.System.now().toEpochMilliseconds() }
        )
    }

    /** Maps a check outcome to one of the `timetable_*` string keys for the UI. */
    fun statusStringKey(result: CheckResult?): String = when (result) {
        is CheckResult.Downloaded, is CheckResult.AlreadyPending -> "timetable_update_ready"
        is CheckResult.UpToDate -> "timetable_up_to_date"
        is CheckResult.Incompatible -> "timetable_update_incompatible"
        is CheckResult.Failed, null -> "timetable_check_failed"
    }
}
