package eu.dotshell.pelo.specific

import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.geojson.StopCollection
import eu.dotshell.pelo.generic.data.models.geojson.Feature
import eu.dotshell.pelo.generic.data.network.transport.TransportApi
import eu.dotshell.pelo.generic.data.network.transport.TransportLineService
import eu.dotshell.pelo.generic.data.network.transport.TransportLinesQuery
import eu.dotshell.pelo.generic.service.TransportServiceProvider

/**
 * Lyon-specific implementation of [TransportLineService].
 * Multiplatform: delegates to [TransportApi] (LyonKtorClient) instead of Retrofit.
 */
class TransportLineServiceImpl : TransportLineService {

    private val transportApi: TransportApi get() = TransportServiceProvider.getTransportApi()

    override suspend fun getMetroLines(): FeatureCollection {
        val all = transportApi.getLines(TransportLinesQuery.StrongLines)
        return all.copy(features = all.features.filter {
            it.properties.transportType.equals("METRO", ignoreCase = true)
                    || it.properties.lineName.uppercase() in listOf("A", "B", "C", "D")
        })
    }

    override suspend fun getTramLines(): FeatureCollection {
        val all = transportApi.getLines(TransportLinesQuery.StrongLines)
        return all.copy(features = all.features.filter {
            it.properties.transportType.equals("TRAM", ignoreCase = true)
        })
    }

    override suspend fun getBusLines(): FeatureCollection {
        return transportApi.getLines(TransportLinesQuery.BusPage(startIndex = 0, count = 10000))
    }

    override suspend fun getBusLineByName(lineName: String): FeatureCollection {
        return transportApi.getLines(TransportLinesQuery.LineByName(lineName))
    }

    override suspend fun getNavigoneLines(): FeatureCollection {
        return transportApi.getLines(TransportLinesQuery.LineByName("navigone"))
    }

    override suspend fun getTrambusLines(): FeatureCollection {
        // Trambus lines start with "TB"
        val allBus = transportApi.getLines(TransportLinesQuery.BusPage(startIndex = 0, count = 1000))
        return allBus.copy(features = allBus.features.filter {
            it.properties.lineName.startsWith("TB", ignoreCase = true)
        })
    }

    override suspend fun getTransportStops(): StopCollection {
        return transportApi.getTransportStops()
    }

    override suspend fun getStrongLines(): FeatureCollection {
        return transportApi.getLines(TransportLinesQuery.StrongLines)
    }

    override suspend fun getLineGeometry(lineName: String): FeatureCollection {
        return transportApi.getLines(TransportLinesQuery.LineByName(lineName))
    }

    override suspend fun getLinesByType(type: String): FeatureCollection {
        return when (type.lowercase()) {
            "metro" -> getMetroLines()
            "tram" -> getTramLines()
            "bus" -> getBusLines()
            "navigone" -> getNavigoneLines()
            "trambus" -> getTrambusLines()
            else -> throw IllegalArgumentException("Unknown transport type: $type")
        }
    }
}
