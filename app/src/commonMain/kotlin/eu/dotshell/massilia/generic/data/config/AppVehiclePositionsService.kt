package eu.dotshell.massilia.generic.data.config

import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.SiriData
import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.SimpleVehiclePosition
import eu.dotshell.massilia.generic.data.models.realtime.vehiclepositions.VehiclePositionsResponse
import eu.dotshell.massilia.generic.data.network.VehiclePositionsService
import eu.dotshell.massilia.platform.Log
import eu.dotshell.massilia.platform.createHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private const val TAG = "VehiclePositions"

/**
 * Cross-platform vehicle-positions service backed by a Ktor SSE stream
 * (replaces the former OkHttp EventSource implementation). Parsing is done
 * with kotlinx.serialization and works on every Ktor engine (OkHttp / Darwin).
 */
class AppVehiclePositionsService(
    private val config: TransportConfigData,
    private val rules: RulesData
) : VehiclePositionsService {

    override fun getVehiclePositionsStreamUrl(): String = config.vehiclePositionsStreamUrl

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(createHttpClientEngine()) {
        install(SSE)
    }

    override fun streamAllVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        return createVehiclePositionsFlow()
    }

    override fun streamVehiclePositionsByLine(lineName: String): Flow<Result<List<SimpleVehiclePosition>>> {
        return streamAllVehiclePositions().map { result ->
            result.map { positions ->
                positions.filter { position ->
                    position.lineName.equals(lineName, ignoreCase = true)
                }
            }
        }
    }

    override fun streamStrongLinesVehiclePositions(): Flow<Result<List<SimpleVehiclePosition>>> {
        val strongLines = rules.strongLines.mapTo(HashSet()) { it.uppercase() }

        return streamAllVehiclePositions().map { result ->
            result.map { positions ->
                positions.filter { position ->
                    strongLines.contains(position.lineName.uppercase())
                }
            }
        }
    }

    private fun createVehiclePositionsFlow(): Flow<Result<List<SimpleVehiclePosition>>> {
        return channelFlow {
            httpClient.sse(
                urlString = getVehiclePositionsStreamUrl(),
                request = { header(HttpHeaders.Accept, "text/event-stream") }
            ) {
                Log.i(TAG, "[SSE] Connection established")
                incoming.collect { event ->
                    if (event.event == "heartbeat") return@collect
                    val data = event.data ?: return@collect
                    runCatching { parsePositionsEventData(data) }
                        .onSuccess { positions ->
                            if (positions.isNotEmpty()) {
                                trySend(Result.success(positions))
                            }
                        }
                        .onFailure { trySend(Result.failure(it)) }
                }
            }
        }.retryWhen { _, attempt ->
            if (attempt >= 10) return@retryWhen false
            val backoff = (1_000L * (1L shl attempt.coerceAtMost(5).toInt())).coerceAtMost(30_000L)
            delay(backoff)
            true
        }
    }

    private fun parsePositionsEventData(data: String): List<SimpleVehiclePosition> {
        val eventJson = json.parseToJsonElement(data).jsonObject
        val payloadElement = eventJson["payload"] ?: eventJson

        // Try VehiclePositionsResponse wrapper first
        runCatching {
            json.decodeFromJsonElement(VehiclePositionsResponse.serializer(), payloadElement)
        }.getOrNull()?.let { parsed ->
            if (parsed.success) {
                val fromResponse = extractPositionsFromSiriData(parsed.data)
                if (fromResponse.isNotEmpty()) return fromResponse
            }
        }

        // Try SiriData directly
        runCatching {
            json.decodeFromJsonElement(SiriData.serializer(), payloadElement)
        }.getOrNull()?.let { siriData ->
            val fromSiri = extractPositionsFromSiriData(siriData)
            if (fromSiri.isNotEmpty()) return fromSiri
        }

        return emptyList()
    }

    private fun extractPositionsFromSiriData(data: SiriData?): List<SimpleVehiclePosition> {
        val activities = data?.siri?.serviceDelivery?.vehicleMonitoringDelivery
            ?.flatMap { it.vehicleActivity ?: emptyList() }
            ?: emptyList()
        return activities.mapNotNull { activity ->
            val journey = activity.monitoredVehicleJourney ?: return@mapNotNull null
            val location = journey.vehicleLocation ?: return@mapNotNull null
            val lat = location.latitude ?: return@mapNotNull null
            val lon = location.longitude ?: return@mapNotNull null
            val lineRef = journey.lineRef?.value ?: return@mapNotNull null
            val vehicleId = activity.vehicleMonitoringRef?.value ?: return@mapNotNull null
            val lineName = extractLineNameFromRef(lineRef).trim()
            if (lineName.isBlank()) return@mapNotNull null
            if (!isValidLineName(lineName)) return@mapNotNull null

            SimpleVehiclePosition(
                vehicleId = vehicleId,
                lineName = lineName,
                latitude = lat,
                longitude = lon,
                bearing = journey.bearing,
                destinationName = journey.destinationName?.firstOrNull()?.value,
                direction = journey.directionRef?.value
            )
        }
    }

    private fun extractLineNameFromRef(lineRef: String): String {
        // The line is in the LAST "::" segment: e.g. "ActIV:Line::T7:SYTRAL" and the
        // double-prefixed "Interpolated:Line::ActIV:Line::T7:SYTRAL" both yield "T7".
        val lastSegment = lineRef.split("::").last()
        val colonIndex = lastSegment.indexOf(":")
        if (colonIndex > 0) return lastSegment.take(colonIndex)
        return lastSegment
    }

    // Precompiled once instead of recompiling every regex for every vehicle on every SSE event.
    private val compiledLineNameRegexes: List<Regex> by lazy {
        rules.lineNameRegexes.mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    private fun isValidLineName(lineName: String): Boolean {
        val upper = lineName.trim().uppercase()
        if (upper.isBlank()) return false
        if (rules.strongLines.any { it.equals(upper, ignoreCase = true) }) return true
        return compiledLineNameRegexes.any { upper.matches(it) }
    }
}
