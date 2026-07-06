package eu.dotshell.massilia.specific

import eu.dotshell.massilia.generic.data.models.geojson.FeatureCollection
import eu.dotshell.massilia.generic.data.models.geojson.StopCollection
import eu.dotshell.massilia.generic.data.network.transport.TransportApi
import eu.dotshell.massilia.generic.data.network.transport.TransportLineService
import eu.dotshell.massilia.generic.data.network.transport.TransportLinesQuery
import eu.dotshell.massilia.generic.service.TransportServiceProvider

/**
 * Marseille RTM implementation of [TransportLineService].
 * Delegates to [TransportApi] (RtmLocalClient) which serves everything from bundled data.
 */
class TransportLineServiceImpl : TransportLineService {

    private val transportApi: TransportApi get() = TransportServiceProvider.getTransportApi()

    override suspend fun getMetroLines(): FeatureCollection {
        val all = transportApi.getLines(TransportLinesQuery.StrongLines)
        return all.copy(features = all.features.filter {
            it.properties.transportType.equals("METRO", ignoreCase = true)
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
        // RTM: navettes maritimes + ferry boat (GTFS route_type 4)
        return transportApi.getLines(TransportLinesQuery.LineByName("navigone"))
    }

    override suspend fun getTrambusLines(): FeatureCollection {
        // RTM equivalent of a "trambus" tier: the B1-B5 BHNS lines
        val allBus = transportApi.getLines(TransportLinesQuery.BusPage(startIndex = 0, count = 10000))
        return allBus.copy(features = allBus.features.filter {
            it.properties.lineName.matches(Regex("^B\\d$", RegexOption.IGNORE_CASE))
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
