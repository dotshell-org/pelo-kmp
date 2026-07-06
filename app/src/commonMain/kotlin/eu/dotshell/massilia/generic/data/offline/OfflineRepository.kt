package eu.dotshell.massilia.generic.data.offline

import eu.dotshell.massilia.platform.ioDispatcher

import eu.dotshell.massilia.generic.data.models.geojson.Feature
import eu.dotshell.massilia.generic.data.models.geojson.StopFeature
import eu.dotshell.massilia.generic.data.models.realtime.alerts.official.TrafficAlert
import eu.dotshell.massilia.generic.data.repository.api.OfflineRepository as ApiOfflineRepository
import eu.dotshell.massilia.platform.FileSystem
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Dedicated persistent storage for offline data.
 * Cross-platform: file IO + gzip via okio ([GzipFileStore]), key-value metadata
 * via the [Settings] abstraction, paths rooted at [FileSystem.filesDir] (NOT
 * cacheDir, so data is not purged by the OS).
 */
class OfflineRepository(context: PlatformContext) : ApiOfflineRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val fileSystem = FileSystem(context)
    private val settings = Settings(context, "offline_data_meta")

    private val offlineDir = "${fileSystem.filesDir()}/offline_data".also { GzipFileStore.ensureDir(it) }
    private val busDir = "$offlineDir/bus".also { GzipFileStore.ensureDir(it) }

    companion object {
        private const val TAG = "OfflineRepository"

        private const val FILE_METRO_LINES = "metro_lines.json.gz"
        private const val FILE_TRAM_LINES = "tram_lines.json.gz"
        private const val FILE_BUS_LINES = "bus_lines.json.gz"
        private const val FILE_NAVIGONE_LINES = "navigone_lines.json.gz"
        private const val FILE_TRAMBUS_LINES = "trambus_lines.json.gz"
        private const val FILE_RX_LINES = "rx_lines.json.gz"
        private const val FILE_STOPS = "stops.json.gz"
        private const val FILE_TRAFFIC_ALERTS = "traffic_alerts.json.gz"

        private const val KEY_LAST_DOWNLOAD = "last_download_timestamp"
        private const val KEY_MAP_TILES_DOWNLOADED = "map_tiles_downloaded"
        private const val KEY_DOWNLOADED_MAP_STYLES = "downloaded_map_styles"
        private const val KEY_SELECTED_MAP_STYLES = "selected_map_styles"
        private const val KEY_DATA_VERSION = "offline_data_version"
        private const val DATA_VERSION = 1
    }

    // ===== SAVE METHODS =====

    suspend fun saveMetroLines(lines: List<Feature>) =
        writeCompressed(FILE_METRO_LINES, lines.sanitizeForSerialization())

    suspend fun saveTramLines(lines: List<Feature>) =
        writeCompressed(FILE_TRAM_LINES, lines.sanitizeForSerialization())

    /** Clears all existing bus line files to prepare for a fresh download. */
    fun clearBusLines() {
        GzipFileStore.delete("$offlineDir/$FILE_BUS_LINES")
        val busFiles = GzipFileStore.list(busDir)
        busFiles.forEach { GzipFileStore.delete(it.toString()) }
        Log.i(TAG, "clearBusLines: cleared ${busFiles.size} files")
    }

    /**
     * Saves a page of bus lines, grouping by line name into individual files.
     * Appends to existing files if a line spans multiple pages.
     */
    suspend fun saveBusLinesPage(lines: List<Feature>) = withContext(ioDispatcher) {
        val safeLines = lines.sanitizeForSerialization()
        val grouped = safeLines.groupBy { it.properties.lineName.uppercase() }
        for ((lineName, features) in grouped) {
            val safeFileName = lineName.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json.gz"
            val filePath = "$busDir/$safeFileName"
            try {
                if (GzipFileStore.exists(filePath)) {
                    val existingJson = GzipFileStore.readGzip(filePath)
                    val existing = if (existingJson != null) {
                        runCatching { json.decodeFromString<List<Feature>>(existingJson) }.getOrDefault(emptyList())
                    } else emptyList()
                    GzipFileStore.writeGzip(filePath, json.encodeToString(existing + features))
                } else {
                    GzipFileStore.writeGzip(filePath, json.encodeToString(features))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save bus line file $safeFileName: ${e.message}", e)
            }
        }
    }

    suspend fun saveNavigoneLines(lines: List<Feature>) =
        writeCompressed(FILE_NAVIGONE_LINES, lines.sanitizeForSerialization())

    suspend fun saveTrambusLines(lines: List<Feature>) =
        writeCompressed(FILE_TRAMBUS_LINES, lines.sanitizeForSerialization())

    suspend fun saveRxLines(lines: List<Feature>) =
        writeCompressed(FILE_RX_LINES, lines.sanitizeForSerialization())

    override suspend fun saveStops(stops: List<StopFeature>) {
        writeCompressed(FILE_STOPS, stops.sanitizeStopsForSerialization())
    }

    override suspend fun clearStopsCache() {
        withContext(ioDispatcher) {
            GzipFileStore.delete("$offlineDir/$FILE_STOPS")
        }
    }

    override suspend fun saveTrafficAlerts(alerts: List<TrafficAlert>) {
        writeCompressed(FILE_TRAFFIC_ALERTS, alerts)
    }

    // ===== LOAD METHODS =====

    suspend fun loadMetroLines(): List<Feature>? = readCompressed(FILE_METRO_LINES)

    suspend fun loadTramLines(): List<Feature>? = readCompressed(FILE_TRAM_LINES)

    /** Returns the names of all available offline bus line files. */
    fun getAvailableBusLineNames(): List<String> {
        return GzipFileStore.list(busDir)
            .filter { it.name.endsWith(".json.gz") }
            .map { it.name.removeSuffix(".json.gz") }
    }

    suspend fun loadNavigoneLines(): List<Feature>? = readCompressed(FILE_NAVIGONE_LINES)

    suspend fun loadTrambusLines(): List<Feature>? = readCompressed(FILE_TRAMBUS_LINES)

    suspend fun loadRxLines(): List<Feature>? = readCompressed(FILE_RX_LINES)

    override suspend fun loadStops(): List<StopFeature>? = readCompressed(FILE_STOPS)

    override suspend fun loadTrafficAlerts(): List<TrafficAlert>? = readCompressed(FILE_TRAFFIC_ALERTS)

    /**
     * Loads non-bus offline lines (metro + tram + navigone + trambus + rx).
     * Bus lines are NOT included to avoid OOM — use the bus files individually.
     */
    override suspend fun loadAllLines(): List<Feature> {
        val metro = loadMetroLines() ?: emptyList()
        val tram = loadTramLines() ?: emptyList()
        val navigone = loadNavigoneLines() ?: emptyList()
        val trambus = loadTrambusLines() ?: emptyList()
        val rx = loadRxLines() ?: emptyList()
        return metro + tram + navigone + trambus + rx
    }

    // ===== METADATA =====

    fun markDownloadComplete() {
        settings.putLong(KEY_LAST_DOWNLOAD, Clock.System.now().toEpochMilliseconds())
        settings.putInt(KEY_DATA_VERSION, DATA_VERSION)
    }

    fun getDownloadedMapStyles(): Set<String> {
        if (settings.contains(KEY_DOWNLOADED_MAP_STYLES)) {
            return settings.getStringSet(KEY_DOWNLOADED_MAP_STYLES, emptySet())
        }
        // Migration: if old boolean was true, assume positron was downloaded
        if (settings.getBoolean(KEY_MAP_TILES_DOWNLOADED, false)) {
            val migrated = setOf("positron")
            settings.putStringSet(KEY_DOWNLOADED_MAP_STYLES, migrated)
            return migrated
        }
        return emptySet()
    }

    fun setDownloadedMapStyles(styles: Set<String>) {
        settings.putStringSet(KEY_DOWNLOADED_MAP_STYLES, styles)
        settings.putBoolean(KEY_MAP_TILES_DOWNLOADED, styles.isNotEmpty())
    }

    fun getSelectedMapStyles(): Set<String> {
        return if (settings.contains(KEY_SELECTED_MAP_STYLES)) {
            settings.getStringSet(KEY_SELECTED_MAP_STYLES, setOf("positron"))
        } else setOf("positron")
    }

    fun setSelectedMapStyles(styles: Set<String>) {
        settings.putStringSet(KEY_SELECTED_MAP_STYLES, styles)
    }

    fun getOfflineDataInfo(): OfflineDataInfo {
        val lastDownload = settings.getLong(KEY_LAST_DOWNLOAD, 0L)
        val downloadedStyles = getDownloadedMapStyles()
        val hasData = lastDownload > 0L && GzipFileStore.list(offlineDir).isNotEmpty()
        val busFiles = GzipFileStore.list(busDir)
        val busCount = busFiles.count { it.name.endsWith(".json.gz") }

        return OfflineDataInfo(
            isAvailable = hasData,
            lastDownloadTimestamp = lastDownload,
            totalSizeBytes = if (hasData) calculateTotalSize() else 0L,
            mapTilesDownloaded = downloadedStyles.isNotEmpty(),
            downloadedMapStyles = downloadedStyles,
            busLinesCount = busCount
        )
    }

    // ===== INTERNAL =====

    private fun calculateTotalSize(): Long {
        val mainSize = GzipFileStore.list(offlineDir).sumOf { p ->
            if (p.name == "bus") 0L else GzipFileStore.size(p.toString())
        }
        val busSize = GzipFileStore.list(busDir).sumOf { GzipFileStore.size(it.toString()) }
        // Include MapLibre offline tiles database (stored by MapLibre in filesDir)
        val mapTilesSize = GzipFileStore.size("${fileSystem.filesDir()}/mbgl-offline.db")
        return mainSize + busSize + mapTilesSize
    }

    private suspend inline fun <reified T> writeCompressed(fileName: String, data: T) {
        withContext(ioDispatcher) {
            try {
                GzipFileStore.writeGzip("$offlineDir/$fileName", json.encodeToString(data))
            } catch (e: Exception) {
                Log.e(TAG, "FAILED writing $fileName: ${e.message}", e)
            }
        }
    }

    private suspend inline fun <reified T> readCompressed(fileName: String): T? =
        withContext(ioDispatcher) {
            try {
                val jsonString = GzipFileStore.readGzip("$offlineDir/$fileName")
                if (jsonString.isNullOrBlank()) null else json.decodeFromString<T>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading $fileName", e)
                GzipFileStore.delete("$offlineDir/$fileName")
                null
            }
        }
}

/**
 * Sanitizes Feature list before kotlinx.serialization encoding.
 * Defensive against null injected into non-nullable Kotlin String fields by
 * legacy reflection-based deserializers.
 */
@Suppress("UNCHECKED_CAST")
fun List<Feature>.sanitizeForSerialization(): List<Feature> {
    return map { feature ->
        val props = feature.properties
        val safeProps = props.copy(
            lineName = (props.lineName as String?) ?: "",
            traceCode = (props.traceCode as String?) ?: "",
            lineId = (props.lineId as String?) ?: "",
            traceType = (props.traceType as String?) ?: "",
            traceName = (props.traceName as String?) ?: "",
            origin = (props.origin as String?) ?: "",
            destination = (props.destination as String?) ?: "",
            originName = (props.originName as String?) ?: "",
            destinationName = (props.destinationName as String?) ?: "",
            transportType = (props.transportType as String?) ?: "",
            startDate = (props.startDate as String?) ?: "",
            lineTypeCode = (props.lineTypeCode as String?) ?: "",
            lineTypeName = (props.lineTypeName as String?) ?: "",
            sortCode = (props.sortCode as String?) ?: "",
            versionName = (props.versionName as String?) ?: "",
            lastUpdate = (props.lastUpdate as String?) ?: "",
            lastUpdateFme = (props.lastUpdateFme as String?) ?: ""
        )
        val safeGeometry = feature.multiLineStringGeometry.copy(
            type = (feature.multiLineStringGeometry.type as String?) ?: "MultiLineString",
            coordinates = sanitizeCoordinates(feature.multiLineStringGeometry.coordinates)
        )
        val safeId = (feature.id as String?) ?: ""
        val safeType = (feature.type as String?) ?: "Feature"
        feature.copy(
            type = safeType,
            id = safeId,
            multiLineStringGeometry = safeGeometry,
            properties = safeProps,
            bbox = sanitizeDoubleList(feature.bbox)
        )
    }
}

/** Sanitizes StopFeature list before kotlinx.serialization encoding. */
@Suppress("UNCHECKED_CAST")
fun List<StopFeature>.sanitizeStopsForSerialization(): List<StopFeature> {
    return map { stop ->
        val safeType = (stop.type as String?) ?: "Feature"
        val safeId = (stop.id as String?) ?: ""
        val safeGeometry = stop.geometry.copy(
            type = (stop.geometry.type as String?) ?: "Point",
            coordinates = sanitizeDoubleList(stop.geometry.coordinates)
        )
        val props = stop.properties
        val safeProps = props.copy(
            nom = (props.nom as String?) ?: "",
            desserte = (props.desserte as String?) ?: ""
        )

        stop.copy(
            type = safeType,
            id = safeId,
            geometry = safeGeometry,
            properties = safeProps,
            bbox = sanitizeDoubleList(stop.bbox)
        )
    }
}

private fun sanitizeDoubleList(raw: Any?): List<Double> {
    val values = raw as? List<*> ?: return emptyList()
    return values.mapNotNull { (it as? Number)?.toDouble() }
}

private fun sanitizeCoordinates(raw: Any?): List<List<List<Double>>> {
    val lines = raw as? List<*> ?: return emptyList()
    return lines.mapNotNull { line ->
        val points = line as? List<*> ?: return@mapNotNull null
        val safePoints = points.mapNotNull { point ->
            val pair = point as? List<*> ?: return@mapNotNull null
            val safePair = pair.mapNotNull { (it as? Number)?.toDouble() }
            safePair.takeIf { it.size >= 2 }
        }
        safePoints.takeIf { it.isNotEmpty() }
    }
}
