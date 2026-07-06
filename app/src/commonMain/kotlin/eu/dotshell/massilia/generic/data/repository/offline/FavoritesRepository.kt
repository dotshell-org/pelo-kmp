package eu.dotshell.massilia.generic.data.repository.offline

import eu.dotshell.massilia.generic.data.local_history.FavoriteAuditEntry
import eu.dotshell.massilia.generic.data.models.stops.Favorite
import eu.dotshell.massilia.generic.data.repository.api.FavoritesRepository as ApiFavoritesRepository
import eu.dotshell.massilia.generic.data.telemetry.TelemetryEmitter
import eu.dotshell.massilia.platform.PlatformContext
import eu.dotshell.massilia.platform.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for managing favorites — both the new user-created favorites
 * and the legacy favorite stops system (kept for migration).
 *
 * Multiplatform: uses [Settings] abstraction + kotlinx.serialization instead of
 * SharedPreferences + Gson.
 */
class FavoritesRepository(context: PlatformContext) : ApiFavoritesRepository {

    private val settings = Settings(context, "massilia_prefs")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val keyFavoriteStops = "favorites_stops"
    private val keyStopDessertePrefix = "stop_desserte_"

    // New keys for the updated favorites system
    private val keyUserFavorites = "user_favorites_v2"

    // Background scope for async audit log writes (fire-and-forget)
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun appendFavoriteAudit(action: String, type: String, refId: String?) {
        val storage = TelemetryEmitter.localHistory() ?: return
        auditScope.launch {
            storage.appendFavoriteAudit(
                FavoriteAuditEntry(
                    atEpochMs = Clock.System.now().toEpochMilliseconds(),
                    action = action,
                    favoriteType = type,
                    refId = refId
                )
            )
        }
    }

    override fun getFavoriteStops(): Set<String> {
        return settings.getStringSet(keyFavoriteStops, emptySet())
    }

    fun saveFavoriteStops(favorites: Set<String>) {
        settings.putStringSet(keyFavoriteStops, favorites)
    }

    override fun toggleFavoriteStop(stopName: String, desserte: String?): Boolean {
        val favorites = getFavoriteStops().toMutableSet()
        val isAdding = !favorites.contains(stopName)

        if (isAdding) favorites.add(stopName) else favorites.remove(stopName)

        settings.putStringSet(keyFavoriteStops, favorites)
        if (isAdding && !desserte.isNullOrEmpty()) {
            settings.putString(keyStopDessertePrefix + stopName, desserte)
        } else if (!isAdding) {
            settings.remove(keyStopDessertePrefix + stopName)
        }

        appendFavoriteAudit(
            action = if (isAdding) "added" else "removed",
            type = "stop",
            refId = stopName
        )
        return isAdding
    }

    fun getDesserteForStop(stopName: String): String? {
        val value = settings.getString(keyStopDessertePrefix + stopName, "")
        return value.takeIf { it.isNotBlank() }
    }

    // === New favorites system ===

    /**
     * Get all user-created favorites.
     */
    override fun getUserFavorites(): List<Favorite> {
        val raw = settings.getString(keyUserFavorites, "").takeIf { it.isNotBlank() }
            ?: return emptyList()
        return try {
            json.decodeFromString<List<Favorite>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Save the list of user-created favorites.
     */
    fun saveUserFavorites(favorites: List<Favorite>) {
        settings.putString(keyUserFavorites, json.encodeToString(favorites))
    }

    /**
     * Add a new favorite.
     */
    override fun addFavorite(favorite: Favorite): Boolean {
        val favorites = getUserFavorites().toMutableList()
        // Check if a favorite with the same name already exists
        if (favorites.any { it.name.equals(favorite.name, ignoreCase = true) }) {
            return false
        }
        favorites.add(favorite)
        saveUserFavorites(favorites)
        appendFavoriteAudit(action = "added", type = "user", refId = favorite.id)
        return true
    }

    /**
     * Remove a favorite by ID.
     */
    override fun removeFavorite(favoriteId: String): Boolean {
        val favorites = getUserFavorites().toMutableList()
        val initialSize = favorites.size
        favorites.removeAll { it.id == favoriteId }
        saveUserFavorites(favorites)
        val removed = favorites.size < initialSize
        if (removed) {
            appendFavoriteAudit(action = "removed", type = "user", refId = favoriteId)
        }
        return removed
    }

    /**
     * Generate a unique ID for a new favorite.
     */
    override fun generateFavoriteId(): String {
        return "fav_${Clock.System.now().toEpochMilliseconds()}"
    }
}
