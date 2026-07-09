package eu.dotshell.pelo.specific.data.local

import eu.dotshell.pelo.generic.data.models.geojson.Feature
import eu.dotshell.pelo.generic.data.models.geojson.FeatureCollection
import eu.dotshell.pelo.generic.data.models.lines.MultiLineStringGeometry
import eu.dotshell.pelo.generic.data.models.lines.TransportLineProperties
import kotlinx.serialization.json.Json
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.readShortLe
import kotlinx.io.readIntLe

object LyonLinesParser {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun parse(bytes: ByteArray): FeatureCollection {
        val buffer = Buffer()
        buffer.write(bytes)
        
        // Read Magic (LYL1)
        val magic = buffer.readString(4)
        if (magic != "LYL1") {
            throw IllegalArgumentException("Invalid magic: \$magic")
        }
        
        val schemaVersion = buffer.readShortLe().toInt()
        val coordScale = buffer.readIntLe().toDouble()
        val lineCount = buffer.readIntLe()
        
        val features = mutableListOf<Feature>()
        
        for (i in 0 until lineCount) {
            val propsLength = buffer.readIntLe()
            val propsJson = buffer.readString(propsLength.toLong())
            val properties = json.decodeFromString<TransportLineProperties>(propsJson)
            
            val pathCount = buffer.readShortLe().toInt()
            val coordinates = mutableListOf<List<List<Double>>>()
            
            for (p in 0 until pathCount) {
                val pointCount = buffer.readIntLe()
                val pathCoords = mutableListOf<List<Double>>()
                
                // read lons
                var currLon = 0
                val lons = IntArray(pointCount)
                for (pt in 0 until pointCount) {
                    currLon += buffer.readIntLe()
                    lons[pt] = currLon
                }
                
                // read lats
                var currLat = 0
                val lats = IntArray(pointCount)
                for (pt in 0 until pointCount) {
                    currLat += buffer.readIntLe()
                    lats[pt] = currLat
                }
                
                for (pt in 0 until pointCount) {
                    pathCoords.add(listOf(lons[pt] / coordScale, lats[pt] / coordScale))
                }
                
                coordinates.add(pathCoords)
            }
            
            features.add(
                Feature(
                    type = "Feature",
                    id = "local_\$i",
                    multiLineStringGeometry = MultiLineStringGeometry(
                        type = "MultiLineString",
                        coordinates = coordinates
                    ),
                    geometryName = null,
                    properties = properties,
                    bbox = null
                )
            )
        }
        
        return FeatureCollection(
            type = "FeatureCollection",
            features = features,
            totalFeatures = features.size,
            numberMatched = features.size,
            numberReturned = features.size
        )
    }
}
