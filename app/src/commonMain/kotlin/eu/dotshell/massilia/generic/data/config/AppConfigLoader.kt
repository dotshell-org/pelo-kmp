package eu.dotshell.massilia.generic.data.config

import eu.dotshell.massilia.platform.FileSystem
import eu.dotshell.massilia.platform.Log
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json

/**
 * Loads and caches the application configuration from the bundled `config.json`
 * asset. Fully cross-platform: reads via the [FileSystem] abstraction and parses
 * with kotlinx.serialization (replaces the former SnakeYAML + Gson pipeline).
 */
object AppConfigLoader {
    private const val TAG = "AppConfigLoader"
    private const val CONFIG_FILE = "config.json"

    @Volatile
    private var config: AppConfig? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun loadConfig(fileSystem: FileSystem): AppConfig {
        config?.let { return it }
        val loaded = parse(fileSystem)
        config = loaded
        return loaded
    }

    fun getConfig(): AppConfig {
        return config ?: throw IllegalStateException(
            "AppConfig not initialized. Call loadConfig(fileSystem) first."
        )
    }

    private fun parse(fileSystem: FileSystem): AppConfig {
        return try {
            val raw = fileSystem.readAsset(CONFIG_FILE)
            val parsed = json.decodeFromString(AppConfig.serializer(), raw)
            val errors = AppConfigValidator.validate(fileSystem, parsed)
            if (errors.isNotEmpty()) {
                val message = buildString {
                    appendLine("$CONFIG_FILE validation failed:")
                    errors.forEach { appendLine("  - $it") }
                }
                throw ConfigLoadException(message)
            }
            parsed
        } catch (e: ConfigLoadException) {
            Log.e(TAG, e.message ?: "Config validation error", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $CONFIG_FILE", e)
            throw ConfigLoadException(
                "Failed to load $CONFIG_FILE: ${e.message}",
                e
            )
        }
    }
}

class ConfigLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
