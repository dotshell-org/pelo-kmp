package eu.dotshell.massilia.specific.data.local

import io.raptor.data.BinaryReader

/**
 * A single line trace (one per GTFS direction), points as [lon, lat] pairs
 * ready for GeoJSON MultiLineString coordinates.
 */
data class RtmLinePath(
    val directionId: Int,
    val points: List<List<Double>>
)

/**
 * A transit line as stored in lines.bin: display name, GTFS colors and
 * one geometry path per direction.
 */
data class RtmLine(
    val idInternal: Int,
    val name: String,
    val colorHex: String,      // GTFS route_color without '#', may be empty
    val textColorHex: String,  // GTFS route_text_color without '#', may be empty
    val gtfsRouteType: Int,    // raw GTFS route_type: 0 tram, 1 metro, 3 bus, 4 ferry
    val paths: List<RtmLinePath>
)

/**
 * Parser for the RLN2 binary format produced by raptor-gtfs-pipeline (`--traces`).
 *
 * Layout (little-endian, strings = u16 byte length + UTF-8):
 * ```
 * magic "RLN2" | u16 schemaVersion | u32 coordScale | u32 lineCount
 * per line: u32 idInternal | str name | str color | str textColor
 *           | u16 transportType | u16 pathCount
 * per path: u16 directionId | u32 pointCount
 *           | pointCount × i32 delta-encoded lon×scale (first absolute)
 *           | pointCount × i32 delta-encoded lat×scale (first absolute)
 * ```
 */
object RtmLinesParser {

    fun parse(bytes: ByteArray): List<RtmLine> {
        val reader = BinaryReader(bytes)
        reader.readMagic("RLN2")
        reader.readUInt16() // schema version
        val coordScale = reader.readUInt32().toDouble()
        val lineCount = reader.readUInt32()

        return List(lineCount) {
            val idInternal = reader.readUInt32()
            val name = readString(reader)
            val color = readString(reader)
            val textColor = readString(reader)
            val transportType = reader.readUInt16()
            val pathCount = reader.readUInt16()

            val paths = List(pathCount) {
                val directionId = reader.readUInt16()
                val pointCount = reader.readUInt32()
                val xs = readDeltaInts(reader, pointCount)
                val ys = readDeltaInts(reader, pointCount)
                RtmLinePath(
                    directionId = directionId,
                    points = List(pointCount) { i ->
                        listOf(xs[i] / coordScale, ys[i] / coordScale)
                    }
                )
            }

            RtmLine(idInternal, name, color, textColor, transportType, paths)
        }
    }

    private fun readString(reader: BinaryReader): String {
        val length = reader.readUInt16()
        return reader.readUTF8(length)
    }

    private fun readDeltaInts(reader: BinaryReader, count: Int): IntArray {
        val out = IntArray(count)
        var current = 0
        for (i in 0 until count) {
            current += reader.readInt32()
            out[i] = current
        }
        return out
    }
}
